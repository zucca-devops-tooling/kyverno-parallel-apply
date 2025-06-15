pipeline {
    agent any

    environment {
        GRADLE_OPTS = '-Dorg.gradle.jvmargs="-Xmx2g -XX:+HeapDumpOnOutOfMemoryError"''

        GH_CREDENTIALS  = credentials('GITHUB_PACKAGES')
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
        stage('Release') {
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
                script {
                    releaseVersion = sh(script: "./gradlew properties -q -Pquiet | grep '^version:' | awk '{print \$2}'", returnStdout: true).trim()
                    echo "Read project version for release: ${releaseVersion}"

                    def changelogNotes = sh(script: """
                        awk '/^## \\[${releaseVersion}\\]/{flag=1; next} /^## \\[/{flag=0} flag' CHANGELOG.md
                    """, returnStdout: true).trim()

                    if (changelogNotes.isEmpty()) {
                        changelogNotes = "No specific changelog notes found for this version."
                    }

                    def tagName = "v${releaseVersion}"
                    sh "git push https://$GH_CREDENTIALS_USR:$GH_CREDENTIALS_PSW@github.com/zucca-devops-tooling/kyverno-parallel-apply.git ${tagName}"

                    sh """
                        export GH_TOKEN="$GH_CREDENTIALS_PSW"
                        gh release create ${tagName} \\
                            --title "Release ${tagName}" \\
                            --notes "${changelogNotes}"
                    """
                    echo "GitHub Release ${tagName} created."
                }
            }
        }
    }
}

def setStatus(context, status, message) {
    publishChecks name: context, conclusion: status, title: 'Jenkins CI', summary: message
}
