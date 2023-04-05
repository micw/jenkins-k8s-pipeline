package pipeline

abstract class AbstractStepModel {

	String stepName
	Closure bodyClosure
	Map globals
	Map vars
	def env

	Closure beforeClosure
	Closure afterClosure

	AbstractStepModel(String stepName, Closure bodyClosure, Map globals, Map vars) {
		this.stepName=stepName
		this.bodyClosure=bodyClosure
		this.globals=globals
		this.vars=vars
		this.env=globals.env
		bodyClosure.resolveStrategy = Closure.DELEGATE_ONLY
		bodyClosure.delegate=this
	}

	void before(Closure beforeClosure) {
		this.beforeClosure=beforeClosure
	}
	void after(Closure afterClosure) {
		this.afterClosure=afterClosure
	}

	List getExtraContainers(config) {
		return []
	}

	List getExtraVolumes(config) {
		return []
	}

	String vars(String name) {
		if (vars[name]) {
			return vars[name]
		}
		throw new Exception("Model variable ${name} is not defined.")
	}

	void execute(config) {
		bodyClosure()
		doExecute(config)
	}

	abstract void doExecute(config)

	void runBeforeScripts(config) {
		if (beforeClosure) {
			beforeClosure.delegate=globals.jenkins
			beforeClosure.resolveStrategy=Closure.DELEGATE_FIRST
			beforeClosure()
		}
	}
	void runAfterScripts(config) {
		if (afterClosure) {
			afterClosure.delegate=globals.jenkins
			afterClosure.resolveStrategy=Closure.DELEGATE_FIRST
			afterClosure()
		}
	}

}
