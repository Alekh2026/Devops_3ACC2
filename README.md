# Gestion des Projets — Plateforme DevOps

Application de gestion de projets (entreprises, équipes, projets et détails techniques) composée d'une **API Spring Boot** et d'une **interface Angular**, livrée automatiquement par **Jenkins** jusqu'à **Kubernetes**, avec contrôle qualité **SonarQube** et dépôt d'artefacts **Nexus**.

![Java](https://img.shields.io/badge/Java-17-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F) ![Angular](https://img.shields.io/badge/Angular-22-DD0031) ![Kubernetes](https://img.shields.io/badge/Kubernetes-minikube-326CE5) ![Jenkins](https://img.shields.io/badge/CI%2FCD-Jenkins-D24939)

---

## Sommaire

1. [Architecture](#architecture)
2. [Structure du dépôt](#structure-du-dépôt)
3. [Prérequis](#prérequis)
4. [Développement local](#développement-local)
5. [Tests et qualité](#tests-et-qualité)
6. [Chaîne CI/CD](#chaîne-cicd)
7. [Mise en place de l'environnement](#mise-en-place-de-lenvironnement)
8. [Déploiement Kubernetes](#déploiement-kubernetes)
9. [Configuration](#configuration)
10. [API](#api)
11. [Dépannage](#dépannage)

---

## Architecture

```
            push                 webhook / polling
 Développeur ───► GitHub ─────────────────────────► Jenkins
                                                      │
        ┌──────────────────── projet-backend ─────────┴───────────────────┐
        │ Build + tests ─► SonarQube ─► Quality Gate ─► Nexus ─► Docker Hub │
        │                                                  └─► Kubernetes   │
        └──────────────────────────────┬──────────────────────────────────┘
                                       │ succès
        ┌──────────────────── projet-frontend ────────────────────────────┐
        │ Build image (npm ci + ng build) ─► Docker Hub ─► Kubernetes ─► Smoke test │
        └──────────────────────────────────────────────────────────────────┘

 Kubernetes · namespace gestion-projets
 Navigateur ─► :4200 ─► frontend (Nginx) ──/api──► backend (Spring Boot) ──► mysql (volume persistant)
```

- Le navigateur ne parle qu'au frontend : Nginx sert l'application et relaie `/api/*` vers le backend. Une seule origine, donc aucune configuration CORS n'est nécessaire en production.
- Le frontend n'est déployé qu'après un backend validé : l'interface ne précède jamais l'API dont elle dépend.
- Chaque image est taguée avec le numéro de build ; Kubernetes déploie toujours le tag exact, ce qui rend chaque version traçable et réversible.

---

## Structure du dépôt

```
├── Jenkinsfile              Pipeline projet-backend
├── backend/                 API Spring Boot (Java 17, Maven)
│   ├── Dockerfile           Image d'exécution (JRE, utilisateur non-root)
│   ├── lombok.config        Exclut le code généré par Lombok de la couverture
│   └── src/
│       ├── main/            controller · service · repository · entity · config · exception
│       └── test/            Tests unitaires et d'intégration (H2 en mémoire)
├── frontend/                Application Angular 22
│   ├── Jenkinsfile          Pipeline projet-frontend
│   ├── Dockerfile           Build Node puis image Nginx non-root
│   ├── nginx.conf           Routage SPA et proxy /api
│   └── proxy.conf.json      Proxy /api pour `ng serve`
├── k8s/                     Manifestes Kubernetes, appliqués dans l'ordre
│   ├── 00-namespace.yaml
│   ├── 10-mysql.yaml        Deployment, volume persistant, Service
│   ├── 20-backend.yaml      Deployment (sondes Actuator), Service
│   └── 30-frontend.yaml     Deployment, Service NodePort 30080
└── ops/                     Unités systemd pour la machine hôte du cluster
    ├── minikube.service     Démarre le cluster au boot
    └── app-tunnel.service   Publie l'application sur le port 4200
```

---

## Prérequis

| Outil | Version | Usage |
|---|---|---|
| Java JDK | 17+ | Backend |
| Maven | 3.6.3+ | Build backend |
| Node.js / npm | 22 / 10+ | Développement frontend |
| Docker | 24+ | Images, SonarQube, Nexus |
| Jenkins | 2.4xx+ | CI/CD (plugins : Pipeline, Git, GitHub, SonarQube Scanner, Docker Pipeline, Credentials Binding) |
| minikube / kubectl | 1.37 / 1.34 | Cluster Kubernetes local |

---

## Développement local

**Base de données** (MySQL dans Docker) :

```bash
docker run -d --name mysql-devops -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=devops_db mysql:8.4
```

**Backend** — http://localhost:8080

```bash
cd backend
mvn spring-boot:run
```

**Frontend** — http://localhost:4200 (les appels `/api` sont relayés vers le backend)

```bash
cd frontend
npm ci
npm start
```

---

## Tests et qualité

```bash
cd backend
mvn clean verify                          # tests + rapport de couverture (target/site/jacoco/index.html)
mvn sonar:sonar -Dsonar.login=<token>     # analyse SonarQube
```

| Niveau | Outils | Portée |
|---|---|---|
| Unitaires | JUnit 5, Mockito | Couche service, cas nominaux et erreurs (404) |
| Web | MockMvc | Tous les endpoints REST |
| Intégration | `@SpringBootTest` + H2 | Démarrage complet du contexte |

Les tests utilisent une base **H2 en mémoire** : aucune base externe n'est nécessaire en CI.
La clé du projet SonarQube, l'URL du serveur et le chemin du rapport JaCoCo sont déclarés dans `backend/pom.xml`.

---

## Chaîne CI/CD

### projet-backend (`Jenkinsfile`)

| Étape | Rôle |
|---|---|
| Build & Unit Tests | `mvn clean verify`, publication des résultats JUnit |
| SonarQube Analysis | Analyse statique et couverture |
| Quality Gate | Bloque la livraison si la qualité n'est pas au rendez-vous |
| Publish to Nexus | Déploie le jar dans `maven-snapshots` |
| Docker Build & Push | Image `devops-backend:<build>` et `:latest` |
| Deploy to Kubernetes | Applique MySQL et le backend, attend le rollout |

Déclencheurs : webhook GitHub, et scrutation du dépôt toutes les 2 minutes en secours.

### projet-frontend (`frontend/Jenkinsfile`)

| Étape | Rôle |
|---|---|
| Docker Build | Build multi-étapes (`npm ci`, `ng build`) — Node n'est pas requis sur Jenkins |
| Docker Push | Image `devops-frontend:<build>` et `:latest` |
| Deploy to Kubernetes | Applique le frontend, attend le rollout |
| Smoke Test | Vérifie la page, la sonde `/healthz` et le proxy `/api` |

Déclencheur : succès de `projet-backend`.

---

## Mise en place de l'environnement

### 1. Services d'outillage

```bash
# SonarQube (+ PostgreSQL) : http://<hôte>:9000
# Nexus                    : http://<hôte>:8081
docker run -d --name nexus --restart unless-stopped -p 8081:8081 \
  -e INSTALL4J_ADD_VM_PARAMS="-Xms512m -Xmx768m -XX:MaxDirectMemorySize=768m" \
  -v nexus-data:/nexus-data sonatype/nexus3:3.75.1
```

Le mot de passe initial de l'administrateur Nexus se trouve dans `/nexus-data/admin.password` du conteneur.

### 2. Jenkins

| Élément | Où le configurer |
|---|---|
| Serveur SonarQube nommé `SonarQube` (avec token) + webhook SonarQube vers `http://<jenkins>/sonarqube-webhook/` | Administrer Jenkins › System |
| Identifiants Docker Hub, id `DockerHub` | Administrer Jenkins › Credentials |
| Compte Nexus dédié (rôle limité aux dépôts Maven), serveur `nexus` | `~jenkins/.m2/settings.xml` |
| Accès au cluster | `~jenkins/.kube/config` (`kubectl config view --flatten --minify`) |
| Utilisateur `jenkins` dans le groupe `docker` | `sudo usermod -aG docker jenkins` |

Exemple de `~jenkins/.m2/settings.xml` :

```xml
<settings>
  <servers>
    <server>
      <id>nexus</id>
      <username>jenkins-deployer</username>
      <password>********</password>
    </server>
  </servers>
</settings>
```

Créer deux jobs *Pipeline from SCM* sur ce dépôt : `projet-backend` (script `Jenkinsfile`) et `projet-frontend` (script `frontend/Jenkinsfile`).

### 3. Webhook GitHub

Exposer Jenkins (par exemple avec `ngrok http 8080`) puis ajouter dans GitHub › Settings › Webhooks l'URL `https://<url-ngrok>/github-webhook/` pour l'événement *push*.

---

## Déploiement Kubernetes

### Préparation du cluster (une seule fois)

```bash
minikube start --driver=docker --cpus=2 --memory=2200

kubectl apply -f k8s/00-namespace.yaml
kubectl -n gestion-projets create secret generic mysql-secret \
  --from-literal=password="$(openssl rand -hex 12)"

# Démarrage automatique et exposition de l'application sur le port 4200
sudo apt-get install -y socat
sudo cp ops/*.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable minikube
sudo systemctl enable --now app-tunnel
```

Le mot de passe MySQL n'apparaît jamais dans le dépôt : il vit uniquement dans le Secret `mysql-secret`.

### Déploiement manuel (hors Jenkins)

```bash
kubectl apply -f k8s/
kubectl -n gestion-projets rollout status deploy/backend
```

### Accès

| URL | Contenu |
|---|---|
| `http://<hôte>:4200` | Application |
| `http://<hôte>:4200/api/projet/all` | API via le proxy Nginx |

### Exploitation

```bash
kubectl -n gestion-projets get pods,svc
kubectl -n gestion-projets logs deploy/backend -f
kubectl -n gestion-projets rollout undo deploy/backend     # revenir à la version précédente
kubectl -n gestion-projets rollout restart deploy/frontend
```

---

## Configuration

Variables d'environnement du backend :

| Variable | Valeur par défaut |
|---|---|
| `SERVER_PORT` | `8080` |
| `DB_URL` | `jdbc:mysql://localhost:3306/test_db?createDatabaseIfNotExist=true` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | `root` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |

Santé : `GET /actuator/health` (groupes `liveness` et `readiness` utilisés par les sondes Kubernetes).

---

## API

| Ressource | Base |
|---|---|
| Entreprise | `/entreprise` |
| Équipe | `/equipe` |
| Projet | `/projet` |
| Détail de projet | `/projet-detaille` |

Chaque ressource expose `POST /add`, `PUT /update`, `GET /all`, `GET /get/{id}` et `DELETE /delete/{id}`.

| Affectation | Endpoint |
|---|---|
| Équipe → entreprise | `PUT /equipe/assign-entreprise/{equipeId}/{entrepriseId}` |
| Équipe → projet | `PUT /equipe/assign-projet/{equipeId}/{projetId}` |
| Détail → projet | `PUT /projet-detaille/assign-projet/{detailId}/{projetId}` |

Une affectation vers un identifiant inexistant renvoie `404 Not Found`.

### Modèle de données

| Entité | Description |
|---|---|
| `Entreprise` | Nom et adresse, regroupe plusieurs équipes |
| `Equipe` | Appartient à une entreprise, possède une spécialité, travaille sur plusieurs projets |
| `Projet` | Sujet partagé entre plusieurs équipes |
| `ProjetDetaille` | Technologie, coût provisoire et date de début d'un projet |

---

## Dépannage

| Symptôme | Piste |
|---|---|
| `Not authorized` pendant `sonar:sonar` | Fournir un token : `-Dsonar.login=<token>` |
| Quality Gate bloqué en `IN_PROGRESS` | Vérifier le webhook SonarQube vers Jenkins |
| `401` lors de la publication Nexus | Vérifier le serveur `nexus` dans `~jenkins/.m2/settings.xml` |
| Pod en `ErrImagePull` | Vérifier que l'image Docker Hub est publique et que le cluster a accès à Internet |
| Pod backend non prêt | `kubectl -n gestion-projets logs deploy/backend` (souvent MySQL pas encore prêt) |
| Application injoignable sur :4200 | `systemctl status app-tunnel` et `minikube status` |
