# About

This is an implementation of a Jenkins build pipeline which uses the Jenkins Kubernetes Plug-In to create dynamic build slaves.


## Features

* To be used with multibranch-pipeline jobs
* Easy to use

* maven build
   * Deploy maven artifacts
   * Automatically add branchname to maven snapshot version
   * Configure which maven settings.xml to use (provided by jenkins)
   * perform maven releases "the pipeline way"
       * On demand, a job tags a release and increases the maven version number
       * Jenkins picks up the tag and builds the release it using the same pipeline that is used for non-release builds
       * The maven release plug-in is only be used for tagging and versioning, so all pipeline steps required for release are performed (not only the maven part)

* docker build
   * determine docker tag from git branch/tag
   * Configure which docker setting to use

* Build environment (currently fixed, will be extended and make customizable in later versions)
  * jenkins/jnlp-slave:alpine (until 3.27-2-alpine or higher is released which contains https://github.com/jenkinsci/docker-jnlp-slave/pull/80)
  * docker:stable-dind
  * evermind/jenkins-maven:3-jdk-8-slim (Maven 3.x, OpenJDK 8.x)
      * This is basically the official image but running with the same user id as jenkins slave does which is important to avoid lots of unexpected behaviour (e.g. non-working ssh client due to file owner mismatch)

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

* When installing Jenkins from HELM chart (https://github.com/helm/charts/tree/master/stable/jenkins), Kubernetes slaves are already set up by default
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

* If you use a private docker registry that requires authentification, go to "Credentials" and add one of type "username/password"
* Use a speaking ID, e.g. my-docker-credentials which can later be referenced in the pipeline

### Git Settings

* To be able to commit tags during maven releases, you need to setup email and name for git
* Go to "Manage Jenkins" -> "Configure System" -> Subsection "Git plugin"
* Set "Global Config user.name Value" and "Global Config user.email Value"


## Project's Jenkinsfile

To use the pipeline in a maven (or other) project, create a file Jenkinsfile.groovy in the root of your project's git repository. It has the following syntax:

```
@Library("JenkinsPipeline@v0.6") _

JenkinsPipeline {
    config {
        mavenSettings("my-maven-settings")
        dockerRegistry("registry.mydomain.com","my-docker-credentials")
    }
    maven {
        before {}
        deploy(true)
        skipTests(false)
        enableReleases("master","other-branch")
        appendBranchToVersion(true,"master","other-branch")
        options("-Dmyprop=${vars.GIT_BRANCH_OR_TAG_NAME}")
        after {}
    }
    docker {
        dir("docker")
        before {}
        imageName("${vars.MAVEN_ARTIFACT}")
        tag("${vars.MAVEN_VERSION}")
        after {}
    }
}
```

* The first line imports this pipeline script library. The part after the @ references a tag or branch. For build stability, you should use a release-tag in your projects
* The JenkinsPipeline block calls the pipeline script
* SCM checkout is always done implicitly
    * This step adds GIT_BRANCH_OR_TAG_NAME to the "vars" which can be referenced by other sections
* The config section configures some basics:
    * mavenSettings() references the ID of the Maven settings.xml described above
    * dockerRegistry() tells docker to use this registry URL (optionally with a credentials ID)
        * if set, all steps will be run with this docker registry configured (e.g. maven tasks that pull docker images will use it)
* If a maven section is present, a maven build will be done
    * if deploy is set to false (default) "mvn verify" is executed. If set to true, "mvn deploy" is executed
    * if skipTests is set to true, maven tests are skipped
    * The maven section adds MAVEN_GROUP, MAVEN_ARTIFACT and MAVEN_VERSION to the "vars" which can be referenced by other sections
    * With enableReleases it is possible to enable maven releases on specific branches
        * enableReleases() without branches enables it for "master"
        * When a build is performed on this branch, parameters are added to the job to perform a maven release and to set the next version
        * When the "release" parameter is enabled, the job will use maven to build/verify the artifact, tag a release and set the new version
        * The tag will be release-1.2.3 (where 1.2.3 is the maven release version)
        * The release itself will be build by a new jenkins job created from the new tag
    * with options(), additional command line options can be passed to maven
    * if appendBranchToVersion is set to true, the branch name will be added to maven before deploying to nexus. This allows to have snapshot of branch builds. Optionally a list of branches that will not be added to the version can be specified (default is to append all branches except "master")
* If a docker section is present, a docker build+push will be performed
    * imageName must be set
    * tag may be set. If not set, the current git branch or tag will be used.
    * If dir is set, the docker build command is exewcuted in the given directory
    * Multiple docker sections are allowed, so it's possible to build more than one docker image (using the dir option)
* All sections (except config) allow a before{} and after{} block that will be executed before/after the actual block execution (e.g. before/after maven build)
    * These blocks can contain any jenkins pipeline command
    * On maven, before/after blocks are ommitted when preparing a release

## Set up the multibranch pipeline job on jenkins

* Create a new job of type "multibranch pipeline"
* Add your project's git repository as source
    * Enable "discover branches" 
    * To build releases, enable "dicover tags" as well
    * If you have the "basic-branch-build-strategies" installed, enable the following build strategies:
        * "Regular branches"
        * If you have "discover branches" enabled above, also enable "Tag"
        * Optionally set "Ignore tags older than" to a reasonable number of days (e.g. 60) to have old release jobs be removed from jenkins
    * You can also filter your branches/tags
* On Build Configuration enable "Jenkinsfile" and set Script Path to "Jenkinsfile.groovy"
* Enable Scan Multibranch Pipeline Triggers
    * Enable "Build whenever a SNAPSHOT dependency is built"
    * Enable "Periodically if not otherwise run", set it to 1 minute or 5 minutes. This will poll git for changes
        * This can be set to a higher value if you have configured git commit hooks that triggers jenkins
* Enable "Discard old items"
    * Optionally set "Days to keep old items" if you want to have old branches/tag jobs in "disabled" state for this amount of time before the jobs gets deleted


# Issues / workarounds

Workarounds are implemented for:
* https://issues.jenkins-ci.org/browse/JENKINS-40337
* https://issues.jenkins-ci.org/browse/JENKINS-43563
* https://stackoverflow.com/questions/53010200/maven-surefire-could-not-find-forkedbooter-class
* https://stackoverflow.com/questions/51678535/how-to-resolve-cannot-retrieve-id-from-docker-when-building-docker-image-usin
* SSH credentials used for git checkout are passed to maven using "ssh-agent". Works only git using SSH key authentification

