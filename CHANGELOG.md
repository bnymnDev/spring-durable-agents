# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [SemVer](https://semver.org/).

## [Unreleased]

## [0.1.1] — 2026-09-05

### Added
- `durable-agents-bom`: bill of materials pinning all modules.
- Release workflow: GitHub release with jars and checksums, GitHub Packages, Maven Central when
  credentials are configured; dispatchable with a version and creates the tag itself.

### Fixed
- Store contract tests compare JSON columns without whitespace (`jsonb` normalises it).

## [0.1.0] — 2026-09-05

First release.

### Added
- `durable-agents-core`: `Agent`, `Steps` (`run`, `llm`, `sideEffect`, `retry`, `timeout`, `version`,
  `compensate`, `approval`), `@DurableAgent`, `@Step`, `@Retry`, deterministic hierarchical step keys,
  replay, strict-replay guard, leases with heartbeat and reaper, compensation, cancellation, events,
  in-memory store, store/codec/listener SPI, `StepPlacementValidator`.
- `durable-agents-store-jdbc`: PostgreSQL and H2 stores on `JdbcClient`, bundled Flyway migrations
  with a dedicated history table, retention.
- `durable-agents-spring-ai`: `DurableStepAdvisor` recording every `ChatClient` call as a replayable
  LLM child step with model, tokens and prompt hash; `DurableToolCallback` wrapping tool and MCP calls.
- `durable-agents-approval`: REST endpoints with role enforcement, Modulith `@Externalized`
  `ApprovalRequested` event.
- `durable-agents-actuator`: `/actuator/agents` with run timelines, health indicator, Micrometer
  metrics, nested observations with GenAI attributes, MDC.
- `durable-agents-test`: `@DurableAgentTest` slice, `FakeChatModel`, `CrashSimulator`,
  `DurableAgentTester`, `AgentRunAssert`, `@DurableAgentIntegrationTest`.
- `durable-agents-starter`: autoconfiguration and `durable-agents.*` properties.
- Examples `ticket-triage` and `order-refund`.

[Unreleased]: https://github.com/bnymnDev/spring-durable-agents/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/bnymnDev/spring-durable-agents/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/bnymnDev/spring-durable-agents/releases/tag/v0.1.0
