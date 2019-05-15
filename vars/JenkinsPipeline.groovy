#!/usr/bin/env groovy

def call(body) {
	model=new pipeline.JenkinsPipelineModel()
	body.delegate=model
	body.resolveStrategy = Closure.DELEGATE_ONLY
	body()

	model.execute([
		env: env,
		steps: steps,
		currentBuild: currentBuild,
		scm: scm,
		docker: docker,
		stopBuildPipeline: false
		])
}
