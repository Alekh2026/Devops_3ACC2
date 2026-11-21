// =====================================================================
//  projet-backend - Spring Boot API (triggers projet-frontend on success)
//  Build -> Tests + coverage -> SonarQube -> Quality Gate
//  -> Nexus (Maven artifact) -> Docker Hub (image) -> Kubernetes
// =====================================================================
pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timeout(time: 30, unit: 'MINUTES')
    }

    triggers {
        githubPush()                 // GitHub webhook (through ngrok)
        pollSCM('H/2 * * * *')       // fallback when the webhook is offline
    }

    environment {
        IMAGE     = 'hyperrftw/devops-backend'
        TAG       = "${env.BUILD_NUMBER}"
        NAMESPACE = 'gestion-projets'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // Unit tests run on an in-memory H2 database, no MySQL needed here.
        stage('Build & Unit Tests') {
            steps {
                dir('backend') {
                    sh 'mvn -B clean verify'
                }
            }
            post {
                always {
                    junit testResults: 'backend/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        // Project key, host and JaCoCo report path are set in backend/pom.xml
        stage('SonarQube Analysis') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('SonarQube') {
                        sh 'mvn -B sonar:sonar'
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // Credentials for the "nexus" server live in ~jenkins/.m2/settings.xml
        stage('Publish to Nexus') {
            steps {
                dir('backend') {
                    sh 'mvn -B deploy -DskipTests'
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'DockerHub',
                                                  usernameVariable: 'DOCKERHUB_USER',
                                                  passwordVariable: 'DOCKERHUB_TOKEN')]) {
                    dir('backend') {
                        sh '''
                            set -e
                            docker build -t "$IMAGE:$TAG" -t "$IMAGE:latest" .
                            printf '%s' "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_USER" --password-stdin
                            docker push "$IMAGE:$TAG"
                            docker push "$IMAGE:latest"
                        '''
                    }
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        // Applies the DB + API manifests, pinning the image to this build's tag
        stage('Deploy to Kubernetes') {
            steps {
                sh '''
                    set -e
                    kubectl apply -f k8s/00-namespace.yaml
                    kubectl apply -f k8s/10-mysql.yaml
                    sed "s|$IMAGE:latest|$IMAGE:$TAG|" k8s/20-backend.yaml | kubectl apply -f -
                    kubectl -n "$NAMESPACE" rollout status deployment/mysql --timeout=180s
                    kubectl -n "$NAMESPACE" rollout status deployment/backend --timeout=300s
                '''
            }
        }

        stage('Archive Artifact') {
            steps {
                archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true
            }
        }
    }

    post {
        success { echo "Backend ${env.TAG} is live in namespace ${env.NAMESPACE}." }
        failure { echo 'Backend pipeline failed, check the stage logs above.' }
        always  { sh 'docker image prune -f || true' }
    }
}
