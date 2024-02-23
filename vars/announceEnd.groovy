def call(String status) {
    sh(
        script: """#!/bin/bash
        "$JENKINS_HOME"/userContent/announce-build-result finished ${status}
        """,
        label: "Announce build end"
    )
}
