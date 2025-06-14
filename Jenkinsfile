pipeline {
    agent any

    environment {
        GRADLE_OPTS = '-Dorg.gradle.jvmargs="-Xmx2g -XX:+HeapDumpOnOutOfMemoryError"'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Fix permissions') {
            steps {
                sh 'chmod +x gradlew'
            }
        }
        stage('Build') {
            steps {
                script {
                    setStatus('build','NEUTRAL','Building the project...')
                    try {
                        sh "./gradlew clean assemble --info --no-daemon"
                        setStatus('build','SUCCESS','Build succeeded')
                    } catch (Exception e) {
                        setStatus('build','FAILURE','Build failed')
                        throw e
                    }
                }
            }
        }
        stage('Test') {
                    steps {
                        script {
                            setStatus('test','NEUTRAL','Running tests...')
                            try {
                                sh "./gradlew test --no-daemon --info"
                                setStatus('test','SUCCESS','Tests passed')
                            } catch (Exception e) {
                                setStatus('test','FAILURE','Tests failed')
                            }
                        }
                    }
                }
        /*
        stage('Spotless') {
            steps {
                script {
                    setStatus('spotless','NEUTRAL','Checking code format...')
                    try {
                        sh "./gradlew check -x test --no-daemon"
                        setStatus('spotless','SUCCESS','Spotless passed')
                    } catch (Exception e) {
                        setStatus('spotless','FAILURE','Spotless failed')
                    }
                }
            }
        }
        stage('Test') {
            steps {
                script {
                    setStatus('test','NEUTRAL','Running tests...')
                    try {
                        sh "./gradlew :kustomtrace:test --no-daemon"
                        setStatus('test','SUCCESS','Tests passed')
                    } catch (Exception e) {
                        setStatus('test','FAILURE','Tests failed')
                    }
                }
            }
        }
        stage('Functional tests') {
            steps {
                script {
                    setStatus('functionalTest','NEUTRAL','Running functional tests...')
                    try {
                        sh "./gradlew :functional-test:test --no-daemon --info"
                        setStatus('functionalTest','SUCCESS','Functional test passed')
                    } catch (Exception e) {
                        setStatus('functionalTest','FAILURE','Functional test failed')
                    }
                }
            }
        }
        stage('Tag') {
            when {
                allOf{
                    expression {
                        def commitMessage = sh(script: "git log -1 --pretty=%B", returnStdout: true).trim()
                        return commitMessage.contains('[release]')
                    }
                    branch 'main'
                }
            }
            steps {
                sh './gradlew tagRelease'
            }
        }
        */
    }
}

def setStatus(context, status, message) {
    publishChecks name: context, conclusion: status, title: 'Jenkins CI', summary: message
}