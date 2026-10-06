# Using Kesh

## Start

```bash
./scripts/run.sh
```

Open http://localhost:8080

## Tips

- Prefer a **new chat** when switching topics completely.  
- For live facts, ask clearly (“today”, “now”, “latest”, “share price”).  
- For Java execution, say “run” / “execute” with “Java snippet/code”.  
- Approvals appear for write/dangerous tools — read the card before approving.  
- Enable integrations only via env vars from `.env.example`.  

## Desks

- **Work** — professional projects and optional work integrations  
- **Personal** — personal documents under personal path allowlist  

History never crosses desks.

## Debugging

Set `KESH_DEBUG=true` or `kesh.debug.enabled=true` to surface internal routing details in the UI. Leave this off for normal use.
