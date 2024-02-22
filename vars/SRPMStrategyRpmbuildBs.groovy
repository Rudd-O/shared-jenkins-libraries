// This strategy calls make srpm in the local directory,
// expecting an SRPM to be built in the local directory.
def call(Map config) {
    return {
        sh (
			script: '''
			rpmbuild --define "_srcrpmdir $PWD" --define "_sourcedir $PWD" -bs *.spec || {
				pwd ; ls -la ; exit 1
			}
			''',
			label: "rpmbuild source RPM from specfile and sources in current directory"
		)
    }
}
