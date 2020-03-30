package pipeline

@groovy.transform.InheritConstructors
class NodeStepModel extends AbstractStepModel {

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

		steps.stage('Node build') {
			steps.container("node13") {
				body()
			}
		}
	}
}