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

def findSpecfile() {
    specfile = sh(
        returnStdout: true,
        script: "find -name '*.spec' | head -1",
        label: "Find specfile"
    ).trim()
    if (specfile == "") {
        error("Could not find any specfile -- failing build.")
    }
    println "Found specfile at ${specfile}."
    return specfile
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
    System.setProperty("org.jenkinsci.plugins.durabletask.BourneShellScript.HEARTBEAT_CHECK_INTERVAL", "120")
}

String getrpmfield(String filename, String field) {
	return sh(
		returnStdout: true,
		script: """#!/bin/bash
			rpmspec -P ${filename} | grep ^${field}: | awk ' { print \$2 } ' | head -1
		""",
        label: "Getting RPM field $field"
	).trim()
}

def getrpmfieldlist(String filename, String fieldPrefix) {
	ret = []
    s = getrpmfield(filename, "${fieldPrefix}")
    if (s != "") {
        ret.add(s)
    }
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

def makeTarballForSpecfile(String sourcetree) {
    // sourcetree is the directory tree that contains the sources
    // tarball will be deposited in the current directory
    // tarball will contain a single folder named after the
    // base name of the source tree specified here
    specfile = findSpecfile()
    tarball_name = basename(getrpmsources(specfile)[0])

    // This makes the tarball.
    sh """
        p=\$PWD
        cd ${sourcetree}
        cd ..
        bn=\$(basename ${sourcetree})
        tar -cvz --exclude=.git -f ${tarball_name} \$bn
        mv -f ${tarball_name} \$p
    """
    return tarball_name
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
            def basename = downloadURLWithSHA256Checksum(url, sum)
            if (actualfilename != "" && actualfilename != basename) {
                sh "mv -f ${basename} ${actualfilename}"
                return actualfilename
            }
            return basename
        }
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

def computeSHA256sum(String filename) {
    fn = shellQuote(filename)
    return sh(
        script: """
            #!/bin/bash
            sha256sum ${fn} | cut -f 1 -d ' '
        """,
        label: "SHA256 sum of ${filename}",
        returnStdout: true
    ).trim()
}

def computeSHA512sum(String filename) {
    fn = shellQuote(filename)
    return sh(
        script: """
            #!/bin/bash
            sha512sum ${fn} | cut -f 1 -d ' '
        """,
        label: "SHA512 sum of ${filename}",
        returnStdout: true
    ).trim()
}

def downloadURLUnchecked(url, outpath, simulate=false) {
    // outpath is the path to the saved file, but if the last
    // character is a slash, the path is assumed to be a directory
    // and the file name will be deduced from the URL.  If outpath
    // is empty or null, the current directory will be the download
    // target.
    // if simulate is true, the actual download is skipped.
    if (outpath == null || outpath == "") {
        outdir = "."
        filename = basename(url)
    } else if (outpath.endswith("/")) {
        outdir = outpath
        filename = basename(url)
    } else {
        outdir = dirname(outpath)
        filename = basename(outpath)
    }
    filename = "${outdir}/${filename}"
    if (!simulate || simulate == null) {
        sh(
            script: """#!/bin/bash -e
                rm -f -- ${shellQuote(filename)}
                wget --progress=dot:giga --timeout=15 -O ${shellQuote(filename)} -- ${shellQuote(url)}
            """,
            label: "Download to ${filename}"
        )
    }
    return filename
}

def downloadURLChecked(url, outputpath, sum, checker) {
    o = downloadURLUnchecked(url, outputpath, true)
    if (checker(o) == sum) {
        return o
    }
    o = downloadURLUnchecked(url, outputpath)
    actualsum = checker(o)
    if (actualsum == sum) {
        return o
    }
    error("Checksum expected ${sum} of ${o} does not match calculated sum ${actualsum}")
}

def downloadURLWithSHA256Checksum(url, sha256sum, outfilename=null) {
    return downloadURLChecked(url, outfilename, sha256sum, { x -> computeSHA512sum(x) })
}

def downloadURLWithSHA512Checksum(url, sha512sum, outfilename=null) {
    return downloadURLChecked(url, outfilename, sha512sum, { x -> computeSHA512sum(x) })
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

def gomodvendor() {
    sh(
        script: 'go mod vendor',
        label: "Vendor Go sources"
    )
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
