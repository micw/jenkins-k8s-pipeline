package pipeline

@groovy.transform.InheritConstructors
class CustomStepModel extends AbstractStepModel {

	String dir = null
	void dir(String dir) {
		this.dir=dir
	}

	String containerName = null
	List extraContainers = []
	List extraVolumes = []

	void container(Map extraArgs=[:],String name) {
		// https://stackoverflow.com/questions/18149102/groovy-method-with-optional-parameters
		containerName=name

		if (extraArgs["image"]!=null) {
			if (extraArgs["tmpfs"]!=null) {
				extraVolumes.add(globals.steps.emptyDirVolume(mountPath: extraArgs["tmpfs"], memory: true));
			}

			def extraContainer=[
				name: name,
				image: extraArgs["image"],
				ttyEnabled: true,
				alwaysPullImage: extraArgs["alwaysPullImage"]?:false
				]
			if (extraArgs["command"]!=null) {
				extraContainer["command"]=extraArgs["command"]
			}
			if (extraArgs["env"]!=null) {
				extraContainer["envVars"]=[]
				for (e in extraArgs["env"]) {
					extraContainer["envVars"].add(
						globals.steps.envVar(key:e.key,value:e.value)
					)
				}
			}
			extraContainers.add(extraContainer)
		}
	}

	@Override
	List getExtraContainers(config) {
		bodyClosure(config,[:])
		return extraContainers
	}
	@Override
	List getExtraVolumes(config) { // must be called after getExtraContainers()
		return extraVolumes
	}

	Closure runClosure

	void run(Closure runClosure) {
		this.runClosure=runClosure
	}

	void execute(config) {
		doExecute(config)
	}

	void doExecute(config) {

		def steps=globals.steps

		def body={
			runBeforeScripts(config)
			if (runClosure) {
				runClosure.delegate=globals.jenkins
				runClosure.resolveStrategy=Closure.DELEGATE_FIRST
				runClosure()
			}
			runAfterScripts(config)
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

		// Run in container
		if (containerName) {
			def innerBody=body
			body={
				steps.container(containerName) {
					innerBody()
				}
			}
		}

		steps.stage(stepName) {
			body()
		}
	}
}
