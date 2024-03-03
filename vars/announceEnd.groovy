def call(String status) {
    sh(
        script: """#!/bin/bash
        "$JENKINS_HOME"/userContent/announce-build-result finished ${status} || true
        """,
        label: "Announce build end"
    )
}
