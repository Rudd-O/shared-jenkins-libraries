def call() {
    return {
        sh(
			script: '''#!/bin/sh -e
			make test''',
			label: 'using make to run tests'
        )
    }
}
