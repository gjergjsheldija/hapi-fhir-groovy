-include .env
export

UID ?= $(shell id -u)
GID ?= $(shell id -g)
IP_ADDRESS ?= $(shell ip -o route get to 8.8.8.8 | sed -n 's/.*src \([0-9.]\+\).*/\1/p')

GIT_TAG ?= $(shell git describe --abbrev=0 --tags 2>/dev/null || git rev-parse HEAD)

IMAGE_NAME = ${DOCKER_REGISTRY}/${APPLICATION_NAME}

help:
	@echo "\e[32m Usage make [target] "
	@echo
	@echo "\e[1m targets:"
	@egrep '^(.+)\:\ ##\ (.+)' ${MAKEFILE_LIST} | column -t -c 2 -s ':#'


clean: ## Clean everything
	docker compose -p ${APPLICATION_NAME} down -v --remove-orphans --rmi all
	docker compose -p ${APPLICATION_NAME} rm -v  -f -s
.PHONY: clean

pull-images: ## Pull all images
	docker compose -p ${APPLICATION_NAME} pull --include-deps
.PHONY: pull-images

shell: ## Opens a command prompt in the FHIR server
	docker compose -p ${APPLICATION_NAME} exec clinomic-fhir-server /bin/bash
.PHONY: shell

watch-logs: ## Open a tail on all the logs
	docker compose -p ${APPLICATION_NAME} logs -f -t
.PHONY: watch-logs

build: ## Build the container
	docker compose build
.PHONY: build

unit-test: ## Build the container
	docker build -t ${IMAGE_NAME} --target unit-tests .
.PHONY: unit-test

integration-tests: ## Start the databases used by the different services
	# Start all containers
	docker compose -p ${APPLICATION_NAME} up -d --remove-orphans karate
.PHONY: integration-tests

dev: ## Start the development image
	@UID=$(UID) GID=$(GID) docker compose -f docker-compose.yaml -p ${APPLICATION_NAME} up --remove-orphans
.PHONY: dev

start-databases: ## Start the databases used by the different services
	# Start all containers
	docker compose -p ${APPLICATION_NAME} up -d --remove-orphans clinomic-fhir-postgres
.PHONY: start-databases

start-server: ## Start the application
	# Start all containers
	docker compose -p ${APPLICATION_NAME} up -d --remove-orphans clinomic-fhir-server
.PHONY: start-server

start: ## Alias to start
start:	start-databases start-server
.PHONY: start

push-image: ## Push image to the internal docker registry
	docker build -t ${IMAGE_NAME}:${GIT_TAG} .
	docker push ${IMAGE_NAME}:${GIT_TAG}
.PHONY: push-image

stop: ## Stop running containers
	docker compose -p ${APPLICATION_NAME} stop
.PHONY: stop

restart: ## Restart the app
restart: stop start
.PHONY: restart

.DEFAULT_GOAL := help
