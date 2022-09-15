package pipeline

import groovy.inspect.swingui.AstNodeToScriptVisitor

class JenkinsPipelineImpl {

	def config=new PipelineConfigModel()
	def pipelineSteps=[]
	def vars=[:]
	def stepContainers=[:]
	Map globals

	JenkinsPipelineImpl(Map globals) {
		this.globals=globals
	}

	def config(body) {
		body.delegate=config
		body.resolveStrategy = Closure.DELEGATE_ONLY
		body()
	}

	def maven(String stepName='Maven build', Closure body) {
		def model=new MavenStepModel(stepName,body,globals,vars);
		pipelineSteps.add(model)
	}

	def docker(String stepName='Docker build', Closure body) {
		def model=new DockerStepModel(stepName,body,globals,vars)
		pipelineSteps.add(model)
	}

	def node(String stepName='Node build', Closure body) {
		def model=new NodeStepModel(stepName,body,globals,vars)
		pipelineSteps.add(model)
	}

	def k8s(String stepName='K8S deployment', Closure body) {
		def model=new K8SStepModel(stepName,body,globals,vars)
		pipelineSteps.add(model)
	}

	def custom(String stepName='Custom step', Closure body) {
		def model=new CustomStepModel(stepName,body,globals,vars)
		pipelineSteps.add(model)
	}

	public void notifyBuildFailure(error) {
		if (config.rocketChatChannel) {
			def message=":exclamation: :crying_cat_face: Build failed: "+error+" - ${globals.env.JOB_NAME} (<${globals.env.BUILD_URL}|#${globals.env.BUILD_NUMBER}>)"
			globals.steps.rocketSend channel: config.rocketChatChannel, rawMessage: true, message: message
		}
	}
	public void notifyBuildOk() {
		if (config.rocketChatChannel) {

			def message=":white_check_mark: :cat: Build OK - ${globals.env.JOB_NAME} (<${globals.env.BUILD_URL}|#${globals.env.BUILD_NUMBER}>)"
			// https://support.cloudbees.com/hc/en-us/articles/217630098-How-to-access-Changelogs-in-a-Pipeline-Job-
			def firstChange=true
			def changeLogSets = globals.currentBuild.changeSets
			for (int i = 0; i < changeLogSets.size(); i++) {
				def entries = changeLogSets[i].items
				for (int j = 0; j < entries.length; j++) {
					def entry = entries[j]
					if (firstChange) {
						firstChange=false
						message+="\nChangelog:"
					}
					message+="\n  ${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}"
				}
			}
			globals.steps.rocketSend channel: config.rocketChatChannel, rawMessage: true, message: message
		}
	}

	public void execute() {
		config.execute(globals)

		def steps=globals.steps

		def containers=[
				steps.containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:4.13.2-1-jdk11',  args: '${computer.jnlpmac} ${computer.name}', alwaysPullImage: true),
				steps.containerTemplate(name: 'docker', image: 'docker:18.09-dind', privileged: true, alwaysPullImage: false, args: '--mtu 1350')
			]

		def volumes=[
			steps.emptyDirVolume(mountPath: '/home/jenkins', memory: false)
		]

		// launch additional containers required for the used steps
		Set containerNames=[]
		// set some initial vars to allow initial body block evaluation
		vars['GIT_BRANCH_OR_TAG_NAME']=''
		for (step in pipelineSteps) {
			for (c in step.getExtraContainers(config)) {
				if (containerNames.add(c.name)) {
					if (c.tmpfs!=null) {
						steps.emptyDirVolume(mountPath: c.tmpfs, memory: true)
					}
					c.remove("tmpfs")

					containers.add(steps.containerTemplate(c))
				}
			}
		}

		def buildSlaveLabel="${UUID.randomUUID().toString()}"
		steps.podTemplate(
			label: buildSlaveLabel,
			volumes: volumes,
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
					step.execute(config)
					if (globals.stopBuildPipeline) {
						break
					}
				}
				notifyBuildOk()
			}
		}
	}
}
