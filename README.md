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

## Usage

Everything below is distilled from the runnable, heavily-commented tutorial at
`src/main/java/com/sapcommercetools/otel/examples/Example.java`. It injects a
deterministic clock/id source so the ids and durations are stable — the
`Output:` blocks are the **real stdout** captured from running it.

### 1. Start a trace with business-attributed child spans

`BusinessSpanFactory` starts a root span and children, and stamps SAP-Commerce
domain attributes (scenario / channel / runway) so a trace reads as a checkout
story rather than a pile of infra spans.

```java
AtomicLong ids = new AtomicLong(1);
AtomicLong fakeNanos = new AtomicLong(0);
BusinessSpanFactory factory = new BusinessSpanFactory(
        ids::getAndIncrement, () -> fakeNanos.getAndAdd(1_000_000L));

Span root = factory.startTrace("checkout-renewal");
factory.withDomain(root, "renewal", "01", "CR"); // scenario, channel, runway

System.out.println("Created trace id: " + root.traceId());
System.out.println("Domain attrs on root: " + root.attributes());
```

```text
Output:
Created trace id: 0000000000000001
Root span id:     0000000000000002  (parent=null)
Domain attrs on root: {commerce.scenario=renewal, commerce.channel=01, commerce.runway=CR}
```

### 2. Assemble and print the span tree

A `TraceRecorder` collects spans that arrive in any order and reassembles them
parent-before-child, and can answer simple analytics questions about a trace.

```java
TraceRecorder recorder = new TraceRecorder();
recorder.record(root);
recorder.record(factory.startChild(root, "validate-cart"));   // + grandchild cpq-pricing-call
recorder.record(factory.startChild(root, "persist-order"));

List<Span> ordered = recorder.assemble(root.traceId());
long channel01 = recorder.countByAttribute(root.traceId(), "commerce.channel", "01");
```

```text
Output:
- checkout-renewal   [span=0000000000000002] 7.0 ms {commerce.scenario=renewal, commerce.channel=01, commerce.runway=CR}
  - validate-cart      [span=0000000000000003] 4.0 ms {commerce.scenario=renewal, commerce.channel=01, commerce.runway=CR}
    - cpq-pricing-call   [span=0000000000000004] 2.0 ms {commerce.scenario=renewal, commerce.channel=01, commerce.runway=CR}
  - persist-order      [span=0000000000000005] 3.0 ms {commerce.scenario=renewal, commerce.channel=01, commerce.runway=CR}
Spans on channel 01: 4 of 4
```

### 3. Turn the VAL_* taxonomy into analytics with ErrorCatalog

`ErrorCatalog` registers `VAL_*` codes with descriptions, records occurrences as
they stream in, and ranks them with `topCodes(n)`. Unregistered codes are still
counted (reported as `(unregistered)`) so nothing is lost.

```java
ErrorCatalog catalog = new ErrorCatalog()
        .register("VAL_0046", "Quote total below the channel minimum")
        .register("VAL_0102", "Renewal end-date precedes start-date")
        .register("VAL_0210", "Configurable product missing required characteristic");
// ... record() occurrences: VAL_0046 x5, VAL_0102 x2, VAL_0210 x8, VAL_9999 x1
for (ErrorCatalog.Entry e : catalog.topCodes(3)) {
    System.out.printf("  %-9s x%-3d  %s%n", e.code(), e.count(), e.description());
}
```

```text
Output:
Top 3 error codes by frequency:
  VAL_0210  x8    Configurable product missing required characteristic
  VAL_0046  x5    Quote total below the channel minimum
  VAL_0102  x2    Renewal end-date precedes start-date
Direct count for VAL_0046: 5
Description for unregistered VAL_9999: (unregistered)
```

Gradle is not required — compile and run with the JDK (Java 21):

```bash
find src/main/java -name '*.java' | xargs javac -d /tmp/ex-otel
java -cp /tmp/ex-otel com.sapcommercetools.otel.examples.Example
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
