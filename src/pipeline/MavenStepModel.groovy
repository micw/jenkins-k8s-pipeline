package pipeline

@groovy.transform.InheritConstructors
class MavenStepModel extends AbstractStepModel {

	boolean deploy=false
	boolean skipTests=false
	List mavenReleaseBranches=[]

	void deploy(boolean deploy=true) {
		this.deploy=deploy
	}

	void skipTests(boolean skipTests=true) {
		this.skipTests=skipTests
	}

	void enableReleases(String... mavenReleaseBranches) {
		if (mavenReleaseBranches.length==0) {
			this.mavenReleaseBranches=["master"]
		} else {
			this.mavenReleaseBranches=mavenReleaseBranches as List
		}
	}

	void doExecute(config,Map globals) {

		def steps=globals.steps

		def body={

			def pomInfo=steps.readMavenPom(file: 'pom.xml')

			if ((globals.gitInfo.name in this.mavenReleaseBranches) && !globals.gitInfo.isTag) {
				config.booleanParameter("MAVEN_RELEASE","Release a new maven version",false)
				config.stringParameter("NEW_MAVEN_VERSION","Version to use for release",pomInfo.version.replace("-SNAPSHOT",""))
			}

			vars['MAVEN_GROUP']=pomInfo.groupId
			vars['MAVEN_ARTIFACT']=pomInfo.artifactId
			vars['MAVEN_VERSION']=pomInfo.version

			if (globals.env.MAVEN_RELEASE && globals.env.MAVEN_RELEASE=="true") {

				def releaseVersion="${globals.env.NEW_MAVEN_VERSION}"
				def releaseTag="release-${releaseVersion}"

				def goal="release:prepare -DpreparationGoals='verify' -DtagNameFormat='${releaseTag}' -DreleaseVersion=${releaseVersion}"

				def mavenCommand="""
					export _JAVA_OPTIONS=-Djdk.net.URLClassPath.disableClassPathURLCheck=true
					export DOCKER_HOST=127.0.0.1
					[ -z "${DOCKER_CONFIG} ] || ( rm -rf ~/.docker; ln -s \${DOCKER_CONFIG} ~/.docker )
					mvn -B -DargLine='-Djava.security.egd=file:///dev/urandom' -Dmaven.test.failure.ignore=false -Dmaven.test.skip=${skipTests} ${goal}
				"""

				globals.currentBuild.description="Prepare release of ${releaseVersion}"

				steps.sh(mavenCommand)
				globals.currentBuild.result = 'NOT_BUILT'
				globals.stopBuildPipeline=true // stop after this step
			} else {
				def goal=deploy?"deploy":"verify"

				def mavenCommand="""
					export _JAVA_OPTIONS=-Djdk.net.URLClassPath.disableClassPathURLCheck=true
					export DOCKER_HOST=127.0.0.1
					[ -z "\${DOCKER_CONFIG}" ] || ( rm -rf ~/.docker; ln -s \${DOCKER_CONFIG} ~/.docker )
					mvn -B -DargLine='-Djava.security.egd=file:///dev/urandom' -Dmaven.test.failure.ignore=false -Dmaven.test.skip=${skipTests} ${goal}
				"""

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

				runBeforeScripts(config,globals)
				steps.sh(mavenCommand)
				runAfterScripts(config,globals)
			}
		}

		// credentials are used for git -> provide the same credentials with ssh-agent
		if (globals.gitCredentialsId) {
			def innerBody=body
			body={
				steps.sshagent([globals.gitCredentialsId]) {
					innerBody()
				}
			}
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