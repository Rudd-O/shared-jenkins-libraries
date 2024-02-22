def call() {
    dir ("../upstream") {
        sh(
            script: 'test -f prometheus2/prometheus2.spec -a -f templates/spec.tpl || { pwd ; ls -la ; exit 1 ; }',
            label: 'Check for keystone files'
        )
        sh(
            script: 'sed -i "s|{{release}}|%{?build_number}%{?!build_number:{{release}}}|g" templates/spec.tpl',
            label: 'Mangle template'
        )
        sh(
            script: 'sed -i "s|Release: 1|Release: %{?build_number}%{?!build_number:1}|g" */*.spec',
            label: 'Mangle all existing specfiles'
        )
        sh(
            script: "python3 ./generate.py || { pwd ; ls -la ; exit 1 ; }",
            label: "Generate specfiles"
        )
    }
}
