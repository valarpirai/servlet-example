# Script Issues

## Script execution timeout

**Symptom.** `{"status":"error","message":"Script execution timeout"}`

**Cause.** The script ran past `script.timeout`, 5000 ms by default.

Raise the property, or fix the script. An infinite loop is the usual reason.

```bash
# Reproduce
curl -X POST http://localhost:8080/api/script \
  -H "Content-Type: application/javascript" \
  -d '{"script":"while(true){}"}'
```

`script.timeout` is a seeded property in `app_properties`, not a value in
`application.yml`.

## Access to class X is not allowed

**Symptom.** `{"status":"error","message":"Access to class X is not allowed"}`

**Cause.** The `ClassShutter` denied the class.

Allowed: collections, strings, primitives, `java.math`, and specific
`java.time` classes. Denied: `System`, `Runtime`, `Thread`, file system,
network, reflection, JDBC, JDK internals.

The full lists are in [sandbox policy](../scripting/sandbox-policy.md), and the
code of record is `ScriptSecurityManager.SandboxClassShutter`.

Prefer exposing a helper on the script scope over widening the policy. See
[script execution](../scripting/script-execution.md).

## Naming a class works, calling it fails

Evaluating `java.lang.System` returns a class object and succeeds. Calling
`java.lang.System.exit(0)` is denied.

The shutter gates instantiation and method calls, not name resolution. This is
expected, not a hole to fix.

## Memory limit hit unexpectedly

**Cause.** The limit measures JVM heap growth over the whole execution,
including Rhino's own overhead, so reported usage exceeds what the script
allocated.

Set `script.maxMemory` above what a script should need rather than at it. The
seeded value is 50 MB.

## A fast script is not timed out

Checks happen every `script.instructionThreshold` instructions, 10,000 by
default. Something that blocks inside a single call is not interrupted.

## Circular dependency detected

**Symptom.** `Circular dependency detected: a.js -> b.js -> c.js -> a.js`

**Cause.** Module imports form a cycle. Resolution happens before execution, so
nothing ran.

Break the cycle. See [script modules](../scripting/script-modules.md).

## Related

- [Script sandbox](../scripting/script-sandbox.md) — the controls
- [Sandbox policy](../scripting/sandbox-policy.md) — allow and block lists
- [Configuration](../architecture/configuration.md) — script properties
