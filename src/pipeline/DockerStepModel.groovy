package pipeline

@groovy.transform.InheritConstructors
class DockerStepModel extends AbstractStepModel {

	String imageName = null

	void imageName(String imageName) {
		this.imageName=imageName
	}

	String tag = null

	void tag(String tag) {
		if (tag!=null) {
			tag=tag.replace("/","-")
		}
		this.tag=tag
	}

	void doExecute(config,Map globals) {

		def steps=globals.steps

		def imageName=this.imageName

		if (!imageName) {
			steps.error("Docker imageName is not set!")
		}

		if (!tag) {
			tag(globals.gitInfo.name)
			steps.echo("Docker tag is not set. Using '${tag}' derived from git tag or branch")
		}

		def body={
			steps.echo("Building docker image '${imageName}'")
			steps.sh("docker build . -t ${imageName}:${tag}")
			steps.echo("Pushing docker image '${imageName}' with tag '${tag}'")
			steps.sh("docker push ${imageName}:${tag}")
		}

		// if docker registry is configured, wrap this in "docker.withRegistry"
		// and add the registry to the image name
		if (config.dockerRegistry) {
			imageName="${config.dockerRegistry}/${imageName}"
			def innerBody=body
			body={
				globals.docker.withRegistry("https://${config.dockerRegistry}",config.dockerRegistryCredentialsId)  {
					innerBody()
				}
			}
		}
		steps.stage('Docker build') {
			steps.container("docker") {
				body()
			}
		}
	}
}