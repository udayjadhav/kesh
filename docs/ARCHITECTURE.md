# Kesh architecture

This document describes how a single user turn moves through the system.

## Layers

1. **UI / API** — Thymeleaf chat + REST endpoints  
2. **Orchestrator** — creates conversation context, records interactions  
3. **Understanding** — current message first; history only via reference resolution  
4. **Routing** — intent → knowledge requirement → capability plan  
5. **Policy** — which tools may run; skips when integrations are off  
6. **Agent loop** — LLM turns + tool execution with compatibility checks  
7. **Grounding** — live claims need tool evidence  
8. **Observability** — audits, metrics, optional learning JSONL  

## Invariants

1. Current user request has priority over historical context.  
2. Historical context cannot automatically change the current intent.  
3. A tool cannot execute unless compatible with the routing plan.  
4. A failed incompatible tool cannot be blindly retried.  
5. Malformed textual tool calls cannot execute.  
6. Internal tool errors do not appear in normal user responses (unless debug).  
7. A previous assistant claim is not a verified external fact.  
8. Current/external factual claims must be grounded when verification is required.  
9. An ambiguous request must not silently inherit the previous tool.  
10. The LLM is not the authorization layer.

## Key packages

| Package | Role |
| --- | --- |
| `com.kesh.nlp` | Classification, knowledge, follow-ups |
| `com.kesh.routing` | Capability plans, tool compatibility |
| `com.kesh.agent` | Loop, conversations, orchestration |
| `com.kesh.tools` | Typed tool implementations |
| `com.kesh.security` | Permissions, path allowlist, approvals |
| `com.kesh.eval` | Answer judge, grounding |
| `com.kesh.writing` | Writing / format pipeline |

## Data on disk

Runtime data defaults to `~/.kesh/` (conversations, learning events).  
This directory is local and should not be committed.
