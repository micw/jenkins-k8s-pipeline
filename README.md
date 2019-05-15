# About

This is an implementation of a Jenkins build pipeline which uses the Jenkins Kubernetes Plug-In to create dynamic build slaves  .

## Features

* Easy to use

* maven build
   * Deploy maven artifacts
   * Automatically add branchname to maven snapshot version
   * Configure which maven setting to use

* docker build
   * determine docker tag from git branch/tag
   * Configure which docker setting to use


# Usage

## Jenkins setup

### Pipeline script settings

* Go to "Manage Jenkins" -> "Configure System" -> Subsection "Global Pipeline Libraries"
* Add a new Library
    * Name: JenkinsPipeline
    * Retrieval method: Modern SCM
    * Git Project Repository: (Github URL of this repository - use HTTPS for anonymous access)
    * Behaviours: Discover branches, Discover tags

### Kubernetes settings

* When installing Jenkins from HELM chart, Kubernetes slaves are already set up by default
* Otherwise go to "Manage Jenkins" -> "Configure System" -> Subsection "Cloud" and Add a cloud of type "kubernetes"
* The pipeline expects the cloud to be named "kubernetes". This will be configurable in a later version.

### Maven settings

* Go to "Credentials" and create username/password credentials for your maven repository
* Go to "Manage Jenkins" -> "Managed files" and create a new "Maven settings.xml"
* Add your maven settings.xml. Use a speaking ID, e.g. my-maven-settings which can later be referenced in the pipeline
* Don't put credentials into the settings.xml, add it via the "credentials" feature of Jenkins
* The following example configures maven with
    * A custom maven repository that mirrors "central" - maven will try to load everything from there by default (allowing the pom.xml contains additional repos)
    * Using the custom repository to load snapshots and releases of artifacts
    * Using the custom repository to load snapshots and releases of maven plug-ins
    * Setting different paths in the the custom repository to be used for deployment of snapshot and release artifacts (no need to specify this in your project POMs)
    * Ensure that credentials with ServerId "nexus" are added that have read/write access to that repository
    * It's also possible to have different users for read and deploy. To do so, replace the "nexus::" part in altReleaseDeploymentRepository and/or altSnapshotDeploymentRepository with a different name and add credentials with ServerId matching this name.

```
<?xml version="1.0" encoding="UTF-8"?>


<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" 
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">


  <interactiveMode>false</interactiveMode>
  <mirrors>
    <mirror>
      <id>nexus</id>
      <mirrorOf>central</mirrorOf>
      <url>https://nexus.mycompany.com/nexus/content/groups/public</url>
    </mirror>
  </mirrors>
  <profiles>
    <profile>
      <id>nexus</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>http://central</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>central</id>
          <url>http://central</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
         </pluginRepository>
      </pluginRepositories>
      <properties>
        <altReleaseDeploymentRepository>nexus::default::https://nexus.mycompany.com/nexus/content/repositories/releases</altReleaseDeploymentRepository>
        <altSnapshotDeploymentRepository>nexus::default::https://nexus.mycompany.com/nexus/content/repositories/snapshots</altSnapshotDeploymentRepository>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>nexus</activeProfile>
  </activeProfiles>

</settings>
```

### Docker settings

* Add credentials for your docker registry with type "username/password"
* Use a speaking ID, e.g. my-docker-credentials which can later be referenced in the pipeline


## Project's Jenkinsfile

Create file Jenkinsfile.groovy which uses the pipeline. It has the following syntax:

```
@Library("JenkinsPipeline@feature/v001") _

JenkinsPipeline {
    config {
        mavenSettings("my-maven-settings")
        dockerRegistry("registry.mydomain.com","my-docker-credentials")
    }
    maven {
        deploy(true)
        skipTests(false)
    }
    docker {
        imageName("${vars.MAVEN_ARTIFACT}")
        tag("${vars.MAVEN_VERSION}")
    }
}
```

* The first line imports this pipeline script library. The part after the @ references a tag or branch. For build stability, you should use a release-tag in your projects
* The JenkinsPipeline block calls the pipeline script
* The config section configures some basics:
    * mavenSettings() references the ID of the Maven settings.xml described above
    * dockerRegistry() tells docker to use this registry URL (optionally with a credentials ID)
        * if set, all steps will be run with this docker registry configured (e.g. maven tasks that pull docker images will use it)
* If a maven section is present, a maven build will be done
    * if deploy is set to false (default) "mvn verify" is executed. If set to true, "mvn deploy" is executed
    * if skipTests is set to false, maven tests are skipped
    * The maven section adds MAVEN_GROUP, MAVEN_ARTIFACT and MAVEN_VERSION to the "vars" which can be referenced by other sections
* If a docker section is present, a docker build+push will be performed
    * imageName must be set
    * tag may be set. If not set, the current git branch or tag will be used.
