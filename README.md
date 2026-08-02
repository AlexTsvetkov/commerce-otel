# commerce-otel

**Business-level distributed tracing and error-taxonomy analytics for SAP Commerce topologies (monolith + BTP microservices + lambdas + eventing).**

> ⚠️ **Status:** early scaffold. The core abstraction, a starter implementation and tests are real; this is a foundation to build on, not a finished product. See [Roadmap](#roadmap).

**Stack:** Java 21 + Gradle.

---

## The problem

You can't watch one cart flow storefront → OCC → decorator lambda → cart-service rules → pricing → export as a single business narrative. APM shows infra spans, not checkout scenarios. Domain error taxonomies live in code with no analytics.

## The solution

An OpenTelemetry-native layer that stitches OCC/OData/NATS/CPI hops into **business transactions**, plus an **error-code catalog & analytics** ('VAL_0046 buying-restriction fires 4k×/day, 80% on channel 02').

See the [project site](https://alextsvetkov.github.io/commerce-otel/) for the full benefits narrative.

## Design principles

1. **Business spans, not just infra** — Spans carry domain semantics — scenario, channel, runway, transaction type — so a trace reads as a checkout story.
2. **Cross-boundary stitching** — Correlates sync REST and async NATS hops into one transaction via propagated trace context.
3. **Error economics** — Turns a de-facto error-code contract into a queryable catalog with live frequency analytics.
4. **Standards-based** — Pure OpenTelemetry — export to any backend you already run.

## Core abstraction

`BusinessSpanFactory` — Creates spans enriched with SAP Commerce domain attributes (scenario, channel, runway) and links async NATS hops back to the originating business transaction.

## Features

| Capability | Description |
|------------|-------------|
| `Semantic conventions` | A SAP-Commerce span/attribute vocabulary. |
| `Trace stitching` | Join NATS-async hops to their originating transaction. |
| `Error catalog` | Registry + analytics over domain error codes. |
| `Exporters` | OTLP-compatible; plug into your collector. |

## Quick start

```bash
gradle build
gradle test
```

## Roadmap

- [ ] Flesh out the core beyond the starter implementation.
- [ ] Wire against a live SAP Commerce / BTP environment.
- [ ] Publish artifacts and usage docs.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md). Conventional commits; generated code stays out of version control.

## License

[MIT](./LICENSE) © 2026 Aliaksandr Tsviatkou

---

*Part of a backend tooling suite for SAP Commerce Cloud. See [`commerce-mcp`](https://github.com/AlexTsvetkov/commerce-mcp) for the AI-native flagship.*
