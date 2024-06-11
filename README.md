# FHIR

## CLINOMIC FHIR server

To be able to start the project you need to have the following software installed on your machine :

- docker
- docker-compose
- make

There are two ways to start the project, one is for development and the other is for production.

### Development

To run the project in development mode you have to build the FHIR server first by executing `make build`. After this you 
only need to execute `make dev`. It will start a FHIR server with a derby database, no security and no validation.

#### H2 database

If you want to run this with an in-memory database, use to config from the `application.yaml`. For some reason, 
the dialect needed for the database is not picked up from the `.env` var, thus putting the dialect in the 
`application.yaml` is required,

#### OpenSearch - WIP

In order to enable OpenSearch, `hibernate.search.enabled` has to be enabled, so does `advanced_lucene_indexing`.

### Production

The production infrastructure is a bit more complex and needs some systems to be already running, like system logs and
so forth. To run it the following steps are needed to be executed in order:

- `make start-logs` will start the docker logging functionality
- `make start-databases` will start the FHIR database
- `make start-server` will start the FHIR server

The `Dockerfile` comes with a rootless docker image which should be used for production. Building it should be done with
the following command : `docker build -t ${IMAGE_NAME} --target default .`

### Stopping

It's advised to use the `make stop` (`make stopdev` for development image) command to stop the running containers. 
In case you also need to remove the containers you need to run `make clean`, or you can combine both commands with 
`make stop clean`.

The following resources are available :

| Service       | Location                | Credentials |
|:--------------|:------------------------|:------------|
| PostgreSQL    | localhost:5432          | admin/admin |
| FHIR Server   | http://localhost:8080/  |             |

## Makefile

Running / stopping / cleaning and the other utility functions needed to run the different applications is done via the
usage of a Makefile. Doing so creates a clean interface for the developers and also for a CI/CD environment. Please
check `Makefile` for more information. The following commands are available from the Makefile :

```makefile
clean                   Clean everything
pull-images             Pull all images
shell                   Opens a command prompt in the FHIR server
watch-logs              Open a tail on all the logs
build                   Build the container
dev                     Start the development image
start-logs              Start the docker logging plugins
start-databases         Start the databases used by the different services
start-server            Start the application
start                   Alias to start
stop                    Stop running containers
stopdev                 Stop running containers (development image)
restart                 Restart the app
integration-tests       Run API tests
unit-test               Run unit tests

```

Setting the `advanced_lucene_indexing` to `true` will cause the server to not find a ValueSet by its url, neither when 
searching regularly nor when expanding.

## Issues

In case of issues with the containers, please run `make stop clean`.

## Deploying new image

The push of the docker image is done via `make push-image`.

## Clinomic specific

Clinomic specific customizations can be found in the `doc` directory.

