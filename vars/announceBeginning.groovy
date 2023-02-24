def call() {
    sh """
       "$JENKINS_HOME"/userContent/announce-build-result begun || true
       """
}
