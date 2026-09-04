# Observability

`durable-agents-actuator` plugs the engine into Actuator, Micrometer and Micrometer Observation
(so OpenTelemetry when tracing is configured).

## Actuator endpoint

Expose it: `management.endpoints.web.exposure.include=agents,health,...`.

| Path | Returns |
|---|---|
| `GET /actuator/agents` | Counts per agent and status, runs active on this instance, pending approvals, the ten most recent failures. |
| `GET /actuator/agents/runs` | The 50 most recent runs. |
| `GET /actuator/agents/runs/{id}` | The run with input, output, owner, lease and the full step timeline. |

A step in the timeline:

```json
{ "stepKey": "01J…:classify:0/llm:0", "stepName": "llm", "kind": "LLM", "status": "COMPLETED",
  "attempt": 1, "model": "gpt-4o-mini", "tokensIn": 412, "tokensOut": 37,
  "input": "sha256:…", "output": "{\"text\":\"…\",\"model\":\"gpt-4o-mini\",…}",
  "startedAt": "…", "finishedAt": "…", "parentKey": "01J…:classify:0" }
```

## Health

`agents` health indicator: `UP` when the store answers; details `staleLeases` (crashed runs waiting
for a resume), `agents`, `reaperLastRun`; `DEGRADED` when the reaper has not run for three intervals;
`DOWN` when the store is unreachable. `management.health.agents.enabled=false` turns it off.

## Metrics

| Meter | Type | Tags |
|---|---|---|
| `agent.step.duration` | timer | `agent`, `step`, `kind`, `status` |
| `agent.run.duration` | timer | `agent`, `status` |
| `agent.llm.tokens` | counter | `agent`, `model`, `direction` (`in`/`out`) |
| `agent.run.active` | gauge | runs executing on this instance |
| `agent.approval.pending` | gauge | approvals waiting |

Tool steps drop their argument hash from the `step` tag (`tool.orders_get`), so cardinality stays
bounded. `durable-agents.observability.metrics=false` turns metrics off.

## Traces

One observation per run (`agent.run`) and one per step (`agent.step`), nested, so a trace shows the
run as a parent span with a child span per step and grandchildren for LLM and tool calls. LLM steps
carry GenAI semantic-convention attributes: `gen_ai.operation.name=chat`, `gen_ai.response.model`,
`gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`. Add `spring-boot-starter-opentelemetry`
and an exporter; `durable-agents.observability.observations=false` turns the observations off.

## Logs

While a run executes the SLF4J MDC carries `runId`, `agent` and `stepKey`. Put them in the pattern:

```yaml
logging:
  pattern:
    correlation: "[%X{runId:-}] [%X{stepKey:-}] "
```

`durable-agents.observability.mdc=false` turns it off.

## Prompts

By default an LLM step stores the SHA-256 of the prompt as its input, not the text: prompts carry
personal data. `durable-agents.observability.record-prompts=true` stores the full text. Tool steps
store their arguments; the assistant text of every chat call is stored as the step output because
that is what replay needs.
