def call(Closure checkout_step = null, Closure srpm_step = null, srpm_deps = null, Closure integration_step = null, Closure test_step = null) {
	pipeline {
		agent none
		options {
			skipDefaultCheckout()
			buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '1'))
			copyArtifactPermission('/zfs-fedora-installer/*')
			parallelsAlwaysFailFast()
		}
		parameters {
			string defaultValue: 'default', description: "Which Fedora releases[:architectures] to build for, separated by spaces; 'default' means the job's default.", name: 'FEDORA_RELEASES', trim: true
			string defaultValue: 'default', description: "Which Qubes OS releases[:architectures] to build for, separated by spaces; 'default' means the job's default.", name: 'QUBES_RELEASES', trim: true
		}
		stages {
			stage("Prep on master") {
				agent { label 'master' }
				stages {
					stage('Begin') {
						steps {
							copyArtifacts(
								projectName: 'get-fedora-releases',
								selector: upstream(fallbackToLastSuccessful: true)
							)
							dir("releases") {
								script {
									env.DEFAULT_FEDORA_RELEASES = readFile('fedora').trim()
									env.DEFAULT_QUBES_RELEASES = "" // We don't build for Qubes OS by default.
								}
								deleteDir()
							}
							script {
							//	announceBeginning()
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
								script {
									if (checkout_step != null) {
										println "Executing custom checkout step."
										checkout_step()
									}
								}
							}
							script {
								env.PUBLISH_TO_REPO = funcs.loadParameter('PUBLISH_TO_REPO', '')
								if (params.FEDORA_RELEASES != 'default') {
									env.FEDORA_RELEASES = params.FEDORA_RELEASES
								} else {
									env.FEDORA_RELEASES = funcs.loadParameter('FEDORA_RELEASES', env.DEFAULT_FEDORA_RELEASES)
								}
								if (params.QUBES_RELEASES != 'default') {
									env.QUBES_RELEASES = params.QUBES_RELEASES
								} else {
									env.QUBES_RELEASES = funcs.loadParameter('QUBES_RELEASES', env.DEFAULT_QUBES_RELEASES)
								}
								env.BUILD_DATE = sh(
									script: """
									#!/bin/bash
									date +%Y.%m.%d
									""".stripIndent().trim(),
									returnStdout: true,
									label: "Get date"
								).trim()
								env.BUILD_SRC_SHORT_COMMIT = sh(
									script: """
									#!/bin/bash
									cd src && git rev-parse --short HEAD
									""".stripIndent().trim(),
									returnStdout: true,
									label: "Get short commit"
								).trim()
								env.BUILD_UPSTREAM_SHORT_COMMIT = sh(
									script: '''
									#!/bin/bash -e
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
									'''.stripIndent().trim(),
									returnStdout: true,
									label: "Get upstreams' short commits"

								).trim()
							}
							updateBuildNumberDisplayName()
							stash includes: '**', name: 'source', useDefaultExcludes: false
						}
					}
				}
			}
			stage('Prep on slave') {
				agent { label 'mock' }
				stages {
					stage('Deps') {
						steps {
							script {
								env.GOPATH = "${env.WORKSPACE}/../../caches/go" 
        						env.PIP_CACHE_DIR="${env.WORKSPACE}/../../caches/pip"
								funcs.dnfInstall([
									'rpm-build',
									'which',
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
								fileOperations([fileDeleteOperation(includes: 'xunit.xml')])
								try {
									dir('src') {
										if (test_step != null) {
											test_step()
										} else {
											if (fileExists("Makefile") && readFile("Makefile").contains("mypy:")) {
												sh(
													script: "make mypy"
												)
											} else if (fileExists("mypy.ini")) {
												sh(
													script: '''
													    pkgname=
													    if grep -q ^name setup.cfg ; then
														    pkgname=$(cat setup.cfg | grep ^name | head -1 | cut -d = -f 2)
													    elif test -f setup.py ; then
														    pkgname=$(python3 setup.py --name)
													    fi
													    if [ -n "$pkgname" ] ; then
														    MYPYPATH=lib:src mypy -p $pkgname
													    fi
													''',
													label: "run mypy directly"
												)
											}
											if (fileExists("setup.py") || fileExists("setup.cfg")) {
												sh(
													script: '''
													pytest --junit-xml=../xunit.xml -o junit_logging=all
													''',
													label: "run pytest"
												)
											}
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
								dir('src') {
									if (srpm_step != null) {
										println "Executing custom SRPM step in directory ./src"
										srpm_step()
									} else if (fileExists('setup.cfg') && !fileExists('setup.py')) {
										sh(
											script: '''
											set -e
											rm -rf build dist
											python=python3
											$python -m build --sdist
											rpmbuild --define "_srcrpmdir ./" --define "_sourcedir dist/" -bs *.spec
											rm -rf build dist *.egg-info
											''',
											label: "Build SRPM with python -m build"
										)
									} else if (fileExists('setup.py') && !fileExists('Makefile.builder')) {
										// The Makefile.builder signifies we have to make an SRPM
										// using make, and ignore setup.py, because this is a
										// Qubes OS builder-powered project.
										sh(
											script: '''
											set -e
											rm -rf build dist
											python3 setup.py sdist
											specs=$(ls -1 *.spec || true)
											if [ "$specs" != "" ] ; then
												rpmbuild --define "_srcrpmdir ./" --define "_sourcedir dist/" -bs *.spec
											else
												python3 setup.py bdist_rpm --spec-only
												rpmbuild --define "_srcrpmdir ./" --define "_sourcedir dist/" -bs dist/*.spec
											fi
											rm -rf build dist *.egg-info
											''',
											label: "Build SRPM with python sdist/bdist_rpm"
										)
									} else if (fileExists('pypipackage-to-srpm.yaml') && sh(
										script: "ls *.spec || true",
										returnStdout: true,
										label: "Find specfiles"
									).trim().contains("spec")) {
										funcs.downloadPypiPackageToSrpmSource()
										SRPMStrategyMakeSRPM()()
									} else if (fileExists('pypipackage-to-srpm.yaml')) {
										sh 'echo "Standalone pypipackage-to-srpm builds are no longer supported.  Use specfile along YAML file instead." >&2 ; false'
									} else {
										SRPMStrategyMakeSRPM()()
									}
								}
							}
							stash includes: 'src/*.src.rpm', name: 'srpm'
						}
					}
				}
			}
			stage('Package') {
				agent none
				steps {
					script {
						parallelized = [:]
						[
							["Fedora", env.FEDORA_RELEASES],
							["Qubes OS", env.QUBES_RELEASES]
						].each { distroandrelease ->
							if (distroandrelease[1] != "") {
								distroandrelease[1].split(" ").each {
									parallelized["${distroandrelease[0]} ${it}"] = {
										node('mock') {
											deleteDir()
											unstash 'srpm'
											sh "pwd ; ls -lR src ; date"
											automock(distroandrelease[0], it)
											stash includes: 'out/*/*.rpm', name: "out-${distroandrelease[0]} ${it}"
										}
									}
								}
							}
						}
						parallel parallelized
						env.STASHES = parallelized.keySet().join("\n")
					}
				}
			}
			stage('Finish on master') {
				agent { label 'master' }
				stages{
					stage('Unstash') {
						steps {
							dir("out") {
								deleteDir()
							}
							script {
								env.STASHES.split("\n").each {
									unstash "out-${it}"
								}
							}
						}
					}
					stage('Sign') {
						steps {
							sh(
								script: '''
								#!/bin/bash -e
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
								GPG_NAME=$( gpg2 --list-keys | grep -E '^      ([ABCDEF0-9])*$' | head -1 )
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
								'''.stripIndent().trim(),
								label: "Sign RPMs."
							)
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
									sh(
										script: "/var/lib/jenkins/userContent/upload-deliverables out/*",
										label: "Upload deliverables"
									)
								}
							}
						}
					}
				}
			}
		}
		//post {
		//	always {
		//		script {
		//			announceEnd(currentBuild.currentResult)
		//		}
		//	}
		//}
	}
}
