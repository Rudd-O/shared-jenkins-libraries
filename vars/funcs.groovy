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
    sh(
        script: """#!/bin/bash -e
          (
              flock 9
              rpm -q ${deps} >/dev/null || sudo dnf install --disablerepo='*qubes*' --disableplugin='*qubes*' -qy ${deps}
          ) 9> /tmp/\$USER-dnf-lock
        """,
        label: "Installing RPM dependencies ${deps}"
    )
}

def aptInstall(deps) {
    deps = deps.collect { shellQuote(it) }
    deps = deps.join(' ')
    sh(
        script: """#!/bin/bash -e
     (
         flock 9
         deps="${deps.join(' ')}"
         dpkg-query -s \$deps >/dev/null || { sudo apt-get -q update && sudo apt-get -y install \$deps ; }
     ) 9> /tmp/\$USER-apt-lock
     """,
        label: "Installing APT dependencies ${deps}"
    )
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
    ]
    // println "Loading parameter ${name} from paths ${paths}."
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
            // println "Could not load from ${path}"
        }
    }
    println "Loaded parameter ${name}=${defaultValue} from default"
    return defaultValue
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
