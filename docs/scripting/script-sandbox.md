# Script Sandbox

Server-side JavaScript runs on Mozilla Rhino inside a sandbox.

`ScriptSecurityManager` owns every control. It builds the `ClassShutter` that
decides which Java classes a script can touch, and the context factory that
enforces time and memory limits.

## Four controls

**Class access.** `SandboxClassShutter.visibleToScripts` is consulted for every
Java class a script names. See
[sandbox policy](./sandbox-policy.md) for the lists.

**Timeout.** `script.timeout`, 5000 ms by default. An instruction observer
checks the clock every `script.instructionThreshold` instructions, 10,000 by
default.

**Memory.** `script.maxMemory` caps heap growth during one execution. The seeded
value is 50 MB. The in-code fallback, used when no database is reachable, is
10 MB.

**Optimization level.** `script.optimizationLevel` is `-1`, interpreted mode.
Slower than compiled, but it keeps the instruction observer working, which is
what makes the timeout enforceable.

## How a class is judged

The shutter applies five rules in order, and the first match wins.

1. On the allow list, permit
2. On the block list, deny
3. Matches a blocked package prefix, deny
4. Starts with `java.util.` or `java.lang.`, permit
5. Anything else, deny

Rule 4 is broader than it looks. Any `java.util` or `java.lang` class not
explicitly blocked is reachable. Rule 5 means everything outside those two
packages needs an explicit allow-list entry.

## Where the limits are read

`ScriptSecurityManager` exposes `getScriptTimeout`, `getMaxMemoryBytes`,
`getOptimizationLevel` and `getInstructionThreshold`. Both `ScriptProcessor` and
`ScriptExecutor` read them, so the two execution paths share one policy.

## Verifying

```bash
mvn test -Dtest=ScriptProcessorSecurityTest
```

These tests are the contract. Treat a failure as a security regression, not a
flaky test.

## Related

- [Sandbox policy](./sandbox-policy.md) — the allow and block lists
- [Script execution](./script-execution.md) — the execution path
- [Script issues](../troubleshooting/script-issues.md) — timeouts and blocked classes
- [Configuration](../architecture/configuration.md) — the script properties
