def f(pkgname, sha256sum, armsha256sum='') {
	script {
		dir("upstream") {
			sh "sed -i 's|{{release}}|%{?build_number}%{?!build_number:{{release}}}|g' templates/spec.tpl"
			sh "python2 ./generate.py --templates ${pkgname}"
			sh """
				sed -i 's|%{_unitdir}|/etc/systemd/system|g' ${pkgname}/autogen_${pkgname}.spec
			"""
		}
		funcs.srpmFromSpecWithUrl(
			"upstream/${pkgname}/autogen_${pkgname}.spec",
			"upstream/${pkgname}",
			"src",
			sha256sum,
			armsha256sum,
		)()
	}
}

def call(pkgname, sha256sum, armsha256sum='') {
    return {
        script {
            f(pkgname, sha256sum, armsha256sum)
        }
    }
}
