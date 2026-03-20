package com.example.servlet.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Security tests for ScriptProcessor JavaScript sandbox. Tests various attempts to bypass sandbox
 * restrictions.
 */
public class ScriptProcessorSecurityTest {

  private ScriptProcessor processor;
  private HttpServletRequest request;

  @BeforeEach
  public void setUp() {
    processor = new ScriptProcessor();
    request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("POST");
    when(request.getPathInfo()).thenReturn("/api/script");
  }

  private void mockRequest(String script) throws IOException {
    String json = String.format("{\"script\":\"%s\",\"params\":{}}", escapeJson(script));
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(json)));
  }

  private String escapeJson(String str) {
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  // Test 1: Blocked classes should not be accessible

  @Test
  public void testSystemClassBlocked() throws Exception {
    String script = "java.lang.System.exit(0)";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject System class access");
  }

  @Test
  public void testRuntimeClassBlocked() throws Exception {
    String script = "java.lang.Runtime.getRuntime().exec('ls')";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject Runtime class access");
  }

  @Test
  public void testProcessBuilderBlocked() throws Exception {
    String script = "new java.lang.ProcessBuilder(['ls']).start()";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject ProcessBuilder access");
  }

  @Test
  public void testThreadClassBlocked() throws Exception {
    String script = "new java.lang.Thread(function(){}).start()";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject Thread class access");
  }

  @Test
  public void testClassLoaderBlocked() throws Exception {
    String script = "java.lang.ClassLoader.getSystemClassLoader()";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject ClassLoader access");
  }

  // Test 2: File system access should be blocked

  @Test
  public void testFileClassBlocked() throws Exception {
    String script = "new java.io.File('/etc/passwd')";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject File class access");
  }

  @Test
  public void testFileReaderBlocked() throws Exception {
    String script = "new java.io.FileReader('/etc/passwd')";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject FileReader access");
  }

  @Test
  public void testFilesClassBlocked() throws Exception {
    String script = "java.nio.file.Files.readAllBytes(java.nio.file.Paths.get('/etc/passwd'))";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject Files class access");
  }

  // Test 3: Network access should be blocked

  @Test
  public void testSocketClassBlocked() throws Exception {
    String script = "new java.net.Socket('evil.com', 1337)";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject Socket class access");
  }

  @Test
  public void testURLClassBlocked() throws Exception {
    String script = "new java.net.URL('http://evil.com').openConnection()";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject URL class access");
  }

  // Test 4: Reflection should be blocked

  @Test
  public void testReflectionBlocked() throws Exception {
    // Try to use reflection to access a method
    String script =
        "var clazz = java.lang.String.class; clazz.getMethod('length', []).invoke('test', []);";
    mockRequest(script);

    var response = processor.process(request);

    // Should fail because reflect package is blocked
    assertEquals(400, response.getStatusCode());
    assertTrue(response.getBody().contains("error"), "Should reject reflection usage");
  }

  @Test
  public void testMethodHandlesBlocked() throws Exception {
    String script = "java.lang.invoke.MethodHandles.lookup()";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject MethodHandles access");
  }

  // Test 5: Database access should be blocked

  @Test
  public void testJDBCBlocked() throws Exception {
    String script = "java.sql.DriverManager.getConnection('jdbc:...')";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject JDBC access");
  }

  // Test 6: JNDI should be blocked (potential RCE)

  @Test
  public void testJNDIBlocked() throws Exception {
    String script = "javax.naming.InitialContext()";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject JNDI access");
  }

  // Test 7: Script engine access should be blocked

  @Test
  public void testScriptEngineBlocked() throws Exception {
    String script = "new javax.script.ScriptEngineManager().getEngineByName('JavaScript')";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject ScriptEngine access");
  }

  // Test 8: Allowed classes should work

  @Test
  public void testArrayListAllowed() throws Exception {
    String script = "var list = new java.util.ArrayList(); list.add('test'); list.get(0);";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(
        response.getBody().contains("test") || response.getBody().contains("success"),
        "ArrayList should be allowed");
  }

  @Test
  public void testHashMapAllowed() throws Exception {
    String script = "var map = new java.util.HashMap(); map.put('key', 'value'); map.get('key');";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(
        response.getBody().contains("value") || response.getBody().contains("success"),
        "HashMap should be allowed");
  }

  @Test
  public void testDateAllowed() throws Exception {
    String script = "new java.util.Date().getTime();";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(
        response.getBody().contains("result") && !response.getBody().contains("error"),
        "Date should be allowed");
  }

  @Test
  public void testMathAllowed() throws Exception {
    String script = "java.lang.Math.sqrt(16);";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("4"), "Math should be allowed");
  }

  // Test 9: Timeout enforcement

  @Test
  public void testTimeoutEnforcement() throws Exception {
    // Infinite loop should timeout or hit memory limit
    String script = "while(true) { var x = 1; }";
    mockRequest(script);

    var response = processor.process(request);

    // Should either timeout (408) or hit memory limit (413)
    assertTrue(
        response.getStatusCode() == 408 || response.getStatusCode() == 413,
        "Should timeout or hit memory limit on infinite loop");
    assertTrue(
        response.getBody().contains("timeout") || response.getBody().contains("memory"),
        "Error message should mention timeout or memory");
  }

  // Test 10: Memory limit enforcement

  @Test
  public void testMemoryLimitEnforcement() throws Exception {
    // Try to allocate large amounts of memory
    String script =
        "var arr = []; for(var i = 0; i < 10000000; i++) { arr.push(new"
            + " java.util.ArrayList()); }";
    mockRequest(script);

    var response = processor.process(request);

    // Should either timeout or hit memory limit
    assertTrue(
        response.getStatusCode() == 408 || response.getStatusCode() == 413,
        "Should timeout or hit memory limit");
  }

  // Test 11: Console.log should be captured

  @Test
  public void testConsoleLogCapture() throws Exception {
    String script = "console.log('test message'); 42;";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("test message"), "Console output should be captured");
    assertTrue(response.getBody().contains("42"), "Result should be returned");
  }

  // Test 12: Attempt to access internal Sun classes

  @Test
  public void testSunClassesBlocked() throws Exception {
    String script = "sun.misc.Unsafe";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject sun.* classes");
  }

  // Test 13: Attempt to access JDK internal classes

  @Test
  public void testJDKInternalClassesBlocked() throws Exception {
    String script = "jdk.internal.misc.Unsafe";
    mockRequest(script);

    var response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject jdk.* classes");
  }

  // Test 14: Security Manager should be blocked

  @Test
  public void testSecurityManagerBlocked() throws Exception {
    // Try to instantiate SecurityManager
    String script = "new java.lang.SecurityManager()";
    mockRequest(script);

    var response = processor.process(request);

    // Should fail because SecurityManager is in blacklist
    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject SecurityManager instantiation");
  }

  // Test 15: JMX should be blocked

  @Test
  public void testJMXBlocked() throws Exception {
    // Try to use JMX MBeanServerFactory
    String script = "javax.management.MBeanServerFactory.createMBeanServer()";
    mockRequest(script);

    var response = processor.process(request);

    // Should fail because javax.management is blocked
    assertEquals(400, response.getStatusCode());
    assertTrue(
        response.getBody().contains("error") || response.getBody().contains("not defined"),
        "Should reject JMX access");
  }
}
