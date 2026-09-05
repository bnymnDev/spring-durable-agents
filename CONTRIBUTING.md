# Contributing

## Setup

```sh
git clone https://github.com/bnymnDev/spring-durable-agents
cd spring-durable-agents
./mvnw verify
```

Java 21 and Docker (optional: the PostgreSQL store tests skip without it). Everything else the Maven
wrapper fetches.

## Ground rules

- **Durability goes through `Steps`.** The `@DurableAgent` bean is never proxied by this library.
  `@Step` is valid only on other beans and is intercepted by plain Spring AOP. Never add self-invocation
  workarounds (`AopContext`, self-injection, AspectJ, ByteBuddy). This is ADR-001 and it is settled.
- **Code between steps is deterministic; anything non-deterministic belongs inside a step.**
  Examples and docs demonstrate the rule, they do not hide it.
- **Core stays small.** `durable-agents-core` depends on `spring-context`, `spring-tx` and JSpecify
  (plus `context-propagation` as optional, used by one class). No Spring AI, no Jackson, no Boot.
  ArchUnit (`ModuleRulesTests` in the starter) enforces this and the module boundaries.
- **Every step is idempotent by key.** Keys are computed (`runId:name:n`, `parentKey/name:n`), never
  random. Replay of a completed key returns the stored result and never re-executes side effects.
- **Serialization is Jackson 3 to JSON.** Step results are records or plain classes with a stable
  shape; shape changes need a version bump.
- **Store operations join the caller's transaction and never open long ones.** The engine never holds
  a transaction across an LLM call.
- **Nothing blocks a platform thread waiting on a model.** Virtual threads, documented.
- **Public API lives in `io.github.bnymndev.durableagents`.** Everything else is `internal` or a
  module package; the root package must not depend on internals.
- **Constructor injection only.** Every autoconfigured bean is `@ConditionalOnMissingBean`.
- **A config property, annotation or endpoint is documented in `docs/` in the same change.**
- **Schema changes are new Flyway migrations.** Never edit an applied one.
- **An architectural choice with alternatives gets an ADR in `DECISIONS.md` before the code.**

## Workflow

- Work on `develop`; `main` carries releases.
- Small commits, [Conventional Commits](https://www.conventionalcommits.org/) (`feat(core): …`,
  `fix(store-jdbc): …`, `docs: …`).
- A new `Steps` operation or annotation attribute: test in `durable-agents-test` or the module's tests
  first, then the implementation, then the docs.
- Examples compile against the published API and are the acceptance test for ergonomics; keep them
  green.
- `./mvnw verify` must pass with warnings as errors in main sources.

## Releasing

1. Set the version and merge to `main`:
   ```sh
   ./mvnw versions:set -DnewVersion=0.1.1 -DgenerateBackupPoms=false
   git commit -am "chore: release 0.1.1" && git push
   ```
2. Run the **release** workflow on `main` with `version=0.1.1` (Actions → release → Run workflow). It
   creates the tag, the GitHub release with jars and checksums, and deploys to GitHub Packages.
   JitPack builds the tag on first request.
3. Maven Central, from your machine, the same way as uuidulid:
   ```sh
   scripts/release-to-central.sh 0.1.1             # checks prerequisites, verifies, signs, uploads
   scripts/release-to-central.sh 0.1.1 --publish   # ... and publishes without the portal click
   ```
   Needs `<server><id>central</id>` in `~/.m2/settings.xml` (Central Portal user token) and a GPG key.
   Without `--publish`, confirm the deployment at https://central.sonatype.com/publishing/deployments.
4. Bump `develop` to the next `-SNAPSHOT`.

The `release` profile attaches sources and javadoc, signs everything and uploads through
`central-publishing-maven-plugin`. Examples are excluded from deployment.
