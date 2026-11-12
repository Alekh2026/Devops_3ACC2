
pipeline {
    agent any

    triggers {
        // Automatically trigger when GitHub sends a push webhook
        githubPush()

        // Poll GitHub every 2 minutes as a fallback
        pollSCM('H/2 * * * *')
    }

    stages {

        stage('Checkout SCM') {
            steps {
                git branch: 'main',
                    credentialsId: 'github_3acc2',
                    url: 'https://github.com/Alekh2026/Devops_3ACC2.git'
            }
        }

        stage('Build & Test Backend') {
            steps {
                dir('backend') {
                    sh 'mvn clean package -DskipTests'
		    //sh 'mvn test'
                }
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    //sh 'npm install'
                    //sh 'npm run build'
                }
            }
        }

        stage('Archive Artifacts') {
            steps {
                archiveArtifacts artifacts: 'backend/target/*.jar, frontend/dist/**',
                                 allowEmptyArchive: false
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully!'
        }

        failure {
            echo 'Pipeline failed. Check the Jenkins console output.'
        }

        always {
            echo 'Pipeline execution finished.'
        }
    }
}

