# CARP WEB SERVICES

The [C]openh[A]gen [R]esearch [P]latform (CARP) enables researchers to run digital phenotyping studies and develop mobile health (mHealth) applications where data is collected on the participant's smartphones and wearable devices. Studies are configured, study participants are managed, and data is securely uploaded and managed in a hosting infrastructure. CARP is a platform for running research studies in the health domain – also known as Digital Phenotyping. Such studies range from technical feasibility studies of novel technology to large-scale clinical studies. The platform is very versatile in terms of support for different types of health domains, both in terms of technical support and configuration. See the [CARP Homepage](https://carp.cachet.dk/) for a description of the entire CARP platform and set of software components. 

The CARP Web Services (CAWS) API provides a REST-based implementation of the [CARP Core Domain Model](https://github.com/cph-cachet/carp.core-kotlin) using Spring Boot.

The architecture is a modular monolith, with loosely coupled services and endpoints separated by feature type (i.e. auth, data points, study, protocols, etc.). Shared services can be found under the common directory, and are again separated into directories by type (rather than controllers, models, views, services, factories, presenters, etc). Overall, the CARP Webservice API architecture is divided into three layers; API Gateway, Security, Services, and Persistence layer.

The above-mentioned features, namely studies, protocols, and deployments are implemented in the [CARP Core library](https://github.com/cph-cachet/carp.core-kotlin), which publishes them as packages and this project uses them as dependencies. Briefly explained, the Core library is developed using an [Onion (Hexagonal) Architecture](https://en.wikipedia.org/wiki/Hexagonal_architecture_(software)), which means that it provides the domain models and business logic, but it requires several dependencies (Database, API) to function in a real environment. These dependencies are provided by this application.

## Table of contents

- [Architecture](#architecture)
  * [Overview](#overview)
  * [Profiles](#profiles)
- [Services](#services)
  * [CARP-Core services](#carp-core-services)
  * [Project exclusive services](#project-exclusive-services)
  * [API Documentation](#api-documentation)
- [Deployment](#deployment)
- [Developer Guide](#developer-guide)
  * [Local Development](#local-development)
  * [Database migrations](#database-migrations)

# Architecture

Essentially, the CARP Webservices uses a modular-monolith architecture, written in [Kotlin](https://kotlinlang.org/) using [Spring Boot](https://spring.io/projects/spring-boot). 

## Overview

- Target: Java version 21.
- Spring Boot is the main framework with [Web MVC](https://docs.spring.io/spring-framework/docs/3.2.x/spring-framework-reference/html/mvc.html).
- Application security is implemented using [Spring Security](https://spring.io/projects/spring-security) OAuth2.
- Dependency management and application building are handled by [Gradle](https://gradle.org/).
- Message Queue management is done by [RabbitMQ](https://www.rabbitmq.com/). 
- The data is managed by a [PostgreSQL DBMS](https://www.postgresql.org/).  For database versioning [Flyway](https://flywaydb.org/) is being used.
- The application is also containerized using [Docker](https://www.docker.com/). [Docker compose](https://docs.docker.com/compose/) files are also configured to help set up different environments.

## Profiles

The project can be set up using different profiles for different environments. The following profiles are available:

- local
- development
- testing
- production

# Services

The main services of the application are the following: 

* [Collections](docs/ws-resources.md)
* [Documents](docs/ws-resources.md)
* [Files](docs/ws-resources.md)
* Data Points
* Studies
* Protocols
* Deployments
* [Data Streams](docs/ws-datastream.md)
* [Exports](docs/ws-exports.md)
  
All of them have a dedicated package with their own controller, service, and persistence layers, however, there are differences in their management.

## CARP-Core services

These services include the `Studies`, `Protocols`, and `Deployments` packages. These are not implemented in this repository but are implemented in [CARP Core](https://github.com/cph-cachet/carp.core-kotlin). 
For each subsystem, there is a service interface defined in CARP Core. This application provides web endpoints to access these services and repository implementations to be used by a core to persist and fetch data. The business logic is entirely separated from CARP Core. 
On the API level, there is one POST endpoint that requires a service functionality-specific data transfer object (defined in CARP Core infrastructure libraries) and delegates it to its destination.

## Project exclusive services

The project-specific services are the rest, plus additional ones, such as security and other infrastructure-specific concerns. These can be found in the 'common' package and 'security' package. These services are entirely implemented in this project, and most of them provide a RESTful API for data manipulation.

## API Documentation

For API documentation we use [Swagger](https://dev.carp.dk/doc/). 

> **Note:** The documentation is currently only available in the development environment.

# Deployment

Follow these steps to deploy CAWS:

1. Edit the [.env file](stack.env). Choose a profile from the list of [Profiles](#profiles).
   - Ignore the properties starting with KC_ for now
2. Copy over the contents of the [template configuration file](src/main/resources/config/application.yml) to the environment specific [configuration file](src/main/resources/config) 
   - ignore the `keycloak` section for now
3. Run `bash deployment.sh`
4. Configure keycloak
   - If you are hosting the stack behind a reverse proxy (e.g. nginx), make sure to read the [official keycloak documentation](https://www.keycloak.org/docs/latest/server_installation/#_setting-up-a-load-balancer-or-proxy) on the subject.
     - uncomment the lines in the [environment file](stack.env) and also in the [docker-compose file](docker-compose.yml) to enable the proxy (add them to the dev and prod commands if necessary).
   - Set up a client for service accounts (Note: this client will only be used by the backend services, not a custom CAWS fronted)
     - Set up a new client by selecting the `Carp` realm after opening `<server>/admin/master/console/`, and click `Create client` under the `Clients` tab.
     - Fill in the general information and then toggle `Client authentication` as well as the `Authorization` options on. Then fill the URL settings.
     - Under the Service account roles tab assign the following roles (filter by clients to find them):
       - `manage-users`
       - `view-users`
       - `query-users`
       - `view-clients`
     - Note: this client is used by Spring to communicate with keycloak for managing accounts; if your application relies on a client that authorizes via the Keycloak service, you will need to create it depending on your application's requirements
   - Under `Users` add a new Admin user and assign the `system-admin` role to it. This user will be used to invite the first researcher. Can be deactivated afterward.
   - Under `Realm settings` -> `Email` configure the email settings. This is used to send out invitations to researchers/researcher_assistants/participants. The same email server can be used as the one specified in the [configuration file](src/main/resources/config/application.yml).
   - Under `Realm settings` -> `Themes` configure the theme settings.
   - Update the [environment file](stack.env) to match the newly created client.
5. Rebuild the carp-ws image and restart the stack.

# Developer Guide

## Local development
  
- Make sure you have Docker (and Java 21) installed on your system.
- Ensure Docker is able to mount the volume specified under carp-ws (/data/carp/storage/local); you may need to configure Docker File Sharing options to enable this
- Clone the project and run `bash deployment.sh`
- Create a .local.env file and copy over the contents of the [.env file](stack.env) to it and fill in the missing values.
- We use IntelliJ IDEA as our IDE, we recommend using it for development. Set up the EnvFile plugin to be able to use the .local.env file.
- Add a new spring boot run configuration with the following parameters:
  - Main class: `dk.cachet.carp.webservices.Application`
  - Active profiles: `local`
  - Tick in the "Enable EnvFile", as well as the "Substitute environment variables" checkbox. Add the .local.env file to the list of env files.

## Database migrations

- Migrations will automatically be executed on the first time
- Any new migrations will also be automatically executed when deployed through Jenkins onto the development or production environments.
