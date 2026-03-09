# Unit Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add JUnit 5 unit tests covering the DataSource strategy layer and core utility classes.

**Architecture:** One test class per production class, zero mocking (all targets are pure Java). Tests live under `src/test/java` mirroring the main source tree. JUnit 5 (Jupiter) added as a `test`-scoped dependency; `maven-surefire-plugin` configured to discover Jupiter tests.

**Tech Stack:** Java 17, JUnit 5.10.2 (junit-jupiter), Maven Surefire 3.2.5, Google Java Format via Spotless.

---

## Files Overview

| Action | File |
|---|---|
| Modify | `pom.xml` |
| Create | `src/test/java/com/example/datasource/DataSourceRegistryTest.java` |
| Create | `src/test/java/com/example/datasource/PostgreSqlStrategyTest.java` |
| Create | `src/test/java/com/example/datasource/MySqlStrategyTest.java` |
| Create | `src/test/java/com/example/datasource/SnowflakeStrategyTest.java` |
| Create | `src/test/java/com/example/servlet/util/TemplateEngineTest.java` |
| Create | `src/test/java/com/example/servlet/util/JsonUtilTest.java` |

---

## Task 1: Add JUnit 5 dependency and Surefire plugin to pom.xml

**Files:**
- Modify: `pom.xml`

### Step 1: Add JUnit 5 dependency inside `<dependencies>`

Add after the last existing `<dependency>` block (before `</dependencies>`):

```xml
        <!-- JUnit 5 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
```

### Step 2: Add Surefire plugin inside `<plugins>`

Add after the `maven-compiler-plugin` block:

```xml
            <!-- Maven Surefire Plugin for running JUnit 5 tests -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
```

### Step 3: Verify compile + test scaffolding resolves

```bash
mvn test-compile -q
```
Expected: BUILD SUCCESS (no test classes yet, that's fine)

### Step 4: Commit

```bash
git add pom.xml
git commit -m "build: add JUnit 5 and Surefire plugin for unit tests"
```

---

## Task 2: DataSourceRegistryTest

**Files:**
- Create: `src/test/java/com/example/datasource/DataSourceRegistryTest.java`

### Step 1: Create the test file

```java
package com.example.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import org.junit.jupiter.api.Test;

class DataSourceRegistryTest {

  @Test
  void getInstance_returnsSameInstance() {
    assertSame(DataSourceRegistry.getInstance(), DataSourceRegistry.getInstance());
  }

  @Test
  void get_knownType_returnsStrategy() {
    assertNotNull(DataSourceRegistry.getInstance().get("postgresql"));
    assertNotNull(DataSourceRegistry.getInstance().get("mysql"));
    assertNotNull(DataSourceRegistry.getInstance().get("snowflake"));
  }

  @Test
  void get_caseInsensitive() {
    DataSourceStrategy s = DataSourceRegistry.getInstance().get("POSTGRESQL");
    assertNotNull(s);
    assertEquals("postgresql", s.getDbType());
  }

  @Test
  void get_unknownType_returnsNull() {
    assertNull(DataSourceRegistry.getInstance().get("oracle"));
  }

  @Test
  void get_null_returnsNull() {
    assertNull(DataSourceRegistry.getInstance().get(null));
  }

  @Test
  void isSupported_knownType_returnsTrue() {
    assertTrue(DataSourceRegistry.getInstance().isSupported("mysql"));
  }

  @Test
  void isSupported_unknownType_returnsFalse() {
    assertFalse(DataSourceRegistry.getInstance().isSupported("oracle"));
  }

  @Test
  void all_returnsThreeStrategies() {
    Collection<DataSourceStrategy> all = DataSourceRegistry.getInstance().all();
    assertEquals(3, all.size());
  }
}
```

### Step 2: Run to verify tests pass

```bash
mvn test -pl . -Dtest=DataSourceRegistryTest -q
```
Expected: BUILD SUCCESS, 8 tests passed

### Step 3: Commit

```bash
git add src/test/java/com/example/datasource/DataSourceRegistryTest.java
git commit -m "test: add DataSourceRegistryTest"
```

---

## Task 3: PostgreSqlStrategyTest and MySqlStrategyTest

**Files:**
- Create: `src/test/java/com/example/datasource/PostgreSqlStrategyTest.java`
- Create: `src/test/java/com/example/datasource/MySqlStrategyTest.java`

### Step 1: Create PostgreSqlStrategyTest

```java
package com.example.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostgreSqlStrategyTest {

  private final PostgreSqlStrategy strategy = new PostgreSqlStrategy();

  @Test
  void getDbType_returnsPostgresql() {
    assertEquals("postgresql", strategy.getDbType());
  }

  @Test
  void buildUrl_returnsUrlFromProps() {
    String url = strategy.buildUrl(Map.of("url", "jdbc:postgresql://localhost:5432/mydb"));
    assertEquals("jdbc:postgresql://localhost:5432/mydb", url);
  }

  @Test
  void buildUrl_missingUrl_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of()));
  }

  @Test
  void buildUrl_blankUrl_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of("url", "  ")));
  }

  @Test
  void buildConnectionProperties_containsUserAndPassword() {
    Properties p = strategy.buildConnectionProperties("alice", "secret", Map.of());
    assertEquals("alice", p.getProperty("user"));
    assertEquals("secret", p.getProperty("password"));
  }

  @Test
  void buildConnectionProperties_noExtraProperties() {
    Properties p = strategy.buildConnectionProperties("alice", "secret", Map.of("url", "jdbc:..."));
    assertEquals(2, p.size());
  }

  @Test
  void getSystemSchemas_containsExpectedSchemas() {
    Set<String> schemas = strategy.getSystemSchemas();
    assertTrue(schemas.contains("information_schema"));
    assertTrue(schemas.contains("pg_catalog"));
    assertTrue(schemas.contains("pg_toast"));
  }
}
```

### Step 2: Create MySqlStrategyTest

```java
package com.example.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MySqlStrategyTest {

  private final MySqlStrategy strategy = new MySqlStrategy();

  @Test
  void getDbType_returnsMysql() {
    assertEquals("mysql", strategy.getDbType());
  }

  @Test
  void buildUrl_returnsUrlFromProps() {
    String url = strategy.buildUrl(Map.of("url", "jdbc:mysql://localhost:3306/mydb"));
    assertEquals("jdbc:mysql://localhost:3306/mydb", url);
  }

  @Test
  void buildUrl_missingUrl_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of()));
  }

  @Test
  void buildUrl_blankUrl_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of("url", "")));
  }

  @Test
  void buildConnectionProperties_containsUserAndPassword() {
    Properties p = strategy.buildConnectionProperties("bob", "pass", Map.of());
    assertEquals("bob", p.getProperty("user"));
    assertEquals("pass", p.getProperty("password"));
  }

  @Test
  void buildConnectionProperties_noExtraProperties() {
    Properties p = strategy.buildConnectionProperties("bob", "pass", Map.of("url", "jdbc:..."));
    assertEquals(2, p.size());
  }

  @Test
  void getSystemSchemas_containsExpectedSchemas() {
    Set<String> schemas = strategy.getSystemSchemas();
    assertTrue(schemas.contains("information_schema"));
    assertTrue(schemas.contains("performance_schema"));
    assertTrue(schemas.contains("sys"));
    assertTrue(schemas.contains("mysql"));
  }
}
```

### Step 3: Run both tests

```bash
mvn test -Dtest="PostgreSqlStrategyTest,MySqlStrategyTest" -q
```
Expected: BUILD SUCCESS, 14 tests passed

### Step 4: Commit

```bash
git add src/test/java/com/example/datasource/PostgreSqlStrategyTest.java \
        src/test/java/com/example/datasource/MySqlStrategyTest.java
git commit -m "test: add PostgreSqlStrategyTest and MySqlStrategyTest"
```

---

## Task 4: SnowflakeStrategyTest

**Files:**
- Create: `src/test/java/com/example/datasource/SnowflakeStrategyTest.java`

### Step 1: Create the test file

```java
package com.example.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class SnowflakeStrategyTest {

  private final SnowflakeStrategy strategy = new SnowflakeStrategy();

  @Test
  void getDbType_returnsSnowflake() {
    assertEquals("snowflake", strategy.getDbType());
  }

  @Test
  void buildUrl_assemblesFromAccount() {
    String url = strategy.buildUrl(Map.of("account", "myorg-myaccount"));
    assertEquals("jdbc:snowflake://myorg-myaccount.snowflakecomputing.com/", url);
  }

  @Test
  void buildUrl_missingAccount_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of()));
  }

  @Test
  void buildUrl_blankAccount_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of("account", "  ")));
  }

  @Test
  void buildConnectionProperties_containsUserAndPassword() {
    Properties p = strategy.buildConnectionProperties("alice", "secret", Map.of("account", "a"));
    assertEquals("alice", p.getProperty("user"));
    assertEquals("secret", p.getProperty("password"));
  }

  @Test
  void buildConnectionProperties_allOptionalFields_included() {
    Map<String, String> props = new HashMap<>();
    props.put("account", "myorg");
    props.put("warehouse", "COMPUTE_WH");
    props.put("database", "MYDB");
    props.put("schema", "PUBLIC");
    props.put("role", "SYSADMIN");

    Properties p = strategy.buildConnectionProperties("u", "p", props);

    assertEquals("COMPUTE_WH", p.getProperty("warehouse"));
    assertEquals("MYDB", p.getProperty("db"));
    assertEquals("PUBLIC", p.getProperty("schema"));
    assertEquals("SYSADMIN", p.getProperty("role"));
  }

  @Test
  void buildConnectionProperties_blankOptionalFields_skipped() {
    Map<String, String> props = new HashMap<>();
    props.put("account", "myorg");
    props.put("warehouse", "  ");
    props.put("database", "");

    Properties p = strategy.buildConnectionProperties("u", "p", props);

    assertNull(p.getProperty("warehouse"));
    assertNull(p.getProperty("db"));
  }

  @Test
  void buildConnectionProperties_absentOptionalFields_skipped() {
    Properties p = strategy.buildConnectionProperties("u", "p", Map.of("account", "myorg"));
    // Only user + password
    assertEquals(2, p.size());
  }

  @Test
  void getSystemSchemas_containsInformationSchema() {
    assertTrue(strategy.getSystemSchemas().contains("information_schema"));
    assertEquals(1, strategy.getSystemSchemas().size());
  }
}
```

### Step 2: Run test

```bash
mvn test -Dtest=SnowflakeStrategyTest -q
```
Expected: BUILD SUCCESS, 9 tests passed

### Step 3: Commit

```bash
git add src/test/java/com/example/datasource/SnowflakeStrategyTest.java
git commit -m "test: add SnowflakeStrategyTest"
```

---

## Task 5: TemplateEngineTest

**Files:**
- Create: `src/test/java/com/example/servlet/util/TemplateEngineTest.java`

### Step 1: Create the test file

```java
package com.example.servlet.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateEngineTest {

  @Test
  void render_nullTemplate_returnsEmpty() {
    assertEquals("", TemplateEngine.render(null, Map.of()));
  }

  @Test
  void render_emptyTemplate_returnsEmpty() {
    assertEquals("", TemplateEngine.render("", Map.of()));
  }

  @Test
  void render_noVariables_returnsTemplateUnchanged() {
    assertEquals("<p>hello</p>", TemplateEngine.render("<p>hello</p>", Map.of()));
  }

  @Test
  void render_simpleVariable_substituted() {
    String result = TemplateEngine.render("Hello {{name}}!", Map.of("name", "World"));
    assertEquals("Hello World!", result);
  }

  @Test
  void render_missingVariable_replacedWithEmpty() {
    String result = TemplateEngine.render("Hello {{name}}!", Map.of());
    assertEquals("Hello !", result);
  }

  @Test
  void render_xssCharacters_escaped() {
    String result = TemplateEngine.render("{{v}}", Map.of("v", "<script>alert('xss')</script>"));
    assertEquals("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;", result);
  }

  @Test
  void render_ampersandEscaped() {
    String result = TemplateEngine.render("{{v}}", Map.of("v", "A & B"));
    assertEquals("A &amp; B", result);
  }

  @Test
  void render_dotNotation_resolvesNestedValue() {
    Map<String, Object> data = Map.of("user", Map.of("name", "Alice"));
    String result = TemplateEngine.render("{{user.name}}", data);
    assertEquals("Alice", result);
  }

  @Test
  void render_dotNotation_missingKey_returnsEmpty() {
    Map<String, Object> data = Map.of("user", Map.of("name", "Alice"));
    String result = TemplateEngine.render("{{user.email}}", data);
    assertEquals("", result);
  }

  @Test
  void render_forLoop_overList_expandsItems() {
    Map<String, Object> data = Map.of("items", List.of("a", "b", "c"));
    String result = TemplateEngine.render("{{#for item in items}}-{{item}}{{/for}}", data);
    assertEquals("-a-b-c", result);
  }

  @Test
  void render_forLoop_emptyList_producesNoOutput() {
    Map<String, Object> data = Map.of("items", List.of());
    String result = TemplateEngine.render("{{#for item in items}}-{{item}}{{/for}}", data);
    assertEquals("", result);
  }

  @Test
  void render_forLoop_overArray_expandsItems() {
    Map<String, Object> data = Map.of("items", new Object[]{"x", "y"});
    String result = TemplateEngine.render("{{#for item in items}}[{{item}}]{{/for}}", data);
    assertEquals("[x][y]", result);
  }
}
```

### Step 2: Run test

```bash
mvn test -Dtest=TemplateEngineTest -q
```
Expected: BUILD SUCCESS, 12 tests passed

### Step 3: Commit

```bash
git add src/test/java/com/example/servlet/util/TemplateEngineTest.java
git commit -m "test: add TemplateEngineTest"
```

---

## Task 6: JsonUtilTest

**Files:**
- Create: `src/test/java/com/example/servlet/util/JsonUtilTest.java`

### Step 1: Create the test file

```java
package com.example.servlet.util;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonUtilTest {

  @Test
  void isValidJson_validObject_returnsTrue() {
    assertTrue(JsonUtil.isValidJson("{\"key\":\"value\"}"));
  }

  @Test
  void isValidJson_validArray_returnsTrue() {
    assertTrue(JsonUtil.isValidJson("[1,2,3]"));
  }

  @Test
  void isValidJson_invalidJson_returnsFalse() {
    assertFalse(JsonUtil.isValidJson("{not json}"));
  }

  @Test
  void isValidJson_emptyString_returnsTrue() {
    // Gson treats empty string as a null JSON element — valid parse
    assertTrue(JsonUtil.isValidJson(""));
  }

  @Test
  void successResponse_hasStatusSuccess() {
    String json = JsonUtil.successResponse(Map.of("key", "val"));
    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
  }

  @Test
  void successResponse_hasDataAndTimestamp() {
    String json = JsonUtil.successResponse("hello");
    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
    assertTrue(obj.has("data"));
    assertTrue(obj.has("timestamp"));
    assertTrue(obj.get("timestamp").getAsLong() > 0);
  }

  @Test
  void errorResponse_hasAllFields() {
    String json = JsonUtil.errorResponse("Not Found", "Resource missing", 404);
    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("Not Found", obj.get("error").getAsString());
    assertEquals("Resource missing", obj.get("message").getAsString());
    assertEquals(404, obj.get("status").getAsInt());
    assertTrue(obj.has("timestamp"));
  }

  @Test
  void toJson_fromJson_roundtrip() {
    Map<String, String> original = Map.of("hello", "world");
    String json = JsonUtil.toJson(original);
    assertTrue(JsonUtil.isValidJson(json));
    assertTrue(json.contains("hello"));
    assertTrue(json.contains("world"));
  }
}
```

### Step 2: Run test

```bash
mvn test -Dtest=JsonUtilTest -q
```
Expected: BUILD SUCCESS, 8 tests passed

### Step 3: Run full test suite

```bash
mvn test -q
```
Expected: BUILD SUCCESS, all tests passed

### Step 4: Commit

```bash
git add src/test/java/com/example/servlet/util/JsonUtilTest.java
git commit -m "test: add JsonUtilTest"
```

---

## Task 7: Spotless check on test sources

Spotless formats test files the same way as production code.

### Step 1: Apply formatting

```bash
mvn spotless:apply -q
```

### Step 2: Verify clean

```bash
mvn spotless:check -q && mvn test -q && echo "ALL CLEAN"
```
Expected: `ALL CLEAN`

### Step 3: Commit any formatting fixes

```bash
git add -u
git status
# Only commit if there are actual changes
git commit -m "style: apply spotless formatting to test sources"
```
If `git status` shows nothing to commit, skip the commit step.
