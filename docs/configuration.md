# Configuration reference

All properties live under `durable-agents.*` and have IDE metadata.

| Property | Default | Meaning |
|---|---|---|
| `durable-agents.enabled` | `true` | Master switch. |
| `durable-agents.store` | `jdbc` | `jdbc` (PostgreSQL, H2) or `memory` (lost on restart; tests and demos). |
| `durable-agents.instance-id` | `hostname:pid` | Lease owner id. Set to the pod name to recognise instances in the store. |
| `durable-agents.lease-duration` | `PT30S` | Lease validity without a heartbeat; a crash is detected after this. |
| `durable-agents.reaper-interval` | `PT10S` | How often expired leases are looked for. |
| `durable-agents.reaper-batch-size` | `50` | Runs resumed per reaper pass. |
| `durable-agents.retention` | `P30D` | Finished runs older than this are deleted. `0` keeps them. |
| `durable-agents.retention-interval` | `PT1H` | How often retention runs. |
| `durable-agents.strict-replay` | `false` | Fail a resume whose step sequence diverged from the history. |
| `durable-agents.scheduling.enabled` | `true` | Reaper, retention and approval expiry on a schedule. The test slice turns it off. |
| `durable-agents.jdbc.migrate` | `true` | Apply the bundled Flyway migrations (own history table). |
| `durable-agents.approval.base-path` | `/agents/approvals` | REST base path. |
| `durable-agents.approval.default-timeout` | `P1D` | Timeout for `steps.approval(role)`. |
| `durable-agents.approval.expiry-interval` | `PT1M` | How often expired approvals are processed. |
| `durable-agents.approval.enforce-roles` | `true` | Require `isUserInRole(requiredRole)` on the endpoints. |
| `durable-agents.approval.security-auto-configured` | `true` | Register a filter chain for the approval paths when the app has none. |
| `durable-agents.observability.record-prompts` | `false` | Store full prompt text instead of its hash. |
| `durable-agents.observability.metrics` | `true` | Micrometer meters. |
| `durable-agents.observability.observations` | `true` | Observations / spans per run and step. |
| `durable-agents.observability.mdc` | `true` | `runId`, `agent`, `stepKey` in the MDC. |

Related Spring properties worth setting:

```yaml
spring.threads.virtual.enabled: true              # agents run on virtual threads
management.endpoints.web.exposure.include: agents,health,metrics
management.health.agents.enabled: true
```

## Beans you can replace

Every bean the starter creates is `@ConditionalOnMissingBean`. Declare your own to override:

| Type | Purpose |
|---|---|
| `AgentRunStore`, `StepStore`, `ApprovalStore` | Another database, another shape. |
| `StepCodec` | Serialization other than Jackson 3. |
| `Executor` named `durableAgentsExecutor` | Where runs execute. |
| `Clock` named `durableAgentsClock` | Time, for tests. |
| `EngineSettings` | Instance id, lease, strict replay, default approval timeout in one record. |
| `StepListener`, `RunListener` beans | Additional in-band observers; all are called. |
| `SecurityFilterChain` | Any chain of your own switches the approval default off. |
| `DurableStepAdvisor` | Different order or prompt recording. |
