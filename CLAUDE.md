@AGENTS.md

# Claude rules for this repo

This is a HAPI FHIR JPA server (healthcare data). Correctness and data safety beat speed.

## Java / Spring

- New code: constructor injection with `private final` fields. Don't add field `@Autowired`; existing field injection may stay until the file is touched for other reasons.
- One logger per class, named after that class: `LoggerFactory.getLogger(ThisClass.class)`. No `System.out`, no `printStackTrace()`.
- Never log PHI (resource bodies, identifiers, names) or secrets (tokens, `App` secrets, passwords). Log ids and resource types only.
- Read config via `@Value("${hapi.fhir.<feature>.<key>:<default>}")` or a `@ConfigurationProperties` class; new YAML keys go in `src/main/resources/application.yaml` with a comment and a sane default.
- Don't swallow exceptions. Catch only what you handle; otherwise rethrow as a HAPI exception (below).
- Prefer the JDK (`java.net.http.HttpClient`, `java.util.concurrent`) and libraries already in `pom.xml`. Adding a dependency needs a stated reason in the PR.
- Lombok is available; use it only where the surrounding code already does.

## HAPI FHIR

- Errors to clients are HAPI exceptions so they render as `OperationOutcome`: `InvalidRequestException` (400), `AuthenticationException` (401), `ForbiddenOperationException` (403), `ResourceNotFoundException` (404), `UnprocessableEntityException` (422), `InternalErrorException` (500).
- Interceptors: annotate with `@Interceptor`, hook with `@Hook(Pointcut.X)`, and use only the parameter types that pointcut documents. Register standalone interceptors via `hapi.fhir.custom-interceptor-classes` in `application.yaml`; interceptors that are an integral part of a feature are registered by that feature's `*Bean` config (server pointcuts on `RestfulServer`, storage pointcuts on the JPA `IInterceptorService`).
- Access data through DAOs from `DaoRegistry` with a `SystemRequestDetails` for internal calls — never JDBC/JPA directly.
- Custom resources follow `com.gjergjsheldija.configuration` (resource class + `*Bean` registering the type, DAO and provider). Nested fields are `@Block` backbone elements.
- Keep changes under `ca.uhn.fhir.jpa.starter` minimal; put features in `com.gjergjsheldija.<feature>`.

## Tests

- TDD: write the failing test first, see it fail, then implement.
- Spring/DB tests extend `com.gjergjsheldija.util.BaseFhirR4ForTesting` (H2). Pure logic gets plain JUnit 5 tests with no Spring context.
- HTTP stubs use `com.sun.net.httpserver.HttpServer` on port 0; no new test libraries without asking.
- Never weaken, skip, or delete a test to make CI pass. Changes to existing tests go in their own commit with the reason in the message.
- Run the unit tests of the feature you changed before marking a PR ready; the full `mvn verify` is optional.

## Workflow (agent software factory)

Coordination lives in GitHub (`gjergjsheldija/hapi-fhir-groovy`, public — nothing secret in issues or PRs).

- **Goal → issues.** A planner agent turns an approved spec in `docs/superpowers/specs/` into issues, each small enough to review (~400 changed lines), with `Depends on #n` lines for ordering.
- **Issue → PR.** One implementer agent per issue, in its own git worktree, on branch `issue-<n>-<slug>`. PR body starts with `Closes #<n>` and lists what was tested. The implementer owns the PR until merge: it watches CI, fixes failures, and answers review comments.
- **Specialist review.** Every PR gets parallel reviews posted as PR comments: code/guideline review (`pr-review-toolkit:code-reviewer`), error handling (`pr-review-toolkit:silent-failure-hunter`), test coverage (`pr-review-toolkit:pr-test-analyzer`), and security (`/security-review`). Findings are fixed or answered with a reason before merge.
- **Merge.** A PR may be merged once the feature's unit tests pass locally and the reviews have no unresolved findings; GitHub CI is informational (owner decision). Squash-merge; delete the branch.
- **Stuck.** After two failed fix rounds on the same CI failure or review finding, stop, label the issue `needs-human`, and summarize what was tried.
- **Commits/PRs/issues carry no AI attribution** — no `Co-Authored-By` trailers, no "Generated with" footers. Commit style: imperative summary, `(#<issue>)` suffix.
