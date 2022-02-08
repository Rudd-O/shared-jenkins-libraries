def call(String[] imageTags) {
	// """Push docker images to server."""
	withEnv(["REGISTRY_AUTH_FILE=.regauth"]) {
		withCredentials([
			usernamePassword(credentialsId: 'docker-auth', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD'),
			string(credentialsId: 'docker-server', variable: 'DOCKER_SERVER'),
		]) {
			def regsources = '{"insecureRegistries": ["' + env.DOCKER_SERVER + '"]}'
			withEnv(["BUILD_REGISTRY_SOURCES=${regsources}"]) {
				sh 'podman login --tls-verify=false -u "$DOCKER_USERNAME" -p "$DOCKER_PASSWORD" "$DOCKER_SERVER"'
				for (imageTag in imageTags) {
					withEnv(["imagetag=${imageTag}"]) {
						sh 'podman push -f v2s2 --tls-verify=false "$imagetag" "$DOCKER_SERVER/$imagetag"'
					}
				}
			}
		}
		sh 'rm -f .regauth'
	}
}
