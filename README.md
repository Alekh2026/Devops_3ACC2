# DevOps - App Gestion des Projets

Application de gestion de projets (entreprises, équipes, projets, détails de projet) avec un **backend Spring Boot** et un **frontend Angular**, livrée de bout en bout par une chaîne CI/CD Jenkins jusqu'à un cluster **Kubernetes (minikube)**.

---

## 📍 Où en est le projet

| Étape | État |
|---|---|
| Backend Spring Boot + API REST | ✅ |
| Tests unitaires (JUnit 5, Mockito, MockMvc) + couverture JaCoCo | ✅ 23 tests, ~100 % sur services/contrôleurs |
| Analyse SonarQube + Quality Gate | ✅ |
| Publication du jar dans Nexus | ✅ `maven-snapshots` |
| Images Docker (backend + frontend) sur Docker Hub | ✅ |
| Pipeline frontend | ✅ |
| Déploiement Kubernetes (MySQL + backend + frontend) | ✅ namespace `gestion-projets` |
| Application accessible depuis la machine hôte | ✅ http://192.168.56.10:4200 |

---

## 🏗️ Architecture

```
 GitHub ──webhook (ngrok) / poll──► Jenkins
                                     │
      ┌──────────── Backend pipeline (Jenkinsfile) ────────────┐
      │ mvn verify → SonarQube → Quality Gate → Nexus          │
      │ → docker build/push → kubectl apply                    │
      └────────────────────────────────────────────────────────┘
      ┌──────────── Frontend pipeline (frontend/Jenkinsfile) ──┐
      │ docker build (npm ci + ng build) → push → kubectl apply │
      │ → smoke test                                            │
      └────────────────────────────────────────────────────────┘
                                     │
                     minikube · namespace gestion-projets
   navigateur ─► :4200 (VM) ─► frontend (Nginx) ─/api─► backend ─► mysql (PVC)
```

Le navigateur ne parle qu'au frontend : Nginx sert l'application Angular et relaie `/api/*` vers le service `backend`. Une seule origine, donc pas de problème de CORS en production.

---

## 📁 Structure

```
├── Jenkinsfile              # pipeline backend
├── backend/                 # API Spring Boot (Java 17, Maven)
│   ├── Dockerfile
│   ├── lombok.config        # code Lombok exclu de la couverture
│   └── src/{main,test}/
├── frontend/                # Angular 22
│   ├── Jenkinsfile          # pipeline frontend
│   ├── Dockerfile           # build Node → image Nginx non-root
│   ├── nginx.conf           # SPA + proxy /api
│   └── proxy.conf.json      # proxy /api pour `ng serve` en local
├── k8s/                     # manifestes Kubernetes (appliqués dans l'ordre)
│   ├── 00-namespace.yaml
│   ├── 10-mysql.yaml        # Deployment + PVC + Service
│   ├── 20-backend.yaml      # Deployment + Service, sondes Actuator
│   └── 30-frontend.yaml     # Deployment + Service NodePort 30080
└── ops/                     # services systemd de la VM
    ├── minikube.service     # démarre le cluster au boot
    └── app-tunnel.service   # publie le NodePort sur le port 4200 de la VM
```

---

## 🖥️ Services de la VM (192.168.56.10)

| Service | URL | Rôle |
|---|---|---|
| Jenkins | http://192.168.56.10:8080 | CI/CD |
| SonarQube | http://192.168.56.10:9000 | Qualité du code (projet `Devops_3ACC2`) |
| Nexus | http://192.168.56.10:8081 | Dépôt d'artefacts Maven |
| Application | http://192.168.56.10:4200 | Frontend + API (`/api/...`) |

Conteneurs Docker hors cluster : `jenkins` (service systemd), `sonarqube` + `sonarqube-db` (Postgres, base interne de SonarQube), `nexus`, `mysql-devops` (MySQL pour lancer le backend en local).

---

## 🔁 Pipelines Jenkins

| Job | Script | Étapes |
|---|---|---|
| `Project` | `Jenkinsfile` | Checkout → Build & tests → SonarQube → Quality Gate → Nexus → Docker → Kubernetes → archive |
| `Angular` | `frontend/Jenkinsfile` | Checkout → Docker build → Docker push → Kubernetes → smoke test |

Déclenchement : webhook GitHub (`githubPush`, via ngrok) et, en secours, scrutation SCM toutes les 2 minutes.
Chaque image est taguée avec le numéro de build **et** `latest` ; Kubernetes déploie toujours le tag exact du build.

### Configuration hors dépôt (jamais commitée)

| Élément | Emplacement |
|---|---|
| Token SonarQube | Jenkins › serveur SonarQube `SonarQube` |
| Identifiants Docker Hub | Jenkins › credential `DockerHub` |
| Compte Nexus `jenkins-deployer` | `/var/lib/jenkins/.m2/settings.xml` (serveur `nexus`) |
| Accès au cluster | `/var/lib/jenkins/.kube/config` |
| Mot de passe MySQL du cluster | Secret Kubernetes `mysql-secret` |

---

## ✅ Tests et qualité

```bash
cd backend
mvn clean verify                                 # tests + rapport target/site/jacoco
mvn sonar:sonar -Dsonar.login=<token>            # analyse SonarQube
```

Les tests utilisent une base **H2 en mémoire** (`src/test/resources/application.properties`) : aucun MySQL n'est nécessaire en CI.

---

## ☸️ Kubernetes

Installation initiale (déjà faite sur la VM) :

```bash
minikube start --driver=docker --cpus=2 --memory=2200
kubectl apply -f k8s/00-namespace.yaml
kubectl -n gestion-projets create secret generic mysql-secret \
  --from-literal=password="$(openssl rand -hex 12)"
sudo apt-get install -y socat
sudo cp ops/*.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable minikube
sudo systemctl enable --now app-tunnel
```

Commandes utiles :

```bash
kubectl -n gestion-projets get pods,svc
kubectl -n gestion-projets logs deploy/backend -f
kubectl -n gestion-projets rollout restart deploy/frontend
```

---

## 💻 Développement local

```bash
# Backend (MySQL local sur 3306, ou variables DB_URL / DB_USERNAME / DB_PASSWORD)
cd backend && mvn spring-boot:run          # http://localhost:8080

# Frontend (les appels /api sont relayés vers localhost:8080)
cd frontend && npm ci && npm start         # http://localhost:4200
```

Variables d'environnement du backend :

| Variable | Défaut |
|---|---|
| `SERVER_PORT` | `8080` |
| `DB_URL` | `jdbc:mysql://localhost:3306/test_db?createDatabaseIfNotExist=true` |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |

---

## 🧩 Modèle de données

| Entité | Description |
|---|---|
| `Entreprise` | Nom et adresse, regroupe plusieurs équipes |
| `Equipe` | Appartient à une entreprise, a une spécialité, travaille sur plusieurs projets |
| `Projet` | Sujet partagé entre plusieurs équipes |
| `ProjetDetaille` | Technologie, coût provisoire, date de début d'un projet |

## 🔌 API

Chaque ressource (`/entreprise`, `/equipe`, `/projet`, `/projet-detaille`) expose `POST /add`, `PUT /update`, `GET /all`, `GET /get/{id}`, `DELETE /delete/{id}`.
Affectations : `PUT /equipe/assign-entreprise/{equipeId}/{entrepriseId}`, `PUT /equipe/assign-projet/{equipeId}/{projetId}`, `PUT /projet-detaille/assign-projet/{detailId}/{projetId}` (404 si un identifiant n'existe pas).
Santé : `GET /actuator/health`.
