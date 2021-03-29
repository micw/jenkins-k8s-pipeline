package pipeline

@groovy.transform.InheritConstructors
class NodeStepModel extends AbstractStepModel {

	String dir = null
	void dir(String dir) {
		this.dir=dir
	}

	int nodeVersionNumber=13

	void nodeVersion(int nodeVersionNumber) {
		this.nodeVersionNumber=nodeVersionNumber
	}

	@Override
	List getExtraContainers(config) {
		bodyClosure(config,[:])
		return [[
			name: "node"+nodeVersionNumber,
			image: "library/node:"+nodeVersionNumber+"-slim",
			command: 'cat',
			ttyEnabled: true,
			alwaysPullImage: true
		]]
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

		steps.stage(stepName) {
			steps.container("node"+nodeVersionNumber) {
				body()
			}
		}
	}
}