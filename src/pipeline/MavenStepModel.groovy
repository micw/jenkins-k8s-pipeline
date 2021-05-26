package pipeline

@groovy.transform.InheritConstructors
class MavenStepModel extends AbstractStepModel {

	boolean deploy=false
	boolean skipTests=false
	boolean appendBranchToVersion=true
	List appendBranchToVersionExceptBranches=["master"]
	String extraOpts=""
	List mavenReleaseBranches=[]
	String javaOpts = "-Djava.security.egd=file:///dev/urandom -Duser.language=de -Duser.region=DE -Dfile.encoding=UTF8"
	int javaVersionNumber=8


	String dir = null
	void dir(String dir) {
		this.dir=dir
	}

	void deploy(boolean deploy=true) {
		this.deploy=deploy
	}

	void skipTests(boolean skipTests=true) {
		this.skipTests=skipTests
	}

	void javaVersion(int javaVersionNumber) {
		this.javaVersionNumber=javaVersionNumber
	}

	void appendBranchToVersion(boolean appendBranchToVersion=true, String... appendBranchToVersionExceptBranches) {
		this.appendBranchToVersion=appendBranchToVersion
		if (appendBranchToVersionExceptBranches.length==0) {
			this.appendBranchToVersionExceptBranches=["master"]
		} else {
			this.appendBranchToVersionExceptBranches=appendBranchToVersionExceptBranches as List
		}
	}

	void options(String extraOpts="") {
		this.extraOpts=extraOpts
	}

	void enableReleases(String... mavenReleaseBranches) {
		if (mavenReleaseBranches.length==0) {
			this.mavenReleaseBranches=["master"]
		} else {
			this.mavenReleaseBranches=mavenReleaseBranches as List
		}
	}

	Closure beforeReleaseClosure
	void beforeRelease(Closure beforeReleaseClosure) {
		this.beforeReleaseClosure=beforeReleaseClosure
	}

	void runBeforeReleaseScripts(config) {
		if (beforeReleaseClosure) {
			beforeReleaseClosure.delegate=globals.jenkins
			beforeReleaseClosure.resolveStrategy=Closure.DELEGATE_FIRST
			beforeReleaseClosure()
		}
	}

	@Override
	List getExtraContainers(config) {
		bodyClosure(config,[:])
		return [[
			name: "maven-java"+javaVersionNumber,
			image:"evermind/jenkins-maven:3-jdk-"+javaVersionNumber+"-slim",
			command: 'cat',
			ttyEnabled: true,
			alwaysPullImage: true
		]]
	}

	void doExecute(config) {

		def steps=globals.steps

		def body={

			def pomInfo=steps.readMavenPom(file: 'pom.xml')


			if (pomInfo.version.contains('${')) { // version contains a variable - need to run maven to get the real version
				pomInfo.version=steps.sh(returnStdout: true, script: 'mvn -B help:evaluate -Dexpression=project.version -q -DforceStdout | tail -1').trim()
			}

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

				def goal="release:prepare -Darguments=-Dmaven.test.skip=${skipTests} -DpreparationGoals='verify' -DtagNameFormat='${releaseTag}' -DreleaseVersion=${releaseVersion}"

		/*
		export _JAVA_OPTIONS="-Djdk.net.URLClassPath.disableClassPathURLCheck=true -Duser.language=de -Duser.region=DE -Dfile.encoding=UTF8"
		*/
				def mavenCommand="""
					export _JAVA_OPTIONS="-Djdk.net.URLClassPath.disableClassPathURLCheck=true"
					mvn -B -DargLine='${javaOpts}' -Dmaven.test.failure.ignore=false -Dmaven.test.skip=${skipTests} ${extraOpts} ${goal}
				"""

				globals.currentBuild.description="Prepare release of ${releaseVersion}"

				runBeforeReleaseScripts(config)
				steps.sh(mavenCommand)
				globals.currentBuild.result = 'NOT_BUILT'
				globals.stopBuildPipeline=true // stop after this step
			} else {
				def goal=deploy?"deploy":"verify"

				def mavenCommand="""
					export _JAVA_OPTIONS="-Djdk.net.URLClassPath.disableClassPathURLCheck=true"
					mvn -B -DargLine='${javaOpts}' -Dmaven.test.failure.ignore=false -Dmaven.test.skip=${skipTests} ${extraOpts} ${goal}
				"""

				def mavenVersion
				if (globals.gitInfo.isTag || !appendBranchToVersion || (globals.gitInfo.name in this.appendBranchToVersionExceptBranches)) {
					mavenVersion=pomInfo.version
				} else {
					mavenVersion=pomInfo.version.replace("-SNAPSHOT","")+"-"+globals.gitInfo.name.replace("/","-")+"-SNAPSHOT"
					mavenCommand = """
						mvn versions:set -DnewVersion='${mavenVersion}'
						${mavenCommand}
					"""
				}
				globals.currentBuild.description=mavenVersion

				// setup docker environment
				steps.sh("""
					[ -z "\${DOCKER_CONFIG}" ] || ( rm -rf ~/.docker; ln -s \${DOCKER_CONFIG} ~/.docker )
				""")

				runBeforeScripts(config)
				steps.sh(mavenCommand)
				runAfterScripts(config)
			}
		}

		// change directory before build?
		if (dir) {
			def innerBody=body
			body={
				steps.dir(dir) {
					innerBody()
				}
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
		steps.stage(stepName) {
			steps.container("maven-java"+javaVersionNumber) {
				steps.withEnv(['DOCKER_HOST=tcp://127.0.0.1:2375']) {
					body()
				}
			}
		}
	}
}
