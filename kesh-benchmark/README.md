# Kesh benchmark

Local routing, retrieval, tool, and security suites grow here.

Do not run these as a gate on every chat. Run them after classifier, policy, or model changes.

```bash
mvn -q -Dtest=RoutingBenchmarkTest test
```

The latest routing report is written to:

- `target/kesh-benchmark/routing-report.md`
- `kesh-benchmark/latest-routing-report.md`

Planned suites:

- `routing/` intent and hard-negative checks
- `rag/` hybrid retrieval
- `tools/` tool selection
- `security/` terminal, SSRF, sandbox, desk isolation
- `java-execution/` compile, timeout, network-off
- `conversation/` follow-up and clarification

A candidate model must not be promoted if terminal false positives rise.
