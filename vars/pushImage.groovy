def call(String imageTag) {
	// """Push docker image to server."""
	withEnv(["REGISTRY_AUTH_FILE=.regauth", "imagetag=${imageTag}"]) {
		withCredentials([
			usernamePassword(credentialsId: 'docker-auth', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD'),
			string(credentialsId: 'docker-server', variable: 'DOCKER_SERVER'),
		]) {
			def regsources = '{"insecureRegistries": ["' + env.DOCKER_SERVER + '"]}'
			withEnv(["BUILD_REGISTRY_SOURCES=${regsources}"]) {
				sh 'env'
				sh '''
				buildah login --tls-verify=false -u "$DOCKER_USERNAME" -p "$DOCKER_PASSWORD" "$DOCKER_SERVER"
				buildah push --tls-verify=false "$imagetag" "docker://$DOCKER_SERVER/$imagetag"
				'''
			}
		}
		sh 'rm -f .regauth'
	}
}
