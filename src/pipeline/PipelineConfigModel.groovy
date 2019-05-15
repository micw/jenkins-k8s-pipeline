package pipeline

class PipelineConfigModel {

	String dockerRegistry
	String dockerRegistryCredentialsId
	boolean executed=false
	Map globals
	List jobParameters=[]

	def dockerRegistry(registry,credentialsId=null) {
		dockerRegistry=registry
		dockerRegistryCredentialsId=credentialsId
	}

	String mavenSettingsId

	def mavenSettings(settingsId) {
		mavenSettingsId=settingsId
	}

	def booleanParameter(String name,String description='',boolean defaultValue=false) {
		jobParameters.add([
			$class: 'BooleanParameterDefinition',
			name: name,
			description: description,
			defaultValue: defaultValue
			])
		updateProperties()
	}
	def stringParameter(String name,String description='',String defaultValue=false) {
		jobParameters.add([
			$class: 'StringParameterDefinition',
			name: name,
			description: description,
			defaultValue: defaultValue
			])
		updateProperties()
	}

	def execute(Map globals) {
		this.globals=globals
		doExecute()
	}

	/**
	 * Execution sets some job properties.
	 * This is called once when the pipeline is set up. If a step adds/changes some build options later, it's re-executed
	 */
	def doExecute() {
		def steps=this.globals.steps

		if (this.jobParameters.isEmpty()) { // avoid the build to be "parameterized" when no params are set
			steps.properties properties: [
				steps.disableConcurrentBuilds(),
				steps.buildDiscarder(steps.logRotator(numToKeepStr: '10')),
				steps.disableResume()
			]
		} else {
			steps.properties properties: [
				steps.disableConcurrentBuilds(),
				steps.buildDiscarder(steps.logRotator(numToKeepStr: '10')),
				steps.disableResume(),
				steps.parameters(this.jobParameters)
			]
		}
		this.executed=true
	}

	/**
	 * This runs doExcete but only when the properties were already set
	 */
	def updateProperties() {
		if (this.executed) {
			doExecute()
		}
	}


}