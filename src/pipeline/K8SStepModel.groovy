package pipeline

@groovy.transform.InheritConstructors
class K8SStepModel extends AbstractStepModel {

	String dir = null
	void dir(String dir) {
		this.dir=dir
	}

	void doExecute(config,Map globals) {

		def steps=globals.steps


		def body={
			runBeforeScripts(config,globals)
            // TBD
			runAfterScripts(config,globals)
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

		if (config.kubeconfigCredentialsId) {
			def innerBody=body
			body={
				steps.configFileProvider([steps.configFile(fileId: config.kubeconfigCredentialsId, variable: 'KUBECONFIG')]) {
					innerBody()
				}
			}
		}


		steps.stage(stepName) {
			steps.container("k8s") {
				body()
			}
		}
	}
}
