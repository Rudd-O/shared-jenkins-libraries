def automockrpms_all(releases) {
    def parallelized = funcs.combo(
        {
            return {
				def n = "RPMs for Fedora ${it[0]}"
				if (it[0].startsWith("q")) {
					n = "RPMs for Qubes OS ${it[0]}"
				}
                stage(n) {
                    script {
                        funcs.automockrpms(it[0])
                    }
                }
            }
        },
        [releases]
    )
    parallelized.failFast = true
    return parallelized
}

def autouploadrpms(myRelease) {
	sh("/var/lib/jenkins/userContent/upload-deliverables out/*")
}

def call(Closure checkout_step = null, Closure srpm_step = null, srpm_deps = null, Closure integration_step = null, Closure test_step = null) {
	pipeline {

		agent { label 'master' }

//		triggers {
//			pollSCM('H H * * *')
//		}

		options {
			skipDefaultCheckout()
			buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '1'))
			copyArtifactPermission('/zfs-fedora-installer/*')
		}

		parameters {
			string defaultValue: '', description: "Which releases to build for (empty means the job's default).", name: 'RELEASE', trim: true
		}

		stages {
			stage('Begin') {
				steps {
					script{
						env.RELEASE = params.RELEASE
					}
					script {
						announceBeginning()
						funcs.durable()
					}
				}
			}
			stage('Checkout') {
				steps {
					dir('out') {
						deleteDir()
					}
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
						if (params.RELEASE == '') {
							env.RELEASE = funcs.loadParameter('RELEASE', '30')
						}
						env.PUBLISH_TO_REPO = funcs.loadParameter('PUBLISH_TO_REPO', '')
						if (checkout_step != null) {
							println "Executing custom checkout step."
							println checkout_step
							checkout_step()
						}
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
				agent { label 'mock' }
				stages {
					stage('Deps') {
						steps {
							script {
								funcs.dnfInstall([
									'rpm-build',
									'which',
									'pypipackage-to-srpm',
									'shyaml',
									'python3-pytest',
									'python3',
									'python3-build',
									'python3-setuptools',
									'python3-pyyaml',
									'python3-mypy',
									'python3-py2pack',
									'python3-wheel',
									'python3-tox-current-env',
									'tox',
									'poetry',
									'golang',
									'make',
									'autoconf',
									'automake',
									'wget',
									'mock',
									'distribution-gpg-keys',
								])
								if (srpm_deps != null) {
									echo "Installing additional dependencies ${srpm_deps}."
									funcs.dnfInstall(srpm_deps)
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
									sh "rm -f xunit.xml"
									if (test_step != null) {
										dir('src') {
											test_step()
										}
									} else {
										dir('src') {
											sh '''
											set -e
											if grep -q ^mypy: Makefile ; then
												make mypy
											elif test -f mypy.ini ; then
												pkgname=
												if grep -q ^name setup.cfg ; then
													pkgname=$(cat setup.cfg | grep ^name | head -1 | cut -d = -f 2)
												elif test -f setup.py ; then
													pkgname=$(python3 setup.py --name)
												fi
												if [ -n "$pkgname" ] ; then
													MYPYPATH=lib:src mypy -p $pkgname
												fi
											fi
											if test -f setup.py -o -f setup.cfg ; then
												rm -f ../xunit.xml
												pytest --junit-xml=../xunit.xml -o junit_logging=all
											fi
											'''
										}
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
					stage('SRPM') {
						steps {
							script {
								if (srpm_step != null) {
									println "Executing custom SRPM step"
									println srpm_step
									srpm_step()
								} else {
									dir('src') {
										// The Makefile.builder signifies we have to make an SRPM
										// using make, and ignore setup.py, because this is a
										// Qubes OS builder-powered project.
										if (fileExists('setup.cfg') && !fileExists('setup.py')) {
											sh '''
												set -e
												rm -rf build dist
												python=python3
												$python -m build --sdist
												rpmbuild --define "_srcrpmdir ./" --define "_sourcedir dist/" -bs *.spec
												rm -rf build dist *.egg-info
											'''
										} else if (fileExists('setup.py') && !fileExists('Makefile.builder')) {
											sh '''
												set -e
												rm -rf build dist
											relnum=$(rpm -qa 'fedora-release*' --queryformat '%{version}\n' | head -1)
												if head -1 setup.py | grep -q python3 ; then
													python=python3
												elif head -1 setup.py | grep -q python2 ; then
													python=python2
												elif [ "$relnum" > 28 ] ; then
													python=python3
												else
													python=python2
												fi
												$python setup.py sdist
												specs=$(ls -1 *.spec || true)
												if [ "$specs" != "" ] ; then
													rpmbuild --define "_srcrpmdir ./" --define "_sourcedir dist/" -bs *.spec
												else
													$python setup.py bdist_rpm --spec-only
													rpmbuild --define "_srcrpmdir ./" --define "_sourcedir dist/" -bs dist/*.spec
												fi
												rm -rf build dist *.egg-info
											'''
										} else if (fileExists('pypipackage-to-srpm.yaml') && sh(
											script: "ls *.spec || true",
											returnStdout: true
										).trim().contains("spec")) {
											funcs.downloadPypiPackageToSrpmSource()
											sh '''
												rpmbuild --define "_srcrpmdir ./" --define "_sourcedir ./" -bs *.spec
												rm -rf build dist *.egg-info
											'''
										} else if (fileExists('pypipackage-to-srpm.yaml')) {
											script {
											def basename = funcs.downloadPypiPackageToSrpmSource()
											funcs.buildDownloadedPypiPackage(basename)
											}
										} else {
											sh 'make srpm'
										}
									}
								}
							}
						}
					}
					stage('RPMs') {
						steps {
							dir('out') {
								deleteDir()
							}
							script {
								println "Building RPMs for releases ${env.RELEASE}"
								parallel automockrpms_all(env.RELEASE.split(' '))
							}
						}
					}
					stage('Stash') {
						steps {
							stash includes: 'out/*/*.rpm', name: 'out'
						}
					}
				}
			}
			stage('Unstash') {
				steps {
					dir("out") {
						deleteDir()
					}
					unstash 'out'
				}
			}
			stage('Sign') {
				steps {
					sh '''#!/bin/bash -e
					olddir="$PWD"
					cd "$JENKINS_HOME"
					PRIVKEY=
					if test -d rpm-sign ; then
					  for f in rpm-sign/RPM-GPG-KEY-*.private.asc ; do
					    if test -f "$f" ; then
					      PRIVKEY="$PWD"/"$f"
					      break
					    fi
					  done
					fi
					if [ "$PRIVKEY" == "" ] ; then
					  >&2 echo error: could not find PRIVKEY in rpm-sign/, aborting
					  exit 40
					fi
					cd "$olddir"
					sign() {
					  if [ -z "$tmpdir" ] ; then
					    tmpdir=$(mktemp -d)
					    trap 'echo rm -rf "$tmpdir"' EXIT
					  fi
					  export GNUPGHOME="$tmpdir"
					  errout=$(gpg2 --import < "$PRIVKEY" 2>&1) || {
					    ret=$?
					    >&2 echo "$errout"
					    return $ret
					  }
					  GPG_NAME=$( gpg2 --list-keys | egrep '^      ([ABCDEF0-9])*$' | head -1 )
					  >&2 echo "Signing package $1."
					  errout=$(rpm --addsign \
					    --define "%_gpg_name $GPG_NAME" \
					    --define '_signature gpg' \
					    --define '_gpgbin /usr/bin/gpg2' \
					    --define '__gpg_sign_cmd %{__gpg} gpg --force-v3-sigs --batch --verbose --no-armor --no-secmem-warning -u "%{_gpg_name}" -sbo %{__signature_filename} --digest-algo sha256 %{__plaintext_filename}' \
					    "$1" 2>&1) || {
					    ret=$?
					    >&2 echo "$errout"
					    return $ret
					  }
					  rpm -K "$1" || true
					  rpm -q --qf '%{SIGPGP:pgpsig} %{SIGGPG:pgpsig}\n' -p "$1"
					}
					for rpm in out/*/*.rpm ; do
					  sign "$rpm"
					done
					'''
				}
			}
			stage('Archive') {
				steps {
					archiveArtifacts artifacts: 'out/*/*.rpm', fingerprint: true
				}
			}
			stage('Integration') {
				when {
					expression {
						return integration_step != null
					}
				}
				steps {
					script {
						integration_step()
					}
				}
			}
			stage('Publish') {
				when {
					expression {
						return env.BRANCH_NAME == "master" || env.BRANCH_NAME.startsWith("unstable-") || env.PUBLISH_TO_REPO != ""
					}
				}
				steps {
					lock('autouploadrpms') {
						script {
							autouploadrpms()
						}
					}
				}
			}
		}
		post {
			always {
				script {
					announceEnd(currentBuild.currentResult)
				}
			}
		}
	}
}
