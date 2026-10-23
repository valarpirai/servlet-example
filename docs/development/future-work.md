# Future Work

Tasks the project has set aside on purpose, and gaps it knows about. Pick from here.
Remove an entry when its work lands.

## Security

Left for later by decision. Treat the app as a trusted-network tool until these land.

| Task | Why it matters |
|---|---|
| Authentication on every endpoint | `/api/script`, the table browser writes, `/api/data-browser/connect` and `/metrics` are open |
| Access control per table | Mark tables read-only for some users. The browser already knows `writable` |
| Reject cross-site writes | A page on any origin can send a no-preflight `POST` with `text/plain`. Require `application/json` and check `Origin` |
| Tighter script sandbox | `ScriptSecurityManager` allows any `java.util` and `java.lang` class not on the blocklist. Scripts can start executors. Switch to an allow-list |
| Protect `/metrics` | It shows thread, memory and pool details |

## Data and consistency

| Task | Why it matters |
|---|---|
| One transaction per write | `TableRecord` writes the row and runs `after` rules on separate connections. A failing `after` rule leaves the row saved |
| Cross-node change events | Routes poll the table every 5 seconds. Modules, scripts and rules have no such signal, so each node sees changes on its next read |
| Per-key property expiry | `PropertiesUtil` clears the whole cache every 5 seconds, so every property reads the database again at once |
| Type-aware `addQuery` | `addQuery('id', '5')` fails on a bigint column. The table browser converts values itself; scripts cannot |

## Table browser

| Task | Why it matters |
|---|---|
| Cache table metadata | Each write runs several `information_schema` queries |
| Edit `json`, `uuid` and `bytea` columns | These show but stay locked |
| More list actions | The Actions menu has Delete only |

## Scripting

| Task | Why it matters |
|---|---|
| `import` from `api_scripts` rows | `import` reads the `modules` table only |

## Tests

| Task | Why it matters |
|---|---|
| Fix three `RouteDispatcherTest` errors | `testDeleteHandlerRoute`, `testHandlerRouteDownload` and `testHandlerRouteWithPathParams` fail with "Handler invocation failed". Routes work at runtime |

## Related

- [Definition of done](./definition-of-done.md) — the commit gate
- [Table editing](../data-browser/table-editing.md) — what the browser writes today
- [Sandbox policy](../scripting/sandbox-policy.md) — the current script limits
- [Table routes](../routing/table-routes.md) — route polling
