def RELEASE = funcs.loadParameter('RELEASE', '30')

def call() {
	pipeline {

		agent { label 'master' }

		options {
			disableConcurrentBuilds()
			skipDefaultCheckout()
			buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '1'))
		}

		parameters {
			string defaultValue: '', description: "Which Fedora releases to build for (empty means the job's default).", name: 'RELEASE', trim: true
		}

		stages {
			stage('Begin') {
				steps {
					script{
						if (params.RELEASE == '') {
							env.RELEASE = funcs.loadParameter('RELEASE', '30')
						} else {
							env.RELEASE = params.RELEASE
						}
					}
					script {
						funcs.announceBeginning()
						funcs.durable()
					}
				}
			}
			stage('Create repository') {
				steps {
					lock('autouploadfedorarpms') {
						script {
							env.RELEASE.split(' ').each {
								sh "/var/lib/jenkins/userContent/upload-deliverables --new ${it}"
							}
						}
					}
				}
			}
		}
		post {
			always {
				script {
					funcs.announceEnd(currentBuild.currentResult)
				}
			}
		}
	}
}
