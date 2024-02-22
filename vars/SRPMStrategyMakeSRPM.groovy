// This strategy calls make srpm in the local directory,
// expecting an SRPM to be built in the local directory.
def call() {
    return {
        sh(
			script: '''#!/bin/sh -e
			make srpm''',
			label: 'using make to create a source RPM'
		)
    }
}
