# commerce-otel

**Business-level distributed tracing and error-taxonomy analytics for SAP Commerce topologies (monolith + BTP microservices + lambdas + eventing).**

**🌐 Live site: https://alextsvetkov.github.io/commerce-otel/**

> ✅ **Status:** working core. A real, tested implementation of the core capability runs offline (no live SAP Commerce instance needed); unit tests pass in CI. Not yet a production product — see [Roadmap](#roadmap) for what would make it one.

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

- [x] Implement the core capability with real logic + unit tests.
- [ ] Broaden coverage (more rules/edge cases) beyond the first working version.
- [ ] Wire against a live SAP Commerce / BTP environment.
- [ ] Publish artifacts and usage docs.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md). Conventional commits; generated code stays out of version control.

## License

[MIT](./LICENSE) © 2026 Aliaksandr Tsviatkou

## Honest assessment

> From the v2 self-critical analysis. Scores use **Gap · Value · Moat · Time-to-revenue · Risk** (for Risk, **higher = safer**). Prior art is named deliberately — "no competitor" is almost never true.

**Scores:** Gap 3 · Value 4 · Moat 2 · TTR 2 · Risk 3

- **Prior art / competition.** SAP Cloud ships Dynatrace; OpenTelemetry auto-instrumentation is free and mature. This adds semantic conventions + error analytics on top — a thin, copyable layer.
- **True differentiator.** SAP-Commerce span semantics + turning the VAL_* taxonomy into live analytics. Needs a UI to be valuable.
- **Kill criterion.** If Dynatrace dashboards + a few custom spans get teams 80% there for free, willingness to pay collapses.
- **Verdict.** **Feature, later** — revisit as a commerce-mcp add-on rather than a separate product.

See the full landscape, go-to-market and the **IP / conflict-of-interest** discussion in [sap-commerce-general-ideas-for-startup.md](https://github.com/AlexTsvetkov/sap-commerce-ideas-for-projects/blob/main/ideas-for-startup/sap-commerce-general-ideas-for-startup.md).

---

*Part of a backend tooling suite for SAP Commerce Cloud. See [`commerce-mcp`](https://github.com/AlexTsvetkov/commerce-mcp) for the AI-native flagship.*
