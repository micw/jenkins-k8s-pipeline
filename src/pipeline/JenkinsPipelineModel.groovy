package pipeline

import groovy.inspect.swingui.AstNodeToScriptVisitor

class JenkinsPipelineModel {

	def config=new PipelineConfigModel()
	def pipelineSteps=[]
	def vars=[:]

	def config(body) {
		body.delegate=config
		body.resolveStrategy = Closure.DELEGATE_ONLY
		body()
	}

	def maven(String stepName='Maven build', Closure body) {
		def model=new MavenStepModel(stepName,body,vars);
		pipelineSteps.add(model)
	}

	def docker(String stepName='Docker build', Closure body) {
		def model=new DockerStepModel(stepName,body,vars)
		pipelineSteps.add(model)
	}

	def node(String stepName='Node build', Closure body) {
		def model=new NodeStepModel(stepName,body,vars)
		pipelineSteps.add(model)
	}

	def k8s(String stepName='K8S deployment', Closure body) {
		def model=new K8SStepModel(stepName,body,vars)
		pipelineSteps.add(model)
	}

	public void execute(Map globals) {

		config.execute(globals)

		def steps=globals.steps

		// TODO: launch only containers required for the used steps
		def containers=[
				steps.containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:3.29-1-alpine',  args: '${computer.jnlpmac} ${computer.name}', alwaysPullImage: true),
				steps.containerTemplate(name: 'docker', image: 'docker:18.09-dind', privileged: true, alwaysPullImage: true, args: '--mtu 1350'),
				steps.containerTemplate(name: 'maven-java8', image: 'evermind/jenkins-maven:3-jdk-8-slim', command: 'cat', ttyEnabled: true, alwaysPullImage: true),
				steps.containerTemplate(name: 'maven-java11', image: 'evermind/jenkins-maven:3-jdk-11-slim', command: 'cat', ttyEnabled: true, alwaysPullImage: true),
				steps.containerTemplate(name: 'node13', image: 'library/node:13-slim', command: 'cat', ttyEnabled: true, alwaysPullImage: true),
				steps.containerTemplate(name: 'k8s', image: 'dtzar/helm-kubectl:3.1.2', command: 'cat', ttyEnabled: true, alwaysPullImage: true),
			]

		def buildSlaveLabel="${UUID.randomUUID().toString()}"
		steps.podTemplate(
			label: buildSlaveLabel,
			volumes: [
				steps.emptyDirVolume(mountPath: '/home/jenkins', memory: false)
			],
			containers: containers) {	
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
					// Workaround for https://issues.jenkins-ci.org/browse/JENKINS-43563, see README.md
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
