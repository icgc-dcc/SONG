import groovy.json.JsonOutput

def version = "UNKNOWN"
def commit = "UNKNOWN"
def repo = "UNKNOWN"
def dockerHubRepo = "overture/song"
def gitHubRegistry = "ghcr.io"
def gitHubRepo = "overture-stack/song"

def pom(path, target) {
    return [pattern: "${path}/pom.xml", target: "${target}.pom"]
}

def jar(path, target) {
    return [pattern: "${path}/target/*.jar",
            target         : "${target}.jar",
            excludePatterns: ["*-exec.jar"]
            ]
}

def tar(path, target) {
    return [pattern: "${path}/target/*.tar.gz",
            target : "${target}-dist.tar.gz"]
}

def runjar(path, target) {
    return [pattern: "${path}/target/*-exec.jar",
            target : "${target}-exec.jar"]
}

pipeline {
    agent {
        kubernetes {
            label 'song-executor'
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jdk
    tty: true
    image: adoptopenjdk/openjdk11:jdk-11.0.6_10-alpine
    env: 
      - name: DOCKER_HOST 
        value: tcp://localhost:2375 
  - name: dind-daemon 
    image: docker:18.06-dind
    securityContext: 
        privileged: true 
        runAsUser: 0
    volumeMounts: 
      - name: docker-graph-storage 
        mountPath: /var/lib/docker 
  - name: docker
    image: docker:18-git
    tty: true
    env: 
      - name: DOCKER_HOST 
        value: tcp://localhost:2375 
  securityContext:
    runAsUser: 1000
  volumes:
  - name: docker-graph-storage 
    emptyDir: {}
"""
        }
    }
    stages {
        stage('Prepare') {
            steps {
                script {
                    commit = sh(returnStdout: true, script: 'git describe --always').trim()
                }
                script {
                    version = readMavenPom().getVersion()
                }
            }
        }
        stage('Test') {
            steps {
                container('jdk') {
                    sh "./mvnw test package"
                }
            }
        }

        stage('Build & Publish Develop') {
            when {
                branch "develop"
            }
            steps {
                container('docker') {
                    withCredentials([usernamePassword(credentialsId:'OvertureDockerHub', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh "docker login -u $USERNAME -p $PASSWORD"
                    }
                    sh "docker build --target=server --network=host -f Dockerfile . -t ${dockerHubRepo}-server:edge -t ${dockerHubRepo}-server:${commit}"
                    sh "docker build --target=client --network=host -f Dockerfile . -t ${dockerHubRepo}-client:edge -t ${dockerHubRepo}-client:${commit}"
                    sh "docker push ${dockerHubRepo}-server:${commit}"
                    sh "docker push ${dockerHubRepo}-server:edge"
                    sh "docker push ${dockerHubRepo}-client:${commit}"
                    sh "docker push ${dockerHubRepo}-client:edge"
                }

                container('docker') {
                    withCredentials([usernamePassword(credentialsId:'OvertureBioGithub', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh "docker login ${gitHubRegistry} -u $USERNAME -p $PASSWORD"
                    }
                    sh "docker build --target=server --network=host -f Dockerfile . -t ${gitHubRegistry}/${gitHubRepo}-server:edge -t ${gitHubRegistry}/${gitHubRepo}-server:${commit}"
                    sh "docker build --target=client --network=host -f Dockerfile . -t ${gitHubRegistry}/${gitHubRepo}-client:edge -t ${gitHubRegistry}/${gitHubRepo}-client:${commit}"
                    sh "docker push ${gitHubRegistry}/${gitHubRepo}-server:${commit}"
                    sh "docker push ${gitHubRegistry}/${gitHubRepo}-server:edge"
                    sh "docker push ${gitHubRegistry}/${gitHubRepo}-client:${commit}"
                    sh "docker push ${gitHubRegistry}/${gitHubRepo}-client:edge"
                }
            }
        }
        stage('Release & tag') {
          when {
            branch "master"
          }
          steps {
                container('docker') {
                    withCredentials([usernamePassword(credentialsId: 'OvertureBioGithub', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                        sh "git tag ${version}"
                        sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/${gitHubRepo} --tags"
                    }
                    withCredentials([usernamePassword(credentialsId:'OvertureDockerHub', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh "docker login -u $USERNAME -p $PASSWORD"
                    }
                    sh "docker build --target=server --network=host -f Dockerfile . -t ${dockerHubRepo}-server:latest -t ${dockerHubRepo}-server:${version}"
                    sh "docker build --target=client --network=host -f Dockerfile . -t ${dockerHubRepo}-client:latest -t ${dockerHubRepo}-client:${version}"
                    sh "docker push ${dockerHubRepo}-server:${version}"
                    sh "docker push ${dockerHubRepo}-server:latest"
                    sh "docker push ${dockerHubRepo}-client:${version}"
                    sh "docker push ${dockerHubRepo}-client:latest"
                }

                container('docker') {
                    withCredentials([usernamePassword(credentialsId:'OvertureBioGithub', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh "docker login ${gitHubRegistry} -u $USERNAME -p $PASSWORD"
                    }
                    sh "docker build --target=server --network=host -f Dockerfile . -t ${gitHubRegistry}/${gitHubRepo}-server:latest -t ${gitHubRegistry}/${gitHubRepo}-server:${version}"
                    sh "docker build --target=client --network=host -f Dockerfile . -t ${gitHubRegistry}/${gitHubRepo}-client:latest -t ${gitHubRegistry}/${gitHubRepo}-client:${version}"
                    sh "docker push ${gitHubRegistry}/${gitHubRepo}-server:${version}"
                    sh "docker push ${gitHubRegistry}/${gitHubRepo}-server:latest"
                    sh "docker push ${gitHubRegistry}/${gitHubRepo}-client:${version}"
                    sh "docker push ${gitHubRegistry}/${gitHubRepo}-client:latest"
                }
            }
        }

        stage('Destination SNAPSHOT') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'test-develop'
                }
            }
            steps {
                script {
                    repo = "dcc-snapshot/bio/overture"
                }
            }
        }

        stage('Destination release') {
            when {
                anyOf {
                    branch 'master'
                    branch 'test-master'
                }
            }
            steps {
                script {
                    repo = "dcc-release/bio/overture"
                }
            }
        }

        stage('Upload Artifacts') {
            when {
                anyOf {
                    branch 'master'
                    branch 'test-master'
                    branch 'develop'
                    branch 'test-develop'
                }
            }
            steps {
                script {
                    
                    project = "song"
                    versionName = "$version"
                    subProjects = ['client', 'core', 'server', 'java-sdk']

                    files = []
                    files.add([pattern: "pom.xml", target: "$repo/$project/$versionName/$project-${versionName}.pom"])

                    for (s in subProjects) {
                        name = "${project}-$s"
                        target = "$repo/$name/$versionName/$name-$versionName"
                        files.add(pom(name, target))
                        files.add(jar(name, target))

                        if (s in ['client', 'server']) {
                            files.add(runjar(name, target))
                            files.add(tar(name, target))
                        }
                    }

                    fileSet = JsonOutput.toJson([files: files])
                    pretty = JsonOutput.prettyPrint(fileSet)
                    print("Uploading files=${pretty}")
                }

                rtUpload(serverId: 'artifactory', spec: fileSet)
            }
        }

		stage('Deploy to Overture QA') {
			when {
				branch "develop"
			}
			steps {
				build(job: "/Overture.bio/provision/helm", parameters: [
						[$class: 'StringParameterValue', name: 'OVERTURE_ENV', value: 'qa' ],
						[$class: 'StringParameterValue', name: 'OVERTURE_CHART_NAME', value: 'song'],
						[$class: 'StringParameterValue', name: 'OVERTURE_RELEASE_NAME', value: 'song'],
						[$class: 'StringParameterValue', name: 'OVERTURE_HELM_CHART_VERSION', value: ''], // use latest
						[$class: 'StringParameterValue', name: 'OVERTURE_HELM_REPO_URL', value: "https://overture-stack.github.io/charts-server/"],
						[$class: 'StringParameterValue', name: 'OVERTURE_HELM_REUSE_VALUES', value: "true" ],
						[$class: 'StringParameterValue', name: 'OVERTURE_ARGS_LINE', value: "--set-string image.tag=${commit}" ]
				])
			}
		}

		stage('Deploy to Overture Staging') {
			when {
				branch "master"
			}
			steps {
				build(job: "/Overture.bio/provision/helm", parameters: [
						[$class: 'StringParameterValue', name: 'OVERTURE_ENV', value: 'staging' ],
						[$class: 'StringParameterValue', name: 'OVERTURE_CHART_NAME', value: 'song'],
						[$class: 'StringParameterValue', name: 'OVERTURE_RELEASE_NAME', value: 'song'],
						[$class: 'StringParameterValue', name: 'OVERTURE_HELM_CHART_VERSION', value: ''], // use latest
						[$class: 'StringParameterValue', name: 'OVERTURE_HELM_REPO_URL', value: "https://overture-stack.github.io/charts-server/"],
						[$class: 'StringParameterValue', name: 'OVERTURE_HELM_REUSE_VALUES', value: "true" ],
						[$class: 'StringParameterValue', name: 'OVERTURE_ARGS_LINE', value: "--set-string image.tag=${version}" ]
				])
			}
		}
    }
}
