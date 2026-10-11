# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/ca/uhn/fhir/jpa/starter`: upstream HAPI JPA starter (entry point `Application`, per-version configs, CR, CDS Hooks, MDM, MCP). Keep changes here minimal so upstream upgrades stay easy.
- `src/main/java/com/gjergjsheldija`: our extensions — scripting (Groovy), security (JWT), auditevent (BALP), logging, cache (Infinispan), configuration (`Configuration` custom resource), terminology, jobs, validation.
- `src/main/resources`: `application*.yaml`, `logback.xml`, example Groovy scripts, terminology capabilities.
- `src/main/webapp`: HAPI Testpage overlay; `custom/`: branding content served by the Testpage.
- `src/test/java` & `src/test/resources`: JUnit 5 + Spock suites; extension tests mirror `com.gjergjsheldija` packages and extend `com.gjergjsheldija.util.BaseFhirR4ForTesting`.
- `docker-compose.yaml`, `docker-compose.dev.yaml`, `config/` (Postgres, Infinispan, Prometheus, Grafana, Tomcat), `Makefile`, `Dockerfile` (targets: `build-hapi`, `tomcat`, `default` distroless): local and production runtime.
- `doc/`: one Markdown file per extension feature.

## Build, Test, and Development Commands
- `mvn clean install`: Compile, run Surefire + Failsafe, and emit `target/ROOT.war`.
- `mvn spring-boot:run -Pboot`: Start the server on port 8080 with hot reload-friendly Boot profile.
- `mvn clean package spring-boot:repackage -Pboot && java -jar target/ROOT.war`: Build and exercise the bootable WAR.
- `make build && make dev`: Build the image and start the dev stack (see `README.md`).
- `docker compose up -d --build`: Launch the server + PostgreSQL + Infinispan + Prometheus/Grafana from `docker-compose.yaml`.
- `docker run -p 8080:8080 hapiproject/hapi:latest`: Compare against the upstream binary distribution.

## Coding Style & Naming Conventions
- Compile target Java 17 (`<java.version>` in `pom.xml`); images run on JRE 21. Tabs for indentation (match the surrounding file), alphabetized imports, no wildcards.
- New extension code goes under `com.gjergjsheldija.<feature>`; mirror packages in tests.
- Prefer descriptive class suffixes (`*Provider`, `*Service`, `*Config`) and constructor injection with `final` collaborators.
- YAML keys stay kebab-case; JSON fixtures use lower_snake_case filenames.

## Testing Guidelines
- `mvn test`: Runs JUnit Jupiter unit suites such as `CustomBeanTest` and `ParallelUpdatesVersionConflictTest`.
- `mvn verify`: Adds integration coverage through Failsafe with the default H2 datasource; if you pivot to PostgreSQL, run `mvn install -DskipTests` until fixtures are updated.
- Store integration suites as `*IT.java` so Failsafe detects them and colocate datasets in `src/test/resources`.
- Leverage Testcontainers and HAPI FHIR test utilities already declared in `pom.xml`.

## Commit & Pull Request Guidelines
- Follow repository precedent: imperative summary, optional scope (`Feature/mcp`), and linked issue `(#123)` when applicable.
- Keep commits narrowly scoped and include config or fixture updates with the code they support.
- PRs should describe runtime impact (profiles, ports, env vars), reference issues, and include UI screenshots when behaviour changes.
- Run `mvn verify` or the relevant Docker workflow before review; note any skipped checks and how to reproduce the result.

## Security & Configuration Tips
- Do not commit secrets; stash overrides under `configs/` and explain required env vars in the PR.
- When enabling external services, update `src/main/resources/application.yaml` plus sample overrides and mention connection expectations for reviewers.
