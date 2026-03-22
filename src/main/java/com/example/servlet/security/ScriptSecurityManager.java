package com.example.servlet.security;

import com.example.servlet.util.PropertiesUtil;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/**
 * Shared security manager for JavaScript execution using Mozilla Rhino. Provides ClassShutter
 * implementation and execution limits (timeout, memory).
 */
public class ScriptSecurityManager {

  private static final long DEFAULT_SCRIPT_TIMEOUT = 5000L; // 5 seconds
  private static final int DEFAULT_OPTIMIZATION_LEVEL = -1; // Interpreted mode
  private static final long DEFAULT_MAX_MEMORY_BYTES = 10485760L; // 10 MB
  private static final int DEFAULT_INSTRUCTION_THRESHOLD = 10000;

  /**
   * ClassShutter implementation using whitelist and blacklist approach - Whitelist: Explicitly safe
   * classes that are always allowed - Blacklist: Dangerous classes/packages that are always blocked
   * - Default: Classes not in whitelist or blacklist are evaluated by package pattern
   */
  public static class SandboxClassShutter implements ClassShutter {
    // Whitelist: Explicitly safe classes that are always allowed
    private static final java.util.Set<String> ALLOWED_CLASSES =
        java.util.Set.of(
            // Common collections
            "java.util.ArrayList",
            "java.util.HashMap",
            "java.util.HashSet",
            "java.util.LinkedList",
            "java.util.TreeMap",
            "java.util.TreeSet",
            "java.util.LinkedHashMap",
            "java.util.LinkedHashSet",
            "java.util.Vector",
            "java.util.Stack",

            // Common utilities
            "java.util.Date",
            "java.util.UUID",
            "java.util.Optional",
            "java.util.Arrays",
            "java.util.Collections",

            // String and primitives
            "java.lang.String",
            "java.lang.StringBuilder",
            "java.lang.StringBuffer",
            "java.lang.Math",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.lang.Character",
            "java.lang.Byte",
            "java.lang.Short",

            // Date/Time (Java 8+)
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.Instant",
            "java.time.Duration",
            "java.time.Period",
            "java.time.ZonedDateTime",
            "java.time.ZoneId",

            // Math
            "java.math.BigDecimal",
            "java.math.BigInteger");

    // Blacklist: Dangerous classes and package prefixes
    private static final java.util.Set<String> BLOCKED_CLASSES =
        java.util.Set.of(
            "java.lang.System",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Process",
            "java.lang.ClassLoader",
            "java.lang.Thread",
            "java.lang.ThreadGroup",
            "java.lang.SecurityManager");

    private static final java.util.Set<String> BLOCKED_PACKAGE_PREFIXES =
        java.util.Set.of(
            "java.io.", // File system access
            "java.nio.file.", // File system access
            "java.net.", // Network access
            "java.lang.reflect.", // Reflection
            "java.lang.invoke.", // Method handles
            "javax.script.", // Script engine access
            "sun.", // Internal Sun classes
            "com.sun.", // Internal Sun classes
            "jdk.", // Internal JDK classes
            "java.security.", // Security manager manipulation
            "javax.naming.", // JNDI (potential RCE)
            "javax.management.", // JMX (management access)
            "java.sql.", // Database access
            "javax.sql." // Database access
            );

    @Override
    public boolean visibleToScripts(String className) {
      // 1. Check whitelist first (explicitly allowed)
      if (ALLOWED_CLASSES.contains(className)) {
        return true;
      }

      // 2. Check blacklist (explicitly blocked classes)
      if (BLOCKED_CLASSES.contains(className)) {
        return false;
      }

      // 3. Check blacklisted package prefixes
      for (String prefix : BLOCKED_PACKAGE_PREFIXES) {
        if (className.startsWith(prefix)) {
          return false;
        }
      }

      // 4. Allow other java.util and java.lang classes by default
      if (className.startsWith("java.util.") || className.startsWith("java.lang.")) {
        return true;
      }

      // 5. Block everything else by default
      return false;
    }
  }

  /** Custom ContextFactory with security, timeout, and memory limits. */
  public static class SecureContextFactory extends ContextFactory {
    private final long scriptTimeout;
    private final int optimizationLevel;
    private final long maxMemoryBytes;
    private final int instructionThreshold;
    private final long startTime;
    private final long memoryBefore;

    public SecureContextFactory(
        long scriptTimeout,
        int optimizationLevel,
        long maxMemoryBytes,
        int instructionThreshold,
        long startTime,
        long memoryBefore) {
      this.scriptTimeout = scriptTimeout;
      this.optimizationLevel = optimizationLevel;
      this.maxMemoryBytes = maxMemoryBytes;
      this.instructionThreshold = instructionThreshold;
      this.startTime = startTime;
      this.memoryBefore = memoryBefore;
    }

    @Override
    protected void observeInstructionCount(Context cx, int instructionCount) {
      long currentTime = System.currentTimeMillis();
      long elapsed = currentTime - startTime;

      // Check timeout
      if (elapsed > scriptTimeout) {
        throw new Error(
            "Script execution timeout exceeded: " + elapsed + "ms > " + scriptTimeout + "ms");
      }

      // Check memory usage
      Runtime runtime = Runtime.getRuntime();
      long memoryNow = runtime.totalMemory() - runtime.freeMemory();
      long memoryUsed = memoryNow - memoryBefore;

      if (memoryUsed > maxMemoryBytes) {
        throw new Error(
            "Script memory limit exceeded: "
                + memoryUsed
                + " bytes > "
                + maxMemoryBytes
                + " bytes");
      }
    }

    @Override
    protected Context makeContext() {
      Context cx = super.makeContext();
      cx.setOptimizationLevel(optimizationLevel);
      cx.setInstructionObserverThreshold(instructionThreshold);
      // Apply ClassShutter to block Java class access for security
      cx.setClassShutter(new SandboxClassShutter());
      return cx;
    }
  }

  /** Get script timeout from configuration with default fallback. */
  public static long getScriptTimeout() {
    return PropertiesUtil.getLong("script.timeout", DEFAULT_SCRIPT_TIMEOUT);
  }

  /** Get optimization level from configuration with default fallback. */
  public static int getOptimizationLevel() {
    return PropertiesUtil.getInt("script.optimizationLevel", DEFAULT_OPTIMIZATION_LEVEL);
  }

  /** Get max memory bytes from configuration with default fallback. */
  public static long getMaxMemoryBytes() {
    return PropertiesUtil.getLong("script.maxMemory", DEFAULT_MAX_MEMORY_BYTES);
  }

  /** Get instruction threshold from configuration with default fallback. */
  public static int getInstructionThreshold() {
    return PropertiesUtil.getInt("script.instructionThreshold", DEFAULT_INSTRUCTION_THRESHOLD);
  }

  /** Create a new ClassShutter instance. */
  public static ClassShutter createClassShutter() {
    return new SandboxClassShutter();
  }

  /** Create a secure ContextFactory with timeout and memory limits. */
  public static SecureContextFactory createSecureContextFactory(long startTime, long memoryBefore) {
    return new SecureContextFactory(
        getScriptTimeout(),
        getOptimizationLevel(),
        getMaxMemoryBytes(),
        getInstructionThreshold(),
        startTime,
        memoryBefore);
  }
}
