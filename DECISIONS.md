# DECISIONS

Architecture decision records. One entry per choice that had alternatives; the reasoning stays here
even when the code moves on.

## ADR-001 — Step interception: explicit `Steps` API instead of proxying the agent class

**Status:** accepted, 2026-09-04

### Problem
The first draft intercepted `@Step` methods on the agent class itself via a Spring proxy. That breaks in the exact situation every agent has: `run()` calls `classify()` on the same object. Spring AOP (JDK or CGLIB) only intercepts calls that go through the proxy; `this.classify()` bypasses it, the step is silently not recorded, and resume re-executes it. This is the well-known `@Transactional` self-invocation trap, now in a place where it costs money (LLM calls) and correctness (side effects twice).

### Options considered
1. **CGLIB proxy + workarounds** — inject the proxy into itself (`ObjectProvider<Self>`), or `AopContext.currentProxy()` with `exposeProxy=true`. Works but every user has to know the trick; `final` classes and records are out; one forgotten `this.` call is a silent bug. Rejected.
2. **AspectJ weaving** (compile-time or load-time) — intercepts `this.` calls correctly. Requires the AspectJ compiler or a `-javaagent`; build plugin setup, IDE friction, Spring Boot fat-jar + LTW pitfalls. Nobody adopts a starter that needs a weaver. Rejected.
3. **Java agent / bytecode instrumentation at startup** (ByteBuddy) — hides the weaver but keeps its fragility; breaks with CDS/AOT/GraalVM native. Rejected.
4. **Explicit `Steps` API** — `steps.run("name", () -> …)`. No proxy on the agent at all. Deterministic keys, loops, nested steps, lambdas, `final` classes, records, GraalVM: all fine. This is what Temporal (activity stubs), DBOS (`DBOS.runStep`), and Restate (`ctx.run`) converge on. **Accepted.**
5. **`@Step` on separate beans** — ordinary Spring AOP works there because the call crosses a bean boundary. Kept as sugar on top of 4, with the same documented rule as `@Transactional`: annotate methods on *another* bean.

### Decision
- Core contract: `Agent<I,O>.run(I input, Steps steps)`. All durability goes through `Steps`.
- `@Step` is allowed only on beans that are not the `@DurableAgent`. A `StepPlacementValidator` (`BeanPostProcessor`) fails application startup with a precise message if a `@DurableAgent` class declares `@Step` methods.
- The AOP interceptor for `@Step` on other beans obtains the active `Steps` from a holder set by the engine for the duration of `run()`. If no run is active, the method executes normally (so step beans stay usable outside agents and in plain unit tests).
- Approvals are never annotation-driven; `steps.approval(...)` keeps the suspension point visible in the run code.

### Consequences
- Slightly more verbose than pure annotations; in exchange no silent failure modes and no build tooling.
- Determinism between steps is a user obligation; `strict-replay` mode catches divergence at resume time.

## ADR-002 — Active run holder: `ThreadLocal` plus Micrometer context-propagation, not `ScopedValue`

**Status:** accepted, 2026-09-04

### Problem
The `@Step` interceptor and the Spring AI advisor need to find the `Steps` of the run executing on the current thread. `ScopedValue` is the modern answer, but it is a preview API on Java 21, the baseline of this project (GA only from Java 25).

### Decision
- `StepsHolder` is a plain `ThreadLocal`, set by the engine for the duration of `run()` and cleared in `finally`.
- Steps with a timeout run on a fresh virtual thread; the engine copies the holder explicitly.
- For threads the user spawns inside a step, a `ThreadLocalAccessor` (`StepsThreadLocalAccessor`) is registered with Micrometer's `ContextRegistry`, so `ContextSnapshot`-aware executors and Spring's `TaskDecorator` propagate the run. `io.micrometer:context-propagation` is an optional dependency of the core; only that one class touches it (enforced by ArchUnit).
- Revisit when the baseline moves to Java 25: replace the `ThreadLocal` with a `ScopedValue` and drop the accessor.

## ADR-003 — Step keys are hierarchical: `runId:name:n` at the top level, `parentKey/name:n` below

**Status:** accepted, 2026-09-04

### Problem
With a flat counter per name, a step started inside another step ("child") shares the counter with children of other steps. When the first parent is replayed from the store, its children never run, the counter does not advance, and the child of a *later* parent gets the key that belongs to the first one: the wrong result is replayed. The Spring AI advisor hit exactly this with two `steps.llm(...)` calls after a resume.

### Decision
- Top-level steps: `runId:name:n`, where `n` counts calls with that name in this run.
- Nested steps: `parentKey/name:n`, where `n` counts calls with that name *under that parent*.
- Side effects use a fixed key `runId:sideEffect:<key>` without a counter (idempotency-key semantics).
- Only top-level steps take part in the strict-replay comparison; whatever happens inside a step is the step's business.

### Consequences
- Keys are longer but self-describing: `01J…:classify:0/llm:0/tool.orders_get.9f3a…:0` reads as a call tree.
- Tool steps embed a hash of their arguments in the name, so a model that calls the same tool with different arguments across a re-run never replays the wrong result.

## ADR-004 — JDBC store with `JdbcClient` and self-contained Flyway migrations

**Status:** accepted, 2026-09-04

### Problem
The store needs JSON columns (`jsonb` in PostgreSQL), an atomic lease acquisition, and a schema that installs itself without colliding with the application's own migrations. Spring Data JDBC repositories add mapping ceremony for a handful of tables and no natural home for `?::jsonb` casts or the conditional update behind a lease.

### Decision
- The three stores are hand-written on `JdbcClient` (from `spring-jdbc`), one class per table, a small `SqlDialect` enum for the two supported databases (PostgreSQL, H2) that differs only in the JSON cast.
- Lease acquisition is one conditional `UPDATE`; the row count is the lock.
- Migrations ship inside the jar under `io/github/bnymndev/durableagents/db/{postgresql,h2}` and run through a dedicated Flyway instance with its own history table `durable_agents_schema_history`, so an application's Flyway or Liquibase never sees them. `durable-agents.jdbc.migrate=false` turns this off for teams that apply SQL themselves.
- Store operations join the caller's transaction when one is active and never open one of their own; the engine never holds a transaction across an LLM call.

## ADR-005 — Events: Spring `ApplicationEventPublisher` in the core, Modulith externalization at the edge

**Status:** accepted, 2026-09-04

The core publishes plain application events (`RunStartedEvent`, `StepCompletedEvent`, `ApprovalRequestedEvent`, …) through `ApplicationEventPublisher`; it has no Spring Modulith dependency. The approval module re-publishes `ApprovalRequestedEvent` as `ApprovalRequested` annotated with `@Externalized`, so an application with `spring-modulith-events-kafka` (or AMQP, SQS, JMS) gets approval notifications on its broker without touching the engine, and the Modulith event publication registry serves as the outbox. A dedicated `agent_event` table is therefore not needed.

## Open questions
- Multi-instance safety beyond leases (advisory locks) — v0.2.
- Reactive support (`ChatClient` streaming) — out of scope until requested.
- A small admin UI on top of `/actuator/agents` — v0.2.
