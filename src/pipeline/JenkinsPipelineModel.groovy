package pipeline

class JenkinsPipelineModel {

	def config=new PipelineConfigModel()
	def pipelineSteps=[]
	def vars=[:]

	def config(body) {
		body.delegate=config
		body.resolveStrategy = Closure.DELEGATE_ONLY
		body()
	}

	def maven(body) {
		pipelineSteps.add(new MavenStepModel(body,vars))
	}

	def docker(body) {
		pipelineSteps.add(new DockerStepModel(body,vars))
	}

	public void execute(Map globals) {
		def steps=globals.steps
		steps.properties properties: [
			steps.disableConcurrentBuilds(),
			steps.buildDiscarder(steps.logRotator(numToKeepStr: '10')),
			steps.disableResume(),
		]
		def buildSlaveLabel="${UUID.randomUUID().toString()}"
		steps.podTemplate(
			label: buildSlaveLabel,
			containers: [
				steps.containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:alpine',  args: '${computer.jnlpmac} ${computer.name}', alwaysPullImage: true),
				steps.containerTemplate(name: 'docker', image: 'docker:stable-dind', privileged: true, alwaysPullImage: true),
				steps.containerTemplate(name: 'maven', image: 'evermind/jenkins-maven:3-jdk-8-slim', command: 'cat', ttyEnabled: true, alwaysPullImage: true),
			]) {	
			steps.node(buildSlaveLabel) {
				GitInfo gitInfo=new GitInfo()
				steps.stage('Checkout SCM') {
					def scmvars=steps.checkout(globals.scm)
					def gittag=steps.sh(script:"git tag -l --points-at HEAD",returnStdout: true).trim()
					if (gittag) {
						gitInfo.isTag=true
						gitInfo.name=gittag
					} else {
						gitInfo.name=scmvars.GIT_BRANCH
						gitInfo.isMaster=gitInfo.name=='master'
					}
					steps.echo "git info: ${gitInfo}"
				}
				globals['gitInfo']=gitInfo

				for (step in pipelineSteps) {
					step.execute(config,globals)
				}
			}
		}
	}
}