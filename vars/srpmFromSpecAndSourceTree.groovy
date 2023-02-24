// Create source RPM from a source tree.  Finds first specfile in src/ and uses that.
def call(String srcdir, String outdir) {
	// srcdir is the directory tree that contains the source files to be tarred up.
	// outdir is where the source RPM is deposited.  It is customarily src/ cos that's where automockrpms finds it.
        println "Retrieving specfiles..."
        filename = sh(
                returnStdout: true,
                script: "find src/ -name '*.spec' | head -1"
        ).trim()
        if (filename == "") {
                error('Could not find any specfile in src/ -- failing build.')
        }
        println "Filename of specfile is ${filename}."

        tarball = funcs.getrpmfield(filename, "Source0")
        if (tarball == "") {
                error("Could not compute filename of source tarball.")
        }
        println "Filename of source tarball is ${tarball}."
        // This makes the tarball.
        sh "p=\$PWD && cd ${srcdir} && cd .. && bn=\$(basename ${srcdir}) && tar -cvz --exclude=.git -f ${tarball} \$bn"
        // The following code copies up to ten source files as specified by the
        // specfile, if they exist in the src/ directory where the specfile is.
        for (i in funcs.getrpmsources(filename)) {
                sh "if test -f src/${i} ; then cp src/${i} ${srcdir}/.. ; fi"
        }
        for (i in funcs.getrpmpatches(filename)) {
                sh "if test -f src/${i} ; then cp src/${i} ${srcdir}/.. ; fi"
        }
        // This makes the source RPM.
        sh "rpmbuild --define \"_srcrpmdir ${outdir}\" --define \"_sourcedir ${srcdir}/..\" -bs ${filename}"
}
