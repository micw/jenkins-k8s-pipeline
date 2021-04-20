#!/usr/bin/env groovy

def call(body) {

	def globals=[
		jenkins: body.owner,
		env: env,
		steps: steps,
		currentBuild: currentBuild,
		scm: scm,
		docker: docker,
		stopBuildPipeline: false
		]

	model=new pipeline.JenkinsPipelineModel(globals)
	body.delegate=model
	body.resolveStrategy = Closure.DELEGATE_ONLY

	try {
		body()
		model.execute()
	} catch (e) {
		node {
			sh "echo error 1"
			model.notifyBuildFailure(e)
			throw(e)
			sh "echo error 2"
		}
	}
}
