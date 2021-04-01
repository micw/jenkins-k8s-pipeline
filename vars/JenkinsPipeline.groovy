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
	body()

	model.execute()
}
