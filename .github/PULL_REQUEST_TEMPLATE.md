## What

<!-- What changes and why. Link the issue or discussion if there is one. -->

## Checklist

- [ ] `./mvnw verify` passes locally (PostgreSQL tests run when Docker is present)
- [ ] Follows the ground rules in [CONTRIBUTING.md](https://github.com/bnymnDev/spring-durable-agents/blob/main/CONTRIBUTING.md): durability through `Steps`, deterministic code between steps, small core
- [ ] New property, annotation or endpoint is documented in `docs/` in the same change
- [ ] Schema changes are new Flyway migrations
- [ ] An architectural choice with alternatives has an ADR in `DECISIONS.md`
- [ ] `CHANGELOG.md` has an entry under `[Unreleased]` for user-visible changes
