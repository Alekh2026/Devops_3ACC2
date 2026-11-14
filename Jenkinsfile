
pipeline {
    agent any

    triggers {
        // Automatically trigger when GitHub sends a push webhook
        githubPush()

        // Poll GitHub every 2 minutes as a fallback
        pollSCM('H/2 * * * *')
    }
	
    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }
		
		stage('Check MySQL Container') {
		    steps {
		        sh '''
		            if ! docker inspect mysql-devops >/dev/null 2>&1; then
		                echo "MySQL container does not exist. Creating it..."
		
		                docker run -d \
		                    --name mysql-devops \
		                    --restart unless-stopped \
		                    -e MYSQL_ROOT_PASSWORD=root \
		                    -e MYSQL_DATABASE=devops_db \
		                    -p 3306:3306 \
		                    -v mysql_data:/var/lib/mysql \
		                    mysql:8.4
		
		            elif [ "$(docker inspect -f '{{.State.Running}}' mysql-devops)" != "true" ]; then
		                echo "MySQL exists but is stopped. Starting it..."
		                docker start mysql-devops
		            else
		                echo "MySQL container already exists and is running."
		            fi
		
		            echo "Waiting for MySQL..."
		
		            for i in $(seq 1 60); do
		                if docker exec mysql-devops \
		                    mysqladmin ping -h 127.0.0.1 \
		                    -uroot -proot --silent 2>/dev/null; then
		
		                    echo "MySQL is ready!"
		                    exit 0
		                fi
		
		                sleep 2
		            done
		
		            echo "MySQL failed to start!"
		            docker logs mysql-devops --tail 50
		            exit 1
		        '''
		    }
		}

        stage('Build Backend & JUnit Tests') {
            steps {
                dir('backend') {
                    sh 'mvn -B clean verify'
                }
            }
            post {
                always {
                    junit testResults: 'backend/target/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        
		stage('SonarQube Analysis') {
		    steps {
		        dir('backend') {
		            withSonarQubeEnv('SonarQube') {
		                sh '''
		                    mvn -B sonar:sonar \
		                    -Dsonar.projectKey=Devops_3ACC2 \
		                    -Dsonar.projectName=Devops_3ACC2 \
		                    -Dsonar.java.binaries=target/classes
		                '''
		            }
		        }
		    }
		}

		stage('Quality Gate') {
		    steps {
		        timeout(time: 5, unit: 'MINUTES') {
		            waitForQualityGate(
		                abortPipeline: true
		            )
		        }
		    }
		}

        stage('Archive Backend Artifact') {
            steps {
                archiveArtifacts artifacts: 'backend/target/*.jar',
                                 fingerprint: true,
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

