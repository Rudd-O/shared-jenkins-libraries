def call(String specfilename, String srcdir, String outdir, String sha256sum='', String armsha256sum='') {
	// Specfile SOURCE0 has the URL.  Sha256sum validates the URL's ontents.
	// srcdir is where the URL file is deposited.
	// outdir is where the source RPM is deposited.  It is customarily src/ cos that's where automockrpms finds it.
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
