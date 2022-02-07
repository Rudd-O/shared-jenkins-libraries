def call(String imageTag) {
	// """Push docker image to server."""
	def server = "docker.dragonfear:80"
	def regsources = '{"insecureRegistries": ["' + server + '"]}'
	withEnv(["BUILD_REGISTRY_SOURCES=${regsources}"]) {
		return sh "buildah push ${imageTag} docker://${server}/${imageTag}"
	}
}
