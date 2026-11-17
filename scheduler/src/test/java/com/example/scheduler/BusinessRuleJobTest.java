package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import com.example.model.Job;
import com.example.record.AsyncRuleCall;
import com.example.repository.FakeJobRepository;
import com.example.util.JsonUtil;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BusinessRuleJobTest {

  private static AsyncRuleCall call() {
    AsyncRuleCall c = new AsyncRuleCall();
    c.ruleId = "7";
    c.ruleName = "Notify";
    c.table = "app_properties";
    c.operation = "update";
    c.current = Map.of("name", "scheduler.workers", "value", "6");
    c.previous = Map.of("name", "scheduler.workers", "value", "4");
    c.depth = 1;
    return c;
  }

  @Test
  void enqueueSavesAOnceJobWithTheWriteAndRetries() {
    FakeJobRepository repo = new FakeJobRepository();
    BusinessRuleJob.enqueue(new Jobs(repo, Clock.systemUTC()), call());

    Job job = repo.rows.values().iterator().next();
    assertEquals("rule: Notify", job.name);
    assertEquals("business-rule", job.handler);
    assertEquals(Job.JAVA, job.handlerType);
    assertEquals("once", job.triggerType);
    assertEquals(3, job.maxRetries);
    assertEquals(30, job.retryDelaySeconds);
    assertEquals("7", job.context.get("ruleId").getAsString());
  }

  @Test
  void theContextReadsBackAsTheSameCall() {
    JsonObject context = JsonUtil.fromJson(JsonUtil.toJson(call()), JsonObject.class);
    AsyncRuleCall back = BusinessRuleJob.parse(context);
    assertEquals("Notify", back.ruleName);
    assertEquals("update", back.operation);
    assertEquals("6", back.current.get("value"));
    assertEquals("4", back.previous.get("value"));
    assertEquals(1, back.depth);
  }

  @Test
  void theHandlerIsRegisteredUnderItsName() {
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    JobHandlerScanner.scanAndRegister(handlers, "com.example.scheduler");
    assertNotNull(handlers.find(Job.JAVA, "business-rule"));
  }
}
