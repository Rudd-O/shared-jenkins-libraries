def call(String imageTag) {
	// """Push docker image to server."""
	withEnv(["REGISTRY_AUTH_FILE=.regauth", "imagetag=${imageTag}"]) {
		withCredentials([
			usernamePassword(credentialsId: 'docker-auth', usernameVariable: 'docker_username', passwordVariable: 'docker_password'),
			string(credentialsId: 'docker-server', variable: 'docker_server'),
		]) {
			sh 'env'
			def regsources = '{"insecureRegistries": ["' + env.docker_server + '"]}'
			withEnv(["BUILD_REGISTRY_SOURCES=${regsources}"]) {
				sh '''
				buildah login -u "$docker_username" -p "$docker_password" "$docker_server"
				buildah push "$imagetag" "docker://$docker_server/$imagetag"
				'''
			}
		}
	}
}
