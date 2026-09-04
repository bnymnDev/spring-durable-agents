# Testing

`durable-agents-test` (test scope) brings a slice, a scripted model, crash simulation and assertions.

## `@DurableAgentTest`

A Spring Boot test slice. It loads `@DurableAgent` beans and every bean that declares `@Step`
methods, the engine on an in-memory store, no scheduler, strict replay on, and a `FakeChatModel`
behind `ChatClient.Builder` when Spring AI is present. Nothing else: add what your steps need with
`includeFilters`.

```java
@DurableAgentTest(includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TicketSystem.class))
class TicketTriageAgentTests {

  @Autowired DurableAgentTester agents;
  @Autowired FakeChatModel chat;

  @BeforeEach void reset() { chat.reset(); }

  @Test
  void resumesAfterCrash() {
    chat.respondWithJson(new Classification("billing", Urgency.NORMAL, "wrong VAT"));
    agents.crashAfter("classify");

    RunResult crashed = agents.run(TicketTriageAgent.class, ticket);
    agents.assertThatRun(crashed).crashed().hasSteps("classify");

    RunResult suspended = agents.resume(crashed.runId());
    agents.assertThatRun(suspended).suspended().hasSteps("classify", "findSimilar", "propose");
    assertThat(chat.calls()).isEqualTo(1);           // classify was replayed, not repeated

    RunResult done = agents.approve(crashed.runId(), "lead");
    agents.assertThatRun(done).completed().step("propose").wasApproved();
  }
}
```

## `DurableAgentTester`

| Method | Does |
|---|---|
| `run(agentClass, input)` | Starts a run and waits for this execution to end. |
| `resume(runId)` | Resumes and waits. |
| `crashAfter(stepName)` | Arms a one-shot crash right after that step completes. The run is left `RUNNING` with a released lease, exactly like a killed process. |
| `approve(runId, by)` / `reject(runId, by, comment)` | Decides the single pending approval of the run and waits. |
| `pendingApprovals()` | What is waiting. |
| `assertThatRun(runId)` | AssertJ entry point. |

## `FakeChatModel`

`respondWith("text", ...)` and `respondWithJson(object)` queue answers; each model call consumes one.
Without a queued answer the `fallback` function answers (`"fake response"` by default). `prompts()`
returns what the agent sent, `calls()` how often. Responses report `fake-chat-model` and a token
usage you can set with `usage(in, out)`, so LLM steps look like the real thing in the store.

Declare your own `ChatModel` bean in a `@TestConfiguration` and the fake backs off.

## Assertions

```java
agents.assertThatRun(id)
    .completed()                       // .failed() .suspended() .cancelled() .crashed() .hasStatus(...)
    .hasOutput(Resolution.class, expected)
    .hasErrorContaining("rejected by")
    .hasSteps("classify", "findSimilar", "propose", "apply")   // top-level, in order
    .step("propose").wasApproved()     // .completed() .failed() .pendingApproval()
    .hasKind(StepKind.APPROVAL).hasAttempts(2).hasModel("fake-chat-model").hasOutputContaining("...");
```

`steps()` returns the raw `StepRecord`s when you need more; `stepWithKey("…:work:2")` picks a loop
iteration.

## Plain unit tests

A `@Step` bean called outside a run just executes; no engine involved. The agent class itself is a
plain object too: `new TicketTriageAgent(chatBuilder, steps).run(ticket, stepsStub)` works with any
`Steps` implementation you like, though the slice is usually less work.

## Integration tests against PostgreSQL

```java
@DurableAgentIntegrationTest
class TriageIntegrationTests { ... }
```

`@SpringBootTest` plus a PostgreSQL Testcontainer wired through `@ServiceConnection`, JDBC store,
strict replay, scheduling on (so the reaper is part of the test). Needs Docker and
`org.testcontainers:testcontainers-postgresql` on the test classpath. The image is
`postgres:17-alpine`; override with `durable-agents.test.postgres-image`.
