# CLAUDE.md

Guidance for Claude Code (and other AI agents) working in this repository.

## What this project is

**commerce-otel** — Business-level distributed tracing and error-taxonomy analytics for SAP Commerce topologies (monolith + BTP microservices + lambdas + eventing).

You can't watch one cart flow storefront → OCC → decorator lambda → cart-service rules → pricing → export as a single business narrative. APM shows infra spans, not checkout scenarios. Domain error taxonomies live in code with no analytics.

**Solution:** An OpenTelemetry-native layer that stitches OCC/OData/NATS/CPI hops into **business transactions**, plus an **error-code catalog & analytics** ('VAL_0046 buying-restriction fires 4k×/day, 80% on channel 02').

> Status: early scaffold. The core abstraction, a starter implementation and tests are real; most capabilities are documented intent, not yet built. Do not claim features exist that aren't in the code.

## Stack

Java 21 + Gradle (`java-library` plugin), JUnit 5.

## Project layout

- `src/main/java/**` — production code (core abstraction: `BusinessSpanFactory`).
- `src/test/java/**` — JUnit 5 tests.
- `build.gradle`, `settings.gradle` — build config.
- `docs/` — GitHub Pages site (`index.html`, `.nojekyll`). Served at https://alextsvetkov.github.io/commerce-otel/.
- `.github/workflows/ci.yml` — CI (build + test on push/PR).

## Common commands

```bash
gradle build      # compile
gradle test       # run tests
```

## Conventions

- Prefer **constructor injection**; interface + `Default*` impl per service.
- No inline literals — use constants classes for log/config/exception strings.
- Keep the core abstraction (`BusinessSpanFactory`) honest so implementations stay swappable.
- **Conventional commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`).
- Generated code (if any) stays out of version control.
- Keep `README.md`, `docs/index.html` and this file in sync when the scope changes.

## Working agreements for agents

- This is part of a **suite of SAP Commerce backend tools**; keep terminology consistent with the sibling repos (e.g. `commerce-mcp`, `flow-context`).
- When adding real behaviour, update the Roadmap in `README.md` and add tests in the same PR.
- Don't introduce a live-backend dependency into the default build — keep the scaffold green on a clean checkout.
- If you change the public contract, reflect it in the docs site and the README capability table.
