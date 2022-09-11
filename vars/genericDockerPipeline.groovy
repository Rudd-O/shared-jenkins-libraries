def call(build_deps = null, test_step = null) {
	pipeline {

		agent { label 'master' }

		options {
			skipDefaultCheckout()
			buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '1'))
		}

		stages {
			stage('Begin') {
				steps {
					script {
						funcs.announceBeginning()
						funcs.durable()
					}
				}
			}
			stage('Checkout') {
				steps {
					dir('src') {
						checkout([
							$class: 'GitSCM',
							branches: scm.branches,
							extensions: [
								[$class: 'CleanBeforeCheckout'],
								[
									$class: 'SubmoduleOption',
									disableSubmodules: false,
									parentCredentials: false,
									recursiveSubmodules: true,
									trackingSubmodules: false
								],
							],
							userRemoteConfigs: scm.userRemoteConfigs
						])
					}
					script {
						env.BUILD_DATE = sh(
							script: "date +%Y.%m.%d",
							returnStdout: true
						).trim()
						env.BUILD_SRC_SHORT_COMMIT = sh(
							script: "cd src && git rev-parse --short HEAD",
							returnStdout: true
						).trim()
						env.BUILD_UPSTREAM_SHORT_COMMIT = sh(
							script: '''
								for a in upstream/*
								do
									if test -d "$a" && test -d "$a"/.git
									then
										oldpwd=$(pwd)
										cd "$a"
										git rev-parse --short HEAD
										cd "$oldpwd"
									fi
								done
							''',
							returnStdout: true
						).trim()
					}
					updateBuildNumberDisplayName()
					dir('src') {
						stash includes: '**', name: 'source', useDefaultExcludes: false
					}
				}
			}
			stage('Dispatch') {
				agent { label 'podman' }
				stages {
					stage('Deps') {
						steps {
							script {
								funcs.dnfInstall(['podman', 'buildah', 'make', 'rsync'])
								if (build_deps != null) {
									echo "Installing additional dependencies ${build_deps}."
									funcs.dnfInstall(build_deps)
								} else {
									echo "No additional dependencies to install."
								}
							}
						}
					}
					stage('Unstash') {
						steps {
							dir('src') {
								deleteDir()
							}
							dir('src') {
								unstash 'source'
							}
						}
					}
					stage('Test') {
						steps {
							script {
								try {
									if (test_step != null) {
										println test_step
										test_step()
									}
								} finally {
									if (fileExists("xunit.xml")) {
										junit 'xunit.xml'
									} else {
										println "xunit.xml does not exist -- cannot save xunit results."
									}
								}
							}
						}
					}
					stage('Package') {
						steps {
							script {
								dir('src') {
									sh 'BUILDAH_ISOLATION=chroot make docker IMAGE_BRANCH=$BRANCH_NAME'
									tags = sh(
										script: "make -s docker-tags IMAGE_BRANCH=$BRANCH_NAME",
										returnStdout: true
									).trim()
									if (tags.indexOf("-dirty") != -1) {
										println "No dirty containers allowed (${tags})."
										sh "git status"
										sh "exit 1"
									}
									env.DOCKER_TAGS = tags
									println "Discovered tags: ${tags}"
								}
							}
						}
					}
					stage('Publish') {
						when {
							expression { env.DOCKER_TAGS != "" }
						}
						steps {
							script {
								splitTags = env.DOCKER_TAGS.split("(\n| )")
								pushImages(splitTags)
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
