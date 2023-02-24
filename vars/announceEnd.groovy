def call(String status) {
    sh """
       "$JENKINS_HOME"/userContent/announce-build-result finished ${status} || true
       """
}
