# Gestion des Projets — Plateforme DevOps

Application de gestion de projets (entreprises, équipes, projets et détails techniques) composée d'une **API Spring Boot** et d'une **interface Angular**, livrée automatiquement par **Jenkins** jusqu'à **Kubernetes**, avec contrôle qualité **SonarQube**, dépôt d'artefacts **Nexus**, **Ingress** comme passerelle d'API et console **Headlamp** pour superviser le cluster.

![Java](https://img.shields.io/badge/Java-17-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F) ![Angular](https://img.shields.io/badge/Angular-22-DD0031) ![Kubernetes](https://img.shields.io/badge/Kubernetes-minikube-326CE5) ![Jenkins](https://img.shields.io/badge/CI%2FCD-Jenkins-D24939)

---

## Sommaire

1. [Architecture](#architecture)
2. [Structure du dépôt](#structure-du-dépôt)
3. [Prérequis](#prérequis)
4. [Développement local](#développement-local)
5. [Tests et qualité](#tests-et-qualité)
6. [Chaîne CI/CD](#chaîne-cicd)
7. [Artefacts Nexus](#artefacts-nexus)
8. [Mise en place de l'environnement](#mise-en-place-de-lenvironnement)
9. [Déploiement Kubernetes](#déploiement-kubernetes)
10. [Vérifier l'application](#vérifier-lapplication)
11. [Configuration](#configuration)
12. [API](#api)
13. [Dépannage](#dépannage)

---

## Architecture

```
 Développeur ─push─► GitHub ─webhook / polling─► Jenkins
                                                   │
   projet-backend : tests ─► SonarQube ─► Quality Gate ─► Nexus ─► Docker Hub ─► Kubernetes
                                                   │ succès
   projet-frontend : build image ─► Nexus ─► Docker Hub ─► Kubernetes ─► smoke test

 Cluster minikube
 ┌─────────────────────────── namespace gestion-projets ───────────────────────────┐
 │  Ingress (gestion-projets.local)                                                │
 │     /       ─► frontend (Nginx + Angular)                                       │
 │     /api/*  ─► backend (Spring Boot) ─► mysql (volume persistant)               │
 └─────────────────────────────────────────────────────────────────────────────────┘
 namespace ingress-nginx : contrôleur Ingress     namespace headlamp : console du cluster
```

- **Ingress = passerelle d'API** : un seul point d'entrée, qui route l'interface et l'API et retire le préfixe `/api` avant d'atteindre le backend.
- **Même origine** pour le navigateur : aucune configuration CORS n'est nécessaire en production.
- **Ordre garanti** : le frontend n'est déployé qu'après un backend validé.
- **Traçabilité** : chaque image porte le numéro de build ; Kubernetes déploie ce tag exact, ce qui permet un retour arrière immédiat.

---

## Structure du dépôt

```
├── backend/                     API Spring Boot (Java 17, Maven)
│   ├── Jenkinsfile              Pipeline projet-backend
│   ├── Dockerfile               Image d'exécution (JRE, utilisateur non-root)
│   ├── pom.xml                  Dépendances, JaCoCo, SonarQube, Nexus
│   ├── lombok.config            Exclut le code Lombok de la couverture
│   └── src/
│       ├── main/                controller · service · repository · entity · config · exception
│       └── test/                Tests unitaires, web et d'intégration (H2)
├── frontend/                    Application Angular 22
│   ├── Jenkinsfile              Pipeline projet-frontend
│   ├── Dockerfile               Build Node puis image Nginx non-root
│   ├── nginx.conf               Routage SPA et proxy /api
│   └── proxy.conf.json          Proxy /api pour `ng serve`
├── k8s/
│   ├── app/                     Application, appliquée dans l'ordre
│   │   ├── 00-namespace.yaml
│   │   ├── 10-mysql.yaml        Deployment, volume persistant, Service
│   │   ├── 20-backend.yaml      Deployment (sondes Actuator), Service
│   │   ├── 30-frontend.yaml     Deployment, Service NodePort 30080
│   │   └── 40-ingress.yaml      Routes UI et passerelle /api
│   └── platform/
│       └── headlamp.yaml        Console web du cluster (lecture seule)
└── ops/                         Unités systemd de la machine hôte du cluster
    ├── minikube.service         Démarre le cluster au boot
    ├── k8s-tunnel@.service      Publie un port du cluster sur la machine hôte
    └── k8s-tunnel.sh
```

---

## Prérequis

| Outil | Version | Usage |
|---|---|---|
| Java JDK | 17+ | Backend |
| Maven | 3.6.3+ | Build backend, publication Nexus |
| Node.js / npm | 22 / 10+ | Développement frontend uniquement |
| Docker | 24+ | Images, SonarQube, Nexus |
| Jenkins | 2.4xx+ | Plugins : Pipeline, Git, GitHub, SonarQube Scanner, Docker Pipeline, Credentials Binding |
| minikube / kubectl | 1.37 / 1.34 | Cluster Kubernetes local |
| socat | — | Publication des ports du cluster |

---

## Développement local

```bash
# Base de données
docker run -d --name mysql-devops -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=devops_db mysql:8.4

# Backend : http://localhost:8080
cd backend && mvn spring-boot:run

# Frontend : http://localhost:4200 (les appels /api sont relayés vers le backend)
cd frontend && npm ci && npm start
```

---

## Tests et qualité

```bash
cd backend
mvn clean verify                          # tests + couverture (target/site/jacoco/index.html)
mvn sonar:sonar -Dsonar.login=<token>     # analyse SonarQube
```

| Niveau | Outils | Portée |
|---|---|---|
| Unitaires | JUnit 5, Mockito | Couche service, cas nominaux et erreurs (404) |
| Web | MockMvc | Tous les endpoints REST |
| Intégration | `@SpringBootTest` + H2 | Démarrage complet du contexte |

Les tests utilisent une base **H2 en mémoire** : aucune base externe n'est nécessaire en CI.

---

## Chaîne CI/CD

### projet-backend — `backend/Jenkinsfile`

| Étape | Rôle |
|---|---|
| Build & Unit Tests | `mvn clean verify`, publication des résultats JUnit |
| SonarQube Analysis | Analyse statique et couverture |
| Quality Gate | Bloque la livraison si le seuil de qualité n'est pas atteint |
| Publish to Nexus | Jar dans `maven-snapshots` |
| Docker Build & Push | `devops-backend:<build>` et `:latest` (3 tentatives) |
| Deploy to Kubernetes | MySQL + backend, attente du rollout |

Déclencheurs : webhook GitHub, scrutation toutes les 2 minutes en secours.

### projet-frontend — `frontend/Jenkinsfile`

| Étape | Rôle |
|---|---|
| Docker Build | Build multi-étapes (`npm ci`, `ng build`) |
| Publish to Nexus | Bundle compilé `frontend-1.0.<build>.tgz` dans `maven-releases` |
| Docker Push | `devops-frontend:<build>` et `:latest` |
| Deploy to Kubernetes | Frontend + routes Ingress |
| Smoke Test | NodePort, page, `/healthz` et passerelle `/api` via l'Ingress |

Déclencheur : succès de `projet-backend`.

---

## Artefacts Nexus

Dans Nexus : **Browse › dépôt › tn › esprit**.

| Artefact | Dépôt | Chemin |
|---|---|---|
| Jar du backend | `maven-snapshots` | `tn/esprit/backend/1.0.0-SNAPSHOT/` |
| Bundle du frontend | `maven-releases` | `tn/esprit/frontend/1.0.<build>/` |

Recherche par API :

```bash
curl -s "http://<hôte>:8081/service/rest/v1/search?group=tn.esprit" | grep -E '"(name|version)"'
```

---

## Mise en place de l'environnement

### 1. Outillage

```bash
docker run -d --name nexus --restart unless-stopped -p 8081:8081 \
  -e INSTALL4J_ADD_VM_PARAMS="-Xms512m -Xmx768m -XX:MaxDirectMemorySize=768m" \
  -v nexus-data:/nexus-data sonatype/nexus3:3.75.1
```

Mot de passe initial de l'administrateur : `/nexus-data/admin.password` dans le conteneur. Créer un compte dédié à Jenkins avec un rôle limité aux dépôts Maven (`nx-repository-view-maven2-*-*`).

### 2. Jenkins

| Élément | Où le configurer |
|---|---|
| Serveur SonarQube `SonarQube` (token) + webhook SonarQube vers `http://<jenkins>/sonarqube-webhook/` | Administrer Jenkins › System |
| Identifiants Docker Hub, id `DockerHub` | Administrer Jenkins › Credentials |
| Compte Nexus, serveur `nexus` | `~jenkins/.m2/settings.xml` |
| Accès au cluster | `~jenkins/.kube/config` (`kubectl config view --flatten --minify`) |
| Accès Docker | `sudo usermod -aG docker jenkins` |

```xml
<!-- ~jenkins/.m2/settings.xml -->
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

Créer deux jobs *Pipeline script from SCM* sur ce dépôt :

| Job | Script Path |
|---|---|
| `projet-backend` | `backend/Jenkinsfile` |
| `projet-frontend` | `frontend/Jenkinsfile` |

### 3. Webhook GitHub

Exposer Jenkins (`ngrok http 8080`) puis ajouter dans GitHub › Settings › Webhooks : `https://<url-ngrok>/github-webhook/`, événement *push*.

---

## Déploiement Kubernetes

### Préparation du cluster (une seule fois)

```bash
minikube start --driver=docker --cpus=2 --memory=2200
minikube addons enable ingress

kubectl apply -f k8s/app/00-namespace.yaml
kubectl -n gestion-projets create secret generic mysql-secret \
  --from-literal=password="$(openssl rand -hex 12)"
kubectl apply -f k8s/platform/headlamp.yaml

# Démarrage automatique et publication des ports
sudo apt-get install -y socat
sudo install -m 755 ops/k8s-tunnel.sh /usr/local/bin/k8s-tunnel
sudo cp ops/*.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable minikube
for t in 4200-30080 80-80 4466-30466; do sudo systemctl enable --now k8s-tunnel@$t; done
```

Le mot de passe MySQL n'apparaît jamais dans le dépôt : il vit uniquement dans le Secret `mysql-secret`.

### Déploiement manuel (hors Jenkins)

```bash
kubectl apply -f k8s/app/
kubectl -n gestion-projets rollout status deploy/backend
```

### Points d'accès

| URL | Contenu |
|---|---|
| `http://gestion-projets.local` | Application via l'Ingress (entrée hosts requise) |
| `http://gestion-projets.local/api/projet/all` | API via la passerelle Ingress |
| `http://<hôte>:4200` | Application via le NodePort |
| `http://<hôte>:4466` | Console Headlamp |

Sur le poste client, ajouter au fichier hosts (`C:\Windows\System32\drivers\etc\hosts` ou `/etc/hosts`) :

```
192.168.56.10  gestion-projets.local
```

Jeton de connexion Headlamp (compte en lecture seule) :

```bash
kubectl -n headlamp get secret headlamp-viewer-token -o jsonpath='{.data.token}' | base64 -d; echo
```

---

## Vérifier l'application

```bash
# 1. État du cluster et de l'application
kubectl get nodes
kubectl -n gestion-projets get pods,svc,ingress

# 2. Contrôleur Ingress opérationnel
kubectl -n ingress-nginx get pods
kubectl -n gestion-projets describe ingress api-gateway

# 3. Interface et passerelle d'API
curl -I -H "Host: gestion-projets.local" http://192.168.49.2/
curl -H "Host: gestion-projets.local" http://192.168.49.2/api/projet/all
curl http://192.168.49.2:30080/api/actuator/health

# 4. Créer puis relire une donnée
curl -X POST -H "Content-Type: application/json" -d '{"sujet":"Démo"}' \
  -H "Host: gestion-projets.local" http://192.168.49.2/api/projet/add
curl -H "Host: gestion-projets.local" http://192.168.49.2/api/projet/all
```

Exploitation courante :

```bash
kubectl -n gestion-projets logs deploy/backend -f
kubectl -n gestion-projets rollout undo deploy/backend      # version précédente
kubectl -n gestion-projets rollout restart deploy/frontend
```

---

## Configuration

| Variable (backend) | Valeur par défaut |
|---|---|
| `SERVER_PORT` | `8080` |
| `DB_URL` | `jdbc:mysql://localhost:3306/test_db?createDatabaseIfNotExist=true` |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |

Santé : `GET /actuator/health` (groupes `liveness` et `readiness` utilisés par les sondes Kubernetes).

---

## API

Chaque ressource (`/entreprise`, `/equipe`, `/projet`, `/projet-detaille`) expose `POST /add`, `PUT /update`, `GET /all`, `GET /get/{id}` et `DELETE /delete/{id}`.

| Affectation | Endpoint |
|---|---|
| Équipe → entreprise | `PUT /equipe/assign-entreprise/{equipeId}/{entrepriseId}` |
| Équipe → projet | `PUT /equipe/assign-projet/{equipeId}/{projetId}` |
| Détail → projet | `PUT /projet-detaille/assign-projet/{detailId}/{projetId}` |

Une affectation vers un identifiant inexistant renvoie `404 Not Found`.

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
| `lookup registry-1.docker.io: i/o timeout` | Problème DNS de la machine : configurer un DNS public (systemd-resolved) |
| Pod en `ErrImagePull` | Image Docker Hub publique ? Accès Internet du cluster ? |
| Ingress répond `404` | Vérifier l'en-tête `Host: gestion-projets.local` et `kubectl get ingress -A` |
| Application injoignable | `minikube status`, `systemctl status 'k8s-tunnel@*'` |
