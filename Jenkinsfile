#!/usr/bin/env groovy

pipeline {
    agent any

    environment {
        APPLICATION_NAME = 'syfosmoppgave'
        ZONE = 'fss'
        DOCKER_SLUG = 'syfo'
        DISABLE_SLACK_MESSAGES = true
    }

    stages {
        stage('initialize') {
            steps {
                script {
                    init action: 'default'
                    sh './gradlew clean'
                    applicationVersionGradle = sh(script: './gradlew -q printVersion', returnStdout: true).trim()
                    env.APPLICATION_VERSION = "${applicationVersionGradle}"
                    if (applicationVersionGradle.endsWith('-SNAPSHOT')) {
                        env.APPLICATION_VERSION = "${applicationVersionGradle}.${env.BUILD_ID}-${env.COMMIT_HASH_SHORT}"
                    }
                    init action: 'updateStatus'
                }
            }
        }
        stage('build') {
            steps {
                sh './gradlew build -x test'
            }
        }
        stage('run tests (unit & intergration)') {
            steps {
                sh './gradlew test'
            }
        }
        stage('create uber jar') {
            steps {
                sh './gradlew shadowJar'
                slackStatus status: 'passed'
            }
        }
        stage('Create kafka topics') {
            steps {
                sh 'echo TODO'
                // TODO
            }
        }
        stage('Create docker image') {
            steps {
                dockerUtils action: 'createPushImage'
            }
        }
        stage('deploy to preprod') {
            steps {
                deployApp action: 'kubectlDeploy', cluster: 'preprod-fss', placeholderFile: "preprod.env"
            }
        }
        stage('deploy to prod') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            steps {
                deployApp action: 'kubectlDeploy', cluster: 'prod-fss', placeholderFile: "prod.env"
            }
        }
    }
    post {
        always {
            postProcess action: 'always'
            junit '**/build/test-results/test/*.xml'
            archiveArtifacts artifacts: 'naiserator-deployment-yamls/*'
            archiveArtifacts artifacts: '**/build/libs/*', allowEmptyArchive: true
        }
        success {
            postProcess action: 'success'
        }
        failure {
            postProcess action: 'failure'
        }
    }
}
