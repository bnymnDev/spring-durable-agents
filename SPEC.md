# SPEC — spring-durable-agents v0.1

## Goal
Make AI agents in Spring Boot boring to run in production: every LLM and tool call is a durable step in Postgres, a crashed or redeployed agent resumes where it stopped, humans can approve steps, and the whole thing is observable through Actuator and Micrometer. Feels like Spring — annotations, autoconfiguration, properties, slice tests — not like a workflow engine bolted on.

Pitch line: *"Durable execution for Spring AI agents. Crash, redeploy, resume."*

## Non-goals (v0.1)
- A general workflow engine (no DAGs, no BPMN, no Temporal replacement)
- Multi-node coordination / leader election; v0.1 assumes one instance owns a run at a time (DB lock)
- Own LLM abstraction — Spring AI's `ChatClient` is the LLM layer
- UI beyond Actuator JSON; a small admin UI is v0.2

## Programming model

Decision (see DECISIONS.md, ADR-001): steps are **explicit calls on a `Steps` object**, not intercepted methods on the agent class. No proxy magic on the agent itself, so self-invocation, loops, lambdas and `final` classes all just work. `@Step` still exists as sugar — but only on *other* Spring beans, intercepted by ordinary Spring AOP, the same rule Spring applies to `@Transactional`.

```java
@DurableAgent("ticket-triage")
public class TicketTriageAgent implements Agent<Ticket, Resolution> {

  private final ChatClient chat;
  private final TriageSteps triage;          // separate bean with @Step methods

  @Override
  public Resolution run(Ticket ticket, Steps steps) {
    var c = steps.llm("classify", Classification.class, () ->
        chat.prompt().user(u -> u.text("Classify: {t}").param("t", ticket.body()))
            .call().entity(Classification.class));

    var similar = steps.retry(3, Duration.ofSeconds(2))
                       .run("findSimilar", () -> triage.findSimilar(c));

    var r = steps.approval("SUPPORT_LEAD", Duration.ofDays(1))
                 .run("propose", () -> triage.propose(c, similar));

    steps.run("apply", () -> triage.apply(r));
    return r;
  }
}

@Component
class TriageSteps {
  @Step(retry = @Retry(maxAttempts = 3))           // sugar: intercepted via Spring AOP
  public List<SimilarTicket> findSimilar(Classification c) { ... }
  @Step public Resolution propose(Classification c, List<SimilarTicket> s) { ... }
  @Step public void apply(Resolution r) { ... }
}
```

Semantics:
- `Agent<I, O>` has one method `O run(I input, Steps steps)`. The engine calls it to start a run and calls it again, with the same input, to resume. `run` must be deterministic *between* steps; everything non-deterministic (LLM, I/O, `now()`, random) goes inside a step. Same contract as Temporal/DBOS, documented up front.
- `steps.run(name, supplier)`: computes `stepKey = runId:name:n` where `n` is the number of previous calls with that name in this run. On resume, a completed key returns the stored value and the supplier is not executed. Loops work because `n` increments.
- Nested `steps.run` inside a step is a child step (`kind = CHILD`, parent key recorded).
- `steps.llm(...)` marks `kind = LLM` and switches the `DurableStepAdvisor` on for the duration of the supplier, so `ChatClient` calls inside are recorded with tokens/model.
- `steps.approval(role, timeout).run(...)`: persists `PENDING_APPROVAL`, then throws `RunSuspended` (a control-flow exception extending `Error`-free `RuntimeException`, never caught by user code by convention; the engine documents "do not catch `RunSuspended`"). On approval the run is resumed; the step supplier runs on resume and its result is stored.
- `steps.sideEffect(key, supplier)` for ad-hoc idempotent values (payment ids, timestamps) without a named step.
- Calling a `@Step` method on another bean from inside `run` (outside a `steps.run`) is still durable: the AOP interceptor resolves the current `Steps` from a scoped holder and creates the step with `name = beanName.methodName`. Calling a `@Step` method from within the same bean is not intercepted — Spring AOP rule — and a startup `StepPlacementValidator` fails fast if a class annotated `@DurableAgent` declares `@Step` methods.
- Determinism guard (opt-in, `durable-agents.strict-replay=true`): on resume the engine compares the sequence of step names against the recorded one and fails with a clear diff if code and history diverged (like Temporal's non-determinism error).

### Annotations
| Annotation | Where | Attributes |
|---|---|---|
| `@DurableAgent` | agent class implementing `Agent<I,O>` | `value` (name), `version` |
| `@Step` | methods of any Spring bean **other than** the agent | `name`, `version`, `retry`, `timeout`, `compensate` |
| `@Retry` | inside `@Step` or via `steps.retry(...)` | `maxAttempts`, `backoff`, `multiplier`, `retryOn`, `abortOn` |

Approvals have no annotation; they are always `steps.approval(...)` so the suspension point is visible in the run code.

### Programmatic API
```java
public interface AgentRuns {
  <I> RunHandle start(Class<? extends Agent<I, ?>> agent, I input);
  RunHandle resume(RunId id);
  RunStatus status(RunId id);
  void cancel(RunId id, String reason);
  Page<RunSummary> find(RunQuery q);
}
public interface StepContext {   // injectable into any step method as parameter
  RunId runId(); String stepKey(); int attempt(); void heartbeat();
  <T> T sideEffect(String key, Supplier<T> s);   // ad-hoc durable value without a @Step method
}
```

## Persistence (Spring Data JDBC, Postgres)
```
agent_run(id ulid pk, agent_name, agent_version, status, input jsonb, output jsonb,
          error text, created_at, updated_at, owner_instance, lease_until, correlation_id)
agent_step(id bigserial pk, run_id fk, step_key, step_name, step_version, sequence,
           status, attempt, input jsonb, output jsonb, error text, kind, tokens_in, tokens_out,
           model, started_at, finished_at, unique(run_id, step_key))
agent_approval(id, run_id, step_key, required_role, requested_at, decided_at, decided_by,
               decision, comment, expires_at)
agent_event(id, run_id, ts, type, payload jsonb)   -- outbox for Modulith externalization
```
Statuses: run `RUNNING | SUSPENDED | COMPLETED | FAILED | CANCELLED`, step `COMPLETED | FAILED | PENDING_APPROVAL | SKIPPED`.
Store SPI in core (`AgentRunStore`, `StepStore`, `ApprovalStore`) so an in-memory and a JDBC implementation both exist; JDBC is the shipped one.
Ownership: a run is executed under a lease (`owner_instance`, `lease_until`, heartbeat); a `RunReaper` scheduled task re-queues runs with expired leases → resume on any instance. This gives crash recovery without a broker.

## Spring AI integration (`durable-agents-spring-ai`)
- **`DurableStepAdvisor`** — a `ChatClient` advisor: wraps every `ChatClient` call made inside a step as a sub-step of kind `LLM`, records prompt hash, model, token usage, latency. Registered automatically when the starter is on the classpath.
- **Tool wrapping** — Spring AI `@Tool` methods on agent beans become durable tool steps (`kind = TOOL`) so a tool executed during an LLM turn is not re-executed on resume.
- **MCP** — Spring AI MCP client tools are wrapped the same way; each MCP tool call is a step with server name + tool name recorded.
- **Structured outputs** — `.entity(Class)` results are stored as the step output; no extra serialization work for the user.
- **Prompt versioning** — `@Step(version)` plus prompt hash lets `find(RunQuery)` show which prompt version produced which output.

## Human in the loop (`durable-agents-approval`)
- REST: `GET /agents/approvals?role=…`, `POST /agents/approvals/{id}/approve`, `POST …/reject`, with `comment`.
- Secured with Spring Security; role from `@Approval(role)` maps to `hasRole(...)`. Autoconfigured `SecurityFilterChain` for `/agents/**` only when the user provides none.
- Spring Modulith event `ApprovalRequested` published on suspend; externalizable to Kafka/SQS/email via Modulith externalization, so teams can plug Slack/Teams notifications in without touching the engine.
- Timeout via scheduled `ApprovalExpirer` and `onTimeout` policy.

## Observability (`durable-agents-actuator`)
- `/actuator/agents` — running/suspended/failed counts per agent, recent failures.
- `/actuator/agents/runs/{id}` — full step timeline.
- Health indicator: store reachable, reaper alive, stale leases count.
- Micrometer: `agent.step.duration` (tags: agent, step, kind, status), `agent.llm.tokens` (in/out, model), `agent.run.active`, `agent.approval.pending`.
- OpenTelemetry: one span per run, child span per step, LLM spans follow GenAI semantic conventions (`gen_ai.*` attributes).
- Optional structured log line per step via `slf4j` MDC (`runId`, `stepKey`).

## Resilience
- `@Retry` implemented in the engine (no Spring Retry dependency); attempts persisted per step.
- `@Step(timeout)` cancels via virtual-thread interruption and marks `FAILED(timeout)`.
- `@Step(compensate)` — saga-style: on run failure after step N, compensating methods of completed steps N..1 run in reverse if defined.
- Idempotency keys for outbound side effects: `StepContext.sideEffect("payment:" + id, …)`.

## Testing support (`durable-agents-test`)
- `@DurableAgentTest` slice: in-memory store, fake `ChatClient` with scripted responses (`FakeChatModel.respondWith(...)`), scheduling off.
- `AgentRunAssert` (AssertJ): `assertThatRun(id).completed().hasSteps("classify","findSimilar","propose","apply").step("propose").wasApproved()`.
- Crash simulation: `crashAfter("findSimilar")` then `resume()` in the same test.
- Testcontainers-backed `@DurableAgentIntegrationTest` for the JDBC store.

## Configuration (`durable-agents.*`)
```yaml
durable-agents:
  enabled: true
  store: jdbc            # jdbc | memory
  lease-duration: PT30S
  reaper-interval: PT10S
  retention: P30D
  approval:
    base-path: /agents/approvals
    default-timeout: P1D
  observability:
    record-prompts: false   # store full prompt text vs. hash only (PII)
    otel: true
```
All properties have metadata + docs; `record-prompts` defaults to hash-only.

## Examples
- **ticket-triage** — Postgres, one LLM step, one approval, Actuator on, Docker Compose, README with curl walkthrough: start run → crash container → resume → approve → done.
- **order-refund** — MCP tools (points at `shopware-mcp` through `agentgate`), compensation on failure, Modulith externalization to a log-based notifier. Demonstrates the three repos together.

## Docs
- README: pitch, 40-line example, feature table, "how resume works" diagram, comparison table (vs. Spring AI alone, Temporal, DBOS, LangGraph4j), Maven/Gradle snippet.
- `docs/`: getting-started, programming-model, persistence, approvals, observability, testing, configuration reference, migration notes.

## Milestones
- **M0 (week 1–2):** core engine, annotations, proxy interception, in-memory store, `Steps` API + `@Step`-on-other-beans resume semantics, `StepPlacementValidator`, ArchUnit rules, first tests.
- **M1 (week 3–4):** JDBC store, Flyway, leases + reaper, crash/resume integration test with Testcontainers.
- **M2 (week 5–6):** Spring AI module: advisor, tool + MCP wrapping, token accounting. ticket-triage example.
- **M3 (week 7–8):** approvals module, Security, Modulith events; actuator module, metrics, OTel.
- **M4 (week 9–10):** test module, retry/timeout/compensate, order-refund example, docs site, Maven Central `0.1.0`, launch (r/java, Spring community, LinkedIn, JUG talk proposal).

## Open questions (record in docs/decisions.md)
- ~~Proxy mechanism~~ — decided, ADR-001 in DECISIONS.md: explicit `Steps` API, `@Step` only on other beans.
- Multi-instance safety beyond leases (advisory locks) — v0.2.
- Reactive support (`ChatClient` streaming) — out of scope until requested.
