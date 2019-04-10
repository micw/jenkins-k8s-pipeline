package pipeline

abstract class AbstractStepModel {

	def bodyClosure
	Map vars

	AbstractStepModel(bodyClosure,vars) {
		this.bodyClosure=bodyClosure
		this.vars=vars
		bodyClosure.delegate=this
		bodyClosure.resolveStrategy = Closure.DELEGATE_ONLY
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

}
