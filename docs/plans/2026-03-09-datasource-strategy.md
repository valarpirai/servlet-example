# DataSource Strategy Pattern Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extract all per-database behaviour (driver info, URL building, connection properties, system schema filtering) into a `DataSourceStrategy` interface with one implementation per supported database.

**Architecture:** A `DataSourceStrategy` interface defines the contract; `PostgreSqlStrategy`, `MySqlStrategy`, and `SnowflakeStrategy` implement it. `DataSourceRegistry` (singleton) holds all registered strategies. `ExtLibManager` and `DataBrowserHandler` delegate to the registry/strategy instead of containing hardcoded DB-specific logic.

**Tech Stack:** Java 17, existing project (Jakarta EE, Tomcat 10, Gson, SLF4J). No new dependencies.

---

## Files Overview

| Action | File |
|---|---|
| Create | `src/main/java/com/example/datasource/DataSourceStrategy.java` |
| Create | `src/main/java/com/example/datasource/DataSourceRegistry.java` |
| Create | `src/main/java/com/example/datasource/PostgreSqlStrategy.java` |
| Create | `src/main/java/com/example/datasource/MySqlStrategy.java` |
| Create | `src/main/java/com/example/datasource/SnowflakeStrategy.java` |
| Modify | `src/main/java/com/example/extlib/ExtLibManager.java` |
| Modify | `src/main/java/com/example/servlet/handler/DataBrowserHandler.java` |

---

## Task 1: DataSourceStrategy interface

**Files:**
- Create: `src/main/java/com/example/datasource/DataSourceStrategy.java`

### Step 1: Create the interface

```java
package com.example.datasource;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Encapsulates all per-database behaviour: driver location, URL construction,
 * connection properties, and which schemas are system-internal.
 *
 * <p>Register implementations in {@link DataSourceRegistry}.
 */
public interface DataSourceStrategy {

  /** Short lowercase key used in API requests: "mysql", "postgresql", "snowflake". */
  String getDbType();

  /** Maven Central relative JAR path, e.g. "org/postgresql/postgresql/42.7.3/...jar". */
  String getJarPath();

  /** Fully-qualified JDBC driver class name. */
  String getDriverClass();

  /**
   * Build the JDBC URL from the user-supplied connection properties.
   *
   * <p>For PostgreSQL/MySQL the caller passes {@code url} directly. For Snowflake the URL is
   * assembled from {@code account}.
   */
  String buildUrl(Map<String, String> props);

  /**
   * Build the {@link Properties} object passed to {@link java.sql.DriverManager#getConnection}.
   * Must always include {@code user} and {@code password}.
   */
  Properties buildConnectionProperties(String user, String password, Map<String, String> props);

  /**
   * Schema names that should be hidden from the table browser (system/internal schemas).
   * Compared case-insensitively.
   */
  Set<String> getSystemSchemas();
}
```

### Step 2: Compile

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

### Step 3: Commit

```bash
git add src/main/java/com/example/datasource/DataSourceStrategy.java
git commit -m "feat: add DataSourceStrategy interface"
```

---

## Task 2: DataSourceRegistry

**Files:**
- Create: `src/main/java/com/example/datasource/DataSourceRegistry.java`

### Step 1: Create the registry

```java
package com.example.datasource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Singleton registry of {@link DataSourceStrategy} implementations. Pre-registers
 * PostgreSQL, MySQL, and Snowflake; new strategies can be added via {@link #register}.
 */
public class DataSourceRegistry {

  private static final DataSourceRegistry INSTANCE = new DataSourceRegistry();

  private final Map<String, DataSourceStrategy> strategies = new LinkedHashMap<>();

  private DataSourceRegistry() {
    register(new PostgreSqlStrategy());
    register(new MySqlStrategy());
    register(new SnowflakeStrategy());
  }

  public static DataSourceRegistry getInstance() {
    return INSTANCE;
  }

  public void register(DataSourceStrategy strategy) {
    strategies.put(strategy.getDbType().toLowerCase(), strategy);
  }

  /** Returns the strategy for {@code dbType} (case-insensitive), or {@code null} if unknown. */
  public DataSourceStrategy get(String dbType) {
    if (dbType == null) return null;
    return strategies.get(dbType.toLowerCase());
  }

  public boolean isSupported(String dbType) {
    return get(dbType) != null;
  }

  /** All registered strategies, in registration order. */
  public Collection<DataSourceStrategy> all() {
    return strategies.values();
  }
}
```

Note: this references `PostgreSqlStrategy`, `MySqlStrategy`, `SnowflakeStrategy` — they don't exist yet, so compilation will fail until Task 3 is done. That's expected.

### Step 2: Commit (compile check deferred to Task 3)

```bash
git add src/main/java/com/example/datasource/DataSourceRegistry.java
git commit -m "feat: add DataSourceRegistry singleton"
```

---

## Task 3: Three strategy implementations

**Files:**
- Create: `src/main/java/com/example/datasource/PostgreSqlStrategy.java`
- Create: `src/main/java/com/example/datasource/MySqlStrategy.java`
- Create: `src/main/java/com/example/datasource/SnowflakeStrategy.java`

### Step 1: Create PostgreSqlStrategy

```java
package com.example.datasource;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class PostgreSqlStrategy implements DataSourceStrategy {

  @Override
  public String getDbType() {
    return "postgresql";
  }

  @Override
  public String getJarPath() {
    return "org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar";
  }

  @Override
  public String getDriverClass() {
    return "org.postgresql.Driver";
  }

  @Override
  public String buildUrl(Map<String, String> props) {
    return props.get("url");
  }

  @Override
  public Properties buildConnectionProperties(
      String user, String password, Map<String, String> props) {
    Properties p = new Properties();
    p.setProperty("user", user);
    p.setProperty("password", password);
    return p;
  }

  @Override
  public Set<String> getSystemSchemas() {
    return Set.of(
        "information_schema",
        "pg_catalog",
        "pg_toast",
        "pg_temp_1",
        "pg_toast_temp_1");
  }
}
```

### Step 2: Create MySqlStrategy

```java
package com.example.datasource;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class MySqlStrategy implements DataSourceStrategy {

  @Override
  public String getDbType() {
    return "mysql";
  }

  @Override
  public String getJarPath() {
    return "com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar";
  }

  @Override
  public String getDriverClass() {
    return "com.mysql.cj.jdbc.Driver";
  }

  @Override
  public String buildUrl(Map<String, String> props) {
    return props.get("url");
  }

  @Override
  public Properties buildConnectionProperties(
      String user, String password, Map<String, String> props) {
    Properties p = new Properties();
    p.setProperty("user", user);
    p.setProperty("password", password);
    return p;
  }

  @Override
  public Set<String> getSystemSchemas() {
    return Set.of("information_schema", "performance_schema", "sys", "mysql");
  }
}
```

### Step 3: Create SnowflakeStrategy

```java
package com.example.datasource;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class SnowflakeStrategy implements DataSourceStrategy {

  @Override
  public String getDbType() {
    return "snowflake";
  }

  @Override
  public String getJarPath() {
    return "net/snowflake/snowflake-jdbc/3.15.0/snowflake-jdbc-3.15.0.jar";
  }

  @Override
  public String getDriverClass() {
    return "net.snowflake.client.jdbc.SnowflakeDriver";
  }

  /** Assembles the Snowflake JDBC URL from the {@code account} property. */
  @Override
  public String buildUrl(Map<String, String> props) {
    return "jdbc:snowflake://" + props.get("account") + ".snowflakecomputing.com/";
  }

  /** Adds optional Snowflake-specific properties: warehouse, db, schema, role. */
  @Override
  public Properties buildConnectionProperties(
      String user, String password, Map<String, String> props) {
    Properties p = new Properties();
    p.setProperty("user", user);
    p.setProperty("password", password);
    addIfPresent(p, props, "warehouse");
    addIfPresent(p, props, "database", "db");
    addIfPresent(p, props, "schema");
    addIfPresent(p, props, "role");
    return p;
  }

  private void addIfPresent(Properties p, Map<String, String> props, String key) {
    addIfPresent(p, props, key, key);
  }

  private void addIfPresent(
      Properties p, Map<String, String> props, String inputKey, String jdbcKey) {
    String val = props.get(inputKey);
    if (val != null && !val.isBlank()) p.setProperty(jdbcKey, val);
  }

  @Override
  public Set<String> getSystemSchemas() {
    return Set.of("information_schema");
  }
}
```

### Step 4: Compile all three + registry

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

### Step 5: Commit

```bash
git add src/main/java/com/example/datasource/
git commit -m "feat: add PostgreSqlStrategy, MySqlStrategy, SnowflakeStrategy implementations"
```

---

## Task 4: Refactor ExtLibManager to use DataSourceRegistry

**Files:**
- Modify: `src/main/java/com/example/extlib/ExtLibManager.java`

**What changes:**
- Remove the inner `DriverInfo` record
- Remove the hardcoded `CATALOGUE` map
- Replace all `CATALOGUE.get(key)` references with `DataSourceRegistry.getInstance().get(key)`
- `isSupported` delegates to `DataSourceRegistry.getInstance().isSupported(dbType)`
- `isDownloaded` gets the jar filename from `strategy.getJarPath()`
- `downloadAndLoad` gets jar path and driver class from the strategy
- `connect` gets jar path and driver class from the strategy

### Step 1: Read ExtLibManager.java in full, then apply changes

The updated `ExtLibManager.java` should look like this (full replacement):

```java
package com.example.extlib;

import com.example.datasource.DataSourceRegistry;
import com.example.datasource.DataSourceStrategy;
import java.io.*;
import java.net.URI;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtLibManager {

  private static final Logger logger = LoggerFactory.getLogger(ExtLibManager.class);
  private static final ExtLibManager INSTANCE = new ExtLibManager();
  private static final String EXTLIB_DIR = "extlib";
  private static final String MAVEN_BASE = "https://repo1.maven.org/maven2/";

  // Tracks which driver classes have already been registered with DriverManager
  private final Set<String> loadedDrivers = ConcurrentHashMap.newKeySet();

  private ExtLibManager() {
    new File(EXTLIB_DIR).mkdirs();
  }

  public static ExtLibManager getInstance() {
    return INSTANCE;
  }

  public boolean isSupported(String dbType) {
    return DataSourceRegistry.getInstance().isSupported(dbType);
  }

  public boolean isDownloaded(String dbType) {
    DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType);
    if (strategy == null) return false;
    return jarFile(strategy).exists();
  }

  /**
   * Downloads the JDBC driver JAR (if not already present), loads it into a URLClassLoader, and
   * registers it with DriverManager. Safe to call multiple times.
   */
  public synchronized void downloadAndLoad(String dbType) throws Exception {
    DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType);
    if (strategy == null) throw new IllegalArgumentException("Unknown dbType: " + dbType);

    File jar = jarFile(strategy);

    if (!jar.exists()) {
      String downloadUrl = MAVEN_BASE + strategy.getJarPath();
      logger.info("Downloading {} from {}", jar.getName(), downloadUrl);
      downloadFile(downloadUrl, jar);
      logger.info("Downloaded {} ({} bytes)", jar.getName(), jar.length());
    } else {
      logger.info("JAR already present: {}", jar.getAbsolutePath());
    }

    // Both the contains-check and loadDriver call execute under this synchronized block,
    // keeping the check-then-act atomic. Do not move either outside the lock.
    if (!loadedDrivers.contains(strategy.getDriverClass())) {
      loadDriver(jar, strategy.getDriverClass());
    }
  }

  /** Opens a JDBC connection. Caller must close it (managed by SessionManager). */
  public Connection connect(
      String dbType, String url, String user, String password, Map<String, String> extraProps)
      throws Exception {
    downloadAndLoad(dbType);
    DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType);
    Properties props = strategy.buildConnectionProperties(user, password, extraProps);
    return DriverManager.getConnection(url, props);
  }

  private File jarFile(DataSourceStrategy strategy) {
    String jarPath = strategy.getJarPath();
    String fileName = jarPath.substring(jarPath.lastIndexOf('/') + 1);
    return new File(EXTLIB_DIR, fileName);
  }

  private void downloadFile(String urlStr, File dest) throws IOException {
    File tmp = new File(dest.getParent(), dest.getName() + ".tmp");
    try (InputStream in = URI.create(urlStr).toURL().openStream();
        OutputStream out = new FileOutputStream(tmp)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    } catch (IOException e) {
      tmp.delete();
      throw e;
    }
    if (!tmp.renameTo(dest)) {
      tmp.delete();
      throw new IOException("Could not rename " + tmp + " to " + dest);
    }
  }

  private void loadDriver(File jarFile, String driverClass) throws Exception {
    URLClassLoader loader =
        new URLClassLoader(
            new URL[] {jarFile.toURI().toURL()},
            Thread.currentThread().getContextClassLoader());
    Driver driver =
        (Driver)
            Class.forName(driverClass, true, loader).getDeclaredConstructor().newInstance();
    DriverManager.registerDriver(new DriverShim(driver));
    loadedDrivers.add(driverClass);
    logger.info("Registered JDBC driver: {}", driverClass);
  }
}
```

### Step 2: Run spotless + compile

```bash
mvn spotless:apply -q && mvn compile -q
```
Expected: BUILD SUCCESS

### Step 3: Commit

```bash
git add src/main/java/com/example/extlib/ExtLibManager.java
git commit -m "refactor: ExtLibManager delegates driver info to DataSourceRegistry"
```

---

## Task 5: Refactor DataBrowserHandler to use strategy

**Files:**
- Modify: `src/main/java/com/example/servlet/handler/DataBrowserHandler.java`

**What changes:**
1. `handleConnect` — remove the `if ("snowflake"...)` URL-building block; use `strategy.buildUrl(props)` and `strategy.buildConnectionProperties(...)` instead. Also remove the Snowflake-specific `extraProps` building.
2. `handleTables` — remove the hardcoded `systemSchemas` Set; use `strategy.getSystemSchemas()` instead.

### Step 1: Update `handleConnect`

Find and replace the entire `handleConnect` method body. The new version reads all form fields into a `Map<String, String> props`, then delegates URL and connection-property building to the strategy:

```java
  /** POST /api/data-browser/connect { dbType, url?, account?, user, password, ... } */
  public void handleConnect(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
      if (!body.has("dbType") || !body.has("user") || !body.has("password")) {
        writeJson(
            res,
            400,
            JsonUtil.errorResponse(
                "Bad Request", "Missing required fields: dbType, user, password", 400));
        return;
      }

      String dbType = body.get("dbType").getAsString().toLowerCase();
      String user = body.get("user").getAsString();
      String password = body.get("password").getAsString();

      DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType);
      if (strategy == null) {
        writeJson(
            res, 400, JsonUtil.errorResponse("Bad Request", "Unknown dbType: " + dbType, 400));
        return;
      }

      // Collect all extra fields from the request body into a flat map
      Map<String, String> props = new HashMap<>();
      body
          .entrySet()
          .forEach(
              e -> {
                if (!e.getKey().equals("dbType")
                    && !e.getKey().equals("user")
                    && !e.getKey().equals("password")) {
                  props.put(e.getKey(), e.getValue().getAsString());
                }
              });

      String url = strategy.buildUrl(props);
      Connection conn =
          ExtLibManager.getInstance().connect(dbType, url, user, password, props);
      String sessionId = SessionManager.getInstance().createSession(conn);

      Map<String, Object> data = Map.of("sessionId", sessionId, "status", "connected");
      writeJson(res, 200, JsonUtil.successResponse(data));

    } catch (Exception e) {
      logger.error("Connection failed", e);
      writeJson(res, 400, JsonUtil.errorResponse("Connection Failed", e.getMessage(), 400));
    }
  }
```

Also add the import:
```java
import com.example.datasource.DataSourceRegistry;
import com.example.datasource.DataSourceStrategy;
```

### Step 2: Update `handleTables` — replace hardcoded systemSchemas

Find this block in `handleTables`:
```java
      // System schemas to exclude across PostgreSQL, MySQL, Snowflake
      Set<String> systemSchemas =
          Set.of(
              "information_schema",
              ...);
```

And replace it with:
```java
      JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();
      // (keep the rest of the method, only change systemSchemas source)
```

Specifically, after getting `conn`, add:
```java
      String dbType = body.has("dbType") ? body.get("dbType").getAsString() : null;
      DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType != null ? dbType : "");
      Set<String> systemSchemas = strategy != null ? strategy.getSystemSchemas() : Set.of("information_schema");
```

Wait — `handleTables` currently doesn't receive `dbType` in the request. The easiest fix is to have the JS send `dbType` in the tables request. But looking at the existing API contract, changing this would also require a JS change.

**Simpler approach:** store `dbType` in the session. Update `SessionManager` to store `(dbType, Connection)` together.

**Even simpler YAGNI approach:** Look up dbType from the JDBC URL via `conn.getMetaData().getURL()` and match it against strategy URL patterns.

**Simplest approach:** pass `dbType` from the JS in the tables request body (a one-line JS change).

Use the simplest approach: pass `dbType` in the tables request.

Update `handleTables` to read `dbType` from the body:
```java
      String dbType = body.has("dbType") ? body.get("dbType").getAsString() : null;
      DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType != null ? dbType : "");
      Set<String> systemSchemas =
          strategy != null ? strategy.getSystemSchemas() : Set.of("information_schema");
```

And remove the hardcoded `Set<String> systemSchemas = Set.of(...)` block.

Also update the JS `loadTables()` to send `dbType`:
```js
const res = await api('/api/data-browser/tables', { sessionId, dbType: document.getElementById('db-type').value });
```

### Step 3: Run spotless + compile

```bash
mvn spotless:apply -q && mvn compile -q
```
Expected: BUILD SUCCESS

### Step 4: Commit

```bash
git add src/main/java/com/example/servlet/handler/DataBrowserHandler.java \
        src/main/resources/static/data-browser.html
git commit -m "refactor: DataBrowserHandler uses DataSourceStrategy for URL building and system schemas"
```

---

## Task 6: Verify end-to-end

### Step 1: Build and start

```bash
mvn -PappRun
```

### Step 2: Smoke test driver-status endpoint

```bash
curl -s -X POST http://localhost:8080/api/data-browser/driver-status \
  -H "Content-Type: application/json" \
  -d '{"dbType":"postgresql"}' | python3 -m json.tool
```
Expected: `{"status":"success","data":{"dbType":"postgresql","downloaded":true/false}}`

### Step 3: Confirm unknown dbType returns 400

```bash
curl -s -X POST http://localhost:8080/api/data-browser/driver-status \
  -H "Content-Type: application/json" \
  -d '{"dbType":"oracle"}' | python3 -m json.tool
```
Expected: `{"error":"Bad Request",...}`

### Step 4: Confirm compile and spotless both pass clean

```bash
mvn spotless:check -q && mvn compile -q && echo "ALL CLEAN"
```
Expected: `ALL CLEAN`

### Step 5: Commit any fixes

```bash
git commit -am "fix: post-verification fixes for datasource strategy"
```
