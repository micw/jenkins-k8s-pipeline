package pipeline

@groovy.transform.InheritConstructors
class MavenStepModel extends AbstractStepModel {

	boolean deploy=false
	boolean skipTests=false

	void deploy(boolean deploy=true) {
		this.deploy=deploy
	}

	void skipTests(boolean skipTests=true) {
		this.skipTests=skipTests
	}

	void doExecute(config,Map globals) {

		def steps=globals.steps

		def goal=deploy?"deploy":"verify"

		def mavenCommand="""
			export _JAVA_OPTIONS=-Djdk.net.URLClassPath.disableClassPathURLCheck=true
			mvn -B -DargLine='-Djava.security.egd=file:///dev/urandom' -Dmaven.test.failure.ignore=false -Dmaven.test.skip=${skipTests} ${goal}
		"""
		def body={

			def pomInfo=steps.readMavenPom(file: 'pom.xml')
			vars['MAVEN_GROUP']=pomInfo.groupId
			vars['MAVEN_ARTIFACT']=pomInfo.artifactId
			vars['MAVEN_VERSION']=pomInfo.version
			def mavenVersion
			if (globals.gitInfo.isMaster || globals.gitInfo.isTag) {
				mavenVersion=pomInfo.version
			} else {
				mavenVersion=pomInfo.version.replace("-SNAPSHOT","")+"-"+globals.gitInfo.name.replace("/","-")+"-SNAPSHOT"
				mavenCommand = """
					mvn versions:set -DnewVersion='${mavenVersion}'
					${mavenCommand}
				"""
			}
			globals.currentBuild.description=mavenVersion

			steps.sh(mavenCommand)
		}
		// if maven settings is configured, wrap this in "withMaven"
		if (config.mavenSettingsId) {
			def innerBody=body
			body={
				steps.withMaven(mavenSettingsConfig: config.mavenSettingsId) {
					innerBody()
				}
			}
		}
		// if docker registry is configured, wrap this in "docker.withRegistry"
		// also extend the shell command to make the docker registry settings usable
		if (config.dockerRegistry) {
			def innerBody=body
			mavenCommand = """
				rm -rf ~/.docker; ln -s \${DOCKER_CONFIG} ~/.docker
				export DOCKER_HOST=127.0.0.1
				${mavenCommand}
			"""
			body={
				globals.docker.withRegistry("https://${config.dockerRegistry}",config.dockerRegistryCredentialsId)  {
					innerBody()
				}
			}
		}
		steps.stage('Maven build') {
			steps.container("maven") {
				body()
			}
		}
	}
}