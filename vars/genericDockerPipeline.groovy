def call(build_deps = null, test_step = null) {
	pipeline {

		agent { label 'master' }

		triggers {
			pollSCM('H H * * *')
		}

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
					stash includes: '**', name: 'source', useDefaultExcludes: false
				}
			}
			stage('Dispatch') {
				agent { label 'podman' }
				stages {
					stage('Deps') {
						steps {
							script {
								funcs.dnfInstall(['podman', 'buildah', 'make'])
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
							deleteDir()
							unstash 'source'
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
					stage('Docker tags') {
						steps {
							dir('src') {
								script {
									def tags = sh(
										script: "make -s docker-tags",
										returnStdout: true
									).trim().split("(\n| )")
									println "Discovered tags: ${tags}"
								}
							}
						}
					}
					stage('Docker build') {
						steps {
							dir('src') {
								sh 'echo BUILDAH_ISOLATION=rootless make docker'
							}
						}
					}
					//stage('Stash') {
					//	steps {
					//		stash includes: 'out/*/*.rpm', name: 'out'
					//	}
					//}
				}
			}
			//stage('Unstash') {
			//	steps {
			//		dir("out") {
			//			deleteDir()
			//		}
			//		unstash 'out'
			//	}
			//}
			//stage('Sign') {
			//	steps {
			//		sh '''#!/bin/bash -e
			//		olddir="$PWD"
			//		cd "$JENKINS_HOME"
			//		PRIVKEY=
			//		if test -d rpm-sign ; then
			//		  for f in rpm-sign/RPM-GPG-KEY-*.private.asc ; do
			//		    if test -f "$f" ; then
			//		      PRIVKEY="$PWD"/"$f"
			//		      break
			//		    fi
			//		  done
			//		fi
			//		if [ "$PRIVKEY" == "" ] ; then
			//		  >&2 echo error: could not find PRIVKEY in rpm-sign/, aborting
			//		  exit 40
			//		fi
			//		cd "$olddir"
			//		sign() {
			//		  if [ -z "$tmpdir" ] ; then
			//		    tmpdir=$(mktemp -d)
			//		    trap 'echo rm -rf "$tmpdir"' EXIT
			//		  fi
			//		  export GNUPGHOME="$tmpdir"
			//		  errout=$(gpg2 --import < "$PRIVKEY" 2>&1) || {
			//		    ret=$?
			//		    >&2 echo "$errout"
			//		    return $ret
			//		  }
			//		  GPG_NAME=$( gpg2 --list-keys | egrep '^      ([ABCDEF0-9])*$' | head -1 )
			//		  >&2 echo "Signing package $1."
			//		  errout=$(rpm --addsign \
			//		    --define "%_gpg_name $GPG_NAME" \
			//		    --define '_signature gpg' \
			//		    --define '_gpgbin /usr/bin/gpg2' \
			//		    --define '__gpg_sign_cmd %{__gpg} gpg --force-v3-sigs --batch --verbose --no-armor --no-secmem-warning -u "%{_gpg_name}" -sbo %{__signature_filename} --digest-algo sha256 %{__plaintext_filename}' \
			//		    "$1" 2>&1) || {
			//		    ret=$?
			//		    >&2 echo "$errout"
			//		    return $ret
			//		  }
			//		  rpm -K "$1" || true
			//		  rpm -q --qf '%{SIGPGP:pgpsig} %{SIGGPG:pgpsig}\n' -p "$1"
			//		}
			//		for rpm in out/*/*.rpm ; do
			//		  sign "$rpm"
			//		done
			//		'''
			//	}
			//}
			//stage('Archive') {
			//	steps {
			//		archiveArtifacts artifacts: 'out/*/*.rpm', fingerprint: true
			//	}
			//}
			//stage('Integration') {
			//	when {
			//		expression {
			//			return integration_step != null
			//		}
			//	}
			//	steps {
			//		script {
			//			integration_step()
			//		}
			//	}
			//}
			//stage('Publish') {
			//	when {
			//		expression {
			//			return env.BRANCH_NAME == "master"
			//		}
			//	}
			//	steps {
			//		lock('autouploadfedorarpms') {
			//			script {
			//				autouploadfedorarpms()
			//			}
			//		}
			//	}
			//}*/
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
