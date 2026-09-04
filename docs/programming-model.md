# Programming model

## The contract

```java
public interface Agent<I, O> {
  O run(I input, Steps steps);
}
```

The engine calls `run` to start a run and calls it again, with the same input, to resume. Between
those calls anything may have happened: a crash, a redeploy, a night waiting for an approval. The
store remembers which steps completed and with what result; `run` must walk the same path so that
the engine can hand those results back.

This is the same contract Temporal, DBOS and Restate use, stated once and enforced once:

> Code between steps is deterministic. Everything non-deterministic runs inside a step.

Deterministic means: same input, same sequence of step names. Loops are fine (the counter in the key
increments), branches on step *results* are fine (the results are replayed), branches on the clock,
on random values or on data read outside a step are not.

## Steps

| Call | What it does |
|---|---|
| `steps.run(name, supplier)` | Runs the supplier once; on replay returns the stored value. The stored class name is used to deserialize; works for records and plain classes. |
| `steps.run(name, Class<T>, supplier)` | Same, with an explicit type. |
| `steps.run(name, TypeRef<T>, supplier)` | Same, for generic results: `new TypeRef<List<Foo>>() {}`. |
| `steps.run(name, runnable)` | A step without a result. |
| `steps.llm(name, type, supplier)` | A step of kind `LLM`. Chat calls inside it are recorded as child steps with model and tokens. |
| `steps.sideEffect(key, type, supplier)` | A durable value keyed by *your* key, not counted: `sideEffect("payment:" + id, ...)` returns the same value forever. Idempotency keys, generated ids, timestamps. |
| `steps.retry(n, backoff)` | Starts a step with a retry policy. `.retry(n, backoff, multiplier)`, `.retryOn(type)`, `.abortOn(type)` refine it. Attempts are persisted; a retry survives a restart. |
| `steps.timeout(duration)` | The step runs on its own virtual thread and is interrupted when the timeout elapses; recorded as `FAILED`. |
| `steps.version(n)` | Bump when the serialized shape of the result changes; a stored result with another version is recomputed. |
| `steps.compensate(runnable)` | Registers an undo that runs, in reverse order, when the run fails later. |
| `steps.approval(role, timeout)` | A step that waits for a human. See below. |
| `steps.heartbeat()` | Extends the lease from inside a long step. |

The builders combine: `steps.retry(3, ofSeconds(2)).timeout(ofSeconds(30)).run("fetch", Doc.class, ...)`.

### Step keys

Every call computes a key. Top level: `runId:name:n`, where `n` counts previous calls with that name
in this run. Inside another step: `parentKey/name:n`. A `sideEffect` key is `runId:sideEffect:<key>`.

```
01J8X…:classify:0
01J8X…:classify:0/llm:0
01J8X…:classify:0/llm:0/tool.orders_get.9f3a2c1b0e77:0
01J8X…:findSimilar:0
01J8X…:sideEffect:payment:10042
```

The key is what makes a step idempotent: a completed key never executes again.

### What happens on replay

1. The engine loads all steps of the run, ordered by sequence.
2. `run` is called. Each `steps.*` call computes its key.
3. Key found with status `COMPLETED` and the same version → the stored output is decoded and returned; the supplier is not called. Listeners get `onStepReplayed`.
4. Key not found, or `FAILED` → the supplier runs; the result is stored; execution continues.
5. Key found with status `PENDING_APPROVAL` → see approvals.

Nothing between steps is recorded, which is why it has to be deterministic.

### Strict replay

`durable-agents.strict-replay=true` compares the sequence of top-level step names produced by the
code with the recorded one on every resume and fails the run with a `NonDeterministicReplay` that
names the position, the recorded name and the actual name. Turn it on in tests and staging; it is
the fastest way to find a `LocalDate.now()` outside a step or a deploy that changed an agent while
runs were in flight.

## `@Step` on other beans

```java
@Component
class TriageSteps {
  @Step(name = "similar", retry = @Retry(maxAttempts = 3, backoff = 500), timeout = "PT10S", compensate = "undoApply")
  public List<SimilarTicket> findSimilar(Classification c, StepContext ctx) { ... }
}
```

Calling a `@Step` method while a run is active records a step named `beanName.methodName` (or
`name`). The method's generic return type is used for replay, so `List<SimilarTicket>` comes back
as a list of `SimilarTicket`, not of maps. A `StepContext` parameter is injected (pass `null` at the
call site); it exposes `runId()`, `stepKey()`, `attempt()`, `heartbeat()` and `sideEffect(...)`.

Outside a run the method just executes, so the bean stays usable in plain unit tests.

The rule: **not on the agent class.** Spring AOP intercepts calls that cross a bean boundary; a bean
calling its own method bypasses the proxy and the step would silently not be recorded. The
`StepPlacementValidator` fails application startup with a message naming the class and the methods.
Private, static and final methods are refused for the same reason. This is [ADR-001](../DECISIONS.md).

## Approvals

```java
Resolution r = steps.approval("SUPPORT_LEAD", Duration.ofDays(1))
    .description("refund 120 EUR for order 10042")
    .onTimeout(ApprovalTimeoutPolicy.REJECT)
    .run("propose", Resolution.class, () -> triage.propose(c, similar));
```

First execution: the step is stored as `PENDING_APPROVAL`, an approval request with the role and an
expiry is created, `ApprovalRequestedEvent` is published, and the run suspends by throwing
`RunSuspended`. **Do not catch `RunSuspended`** (or `RunCancelled`); let them reach the engine.

When someone with the role approves, the run is resumed, `run` replays up to the approval step, the
supplier executes, its result is stored, the run continues. On rejection the step fails with an
`ApprovalRejected` naming who rejected and why, compensations run, the run is `FAILED`. On expiry
the `onTimeout` policy decides: `FAIL` (default), `REJECT`, or `APPROVE`.

Approvals are never annotation-driven. The suspension point is visible in the run code on purpose.

## Compensation

A saga in three lines:

```java
String reservationId = steps.compensate(() -> payments.release(orderId))
    .run("reserve", String.class, () -> payments.reserve(orderId, amount));
```

or `@Step(compensate = "releaseReservation")` on a step bean, where the compensating method takes
the same parameters. When the run fails after that step, the engine runs the registered
compensations in reverse order of the completed steps. Each compensation is itself a durable step
(`compensate:<name>`, kind `COMPENSATION`), so a resume of a failed run does not undo twice. A
`cancel()` does not compensate; it stops.

## Errors, crashes and cancellation

| What | Effect |
|---|---|
| Supplier throws an `Exception` | Attempt recorded as `FAILED`. Retried if the policy says so; otherwise the exception propagates, compensations run, the run is `FAILED`. |
| Supplier or listener throws an `Error` | Treated like a process crash: nothing else is recorded, the run stays `RUNNING`, its lease is released. The reaper or `resume()` continues it. `SimulatedCrash` in the test module is an `Error` for exactly this reason. |
| `runs.cancel(id, reason)` | A suspended run is `CANCELLED` at once; a running one at its next step boundary (`RunCancelled` is thrown into the agent). |
| Lease lost to another instance | The current execution stops at its next step boundary without writing anything. |

## Serialization

Inputs, outputs and step results are JSON in the store (Jackson 3 by default; replace the
`StepCodec` bean for anything else). Use records or plain classes with a stable shape. When the
shape changes, bump `steps.version(n)` or `@Step(version = n)` so old results are recomputed instead
of failing to decode. Spring AI `.entity(Class)` results are stored as the step output without extra
work.

## Events

The engine publishes Spring application events synchronously in the executing thread:
`RunStartedEvent`, `RunCompletedEvent`, `RunFailedEvent`, `RunSuspendedEvent`, `RunCancelledEvent`,
`StepCompletedEvent`, `ApprovalRequestedEvent`, `ApprovalDecidedEvent`. `@EventListener` methods see
them; `@TransactionalEventListener` works when the caller runs in a transaction.

For in-band observation (timing, tracing) implement `StepListener` or `RunListener` beans; the
actuator module does exactly that.
