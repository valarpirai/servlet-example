package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import com.example.repository.FakeJobRepository;
import com.example.script.ScriptExecutor;
import com.example.security.ScriptGlobals;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobsTest {

  private final FakeJobRepository repo = new FakeJobRepository();
  private final Jobs jobs = new Jobs(repo, Clock.systemUTC());

  private static Map<String, Object> fields() {
    Map<String, Object> f = new HashMap<>();
    f.put("name", "nightly-cleanup");
    f.put("handlerType", "script");
    f.put("handler", "cleanup");
    f.put("triggerType", "daily");
    f.put("triggerSpec", Map.of("time", "02:30"));
    return f;
  }

  // --- validation ---

  @Test
  void validJobIsSaved() {
    String id = jobs.create(fields());
    Job row = repo.row(id);
    assertEquals("nightly-cleanup", row.name);
    assertEquals("daily", row.triggerType);
    assertEquals("02:30", row.triggerSpec.get("time").getAsString());
  }

  @Test
  void eachBadFieldIsRejectedAndNothingIsSaved() {
    for (String[] bad :
        new String[][] {
          {"name", " "},
          {"handlerType", "python"},
          {"handler", ""},
          {"triggerType", "weekly"},
        }) {
      Map<String, Object> f = fields();
      f.put(bad[0], bad[1]);
      assertThrows(IllegalArgumentException.class, () -> jobs.create(f), bad[0]);
    }
    assertTrue(repo.rows.isEmpty());
  }

  @Test
  void badTriggerSpecIsRejected() {
    Map<String, Object> f = fields();
    f.put("triggerSpec", Map.of("time", "not-a-time"));
    assertThrows(IllegalArgumentException.class, () -> jobs.create(f));
  }

  @Test
  void wholeScriptNumbersAreStoredWithoutADecimalPoint() {
    Map<String, Object> f = fields();
    f.put("triggerType", "interval");
    f.put("triggerSpec", Map.of("seconds", 3600.0));
    String id = jobs.create(f);
    assertEquals("{\"seconds\":3600}", repo.row(id).triggerSpec.toString());
  }

  @Test
  void retryBoundsAreChecked() {
    Map<String, Object> f = fields();
    f.put("retryJitter", 1.5);
    assertThrows(IllegalArgumentException.class, () -> jobs.create(f));
  }

  @Test
  void unknownFieldIsRejected() {
    Map<String, Object> f = fields();
    f.put("colour", "red");
    assertThrows(IllegalArgumentException.class, () -> jobs.create(f));
  }

  @Test
  void onDemandJobNeedsNoSpec() {
    Map<String, Object> f = fields();
    f.put("triggerType", "on_demand");
    f.remove("triggerSpec");
    assertDoesNotThrow(() -> jobs.create(f));
  }

  // --- handler existence ---

  private Jobs checked(String... existing) {
    java.util.Set<String> known = java.util.Set.of(existing);
    return new Jobs(
        repo, Clock.systemUTC(), (type, handler) -> known.contains(type + ":" + handler));
  }

  @Test
  void missingHandlerIsRejectedAndNothingIsSaved() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> checked().create(fields()));
    assertTrue(e.getMessage().contains("cleanup"));
    assertTrue(repo.rows.isEmpty());
    Map<String, Object> java = fields();
    java.put("handlerType", "java");
    assertThrows(IllegalArgumentException.class, () -> checked().create(java));
  }

  @Test
  void existingHandlerIsAccepted() {
    assertDoesNotThrow(() -> checked("script:cleanup").create(fields()));
  }

  @Test
  void validateAlsoLooksForTheHandler() {
    assertThrows(IllegalArgumentException.class, () -> checked().validate(fields()));
  }

  @Test
  void updateLooksForTheHandlerOnlyWhenItChanges() {
    String id = jobs.create(fields());
    Jobs strict = checked();
    assertDoesNotThrow(() -> strict.update(id, Map.of("priority", 3)));
    assertThrows(IllegalArgumentException.class, () -> strict.update(id, Map.of("handler", "x")));
    assertEquals("cleanup", repo.row(id).handler);
    assertTrue(checked("script:other").update(id, Map.of("handler", "other")));
  }

  // --- enqueue and runNow ---

  @Test
  void enqueueSavesAOnceJobWithItsContext() {
    String id = jobs.enqueue("report", "java", "hello", Map.of("userId", 42));
    Job row = repo.row(id);
    assertEquals("once", row.triggerType);
    assertEquals(42, row.context.get("userId").getAsInt());
  }

  @Test
  void runNowCopiesTheJobAndLeavesItsSchedule() {
    String id = jobs.create(fields());
    String copyId = jobs.runNow(id);
    assertNotEquals(id, copyId);
    assertEquals("once", repo.row(copyId).triggerType);
    assertEquals("cleanup", repo.row(copyId).handler);
    assertEquals("daily", repo.row(id).triggerType);
  }

  // --- read, update, delete ---

  @Test
  void getAndListShowTheScriptView() {
    String id = jobs.create(fields());
    JobInfo info = jobs.get(id);
    assertEquals("ready", info.getStateName());
    assertEquals("{\"time\":\"02:30\"}", info.getTriggerSpec());
    assertEquals(1, jobs.list().size());
    assertEquals(1, jobs.listByState(Job.READY).size());
    assertEquals(0, jobs.listByState(Job.ERROR).size());
    assertNull(jobs.get("missing"));
  }

  @Test
  void updateChangesFieldsAndClearsAnError() {
    String id = jobs.create(fields());
    repo.row(id).state = Job.ERROR;
    repo.row(id).lastError = "boom";
    assertTrue(jobs.update(id, Map.of("priority", 5)));
    assertEquals(5, repo.row(id).priority);
    assertEquals(Job.READY, repo.row(id).state);
    assertNull(repo.row(id).lastError);
  }

  @Test
  void updateRejectsAnInvalidResultAndAMissingJob() {
    String id = jobs.create(fields());
    assertThrows(
        IllegalArgumentException.class, () -> jobs.update(id, Map.of("triggerType", "weekly")));
    assertThrows(IllegalArgumentException.class, () -> jobs.update("missing", Map.of()));
  }

  @Test
  void aHeldJobCannotBeChangedOrDeleted() {
    String id = jobs.create(fields());
    repo.row(id).state = Job.RUNNING;
    assertFalse(jobs.update(id, Map.of("priority", 5)));
    assertFalse(jobs.delete(id));
    assertNotNull(repo.row(id));
  }

  @Test
  void deleteRemovesAnIdleJob() {
    String id = jobs.create(fields());
    assertTrue(jobs.delete(id));
    assertNull(repo.row(id));
    assertFalse(jobs.delete(id));
  }

  @Test
  void historyListsRunsOfOneJob() {
    JobRun mine = new JobRun();
    mine.jobId = "a";
    JobRun other = new JobRun();
    other.jobId = "b";
    repo.runs.add(mine);
    repo.runs.add(other);
    assertEquals(1, jobs.history("a", 10).size());
  }

  // --- scripts ---

  @Test
  void scriptsManageJobsThroughTheGlobal() {
    ScriptGlobals.register(
        "Jobs", () -> new Jobs(repo, Clock.systemUTC()), Jobs.class, JobInfo.class, JobRun.class);
    String script =
        "var id = Jobs.create({name:'j', handlerType:'java', handler:'hello',"
            + " triggerType:'interval', triggerSpec:{seconds:60}});"
            + " var info = Jobs.get(id); Jobs.update(id, {priority: 9});"
            + " info.name + ':' + info.stateName + ':' + Jobs.list().size()";
    ScriptExecutor.ScriptExecutionResult result = new ScriptExecutor().execute(script, null);
    assertTrue(result.isSuccess(), result.getMessage());
    assertEquals("j:ready:1", String.valueOf(result.getResult()));
    assertEquals(9, repo.rows.values().iterator().next().priority);
  }
}
