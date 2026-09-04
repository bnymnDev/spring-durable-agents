# ticket-triage

The reference application: one LLM step, one `@Step` bean, one approval, one side effect. PostgreSQL
through Docker Compose, OpenAI as the model, Actuator on.

```
POST /tickets ──▶ classify (LLM) ──▶ findSimilar (@Step, retry 3) ──▶ propose (approval: SUPPORT_LEAD) ──▶ apply
```

## Run it

```sh
docker compose up -d                       # PostgreSQL on 5432
export OPENAI_API_KEY=sk-...
../../mvnw -pl examples/ticket-triage spring-boot:run
```

## Walk through a crash

**1. Open a ticket.** The response carries the run id; the run executes on a virtual thread.

```sh
curl -s -X POST localhost:8080/tickets -H 'content-type: application/json' -d '{
  "id": "T-2001", "customer": "acme", "subject": "Invoice shows wrong VAT",
  "body": "The August invoice lists 19% VAT but we are a reverse-charge customer."
}'
# {"ticketId":"T-2001","runId":"01J..."}
```

**2. Kill the application** while the run is in flight (or right after; the run suspends at the
approval anyway). `Ctrl-C` is enough. The run stays `RUNNING` in the database with a lease that expires
after `durable-agents.lease-duration` (30 seconds).

**3. Start it again.** Within `reaper-interval` (10 seconds) the log says:

```
Resumed run 01J... of agent 'ticket-triage' after its lease expired
```

`classify` and `findSimilar` are replayed from `agent_step`; the model is not called a second time.
The run reaches the approval and suspends:

```sh
curl -s localhost:8080/runs/01J...
# {"id":"01J...","agentName":"ticket-triage","status":"SUSPENDED",...}
```

**4. Approve as the support lead.**

```sh
curl -s -u lead:lead localhost:8080/agents/approvals
# [{"id":"01J...","runId":"01J...","stepName":"propose","requiredRole":"SUPPORT_LEAD","description":"..."}]
curl -s -u lead:lead -X POST localhost:8080/agents/approvals/<approvalId>/approve \
     -H 'content-type: application/json' -d '{"comment":"looks right"}'
```

**5. Done.** The resolution is applied exactly once.

```sh
curl -s localhost:8080/tickets/T-2001
curl -s localhost:8080/actuator/agents/runs/01J...      # the full step timeline
curl -s localhost:8080/actuator/agents                   # counts per agent and status
```

## What to look at

- `TicketTriageAgent`: the four steps and the determinism rule in action.
- `TriageSteps`: `@Step` on a separate bean; `findSimilar` returns `List<SimilarTicket>` and replays with the right element type.
- `agent_step` in PostgreSQL: one row per step, `kind = LLM` rows carry `model`, `tokens_in`, `tokens_out`.
- `TicketTriageAgentTests`: the same flow with `@DurableAgentTest`, a scripted model and a simulated crash. No Docker, no API key.
