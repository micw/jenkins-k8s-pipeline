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

	def maven(Closure body) {
		pipelineSteps.add(new MavenStepModel(body,vars))
	}

	def docker(Closure body) {
		pipelineSteps.add(new DockerStepModel(body,vars))
	}

	public void execute(Map globals) {

		config.execute(globals)

		def steps=globals.steps

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
					def scmvars=steps.checkout([
						$class: 'GitSCM',
						branches: globals.scm.branches,
						extensions: globals.scm.extensions + [[$class: 'LocalBranch'], [$class: 'CleanCheckout']],
						userRemoteConfigs: globals.scm.userRemoteConfigs
					])
					if (globals.scm.userRemoteConfigs.size()>0 && globals.scm.userRemoteConfigs[0].credentialsId) {
						globals['gitCredentialsId']=globals.scm.userRemoteConfigs[0].credentialsId
					}
					if (scmvars.GIT_AUTHOR_NAME && scmvars.GIT_AUTHOR_EMAIL) {
						steps.sh(script:"""
							git config user.name '${scmvars.GIT_AUTHOR_NAME}'
							git config user.email '${scmvars.GIT_AUTHOR_EMAIL}'
							""")
					}
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
				vars['GIT_BRANCH_OR_TAG_NAME']=gitInfo.name


				for (step in pipelineSteps) {
					step.execute(config,globals)
					if (globals.stopBuildPipeline) {
						break
					}
				}
			}
		}
	}
}