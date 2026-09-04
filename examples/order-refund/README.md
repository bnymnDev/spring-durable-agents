# order-refund

Three repositories in one flow: the agent reads a Shopware order through
[shopware-mcp](https://github.com/bnymnDev/shopware-mcp), every tool call passes through
[agentgate](https://github.com/bnymnDev/agentgate) (policy, audit log, kill switch), and
spring-durable-agents makes the whole thing crash-safe with a human in the loop.

```
POST /refunds ──▶ assess (LLM + MCP tools) ──▶ reserve (@Step, compensated) ──▶ approveRefund (FINANCE, > 100)
                                                                             ──▶ transitionOrder (MCP tool as @Step)
                                                                             ──▶ notify (Modulith event)
```

## Run it

```sh
docker compose up -d
export OPENAI_API_KEY=sk-...
export SHOPWARE_URL=https://shop.example.com SHOPWARE_CLIENT_ID=SWIA... SHOPWARE_CLIENT_SECRET=...
go install github.com/bnymnDev/agentgate/cmd/agentgate@latest     # or brew install --cask agentgate
../../mvnw -pl examples/order-refund spring-boot:run
```

`agentgate.yaml` in this directory is the policy: the model may read anything, may move an order to
`refund`, and may not touch prices or stock. `agentgate tail` shows the calls as the agent makes them.

## Walk through

```sh
curl -s -X POST localhost:8080/refunds -H 'content-type: application/json' \
     -d '{"orderNumber":"10042","reason":"parcel arrived damaged, photos attached"}'
# {"runId":"01J..."}

curl -s localhost:8080/refunds/01J...                      # SUSPENDED when the amount is above 100
curl -s -u finance:finance localhost:8080/agents/approvals   # what finance sees
curl -s -u finance:finance -X POST localhost:8080/agents/approvals/<id>/approve
curl -s localhost:8080/actuator/agents/runs/01J...           # LLM step, TOOL steps, approval, transition, notify
```

## What it demonstrates

- **Tool calls as steps.** `orders_get` and friends appear as `kind = TOOL` rows under the `assess` step, keyed by tool name and argument hash. A resume after a crash inside the LLM turn does not repeat them.
- **A tool called by code, not by the model.** `RefundSteps.transitionOrder` picks the MCP tool and calls it inside a `@Step` with a 30 second timeout.
- **Compensation.** `reserve` declares `compensate = "releaseReservation"`. Reject the approval and watch `compensate:refundSteps.reserve` appear in the timeline and the reservation disappear.
- **Idempotency key from `StepContext.sideEffect`.** The reservation key is generated once per order and survives retries and resumes.
- **Notifications without touching the engine.** `RefundNotifier` listens to `ApprovalRequested` and `RefundProcessed`, both Spring Modulith events. Add `spring-modulith-events-kafka` and they leave the process.
- **Tests without a shop.** `RefundAgentTests` scripts the model, fakes the MCP tool provider and simulates the crash.
