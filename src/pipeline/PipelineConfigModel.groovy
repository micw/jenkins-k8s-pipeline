package pipeline

class PipelineConfigModel {

	String dockerRegistry
	String dockerRegistryCredentialsId

	def dockerRegistry(registry,credentialsId=null) {
		dockerRegistry=registry
		dockerRegistryCredentialsId=credentialsId
	}

	String mavenSettingsId

	def mavenSettings(settingsId) {
		mavenSettingsId=settingsId
	}
}