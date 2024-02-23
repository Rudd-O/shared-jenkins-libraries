// This strategy calls make srpm in the local directory,
// expecting an SRPM to be built in the local directory.
def call() {
    return {
        sh (
			script: '''
			./autogen.sh && ./configure --prefix=/usr && make dist && rpmbuild --define _srcrpmdir" $PWD" -ts *.tar.gz || {
				test -f config.log && cat config.log || true
				exit 8
			}
			''',
			label: "configure for source RPM"
		)
    }
}
