# About

This is an implementation of a Jenkins build pipeline which uses the Jenkins Kubernetes Plug-In to create dynamic build slaves.

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

### Kubernetes settings


### Maven settings

* TBD
    * Mirror
    * Credentials
    * Snapshot/Release Repos

### Docker settings

* Creedentials

## Project's Jenkinsfile

Create a Jenkinsfile with the following content:

```
@Library("JenkinsPipeline@feature/v001") _

JenkinsPipeline {
    config {
        mavenSettings("my-maven-settings")
        dockerRegistry("registry.mydomain.com","my-docker-credentials-id")
    }
    maven {
        deploy(true)
    }
    docker {
    }
}
```
