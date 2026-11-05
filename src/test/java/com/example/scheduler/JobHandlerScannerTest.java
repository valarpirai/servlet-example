package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobHandlerScannerTest {

  private static HandlerRegistry registry() {
    return new HandlerRegistry(n -> null);
  }

  @Test
  void registersAnAnnotatedHandlerUnderItsName() throws Exception {
    HandlerRegistry registry = registry();
    Map<String, Class<?>> found =
        JobHandlerScanner.scanAndRegister(registry, "com.example.scantest.ok");
    assertEquals(1, found.size());
    JobHandler handler = registry.find(Job.JAVA, "scan-ok");
    assertNotNull(handler);
    assertTrue(handler.execute(new JsonObject()).get("ran").getAsBoolean());
  }

  @Test
  void findsTheSampleHandlerInTheRealPackage() {
    HandlerRegistry registry = registry();
    Map<String, Class<?>> found =
        JobHandlerScanner.scanAndRegister(registry, "com.example.scheduler");
    assertEquals(HelloWorldJobHandler.class, found.get("hello-world"));
    assertNotNull(registry.find(Job.JAVA, "hello-world"));
  }

  @Test
  void anUnannotatedNameIsNotRegistered() {
    HandlerRegistry registry = registry();
    JobHandlerScanner.scanAndRegister(registry, "com.example.scantest.ok");
    assertNull(registry.find(Job.JAVA, "other"));
  }

  @Test
  void twoClassesWithOneNameFailTheScan() {
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> JobHandlerScanner.scanAndRegister(registry(), "com.example.scantest.dup"));
    assertTrue(e.getMessage().contains("'same'"));
    assertTrue(e.getMessage().contains("FirstHandler"));
    assertTrue(e.getMessage().contains("SecondHandler"));
  }

  @Test
  void aClassThatIsNotAJobHandlerFailsTheScan() {
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () ->
                JobHandlerScanner.scanAndRegister(registry(), "com.example.scantest.notahandler"));
    assertTrue(e.getMessage().contains("must implement JobHandler"));
  }

  @Test
  void aHandlerWithoutANoArgumentConstructorFailsTheScan() {
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> JobHandlerScanner.scanAndRegister(registry(), "com.example.scantest.noctor"));
    assertTrue(e.getMessage().contains("no-argument constructor"));
  }
}
