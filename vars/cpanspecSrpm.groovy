def f(addProvides=null) {
    def ap = ""
    if (addProvides != null) {
        ap = addProvides.collect{ "--add-provides " + shellQuote(it) }.join(" ")
    }
    dir('src') {
        sh """
            pkg=\$(echo ${shellQuote(env.JOB_NAME)} | sed 's/^perl-//' | sed 's|/.*||' | sed 's/-/::/g')
            cpanspec -v -r ${env.BUILD_NUMBER} -s --packager "Generic RPM builder" ${ap} "\$pkg"
        """
    }
}

def call(addProvides=null) {
    script {
        f(addProvides)
    }
    println "Done with cpanspecSrpm"
}
