def f(pkgname, sha256sum, armsha256sum='') {
	script {
		dir("upstream") {
			sh "sed -i 's|{{release}}|%{?build_number}%{?!build_number:{{release}}}|g' templates/spec.tpl"
			sh "python3 ./generate.py --templates ${pkgname}"
			sh """
				sed -i 's|%{_unitdir}|/etc/systemd/system|g' ${pkgname}/autogen_${pkgname}.spec
			"""
		}
		if (armsha256sum != '') {
			srpmFromSpecWithUrl specfile: "upstream/${pkgname}/autogen_${pkgname}.spec", srcdir: "upstream/${pkgname}", outdir: "src", sha256sum: sha256sum, armsha256sum: armsha256sum
		} else {
			srpmFromSpecWithUrl specfile: "upstream/${pkgname}/autogen_${pkgname}.spec", srcdir: "upstream/${pkgname}", outdir: "src", sha256sum: sha256sum
		}
	}
}

def call(pkgname, sha256sum, armsha256sum='') {
    return {
        script {
            f(pkgname, sha256sum, armsha256sum)
        }
    }
}
