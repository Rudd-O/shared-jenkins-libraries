def call(Map config) {
        // config.specfile is a file where SOURCE0 has the URL.
        // config.srcdir is where the URL file is deposited.
        // config.outdir is where the source RPM is deposited.  It is customarily src/ cos that's where automockrpms finds it.
        // config.sha256sum validates the URL's contents.
        // config.armsha256sum = '' validates the ARM URL's contents.
        // Specfile
        url = funcs.getrpmfield(config.specfile, "Source0")
        if (url == "") {
                error("Could not compute URL of source tarball.")
        }
        funcs.downloadUrl(url, null, config.sha256sum, config.srcdir)
        if (config.containsKey("armsha256sum")){
                armurl = url.replace("amd64", "arm64")
                funcs.downloadUrl(armurl, null, config.armsha256sum, config.srcdir)
                name = funcs.getrpmfield(config.specfile, "Name")
                version = funcs.getrpmfield(config.specfile, "Version")
                dir(config.srcdir) {
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
                sh "sed -i 's|%setup -q -n %{name}-%{version}.linux-amd64|%setup -q -n %{name}-%{version}.linux-%{_target_cpu}|' ${config.specfile}"
        }
        sh "rpmbuild --define \"_srcrpmdir ${config.outdir}\" --define \"_sourcedir ${config.srcdir}\" -bs ${config.specfile}"
}
