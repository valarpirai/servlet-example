# Script Globals

A module can add a global name to every script. Core does not need to know the module.

## What it does

`ScriptGlobals` in `core` holds a registry. A module registers a name, a factory and
the classes scripts may touch. Each script run calls the factory and binds the value.

`ScriptExecutor` and `ScriptScopeBuilder` apply the registry. Scripts run by the
script endpoint, API scripts, job scripts and business rules all see the globals.

## Registering one

```java
ScriptGlobals.register("Jobs", Jobs::new, Jobs.class, JobInfo.class, JobRun.class);
```

Call it once at startup. `SchedulerBootstrap.create` registers `Jobs`. A later call
with the same name replaces the first.

The listed classes become visible to the sandbox. Any other class the value hands
back stays hidden. List every class a script can reach through the global.

## Built-in globals

| Global | Module | Purpose |
|---|---|---|
| `TableRecord` | core | Read and write rows |
| `BusinessRules` | core | [Manage business rules](./managing-business-rules.md) |
| `Jobs` | scheduler | [Manage background jobs](../scheduler/managing-jobs.md) |

`TableRecord` and `BusinessRules` are set in `ScriptExecutor` and `ScriptScopeBuilder`.
New globals should use the registry.

## Rules for a global

- Build a fresh value per run. Do not share state between scripts.
- Return plain data: text, numbers, small beans. Scripts cannot read a hidden class.
- Throw `IllegalArgumentException` for bad input. The script sees the message.

## Related

- [Script sandbox](./script-sandbox.md) — what scripts may use
- [Table records and business rules](./table-records-and-rules.md) — `TableRecord`
- [Modules](../architecture/modules.md) — where the registry lives
