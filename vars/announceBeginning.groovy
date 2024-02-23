def call() {
    sh(
        script: """#!/bin/bash
        "$JENKINS_HOME"/userContent/announce-build-result begun || true
        """,
        label: "Announce build beginning"
    )
}
