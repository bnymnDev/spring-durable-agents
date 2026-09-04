# Migration notes

## Changing an agent while runs are in flight

Runs remember step names and results, not code. When you deploy a new version of an agent:

- **Adding a step at the end** is safe: old runs replay their history and execute the new step.
- **Adding a step in the middle** shifts the sequence. With `strict-replay=true` old runs fail with a
  `NonDeterministicReplay` naming the position; without it they run the new step (its key is new)
  and continue, which is usually what you want for an added read-only step and rarely for a side
  effect. Bump `@DurableAgent(version = n)` so runs of the two versions can be told apart in the
  store and decide per agent.
- **Renaming a step** makes its old results invisible; the step executes again. Keep names stable, or
  drain in-flight runs before renaming.
- **Changing a result type** in an incompatible way: bump `steps.version(n)` / `@Step(version = n)`
  and the old result is recomputed instead of failing to decode.

## Schema

The bundled migrations are versioned (`V1__durable_agents_init.sql`, …) and tracked in
`durable_agents_schema_history`. A new library version that changes the schema ships a new migration;
never edit an applied one. With `durable-agents.jdbc.migrate=false` apply the SQL from the jar
yourself before deploying the new version.

## From Spring AI alone

An agent that today is a `@Service` calling `ChatClient` becomes an `Agent<I, O>`: wrap each model
call in `steps.llm(...)`, each side effect in `steps.run(...)` or a `@Step` method on another bean,
and start it through `AgentRuns` instead of calling it directly. The `ChatClient` code inside the
steps does not change; the advisor is added by the starter.
