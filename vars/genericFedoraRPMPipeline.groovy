def shellLib() {
	return '''
function multail() {
  local ppid
  local pids
  local basename
  local pidfile
  local sedpid
  local tailpid
  pids=
  ppid="$1"
  shift
  while [ "$1" != "" ] ; do
    basename=`basename "$1"`
    pidfile=`mktemp`
    ( tail -F "$1" --pid="$ppid" & echo $! 1>&3 ) 3> "$pidfile" | sed -u "s/^/$basename: /" >&2 &
    sedpid=$!
    tailpid=$(<"$pidfile")
    rm -f "$pidfile"
    pids="$tailpid $sedpid $pids"
    shift
  done
  echo "$pids"
}

function suspendshellverbose() {
    local oldopts
    local retval
    oldopts=$( echo $- | grep x )
    set +x
    retval=0
    "$@" || retval=$?
    if [ -n "$oldopts" ]; then set -x ; else set +x ; fi
    return $retval
}

function mocklock() {
    local release="$1"
    shift
    local arch="$1"
    shift

    local basedir=~/.mock
    mkdir -p "$basedir"
    local jail="fedora-$release-$arch-generic"
    local cfg="$basedir/$jail.cfg"
    local root="$basedir/jail/$jail"
    local cache_topdir="$basedir"/cache

    local tmpcfg=$(mktemp ~/.mock/XXXXXX)
    cat > "$tmpcfg" <<EOF
config_opts['basedir'] = '$basedir'
config_opts['cache_topdir'] = '$cache_topdir'
config_opts['root'] = '$root'
config_opts['target_arch'] = '$arch'
config_opts['legal_host_arches'] = ('$arch',)
# rpmdevtools was installed to support rpmdev-bumpspec below
# python-setuptools was installed to allow for python builds
config_opts['chroot_setup_cmd'] = 'install @buildsys-build autoconf automake gettext-devel libtool git rpmdevtools python-setuptools python3-setuptools /usr/bin/python'
config_opts['extra_chroot_dirs'] = ['/run/lock']
config_opts['dist'] = 'fc$release'  # only useful for --resultdir variable subst
config_opts['releasever'] = '$release'
config_opts['nosync'] = True
config_opts['nosync_force'] = True
config_opts['plugin_conf']['ccache_enable'] = False
config_opts['use_nspawn'] = True
config_opts['cleanup_on_success'] = False
config_opts['cleanup_on_failure'] = False
config_opts['package_manager'] = 'dnf'
config_opts['rpmbuild_networking'] = False
config_opts['plugin_conf']['root_cache_enable'] = True

config_opts['yum.conf'] = """
[main]
keepcache=1
cachedir=/var/cache/yum
debuglevel=9
reposdir=/dev/null
logfile=/var/log/yum.log
retries=20
obsoletes=1
gpgcheck=1
assumeyes=1
syslog_ident=mock
syslog_device=
install_weak_deps=0
metadata_expire=0
mdpolicy=group:primary
best=1

# repos

[fedora]
name=fedora
mirrorlist=http://mirrors.fedoraproject.org/mirrorlist?repo=fedora-\\$releasever&arch=\\$basearch
failovermethod=priority
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-fedora-\\$releasever-primary
gpgcheck=1

[updates]
name=updates
mirrorlist=http://mirrors.fedoraproject.org/mirrorlist?repo=updates-released-f\\$releasever&arch=\\$basearch
failovermethod=priority
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-fedora-\\$releasever-primary
gpgcheck=1

[rpmfusion-free]
name=RPM Fusion for Fedora \\$releasever - Free
#baseurl=http://download1.rpmfusion.org/free/fedora/releases/\\$releasever/Everything/\\$basearch/os/
mirrorlist=http://mirrors.rpmfusion.org/mirrorlist?repo=free-fedora-\\$releasever&arch=\\$basearch
enabled=1
metadata_expire=7d
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-rpmfusion-free-fedora-\\$releasever

[rpmfusion-free-updates]
name=RPM Fusion for Fedora \\$releasever - Free - Updates
#baseurl=http://download1.rpmfusion.org/free/fedora/updates/\\$releasever/\\$basearch/
mirrorlist=http://mirrors.rpmfusion.org/mirrorlist?repo=free-fedora-updates-released-\\$releasever&arch=\\$basearch
enabled=1
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-rpmfusion-free-fedora-\\$releasever

[rpmfusion-nonfree]
name=RPM Fusion for Fedora \\$releasever - Nonfree
#baseurl=http://download1.rpmfusion.org/nonfree/fedora/releases/\\$releasever/Everything/\\$basearch/os/
mirrorlist=http://mirrors.rpmfusion.org/mirrorlist?repo=nonfree-fedora-\\$releasever&arch=\\$basearch
enabled=1
metadata_expire=7d
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-rpmfusion-nonfree-fedora-\\$releasever

[rpmfusion-nonfree-updates]
name=RPM Fusion for Fedora \\$releasever - Nonfree - Updates
#baseurl=http://download1.rpmfusion.org/nonfree/fedora/updates/\\$releasever/\\$basearch/
mirrorlist=http://mirrors.rpmfusion.org/mirrorlist?repo=nonfree-fedora-updates-released-\\$releasever&arch=\\$basearch
enabled=1
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-rpmfusion-nonfree-fedora-\\$releasever

[dragonfear]
name=dragonfear
baseurl=http://dnf-updates.dragonfear/fc\\$releasever/
gpgcheck=0
metadata_expire=30
"""
EOF

    if cmp "$cfg" "$tmpcfg" ; then
        rm -f "$tmpcfg"
    else
        mv -f "$tmpcfg" "$cfg"
        echo Configured "$cfg" as follows >&2
        echo =============================== >&2
        cat "$cfg" >&2
        echo =============================== >&2
    fi

    jaillock="$cfg".lock

    flock "$jaillock" bash -c '
        rpm -q mock nosync >/dev/null 2>&1 || {
            echo Initializing local packages mock nosync.
            sudo dnf install -qy mock nosync || exit $?
        }
    ' >&2

    flock "$jaillock" /usr/bin/mock -r "$cfg" "$@"
}

function mockfedorarpms() {
# build $4- for fedora release number $1 arch $2 and deposit in $3
  local tailpids
  local relpath
  local retval
  local release
  local definebuildnumber

  definebuildnumber='no_build_number 1'
  if [ "$1" == "--define_build_number" ]
  then
     test -n "$BUILD_NUMBER"
     definebuildnumber="build_number $BUILD_NUMBER"
     shift
  fi

  relpath=`python -c 'import os, sys ; print os.path.relpath(sys.argv[1])' "$3"`

  release="$1"
  target="$2"
  shift
  shift
  shift

  mocklock "$release" "$target" \
    --unpriv \
    --define "$definebuildnumber" \
    --resultdir=./"$relpath"/ --rebuild "$@" &
  pid=$!

  tailpids=`multail "$pid" "$relpath"/build.log`

  wait "$pid" || retval=$?

  for tailpid in $tailpids ; do
      while kill -0 $tailpid >/dev/null 2>&1 ; do
          sleep 0.1
      done
  done

  return $retval
}

function automockfedorarpms() {
  for file in src/*.src.rpm ; do
    test -f "$file" || { echo "$file is not a source RPM" >&2 ; return 19 ; }
  done
  mkdir -p out/$2
  suspendshellverbose mockfedorarpms $1 $2 $3 out/$2/ src/*.src.rpm
  # mv -f src/*.src.rpm out/
}

function autouploadrpms() {
  /var/lib/jenkins/userContent/upload-deliverables out/*/*.rpm
}
'''
}

def listify(aShellString) {
  result = aShellString.readLines()
  if (result.size() == 1 && result[0] == "") {
    result = []
  }
  return result
}

def autolistrpms() {
  return listify(sh(
    script: "ls -1 out/*/*.rpm 2>/dev/null || true",
    returnStdout: true
  ).trim())
}

def automockfedorarpms(myRelease) {
	if (myRelease.indexOf(":") != -1) {
		stuff = myRelease.split(':')
		myRelease = stuff[0]
		myArch = stuff[1]
	} else {
		myArch = "x86_64"
	}
	sh("set -e\n" + shellLib() + "\nautomockfedorarpms --define_build_number ${myRelease} ${myArch}")
}

def automockfedorarpms_all(releases) {
	funcs.combo({
		def myRelease = it[0]
			return {
				stage("RPMs for Fedora ${myRelease}") {
					script {
						automockfedorarpms(myRelease)
					}
				}
			}
		}, [
			releases,
		])
}

def autouploadfedorarpms(myRelease) {
	sh("set -e\n" + shellLib() + "\nautouploadrpms")
}

def RELEASE = funcs.loadParameter('parameters.groovy', 'RELEASE', '30')

def call(checkout_step = null, srpm_step = null, srpm_deps = null, integration_step = null) {
	pipeline {

		agent { label 'master' }

		triggers {
			pollSCM('H H * * *')
		}

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
							env.RELEASE = funcs.loadParameter('parameters.groovy', 'RELEASE', '30')
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
						if (checkout_step != null) {
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
										pushd "$a" >/dev/null
										git rev-parse --short HEAD
										popd >/dev/null
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
									'pypipackage-to-srpm',
									'shyaml',
									'python2-nose',
									'python3-nose',
									'python2',
									'python3',
									'python2-setuptools',
									'python3-setuptools',
									'python3-setuptools_scm',
									'python3-setuptools_scm_git_archive',
									'python2-pyyaml',
									'python3-PyYAML',
									'python3-pyxdg', // Some tests need this.
									'golang',
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
									dir('src') {
										sh '''
										set -e
										rm -f ../xunit.xml
										if test -f setup.py ; then
											relnum=$(rpm -q fedora-release --queryformat '%{version}')
											if head -1 setup.py | grep -q python3 ; then
												python=nosetests-3
											elif head -1 setup.py | grep -q python2 ; then
												python=nosetests-2
											elif [ "$relnum" > 28 ] ; then
												python=nosetests-3
											else
												python=nosetests-2
											fi
											if [ $(find . -name '*_test.py' -o -name 'test_*.py' | wc -l) != 0 ] ; then
												$python -v --with-xunit --xunit-file=../xunit.xml
											fi
										fi
										'''
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
									println srpm_step
									srpm_step()
								} else {
									dir('src') {
										// The Makefile.builder signifies we have to make an SRPM
										// using make, and ignore setup.py, because this is a
										// Qubes OS builder-powered project.
										if (fileExists('setup.py') && !fileExists('Makefile.builder')) {
											sh '''
												set -e
												rm -rf build dist
												relnum=$(rpm -q fedora-release --queryformat '%{version}')
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
												$python setup.py bdist_rpm --spec-only
												rpmbuild --define "_srcrpmdir ./" --define "_sourcedir dist/" -bs dist/*.spec
												rm -rf build dist
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
								println "Building RPMs for Fedora releases ${env.RELEASE}"
								parallel automockfedorarpms_all(env.RELEASE.split(' '))
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
						return env.BRANCH_NAME == "master"
					}
				}
				steps {
					lock('autouploadfedorarpms') {
						script {
							autouploadfedorarpms()
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
