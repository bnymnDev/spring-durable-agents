# Human in the loop

`steps.approval(role, timeout)` suspends a run until someone with `role` decides. The module
`durable-agents-approval` adds REST endpoints, security defaults and an externalizable event.

## Endpoints

Base path `durable-agents.approval.base-path`, default `/agents/approvals`.

| Method | Path | Does |
|---|---|---|
| `GET` | `/agents/approvals` | Pending approvals the caller may decide. `?role=X` filters by role instead. |
| `GET` | `/agents/approvals/{id}` | One pending approval. |
| `POST` | `/agents/approvals/{id}/approve` | Body `{"comment": "..."}` optional. Records the decision, resumes the run, returns `202`. |
| `POST` | `/agents/approvals/{id}/reject` | Same, the run fails at the approval step. |

Responses: `404` unknown or already decided, `403` caller lacks the role, `409` decided concurrently.

A pending approval:

```json
{
  "id": "01J8X…", "runId": "01J8W…", "agentName": "ticket-triage",
  "stepKey": "01J8W…:propose:0", "stepName": "propose", "requiredRole": "SUPPORT_LEAD",
  "description": "wrong VAT on invoice (NORMAL)", "requestedAt": "…", "expiresAt": "…", "correlationId": "T-2001"
}
```

## Authorization

The controller checks `HttpServletRequest#isUserInRole(requiredRole)`, which Spring Security backs
with `ROLE_<role>` authorities. `durable-agents.approval.enforce-roles=false` turns the check off when
another layer authorizes (an API gateway, a service mesh).

When the application defines no `SecurityFilterChain`, the starter registers one for the approval
paths only: authenticated, HTTP Basic, CSRF off. It is a starting point; the moment you declare your
own chain it steps aside. `durable-agents.approval.security-auto-configured=false` turns it off
explicitly.

## Notifications

On every request the engine publishes `ApprovalRequestedEvent`; the module republishes it as
`ApprovalRequested`, a Spring Modulith `@Externalized` event with routing target
`durable-agents.approvals::<role>`. Add `spring-modulith-events-kafka` (or AMQP, SQS, JMS) and every
request lands on your broker without touching the engine. Or listen in process:

```java
@ApplicationModuleListener
void on(ApprovalRequested request) {
  slack.post("#support-leads", request.description() + " → POST " + request.decideUrl() + "/approve");
}
```

`ApprovalDecidedEvent` follows every decision, including expiry.

## Timeouts

`durable-agents.approval.expiry-interval` (default 1 minute) is how often expired requests are
detected. An expired request is marked `EXPIRED`, `ApprovalDecidedEvent` is published, and the run is
resumed; the step's `onTimeout` policy decides what happens: `FAIL` (default), `REJECT` or `APPROVE`.

`steps.approval(role)` without a timeout uses `durable-agents.approval.default-timeout` (one day).

## Programmatic access

`Approvals` is a bean: `pendingForRole(role)`, `pending(id)`, `approve(id, by, comment)`,
`reject(id, by, comment)`, `expire()`. Use it from a Slack bot, a CLI, a scheduled auto-approver for
small amounts. Authorization is the caller's job there.
