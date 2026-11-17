# Local PostgreSQL with PgBouncer

This is what the local `docker-compose.yml` does. It starts PostgreSQL and a PgBouncer
pooler in front of it.

## Start

```bash
docker compose up -d
```

| Service | Host port | Purpose |
|---|---|---|
| `postgres` | 5432 | PostgreSQL 17, database `appdb`, user `postgres` |
| `pgbouncer` | 6432 | Connection pooler for `appdb` |

## Point the app at the pooler

The default `db.url` targets port 5432, the database itself. Override it to use the pooler:

```bash
DB_URL=jdbc:postgresql://localhost:6432/appdb mvn -PappRun
```

Credentials stay `postgres` / `postgres`. See [Configuration](../architecture/configuration.md).

## Pooler settings

`pgbouncer/pgbouncer.ini` sets the pool. Credentials sit in `pgbouncer/userlist.txt`.

- `pool_mode = transaction`: a server connection serves one transaction, then returns.
- `auth_type = scram-sha-256`: matches the PostgreSQL 17 default.
- `max_prepared_statements = 100`: lets prepared statements work in transaction mode.
- `default_pool_size = 20`: server connections per database and user.
- `max_client_conn = 200`: client connections PgBouncer accepts.

The credentials are for local use only.

## Check it

```bash
PGPASSWORD=postgres psql -h localhost -p 6432 -U postgres appdb -c 'select 1'
```

The app starts and Flyway applies all migrations through port 6432.

## Related

- [Configuration](../architecture/configuration.md) — `db.url` and env vars
- [Workflows](./workflows.md) — dev server
- [Debugging](./debugging.md) — logs
