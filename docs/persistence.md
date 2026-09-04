# Persistence

## Schema

Three tables, created by the bundled Flyway migrations on first start.

```
agent_run       id (ULID) · agent_name · agent_version · status · input (jsonb) · output (jsonb) · error
                created_at · updated_at · owner_instance · lease_until · correlation_id
agent_step      run_id → agent_run · step_key · step_name · step_version · sequence · status · attempt
                input · output (jsonb) · output_type · error · kind · parent_key
                tokens_in · tokens_out · model · started_at · finished_at        unique (run_id, step_key)
agent_approval  id · run_id → agent_run · step_key · required_role · description
                requested_at · expires_at · decision · decided_at · decided_by · comment
```

Statuses: run `RUNNING | SUSPENDED | COMPLETED | FAILED | CANCELLED`; step
`COMPLETED | FAILED | PENDING_APPROVAL | SKIPPED`; approval `PENDING | APPROVED | REJECTED | EXPIRED`.
Kinds: `STEP | CHILD | LLM | TOOL | SIDE_EFFECT | APPROVAL | COMPENSATION`.

The SQL lives in the jar under `io/github/bnymndev/durableagents/db/postgresql/` and `…/h2/`. The
migrations run through their own Flyway instance with the history table
`durable_agents_schema_history`, so they never interfere with the application's Flyway or Liquibase.
Set `durable-agents.jdbc.migrate=false` to apply the SQL yourself.

Supported databases: PostgreSQL (production) and H2 in PostgreSQL mode (tests, local runs). The
dialect is detected from the connection metadata.

## Leases and the reaper

A run executes under a lease: `owner_instance` and `lease_until` on `agent_run`, renewed by a
heartbeat every third of `durable-agents.lease-duration`. Acquiring a lease is a single conditional
`UPDATE`; the row count is the lock, so two instances cannot own the same run.

When an instance dies, its runs keep `status = RUNNING` with a lease that stops being renewed. Every
`durable-agents.reaper-interval` the reaper on each instance looks for `RUNNING` runs with
`lease_until <= now`, takes the lease and resumes them. Whichever instance wins the `UPDATE` runs it.
This gives crash recovery without a broker; v0.1 assumes one instance owns a run at a time.

A run that lost its lease (network partition, long GC pause) notices at its next step boundary and
stops without writing anything; the new owner has already taken over.

## Retention

`durable-agents.retention` (default 30 days) deletes `COMPLETED`, `FAILED` and `CANCELLED` runs whose
last update is older than that, with their steps and approvals (`ON DELETE CASCADE`), every
`durable-agents.retention-interval`. `0` keeps everything.

## Transactions

Store operations use the caller's transaction when one is active and never open one of their own.
The engine never holds a transaction across an LLM call; a step that writes to your tables inside a
`@Transactional` method commits with that method, and the step row is written right after.

## Bring your own store

The SPI is three interfaces in `io.github.bnymndev.durableagents.spi`: `AgentRunStore`, `StepStore`,
`ApprovalStore`. Declare beans of those types and the autoconfiguration backs off. The in-memory
implementation (`durable-agents.store=memory`) is the reference for the semantics, in particular
`tryAcquireLease`.

## Reading the data

- `/actuator/agents/runs/{id}` returns the run with its full step timeline.
- `AgentRuns.find(RunQuery)` pages runs by agent, status and correlation id.
- Plain SQL works too; the schema is stable and documented. Token accounting per model:

```sql
select model, sum(tokens_in) as in_tokens, sum(tokens_out) as out_tokens, count(*) as calls
from agent_step where kind = 'LLM' and model is not null group by model;
```
