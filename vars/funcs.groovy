def combo(task, axes) {
    def tasks = [:]
    def comboEntry = []
    def comboBuilder
    comboBuilder = {
        def a, int level -> for ( entry in a[0] ) {
            comboEntry[level] = entry
            if (a.size() > 1) {
                comboBuilder(a.drop(1), level + 1)
            }
            else {
                tasks[comboEntry.join(" ")] = task(comboEntry.collect())
            }
        }
    }
    comboBuilder(axes, 0)
    tasks.sort { it.key }
    return tasks
}

def toInt(aNumber) {
    return aNumber.toInteger().intValue()
}

def wrapTag(aString, tag, style='') {
	if (style != '') {
		return "<" + tag + " style='" + groovy.xml.XmlUtil.escapeXml(style) + "'>" + aString + "</" + tag + ">"
	} else {
		return "<" + tag + ">" + aString + "</" + tag + ">"
	}
}

def wrapLi(aString) {
	return wrapTag(aString, "li")
}

def wrapPre(aString) {
	return wrapTag(aString, "pre")
}

def wrapKbd(aString) {
	return wrapTag(aString, "pre", 'display: inline')
}

def wrapUl(aString) {
	return wrapTag(aString, "ul")
}

def escapeXml(aString) {
    return groovy.xml.XmlUtil.escapeXml(aString)
}

def durable() {
    System.setProperty("org.jenkinsci.plugins.durabletask.BourneShellScript.HEARTBEAT_CHECK_INTERVAL", "86400")
}

String getrpmfield(String filename, String field) {
	String str = sh(
		returnStdout: true,
		script: """#!/bin/bash
			rpmspec -P ${filename} | grep ^${field}: | awk ' { print \$2 } ' | head -1
		"""
	).trim()
	return str
}

def getrpmfieldlist(String filename, String fieldPrefix) {
	ret = []
	for (i in [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]) {
		s = getrpmfield(filename, "${fieldPrefix}${i}")
		if (s == "") {
			break
		}
		ret.add(s)
	}
	return ret
}


def getrpmsources(String filename) {
	return getrpmfieldlist(filename, "Source")
}

def getrpmpatches(String filename) {
	return getrpmfieldlist(filename, "Patch")
}

def dnfInstall(deps) {
  sh """#!/bin/bash -xe
     (
         flock 9
         deps="${deps.join(' ')}"
         rpm -q \$deps || sudo dnf install --disablerepo='*qubes*' --disableplugin='*qubes*' -y \$deps
     ) 9> /tmp/\$USER-dnf-lock
     """
}

def aptInstall(deps) {
  sh """#!/bin/bash -e
     (
         flock 9
         deps="${deps.join(' ')}"
         dpkg-query -s \$deps >/dev/null || { sudo apt-get -q update && sudo apt-get -y install \$deps ; }
     ) 9> /tmp/\$USER-apt-lock
     """
}

def aptEnableSrc() {
  sh '''#!/bin/bash -xe
     (
         flock 9
         changed=0
         tmp=$(mktemp)
         cat /etc/apt/sources.list > "$tmp"
         sed -i 's/#deb-src/deb-src/' "$tmp"
         cmp /etc/apt/sources.list "$tmp" || {
           sudo tee /etc/apt/sources.list < "$tmp"
           changed=1
         }
         cat /etc/apt/sources.list > "$tmp"
         sed -E -i 's|debian main/([a-z]+) main|debian \\1 main|' "$tmp"
         cmp /etc/apt/sources.list "$tmp" || {
           sudo tee /etc/apt/sources.list < "$tmp"
           changed=1
         }
         if [ $changed = 1 ] ; then sudo apt-get -q update ; fi
     ) 9> /tmp/\$USER-apt-lock
     '''
}

def announceBeginning() {
    sh '''
       test -x /usr/local/bin/announce-build-result && f=/usr/local/bin/announce-build-result || test -f /var/lib/jenkins/userContent/announce-build-result && f=/var/lib/jenkins/userContent/announce-build-result || exit 0
       $f has begun
       '''
}

def announceEnd(status) {
    sh """
       test -x /usr/local/bin/announce-build-result && f=/usr/local/bin/announce-build-result || test -f /var/lib/jenkins/userContent/announce-build-result && f=/var/lib/jenkins/userContent/announce-build-result || exit 0
       \$f finished with status ${status}
       """
}

def uploadDeliverables(spec) {
    sh """
       test -x /usr/local/bin/announce-build-result && f=/usr/local/bin/announce-build-result || test -f /var/lib/jenkins/userContent/announce-build-result && f=/var/lib/jenkins/userContent/announce-build-result || exit 0
       \$f ${spec}
       """
}

def basename(aString) {
    return aString.split('/')[-1]
}

def dirname(aString) {
    return aString[0..-basename(aString).size()-2]
}

def describeCause(currentBuild) {
	def causes = currentBuild.rawBuild.getCauses()
	def manualCause = currentBuild.rawBuild.getCause(hudson.model.Cause$UserIdCause)
	def scmCause = currentBuild.rawBuild.getCause(hudson.triggers.SCMTrigger$SCMTriggerCause)
	def upstreamCause = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
	def buildTrigger = "Triggered by ${causes}"
	if (upstreamCause != null) {
		buildTrigger = "Triggered by upstream job " + upstreamCause.upstreamProject
	} else if (manualCause != null) {
		buildTrigger = "${manualCause.shortDescription}"
	} else if (scmCause != null) {
		buildTrigger = "${scmCause.shortDescription}"
	}
	return buildTrigger
}

def isUpstreamCause(currentBuild) {
	def upstreamCause = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
	return upstreamCause != null
}

def getUpstreamProject(currentBuild) {
	if (isUpstreamCause(currentBuild)) {
		return currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause).upstreamProject
	}
	return null
}

def loadParameter(filename, name, defaultValue) {
    GroovyShell shell = new GroovyShell()
    defaultsScript = [:]
    def path = env.JENKINS_HOME + "/jobs/" + env.JOB_NAME + "/" + "parameters.groovy"
    try {
        defaultsScript = shell.parse(new File(path)).run()
    }
    catch(IOException ex){
        println "Could not load from ${path}"
        path = env.JENKINS_HOME + "/jobs/" + env.JOB_NAME.split("/")[0] + "/" + "parameters.groovy"
        try {
            defaultsScript = shell.parse(new File(path)).run()
        }
        catch(IOException ex2) {
            println "Could not load from ${path} either"
	    defaultsScript = [:]
        }
    }
    x = defaultsScript.find{ it.key == name }?.value
    if (x) {
        println "Loaded parameter ${name}=${x} from ${path}"
        return x
    }
    println "Could not find parameter ${name}, returning default ${defaultValue}"
    return defaultValue
}

def srpmFromSpecWithUrl(specfilename, srcdir, outdir, sha256sum='', armsha256sum='') {
	// Specfile SOURCE0 has the URL.  Sha256sum validates the URL's ontents.
	// srcdir is where the URL file is deposited.
	// outdir is where the source RPM is deposited.  It is customarily src/ cos that's where automockfedorarpms finds it.
	return {
		url = getrpmfield(specfilename, "Source0")
		if (url == "") {
			error("Could not compute URL of source tarball.")
		}
		downloadUrl(url, null, sha256sum, srcdir)
		if (armsha256sum != ''){
			armurl = url.replace("amd64", "arm64")
			downloadUrl(armurl, null, armsha256sum, srcdir)
			name = getrpmfield(specfilename, "Name")
			version = getrpmfield(specfilename, "Version")
			dir(srcdir) {
				sh """
				tar zxvmf \$(basename ${url})
				rm -f \$(basename ${url})
				tar zxvmf \$(basename ${armurl})
				rm -f \$(basename ${armurl})
				n=\$(echo \$(basename ${url}) | sed 's/.tar.gz\$//')
				mv \$n \$(echo \$n | sed 's/amd64/x86_64/')
				n=\$(echo \$(basename ${armurl}) | sed 's/.tar.gz\$//')
				mv \$n \$(echo \$n | sed 's/arm64/aarch64/')
				tar cvzf \$(basename ${url}) ${name}*/*
				tar ztvmf \$(basename ${url})
				"""
			}
			sh "sed -i 's|%setup -q -n %{name}-%{version}.linux-amd64|%setup -q -n %{name}-%{version}.linux-%{_target_cpu}|' ${specfilename}"
		}
		sh "rpmbuild --define \"_srcrpmdir ${outdir}\" --define \"_sourcedir ${srcdir}\" -bs ${specfilename}"
	}
}

def downloadPypiPackageToSrpmSource() {
        // Download source specified by pypipackage-to-srpm.yaml manifest.
        def url = sh(
            script: 'shyaml get-value url < pypipackage-to-srpm.yaml',
            returnStdout: true
        ).trim()
        def sum = sh(
            script: 'shyaml get-value sha256sum < pypipackage-to-srpm.yaml',
            returnStdout: true
        ).trim()
        def actualfilename = sh(
            script: 'shyaml get-value source_filename < pypipackage-to-srpm.yaml || true',
            returnStdout: true
        ).trim()
        def basename = downloadUrl(url, null, sum, ".")
        if (actualfilename != "" && actualfilename != basename) {
            sh "mv -f ${basename} ${actualfilename}"
            return actualfilename
        }
        return basename
}

def buildDownloadedPypiPackage(basename, opts="") {
        // Build pypipackage-downloaded tarball, assuming pypipackage-to-srpm.yaml
        // manifest presence, as well as patches present in the same directory.
        sh """
        y=pypipackage-to-srpm.yaml
        disable_debug=
        mangle_name=
        if [ "\$(shyaml get-value disable_debug False < \$y)" == "True" ] ; then
                disable_debug=--disable-debug
        fi
        if [ "\$(shyaml get-value mangle_name True < \$y)" == "False" ] ; then
                mangle_name=--no-mangle-name
        fi
        epoch=\$(shyaml get-value epoch '' < \$y || true)
        if [ "\$epoch" != "" ] ; then
                epoch="--epoch=\$epoch"
        fi
        python_versions=\$(shyaml get-values python_versions < \$y || true)
        if [ "\$python_versions" == "" ] ; then
                python_versions="2 3"
        fi
        if [ "\$python_versions" == "2 3" -o "\$python_versions" == "3 2" ] ; then
                if [ "\$mangle_name" == "--no-mangle-name" ] ; then
                        >&2 echo error: cannot build for two Python versions without mangling the name of the package
                        exit 36
                fi
        fi
        diffs=1
        for f in *.diff ; do
                test -f "\$f" || diffs=0
        done
        for v in \$python_versions ; do
                if [ "\$diffs" == "1" ] ; then
                        python"\$v" `which pypipackage-to-srpm` --no-binary-rpms \$epoch \$mangle_name \$disable_debug ${opts} "${basename}" *.diff
                else
                        python"\$v" `which pypipackage-to-srpm` --no-binary-rpms \$epoch \$mangle_name \$disable_debug ${opts} "${basename}"
                fi
        done
        """
}

// Returns a list of files based on the spec glob passed to this function.
def glob(spec) {
	def filelist = []
	withEnv(["spec=${spec}"]) {
		filelist = sh(
			script: '''#!/bin/bash -e
			for f in $spec ; do if [ -e "$f" ] ; then echo "$f" ; fi ; done
			''',
			returnStdout: true
		).trim().split("\n")
	}
	if (filelist.size() == 1 && filelist[0] == "") {
		filelist = []
	}
	return filelist
}

// Create source RPM from a source tree.  Finds first specfile in src/ and uses that.
def srpmFromSpecAndSourceTree(srcdir, outdir) {
	// srcdir is the directory tree that contains the source files to be tarred up.
	// outdir is where the source RPM is deposited.  It is customarily src/ cos that's where automockfedorarpms finds it.
	return {
		println "Retrieving specfiles..."
		filename = sh(
			returnStdout: true,
			script: "find src/ -name '*.spec' | head -1"
		).trim()
		if (filename == "") {
			error('Could not find any specfile in src/ -- failing build.')
		}
		println "Filename of specfile is ${filename}."

		tarball = getrpmfield(filename, "Source0")
		if (tarball == "") {
			error("Could not compute filename of source tarball.")
		}
		println "Filename of source tarball is ${tarball}."
		// This makes the tarball.
		sh "p=\$PWD && cd ${srcdir} && cd .. && bn=\$(basename ${srcdir}) && tar -cvz --exclude=.git -f ${tarball} \$bn"
		// The following code copies up to ten source files as specified by the
		// specfile, if they exist in the src/ directory where the specfile is.
		for (i in getrpmsources(filename)) {
			sh "if test -f src/${i} ; then cp src/${i} ${srcdir}/.. ; fi"
		}
		for (i in getrpmpatches(filename)) {
			sh "if test -f src/${i} ; then cp src/${i} ${srcdir}/.. ; fi"
		}
		// This makes the source RPM.
		sh "rpmbuild --define \"_srcrpmdir ${outdir}\" --define \"_sourcedir ${srcdir}/..\" -bs ${filename}"
	}
}

def srpmFromSpecAndDirContainingSpecSources(srcdir, outdir) {
	// Assumes the srcdir contains the sources that do not exist
	// in src/ -- then merges the sources from src/ into srcdir/
	// srcdir is the directory tree that contains the source files not already available in src/.
	// outdir is where the source RPM is deposited.  It is customarily src/ cos that's where automockfedorarpms finds it.
	return {
		filename = sh(
			returnStdout: true,
			script: "find src/ -name '*.spec' | head -1"
		).trim()
		for (i in getrpmsources(filename)) {
			sh "if test -f src/${i} ; then cp src/${i} ${srcdir}/ ; fi"
		}
		for (i in getrpmpatches(filename)) {
			sh "if test -f src/${i} ; then cp src/${i} ${srcdir}/ ; fi"
		}
		// This makes the source RPM.
		sh "rpmbuild --define \"_srcrpmdir ${outdir}\" --define \"_sourcedir ${srcdir}/\" -bs ${filename}"
	}
}

def checkoutRepoAtCommit(repo, commit, outdir) {
	// outdir is the directory where the repo will be checked out.
	return {
		dir(outdir) {
			checkout(
				[
					$class: 'GitSCM',
					branches: [[name: commit]],
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
					submoduleCfg: [],
					userRemoteConfigs: [[url: repo]]
				]
			)
		}
	}
}

def downloadUrl(url, filename, sha256sum, outdir) {
	// outdir is the directory where the file will appear.
	// basename can be null, optionally, in which case it will be computed automatically and returned.
        if (filename == null || filename == "") {
            filename = basename(url)
        }
        if (outdir == null || outdir == "") {
            outdir = "."
        }
        sh """
                set -x
                set +e
                s=\$(sha256sum ${outdir}/${filename} | cut -f 1 -d ' ' || true)
                if [ "\$s" != "${sha256sum}" ] ; then
                        rm -f -- ${outdir}/${filename}
                        wget --progress=dot:giga --timeout=15 -O ${outdir}/${filename} -- ${url} || exit \$?
                        s=\$(sha256sum ${outdir}/${filename} | cut -f 1 -d ' ' || true)
                        if [ "\$s" != "${sha256sum}" ] ; then
                            >&2 echo error: SHA256 sum "\$s" of file "${outdir}/${filename}" does not match expected sum "${sha256sum}"
                            exit 8
                        fi
                fi
        """
        return filename
}

def downloadURLAndGPGSignature(dataURL, signatureURL) {
    def urlBase = basename(dataURL)
    def checksumBase = basename(signatureURL)
    sh """
    rm -f -- ${urlBase} ${checksumBase}
    wget -c --progress=dot:giga --timeout=15 -- ${dataURL}
    wget -c --progress=dot:giga --timeout=15 -- ${signatureURL}
    """
}

def downloadURLWithGPGVerification(dataURL, signatureURL, keyServer, keyID) {
    downloadURLAndGPGSignature(dataURL, signatureURL)
    def signatureBase = basename(signatureURL)
    sh """
    GNUPGHOME=`mktemp -d /tmp/.gpg-tmp-XXXXXXX`
    export GNUPGHOME
    eval \$(gpg-agent --homedir "\$GNUPGHOME" --daemon)
    trap 'rm -rf "\$GNUPGHOME"' EXIT
    gpg2 --verbose --homedir "\$GNUPGHOME" --keyserver ${keyServer} --recv ${keyID}
    gpg2 --verbose --homedir "\$GNUPGHOME" --verify ${signatureBase}
    """
}

def downloadURLWithSHA256Verification(dataURL, checksumURL) {
    def urlBase = basename(dataURL)
    def checksumBase = basename(checksumURL)
    sh """
    rm -f -- ${urlBase} ${checksumBase}
    wget -c --progress=dot:giga --timeout=15 -- ${dataURL}
    wget -c --progress=dot:giga --timeout=15 -- ${checksumURL}
    """
    sh """
    sha256sum -c --ignore-missing ${checksumBase}
    """
}

def downloadURLWithGPGAndSHA256Verification(dataURL, checksumURL, keyServer, keyID) {
    downloadURLWithSHA256Verification(dataURL, checksumURL)
    def checksumBase = basename(checksumURL)
    sh """
    GNUPGHOME=`mktemp -d /tmp/.gpg-tmp-XXXXXXX`
    export GNUPGHOME
    eval \$(gpg-agent --homedir "\$GNUPGHOME" --daemon)
    trap 'rm -rf "\$GNUPGHOME"' EXIT
    gpg2 --verbose --homedir "\$GNUPGHOME" --keyserver ${keyServer} --recv ${keyID}
    gpg2 --verbose --homedir "\$GNUPGHOME" --verify ${checksumBase}
    """
}

def mockShellLib() {
    return '''
set +x >/dev/null 2>&1

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

function config_mocklock() {
    local release="$1"
    local arch="$2"

    local basebase
    if test -n "$MOCK_CACHEDIR" ; then
        basebase="$MOCK_CACHEDIR"
    else
        basebase="$WORKSPACE/mock"
    fi

    local basedir="$basebase"
    mkdir -p "$basedir"

    local jail="fedora-$release-$arch-generic"
    local cfg="$basedir/$jail.cfg"
    local root="$basedir/jail/$jail"
    local cache_topdir="$basedir"/cache

    local tmpcfg=$(mktemp "$basedir"/XXXXXX)
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
config_opts['isolation'] = 'nspawn'
config_opts['rpmbuild_networking'] = False
config_opts['dist'] = 'fc$release'  # only useful for --resultdir variable subst
config_opts['releasever'] = '$release'
config_opts['nosync'] = True
config_opts['nosync_force'] = True
config_opts['plugin_conf']['ccache_enable'] = False
config_opts['plugin_conf']['generate_completion_cache_enable'] = False
config_opts['use_bootstrap'] = False
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

    if cmp "$cfg" "$tmpcfg" >&2 ; then
        rm -f "$tmpcfg"
    else
        mv -f "$tmpcfg" "$cfg"
        echo Configured "$cfg" as follows >&2
        echo =============================== >&2
        cat "$cfg" >&2
        echo =============================== >&2
    fi
    echo "$cfg"
}

function mocklock() {
    local release="$1"
    shift
    local arch="$1"
    shift
    local cfg

    echo About to run mock. >&2
    echo "I am user $(whoami)." >&2
    echo "We will be using release $release and arch $arch." >&2
    echo "Arguments for mock: $@" >&2

    cfg=$( config_mocklock "$release" "$arch" )

    echo "Using mock config $cfg." >&2

    local ret=60
    while [ "$ret" == "60" ] ; do
        grep mock /etc/group >/dev/null 2>&1 || groupadd -r mock
        flock "$cfg".lock /usr/bin/mock -r "$cfg" "$@" < /dev/null && ret=0 || ret=$?
        if [ "$ret" == "60" ] ; then
            echo "Sleeping for 15 seconds" >&2
            sleep 15
        fi
    done
    return "$ret"
}
'''
}

def mock(String release, String arch, ArrayList args) {
    def quotedargs = args.collect{ shellQuote(it) }.join(" ")
    def mocklock = "mocklock " + release + " " + arch + " " + quotedargs
    def cmd = mockShellLib() + mocklock
    sh(
        script: cmd,
        label: "Run mocklock ${release} ${arch}"
    )
}

def automockfedorarpms(String myRelease) {
    def stuff = ""
    def release = ""
    def arch = ""
    if (myRelease.indexOf(":") != -1) {
            stuff = myRelease.split(':')
            release = stuff[0]
            arch = stuff[1]
    } else {
            release = myRelease
            arch = "x86_64"
    }
    println "Release detected: ${release}.  Arch detected: ${arch}."
    def detected = sh(
        script: '''#!/bin/sh -e
        for file in src/*.src.rpm ; do
            test -f "$file" || { echo "$file is not a source RPM" >&2 ; exit 19 ; }
            echo "$file"
        done
        ''',
        returnStdout: true,
        label: "Check we have source RPMs."
    ).trim().split("\n")
    println "We have found the following source RPMs: " + detected.join(", ")
    dir("out/${release}") {
        sh(
            script: 'echo Created out directory $PWD. >&2',
            label: "Create out directory for this release."
        )
    }
    ArrayList args = [
        "--unpriv",
        "--verbose",
        "--define=build_number ${BUILD_NUMBER}",
        "--resultdir=out/${release}",
        "--rebuild"
    ]
    for (srpm in detected) {
        args << srpm
    }
    mock(release, arch, args)
}
