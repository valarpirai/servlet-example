# Sandbox Policy

The exact allow and block lists, as `ScriptSecurityManager.SandboxClassShutter`
holds them.

## Allowed

Collections and utilities:

```
java.util.ArrayList        java.util.HashMap        java.util.HashSet
java.util.LinkedList       java.util.TreeMap        java.util.TreeSet
java.util.LinkedHashMap    java.util.LinkedHashSet  java.util.Vector
java.util.Stack            java.util.Date           java.util.UUID
java.util.Optional         java.util.Arrays         java.util.Collections
```

Strings, primitives and math:

```
java.lang.String    java.lang.StringBuilder  java.lang.StringBuffer
java.lang.Math      java.lang.Integer        java.lang.Long
java.lang.Double    java.lang.Float          java.lang.Boolean
java.lang.Character java.lang.Byte           java.lang.Short
java.math.BigDecimal  java.math.BigInteger
```

Date and time. These are listed individually, not by prefix:

```
java.time.LocalDate   java.time.LocalDateTime  java.time.LocalTime
java.time.Instant     java.time.Duration       java.time.Period
java.time.ZonedDateTime  java.time.ZoneId
```

A `java.time` class not on that list is denied, because `java.time` is not
covered by the default-allow rule.

## Blocked classes

```
java.lang.System        java.lang.Runtime      java.lang.ProcessBuilder
java.lang.Process       java.lang.ClassLoader  java.lang.Thread
java.lang.ThreadGroup   java.lang.SecurityManager
```

These sit inside `java.lang`, which the default rule would otherwise allow. The
block list exists to carve them back out.

## Blocked package prefixes

| Prefix | Closes off |
|---|---|
| `java.io.` | File system |
| `java.nio.file.` | File system |
| `java.net.` | Network |
| `java.lang.reflect.` | Reflection |
| `java.lang.invoke.` | Method handles |
| `javax.script.` | Nested script engines |
| `java.security.` | Security manager manipulation |
| `javax.naming.` | JNDI, a remote code execution path |
| `javax.management.` | JMX |
| `java.sql.` | Database |
| `javax.sql.` | Database |
| `sun.` | JDK internals |
| `com.sun.` | JDK internals |
| `jdk.` | JDK internals |

## Limitations

**Naming a class is not using it.** The shutter gates instantiation and method
calls. Evaluating `java.lang.System` as an expression returns a class object and
succeeds. `java.lang.System.exit(0)` is denied.

**Memory accounting is approximate.** The limit measures JVM heap growth across
the execution, which includes Rhino's own overhead. Set the limit above what a
script should need, not at it.

**Timeouts are granular.** The clock is checked every 10,000 instructions. A
tight loop is caught. Something that blocks inside one call is not.

**No disk quota.** Scripts cannot reach the file system, but they can still
allocate. The memory limit is the only bound.

**Rhino predates Java modules.** Keep the Rhino version current; the pin lives
in `shared-build/catalog/thirdparty/pom.xml`.

## Changing the policy

Edit the sets in `ScriptSecurityManager.SandboxClassShutter` and add a test to
`ScriptProcessorSecurityTest` covering the new class in both directions.

Widening the list is a security decision. Prefer exposing a helper on the script
scope over allowing a Java class.

## Related

- [Script sandbox](./script-sandbox.md) — the controls and rule order
- [Script execution](./script-execution.md) — how scope is built
- [Script issues](../troubleshooting/script-issues.md) — diagnosing a denial
