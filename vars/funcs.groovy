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
    deps = deps.collect { shellQuote(it) }
    deps = deps.join(' ')
    sh """#!/bin/bash -xe
          (
              flock 9
              sudo dnf install --disablerepo='*qubes*' --disableplugin='*qubes*' -y ${deps}
              sudo dnf upgrade --disablerepo='*qubes*' --disableplugin='*qubes*' -y ${deps}
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
    sh """
       "$JENKINS_HOME"/userContent/announce-build-result begun || true
       """
}

def announceEnd(status) {
    sh """
       "$JENKINS_HOME"/userContent/announce-build-result finished ${status} || true
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

def getUpstreamBuild(currentBuild) {
	if (isUpstreamCause(currentBuild)) {
		return currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause).upstreamBuild
	}
	return null
}

def loadParameter(name, defaultValue) {
    GroovyShell shell = new GroovyShell()
    defaultsScript = [:]
    def paths = [
      env.WORKSPACE + "/src/" + "build.parameters",
      env.WORKSPACE + "/" + "build.parameters",
      env.JENKINS_HOME + "/jobdsl/" + env.JOB_NAME + ".parameters",
      env.JENKINS_HOME + "/jobdsl/" + env.JOB_NAME.split("/")[0] + ".parameters",
    ]
    println "Loading parameter ${name} from paths ${paths}."
    for (path in paths) {
        try {
            defaultsScript = shell.parse(new File(path)).run()
            x = defaultsScript.find{ it.key == name }?.value
            if (x) {
                println "Loaded parameter ${name}=${x} from ${path}"
                return x
            } else {
                println "Could not find ${name} in ${path}"
            }
        }
        catch(IOException ex){
            println "Could not load from ${path}"
        }
    }
    println "Could not find parameter ${name}, returning default ${defaultValue}"
    return defaultValue
}

def srpmFromSpecWithUrl(specfilename, srcdir, outdir, sha256sum='', armsha256sum='') {
	// Specfile SOURCE0 has the URL.  Sha256sum validates the URL's ontents.
	// srcdir is where the URL file is deposited.
	// outdir is where the source RPM is deposited.  It is customarily src/ cos that's where automockrpms finds it.
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
        if (url.startsWith("git+")) {
            def basename = sh(
                script: 'pip download --no-input --no-binary=:all: --exists-action=w --no-deps ' + shellQuote(url),
                returnStdout: true
            )
            def splitbasename = basename.split("\n")
            basename = splitbasename[-2]
            basename = basename.split("/")[1]
            sh 'rm -rf sdir && unzip -d sdir -o ' + shellQuote(basename) + ' && cd sdir/* && python3 setup.py sdist && mv dist/* ../..'
            return basename.substring(0, basename.length()-4) + '.tar.gz'
        } else {
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
}

def buildDownloadedPypiPackage(basename, opts="") {
        // Build pypipackage-downloaded tarball, assuming pypipackage-to-srpm.yaml
        // manifest presence, as well as patches present in the same directory.
        sh """
        y=pypipackage-to-srpm.yaml
        disable_debug=
        if [ "\$(shyaml get-value disable_debug False < \$y)" == "True" ] ; then
                disable_debug=--disable-debug
        fi
        if [ "\$(shyaml get-value arch_dependent False < \$y)" == "True" ] ; then
                arch_dependent=--arch-dependent
        fi
        if [ "\$(shyaml get-value module_to_save < \$y)" != "" ] ; then
                module_to_save=--module-to-save=\$(shyaml get-value module_to_save < \$y)
        fi
        if [ "\$(shyaml get-values extra_globs < \$y)" != "" ] ; then
            for v in \$(shyaml get-values extra_globs < \$y) ; do
                extra_globs="--extra-globs=\$v \$extra_globs"
            done
        fi
        if [ "\$(shyaml get-values buildrequires < \$y)" != "" ] ; then
            for v in \$(shyaml get-values buildrequires < \$y) ; do
                extra_buildrequires="--extra-buildrequires=\$v \$extra_buildrequires"
            done
        fi
        if [ "\$(shyaml get-values requires < \$y)" != "" ] ; then
            for v in \$(shyaml get-values requires < \$y) ; do
                extra_requires="--extra-requires=\$v \$extra_requires"
            done
        fi
        epoch=\$(shyaml get-value epoch '' < \$y || true)
        if [ "\$epoch" != "" ] ; then
                epoch="--epoch=\$epoch"
        fi
        diffs=1
        for f in *.diff ; do
                test -f "\$f" || diffs=0
        done
        if [ "\$diffs" == "1" ] ; then
                python3 `which pypipackage-to-srpm` --no-binary-rpms \$module_to_save \$extra_globs \$extra_requires \$extra_buildrequires \$arch_dependent \$epoch \$disable_debug ${opts} "${basename}" *.diff
        else
                python3 `which pypipackage-to-srpm` --no-binary-rpms \$module_to_save \$extra_globs \$extra_requires \$extra_buildrequires \$arch_dependent \$epoch \$disable_debug ${opts} "${basename}"
        fi
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
	// outdir is where the source RPM is deposited.  It is customarily src/ cos that's where automockrpms finds it.
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
	// outdir is where the source RPM is deposited.  It is customarily src/ cos that's where automockrpms finds it.
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
function config_mocklock_fedora() {
    local outputfile="$1"
    local release="$2"
    local arch="$3"
    local basedir="$4"
    local root="$5"
    local cache_topdir="$6"

    cat > "$outputfile" <<EOF
config_opts['print_main_output'] = True

config_opts['basedir'] = '$basedir'
config_opts['root'] = '$root'
config_opts['target_arch'] = '$arch'
config_opts['legal_host_arches'] = ('$arch',)
# rpmdevtools was installed to support rpmdev-bumpspec below
# python-setuptools was installed to allow for python builds
config_opts['chroot_setup_cmd'] = 'install @buildsys-build autoconf automake gettext-devel libtool git rpmdevtools python-setuptools python3-setuptools /usr/bin/python3 shadow-utils /bin/sh'
config_opts['extra_chroot_dirs'] = ['/run/lock']
config_opts['isolation'] = 'simple'
config_opts['rpmbuild_networking'] = True
config_opts['use_host_resolv'] = False
config_opts['dist'] = 'fc$release'  # only useful for --resultdir variable subst
config_opts['releasever'] = '$release'
config_opts['nosync'] = False
config_opts['nosync_force'] = False
config_opts['plugin_conf']['ccache_enable'] = False
config_opts['plugin_conf']['generate_completion_cache_enable'] = False
config_opts['use_bootstrap'] = False
config_opts['cleanup_on_success'] = False
config_opts['cleanup_on_failure'] = True
config_opts['package_manager'] = 'dnf'

config_opts['cache_topdir'] = '$cache_topdir'
config_opts['plugin_conf']['root_cache_enable'] = True
config_opts['plugin_conf']['root_cache_opts'] = {}
config_opts['plugin_conf']['root_cache_opts']['age_check'] = True
config_opts['plugin_conf']['root_cache_opts']['max_age_days'] = 30
config_opts['plugin_conf']['root_cache_opts']['dir'] = '%(cache_topdir)s/%(root)s/root_cache/'
config_opts['plugin_conf']['root_cache_opts']['compress_program'] = 'gzip'
config_opts['plugin_conf']['root_cache_opts']['decompress_program'] = 'gunzip'
config_opts['plugin_conf']['root_cache_opts']['extension'] = '.gz'
config_opts['plugin_conf']['root_cache_opts']['exclude_dirs'] = ['./proc', './sys', './dev', './tmp/ccache', './var/cache/yum', './builddir', './build' ]

config_opts['yum.conf'] = """
[main]
keepcache=1
cachedir=/var/cache/yum
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
}

function config_mocklock_qubes() {
    local outputfile="$1"
    local release="$2"
    local arch="$3"
    local basedir="$4"
    local root="$5"
    local cache_topdir="$6"

    if [ "$release" == "q4.1" ] ; then
        local fedorareleasever=32
    else
        echo Do not know what the matching Fedora release version is for Qubes $release >&2
        exit 55
    fi
    release=$(echo "$release" | sed 's/^.//') # strip first char

    cat > "$outputfile" <<EOF
config_opts['print_main_output'] = True

config_opts['releasever'] = '$release'
config_opts['fedorareleasever'] = '$fedorareleasever'

config_opts['target_arch'] = 'x86_64'
config_opts['legal_host_arches'] = ('x86_64',)

config_opts['basedir'] = '$basedir'
config_opts['root'] = '$root'
config_opts['cleanup_on_success'] = True
config_opts['cleanup_on_failure'] = True

config_opts['description'] = 'Qubes OS {{ releasever }}'

config_opts['chroot_setup_cmd'] = 'install systemd bash coreutils tar dnf qubes-release rpm-build'

config_opts['dist'] = 'q{{ releasever }}'
config_opts['extra_chroot_dirs'] = [ '/run/lock', ]
config_opts['package_manager'] = 'dnf'

config_opts['dnf.conf'] = """
[main]
keepcache=1
debuglevel=1
reposdir=/dev/null
logfile=/var/log/yum.log
retries=20
obsoletes=1
gpgcheck=0
assumeyes=1
syslog_ident=mock
syslog_device=
install_weak_deps=0
metadata_expire=0
best=1
protected_packages=
user_agent={{ user_agent }}

# repos

[fedora]
name=fedora
metalink=https://mirrors.fedoraproject.org/metalink?repo=fedora-{{ fedorareleasever }}&arch={{ target_arch }}
gpgkey=file:///usr/share/distribution-gpg-keys/fedora/RPM-GPG-KEY-fedora-{{ fedorareleasever }}-primary
gpgcheck=1
skip_if_unavailable=False

[updates]
name=updates
metalink=https://mirrors.fedoraproject.org/metalink?repo=updates-released-f{{ fedorareleasever }}&arch={{ target_arch }}
gpgkey=file:///usr/share/distribution-gpg-keys/fedora/RPM-GPG-KEY-fedora-{{ fedorareleasever }}-primary
gpgcheck=1
skip_if_unavailable=False

[qubes-dom0-current]
name = Qubes Dom0 Repository (updates)
metalink = https://yum.qubes-os.org/r{{ releasever }}/current/dom0/fc{{ fedorareleasever }}/repodata/repomd.xml.metalink
skip_if_unavailable=False
enabled = 1
metadata_expire = 6h
gpgcheck = 1
gpgkey = file:///usr/share/distribution-gpg-keys/qubes/qubes-release-4-signing-key.asc

"""
EOF
}

function mocklock() {
    local release="$1"
    shift
    local arch="$1"
    shift

    local basedir="$MOCK_CACHEDIR"
    if test -z "$basedir" ; then
        echo 'No MOCK_CACHEDIR variable configured on this slave.  Aborting.' >&2
        exit 56
    fi

    mkdir -p "$basedir"

    local jail="fedora-$release-$arch-generic"
    local cfgfile="$basedir/$jail.cfg"
    local root="$jail"
    local cache_topdir=/var/cache/mock

    local configurator=config_mocklock_fedora
    if [[ $release == q* ]] ; then
        configurator=config_mocklock_qubes
    fi

    local tmpcfg=$(mktemp "$basedir"/XXXXXX)
    local cfgret=0
    $configurator "$tmpcfg" "$release" "$arch" "$basedir" "$root" "$cache_topdir" || cfgret=$?
    if [ "$cfgret" != "0" ] ; then rm -f "$tmpcfg" ; return "$cfgret" ; fi

    if cmp "$cfgfile" "$tmpcfg" >&2 ; then
        rm -f "$tmpcfg"
    else
        mv -f "$tmpcfg" "$cfgfile"
        echo Reconfigured "$cfgfile" >&2
    fi

    local ret=60
    while [ "$ret" == "60" ] ; do
        echo "Running process in mock jail" >&2
        flock "$cfgfile".lock /usr/bin/mock -r "$cfgfile" "$@" < /dev/null && ret=0 || ret=$?
        if [ "$ret" == "60" ] ; then
            echo "Sleeping for 15 seconds" >&2
            sleep 15
        fi
    done
    return "$ret"
}
'''
}

def mock(String release, String arch, ArrayList args, ArrayList srpms) {
    def quotedargs = args.collect{ shellQuote(it) }.join(" ")
    for (srpm in srpms) {
        def mocklock = "mocklock " + release + " " + arch + " " + quotedargs + " " + shellQuote(srpm)
        println "Will run ${mocklock} now."
        def cmd = "set +x >/dev/null 2>&1\n" + mockShellLib() + mocklock
        sh(
            script: cmd,
            label: "Run mocklock ${release} for ${srpm} on ${arch}"
        )
    }
}

def automockrpms(String myRelease) {
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
        "--postinstall",
        "--define=build_number ${BUILD_NUMBER}",
        "--resultdir=out/${release}",
        "--rebuild"
    ]
    ArrayList srpms = []
    for (d in detected) {
        srpms << d
    }
    mock(release, arch, args, srpms)
}

def repos() {
    def jobs = []
    jobs = sh(
        script: 'cd /srv/git && ls -1',
        returnStdout: true
    )
    splitData = jobs.tokenize("\n")
    jobs = splitData.findAll {
        ! (it == "post-receive" || it == "build" || it == "shared-jenkins-libraries.git" || it == "") 
    }
    return jobs
}

def defineJobViaDSL(job) {
    jobDsl(
        scriptText: """
    multibranchPipelineJob("${job}") {
        description "Job ${job}.  Set up by generic build code."
        displayName "${job}"
        branchSources {
          git {
            id = "${job}"
            remote("file:///srv/git/${job}.git")
          }
        }
        triggers {
          cron("H H * * *")
        }
        configure { x ->
          x / orphanedItemStrategy(class: 'com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy') {
            pruneDeadBranches('true')
            daysToKeep('-1')
            numToKeep('-1')
          }
          def traits = x / sources / data / 'jenkins.branch.BranchSource' / source / traits
          traits << 'jenkins.plugins.git.traits.BranchDiscoveryTrait' {}
          traits << 'jenkins.plugins.git.traits.TagDiscoveryTrait' {}
        }
        if (!jenkins.model.Jenkins.instance.getItemByFullName("${job}")) {
          queue("${job}")
        }
    }
    """,
        sandbox: true
    )
}

def defineJobs() {
    def r = repos()
    x = r.collect{ it.substring(0, it.length() - 4) }
    for (z in x) {
        defineJobViaDSL(z)
    }
}
