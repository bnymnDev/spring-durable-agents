# Security

## Reporting

Report vulnerabilities privately through GitHub's security advisories on this repository
(*Security → Report a vulnerability*). Please do not open a public issue for something exploitable.
Expect an acknowledgement within a few days.

## What is in scope

- The engine and stores: a way to make a completed step run again, to replay a result into the wrong
  run, or to take a lease another instance holds.
- The approval endpoints: deciding an approval without the required role, or reading approvals of a
  role one does not hold.
- The Spring AI advisor: stored prompts or tool arguments leaking where the configuration says they
  should not (`durable-agents.observability.record-prompts=false` stores hashes only).

## What the library does and does not do

- Step inputs and outputs are stored as JSON in your database. What the agent sees, the database
  sees; protect it as you protect the rest of the application data.
- The default security filter chain for the approval endpoints is a starting point (HTTP Basic,
  authenticated). It steps aside the moment the application defines its own chain.
- Tool calls made by a model are executed by Spring AI; this library records them, it does not
  decide whether they are safe. Put [agentgate](https://github.com/bnymnDev/agentgate) or an
  equivalent policy layer in front of MCP servers you do not trust unconditionally.
