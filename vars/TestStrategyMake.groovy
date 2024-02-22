def call(String target) {
    return {
        sh(
			script: """#!/bin/sh -e
			make ${target}
            """,
			label: "using make ${target} to run tests"
        )
    }
}
