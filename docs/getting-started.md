# Getting started

## 1. Add the starter

```xml
<dependency>
  <groupId>io.github.bnymndev</groupId>
  <artifactId>durable-agents-starter</artifactId>
  <version>0.1.0</version>
</dependency>
<!-- optional modules -->
<dependency><groupId>io.github.bnymndev</groupId><artifactId>durable-agents-spring-ai</artifactId><version>0.1.0</version></dependency>
<dependency><groupId>io.github.bnymndev</groupId><artifactId>durable-agents-approval</artifactId><version>0.1.0</version></dependency>
<dependency><groupId>io.github.bnymndev</groupId><artifactId>durable-agents-actuator</artifactId><version>0.1.0</version></dependency>
<dependency><groupId>io.github.bnymndev</groupId><artifactId>durable-agents-test</artifactId><version>0.1.0</version><scope>test</scope></dependency>
```

The starter needs a `DataSource` (PostgreSQL in production, H2 for local runs). The bundled Flyway
migrations create `agent_run`, `agent_step` and `agent_approval` on first start, tracked in their own
history table. For a quick look without a database:

```yaml
durable-agents:
  store: memory
```

Turn on virtual threads; the engine runs every agent on one and nothing blocks a platform thread
while a model thinks:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

## 2. Write an agent

```java
@DurableAgent("ticket-triage")
public class TicketTriageAgent implements Agent<Ticket, Resolution> {

  private final ChatClient chat;
  private final TriageSteps triage;

  public TicketTriageAgent(ChatClient.Builder builder, TriageSteps triage) {
    this.chat = builder.build();
    this.triage = triage;
  }

  @Override
  public Resolution run(Ticket ticket, Steps steps) {
    var c = steps.llm("classify", Classification.class, () ->
        chat.prompt().user(u -> u.text("Classify: {t}").param("t", ticket.body()))
            .call().entity(Classification.class));

    var similar = steps.retry(3, Duration.ofSeconds(2))
        .run("findSimilar", new TypeRef<List<SimilarTicket>>() {}, () -> triage.findSimilar(c));

    var r = steps.approval("SUPPORT_LEAD", Duration.ofDays(1))
        .run("propose", Resolution.class, () -> triage.propose(c, similar));

    steps.run("apply", () -> triage.apply(r));
    return r;
  }
}
```

```java
@Component
class TriageSteps {
  @Step(retry = @Retry(maxAttempts = 3))
  public List<SimilarTicket> findSimilar(Classification c) { ... }
  @Step public Resolution propose(Classification c, List<SimilarTicket> s) { ... }
  @Step public void apply(Resolution r) { ... }
}
```

Two rules, both enforced:

- **Everything non-deterministic goes inside a step.** LLM calls, HTTP, `Instant.now()`, random ids.
  The code between steps runs again on every resume and must produce the same sequence of step names.
- **`@Step` methods live on a bean other than the agent.** Spring AOP cannot intercept a bean calling
  itself; the starter refuses to boot when an agent class declares `@Step` methods.

## 3. Start runs

```java
@RestController
class TicketController {
  private final AgentRuns runs;
  TicketController(AgentRuns runs) { this.runs = runs; }

  @PostMapping("/tickets")
  Map<String, String> open(@RequestBody Ticket ticket) {
    RunHandle handle = runs.start(TicketTriageAgent.class, ticket, ticket.id());
    return Map.of("runId", handle.runId().value());
  }
}
```

`start` returns immediately. `handle.completion()` is a `CompletableFuture<RunResult>` for this
execution; `runs.status(id)`, `runs.summary(id)` and `runs.output(id, Resolution.class)` read the
store.

## 4. Crash, redeploy, resume

Kill the process while a run is in flight. The run stays `RUNNING` in the store with a lease that
expires after `durable-agents.lease-duration` (30 s). When any instance starts, its reaper picks the
run up within `durable-agents.reaper-interval` (10 s), replays every completed step from the store and
continues at the first step without a result. No model call is repeated, no side effect runs twice.

`runs.resume(id)` does the same thing on demand, also for a `FAILED` run after you fixed the cause.

## 5. Approve

With `durable-agents-approval` on the classpath:

```sh
curl -u lead:secret localhost:8080/agents/approvals
curl -u lead:secret -X POST localhost:8080/agents/approvals/{id}/approve -H 'content-type: application/json' -d '{"comment":"ok"}'
```

The caller needs the approval's role (`ROLE_SUPPORT_LEAD` with Spring Security). Details in
[approvals.md](approvals.md).

## 6. Test

```java
@DurableAgentTest
class TicketTriageAgentTests {
  @Autowired DurableAgentTester agents;
  @Autowired FakeChatModel chat;

  @Test
  void resumesAfterCrash() {
    chat.respondWithJson(new Classification("billing", Urgency.NORMAL, "wrong VAT"));
    agents.crashAfter("classify");

    RunResult crashed = agents.run(TicketTriageAgent.class, ticket);
    RunResult suspended = agents.resume(crashed.runId());
    RunResult done = agents.approve(crashed.runId(), "lead");

    agents.assertThatRun(done).completed().hasSteps("classify", "findSimilar", "propose", "apply")
        .step("propose").wasApproved();
    assertThat(chat.calls()).isEqualTo(1);
  }
}
```

No database, no Docker, no API key. See [testing.md](testing.md).

## Next

- [programming-model.md](programming-model.md) — every `Steps` operation, keys, replay, determinism, compensation
- [persistence.md](persistence.md) — the schema, leases, retention, bringing your own store
- [observability.md](observability.md) — `/actuator/agents`, metrics, spans, MDC
- [configuration.md](configuration.md) — every property
- The two examples under [`examples/`](../examples)
