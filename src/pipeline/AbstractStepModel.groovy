package pipeline

abstract class AbstractStepModel {

	String stepName
	Closure bodyClosure
	Map vars

	Closure beforeClosure
	Closure afterClosure

	AbstractStepModel(String stepName, Closure bodyClosure,Map vars) {
		this.stepName=stepName
		this.bodyClosure=bodyClosure
		this.vars=vars
		bodyClosure.delegate=this
		bodyClosure.resolveStrategy = Closure.DELEGATE_ONLY
	}

	void before(Closure beforeClosure) {
		this.beforeClosure=beforeClosure
	}
	void after(Closure afterClosure) {
		this.afterClosure=afterClosure
	}


	String vars(String name) {
		if (vars[name]) {
			return vars[name]
		}
		throw new Exception("Model variable ${name} is not defined.")
	}

	void execute(config,Map globals) {
		bodyClosure()
		doExecute(config,globals)
	}

	abstract void doExecute(config,Map globals)

	void runBeforeScripts(config,Map globals) {
		if (beforeClosure) {
			beforeClosure.delegate=globals.jenkins
			beforeClosure.resolveStrategy=Closure.DELEGATE_FIRST
			beforeClosure()
		}
	}
	void runAfterScripts(config,Map globals) {
		if (afterClosure) {
			afterClosure.delegate=globals.jenkins
			afterClosure.resolveStrategy=Closure.DELEGATE_FIRST
			afterClosure()
		}
	}

}
