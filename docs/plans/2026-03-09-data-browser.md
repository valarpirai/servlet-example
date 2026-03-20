# Data Browser Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a web-based database browser that downloads JDBC drivers on demand, connects to PostgreSQL/MySQL/Snowflake, and lets users browse tables and run SQL queries.

**Architecture:** Dynamic JDBC driver loading via `URLClassLoader` + `DriverShim` (no restart needed). Connections managed server-side with 30-min TTL. Connection credentials stored in browser `localStorage`, sessionId held in memory for the tab's lifetime.

**Tech Stack:** Java 17, Mozilla Rhino (not used here), Gson, embedded Tomcat 10, vanilla JS + fetch API, no new Maven deps required.

---

## Driver Catalogue Reference

| dbType | Maven JAR path on repo1.maven.org | Driver class |
|---|---|---|
| `mysql` | `com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar` | `com.mysql.cj.jdbc.Driver` |
| `postgresql` | `org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar` | `org.postgresql.Driver` |
| `snowflake` | `net/snowflake/snowflake-jdbc/3.15.0/snowflake-jdbc-3.15.0.jar` | `net.snowflake.client.jdbc.SnowflakeDriver` |

Maven Central base: `https://repo1.maven.org/maven2/`

---

## Task 1: ExtLib Directory + DriverShim

**Files:**
- Create: `extlib/.gitkeep`
- Create: `src/main/java/com/example/extlib/DriverShim.java`
- Modify: `.gitignore`

### Step 1: Create `extlib/` and gitignore the JARs

```bash
mkdir -p extlib
touch extlib/.gitkeep
```

Add to `.gitignore`:
```
extlib/*.jar
```

### Step 2: Create `DriverShim.java`

`DriverShim` is required because `DriverManager` uses the calling thread's classloader to filter registered drivers. A driver loaded via a child `URLClassLoader` is invisible to `DriverManager` without this wrapper.

```java
package com.example.extlib;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Wraps a JDBC Driver loaded from an external URLClassLoader so that
 * DriverManager (which filters by system classloader) can use it.
 */
public class DriverShim implements Driver {

    private final Driver delegate;

    public DriverShim(Driver delegate) {
        this.delegate = delegate;
    }

    @Override public Connection connect(String url, Properties info) throws SQLException {
        return delegate.connect(url, info);
    }
    @Override public boolean acceptsURL(String url) throws SQLException {
        return delegate.acceptsURL(url);
    }
    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
    }
    @Override public int getMajorVersion() { return delegate.getMajorVersion(); }
    @Override public int getMinorVersion() { return delegate.getMinorVersion(); }
    @Override public boolean jdbcCompliant() { return delegate.jdbcCompliant(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
```

### Step 3: Commit

```bash
git add extlib/.gitkeep .gitignore src/main/java/com/example/extlib/DriverShim.java
git commit -m "feat: add extlib directory and DriverShim for dynamic JDBC loading"
```

---

## Task 2: ExtLibManager

**Files:**
- Create: `src/main/java/com/example/extlib/ExtLibManager.java`

`ExtLibManager` is a singleton that:
1. Knows the driver catalogue (dbType → JAR URL + driver class name)
2. Downloads JARs from Maven Central to `extlib/` (skips if already present)
3. Loads each JAR into a `URLClassLoader`, instantiates the driver, wraps it in `DriverShim`, registers with `DriverManager`
4. Returns a `Connection` given dbType + url + user + password

### Step 1: Create `ExtLibManager.java` — Part A (class skeleton + catalogue)

```java
package com.example.extlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExtLibManager {

    private static final Logger logger = LoggerFactory.getLogger(ExtLibManager.class);
    private static final ExtLibManager INSTANCE = new ExtLibManager();
    private static final String EXTLIB_DIR = "extlib";
    private static final String MAVEN_BASE = "https://repo1.maven.org/maven2/";

    // Tracks which driver classes have already been registered with DriverManager
    private final Set<String> loadedDrivers = ConcurrentHashMap.newKeySet();

    public record DriverInfo(String jarPath, String driverClass) {}

    private static final Map<String, DriverInfo> CATALOGUE = Map.of(
        "mysql", new DriverInfo(
            "com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar",
            "com.mysql.cj.jdbc.Driver"
        ),
        "postgresql", new DriverInfo(
            "org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar",
            "org.postgresql.Driver"
        ),
        "snowflake", new DriverInfo(
            "net/snowflake/snowflake-jdbc/3.15.0/snowflake-jdbc-3.15.0.jar",
            "net.snowflake.client.jdbc.SnowflakeDriver"
        )
    );

    private ExtLibManager() {
        new File(EXTLIB_DIR).mkdirs();
    }

    public static ExtLibManager getInstance() { return INSTANCE; }

    public boolean isSupported(String dbType) {
        return CATALOGUE.containsKey(dbType.toLowerCase());
    }

    public boolean isDownloaded(String dbType) {
        DriverInfo info = CATALOGUE.get(dbType.toLowerCase());
        if (info == null) return false;
        String fileName = info.jarPath().substring(info.jarPath().lastIndexOf('/') + 1);
        return new File(EXTLIB_DIR, fileName).exists();
    }
}
```

### Step 2: Add `downloadAndLoad(dbType)` method to `ExtLibManager`

Add inside the class:

```java
    /**
     * Downloads the JDBC driver JAR for dbType (if not already present),
     * loads it into a URLClassLoader, and registers it with DriverManager.
     * Safe to call multiple times — skips if already loaded.
     */
    public synchronized void downloadAndLoad(String dbType) throws Exception {
        String key = dbType.toLowerCase();
        DriverInfo info = CATALOGUE.get(key);
        if (info == null) throw new IllegalArgumentException("Unknown dbType: " + dbType);

        String fileName = info.jarPath().substring(info.jarPath().lastIndexOf('/') + 1);
        File jarFile = new File(EXTLIB_DIR, fileName);

        // Download if not present
        if (!jarFile.exists()) {
            String downloadUrl = MAVEN_BASE + info.jarPath();
            logger.info("Downloading {} from {}", fileName, downloadUrl);
            downloadFile(downloadUrl, jarFile);
            logger.info("Downloaded {} ({} bytes)", fileName, jarFile.length());
        } else {
            logger.info("JAR already present: {}", jarFile.getAbsolutePath());
        }

        // Load if not already registered
        if (!loadedDrivers.contains(info.driverClass())) {
            loadDriver(jarFile, info.driverClass());
        }
    }

    private void downloadFile(String urlStr, File dest) throws IOException {
        URL url = new URL(urlStr);
        try (InputStream in = url.openStream();
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private void loadDriver(File jarFile, String driverClass) throws Exception {
        URLClassLoader loader = new URLClassLoader(
            new URL[]{jarFile.toURI().toURL()},
            Thread.currentThread().getContextClassLoader()
        );
        Driver driver = (Driver) Class.forName(driverClass, true, loader)
                                      .getDeclaredConstructor()
                                      .newInstance();
        DriverManager.registerDriver(new DriverShim(driver));
        loadedDrivers.add(driverClass);
        logger.info("Registered JDBC driver: {}", driverClass);
    }
```

### Step 3: Add `connect(dbType, url, user, password, extraProps)` method

```java
    /**
     * Opens a JDBC connection. Caller must close it (managed by SessionManager).
     * extraProps: additional JDBC properties (e.g. warehouse, db, schema for Snowflake)
     */
    public Connection connect(String dbType, String url, String user, String password,
                               Map<String, String> extraProps) throws Exception {
        // Ensure driver is loaded
        downloadAndLoad(dbType);

        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        if (extraProps != null) props.putAll(extraProps);

        return DriverManager.getConnection(url, props);
    }
```

### Step 4: Commit

```bash
git add src/main/java/com/example/extlib/ExtLibManager.java
git commit -m "feat: add ExtLibManager with Maven Central download and dynamic JDBC loading"
```

---

## Task 3: SessionManager

**Files:**
- Create: `src/main/java/com/example/extlib/SessionManager.java`

Sessions are stored server-side. TTL is 30 minutes of inactivity. A background thread cleans expired sessions every 5 minutes.

### Step 1: Create `SessionManager.java`

```java
package com.example.extlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static final SessionManager INSTANCE = new SessionManager();
    private static final long TTL_MS = 30 * 60 * 1000L; // 30 minutes

    private record Session(Connection connection, long[] lastAccessed) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "session-cleaner");
        t.setDaemon(true);
        return t;
    });

    private SessionManager() {
        cleaner.scheduleAtFixedRate(this::evictExpired, 5, 5, TimeUnit.MINUTES);
    }

    public static SessionManager getInstance() { return INSTANCE; }

    public String createSession(Connection connection) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(connection, new long[]{System.currentTimeMillis()}));
        logger.info("Session created: {}", id);
        return id;
    }

    /** Returns connection and refreshes TTL, or null if session not found / expired. */
    public Connection getConnection(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) return null;
        session.lastAccessed()[0] = System.currentTimeMillis();
        return session.connection();
    }

    public void removeSession(String sessionId) {
        Session session = sessions.remove(sessionId);
        if (session != null) {
            try { session.connection().close(); } catch (SQLException ignored) {}
            logger.info("Session removed: {}", sessionId);
        }
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue().lastAccessed()[0]) > TTL_MS;
            if (expired) {
                try { entry.getValue().connection().close(); } catch (SQLException ignored) {}
                logger.info("Session evicted (TTL): {}", entry.getKey());
            }
            return expired;
        });
    }
}
```

### Step 2: Commit

```bash
git add src/main/java/com/example/extlib/SessionManager.java
git commit -m "feat: add SessionManager with 30-min TTL and background eviction"
```

---

## Task 4: DataBrowserHandler

**Files:**
- Create: `src/main/java/com/example/servlet/handler/DataBrowserHandler.java`

This handler is **not** a `RequestProcessor` — it's a dedicated class called directly from `RouterServlet`. It handles four POST sub-paths and one GET (served by RouterServlet as static file).

### Step 1: Create class skeleton + helper methods

```java
package com.example.servlet.handler;

import com.example.extlib.ExtLibManager;
import com.example.extlib.SessionManager;
import com.example.servlet.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.*;
import java.util.*;

public class DataBrowserHandler {

    private static final Logger logger = LoggerFactory.getLogger(DataBrowserHandler.class);
    private static final DataBrowserHandler INSTANCE = new DataBrowserHandler();

    private DataBrowserHandler() {}

    public static DataBrowserHandler getInstance() { return INSTANCE; }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = req.getReader()) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void writeJson(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().print(body);
        res.getWriter().flush();
    }
}
```

### Step 2: Add `handleDownload` method

```java
    /** POST /api/data-browser/download  { "dbType": "postgresql" } */
    public void handleDownload(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
            String dbType = body.get("dbType").getAsString();

            if (!ExtLibManager.getInstance().isSupported(dbType)) {
                writeJson(res, 400, JsonUtil.errorResponse("Bad Request", "Unknown dbType: " + dbType, 400));
                return;
            }

            ExtLibManager.getInstance().downloadAndLoad(dbType);
            Map<String, Object> data = Map.of("status", "ready", "dbType", dbType);
            writeJson(res, 200, JsonUtil.successResponse(data));

        } catch (Exception e) {
            logger.error("Driver download failed", e);
            writeJson(res, 500, JsonUtil.errorResponse("Download Failed", e.getMessage(), 500));
        }
    }
```

### Step 3: Add `handleConnect` method

Builds the JDBC URL for Snowflake from individual fields; uses the raw `url` field for MySQL/PostgreSQL.

```java
    /** POST /api/data-browser/connect  { dbType, url?, account?, user, password, warehouse?, database?, schema?, role? } */
    public void handleConnect(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
            String dbType = body.get("dbType").getAsString().toLowerCase();
            String user = body.get("user").getAsString();
            String password = body.get("password").getAsString();

            String url;
            Map<String, String> extraProps = new HashMap<>();

            if ("snowflake".equals(dbType)) {
                String account = body.get("account").getAsString();
                url = "jdbc:snowflake://" + account + ".snowflakecomputing.com/";
                if (body.has("warehouse")) extraProps.put("warehouse", body.get("warehouse").getAsString());
                if (body.has("database"))  extraProps.put("db",        body.get("database").getAsString());
                if (body.has("schema"))    extraProps.put("schema",    body.get("schema").getAsString());
                if (body.has("role"))      extraProps.put("role",      body.get("role").getAsString());
            } else {
                url = body.get("url").getAsString();
            }

            Connection conn = ExtLibManager.getInstance().connect(dbType, url, user, password, extraProps);
            String sessionId = SessionManager.getInstance().createSession(conn);

            Map<String, Object> data = Map.of("sessionId", sessionId, "status", "connected");
            writeJson(res, 200, JsonUtil.successResponse(data));

        } catch (Exception e) {
            logger.error("Connection failed", e);
            writeJson(res, 400, JsonUtil.errorResponse("Connection Failed", e.getMessage(), 400));
        }
    }
```

### Step 4: Add `handleTables` method

```java
    /** POST /api/data-browser/tables  { "sessionId": "..." } */
    public void handleTables(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
            String sessionId = body.get("sessionId").getAsString();

            Connection conn = SessionManager.getInstance().getConnection(sessionId);
            if (conn == null) {
                writeJson(res, 401, JsonUtil.errorResponse("Session Expired", "Session not found or expired", 401));
                return;
            }

            DatabaseMetaData meta = conn.getMetaData();
            Map<String, List<String>> schemaMap = new LinkedHashMap<>();

            try (ResultSet schemas = meta.getSchemas()) {
                while (schemas.next()) {
                    schemaMap.put(schemas.getString("TABLE_SCHEM"), new ArrayList<>());
                }
            }

            // Fallback for DBs that don't support getSchemas() well
            if (schemaMap.isEmpty()) schemaMap.put("default", new ArrayList<>());

            for (String schema : schemaMap.keySet()) {
                try (ResultSet tables = meta.getTables(null, schema, "%", new String[]{"TABLE", "VIEW"})) {
                    while (tables.next()) {
                        schemaMap.get(schema).add(tables.getString("TABLE_NAME"));
                    }
                }
            }

            // Convert to list of {schema, tables[]} for JSON
            List<Map<String, Object>> result = new ArrayList<>();
            schemaMap.forEach((schema, tables) -> {
                if (!tables.isEmpty()) {
                    result.add(Map.of("schema", schema, "tables", tables));
                }
            });

            writeJson(res, 200, JsonUtil.successResponse(Map.of("schemas", result)));

        } catch (Exception e) {
            logger.error("Failed to list tables", e);
            writeJson(res, 500, JsonUtil.errorResponse("Query Failed", e.getMessage(), 500));
        }
    }
```

### Step 5: Add `handleQuery` method

```java
    /** POST /api/data-browser/query  { sessionId, sql, page (1-based) } */
    public void handleQuery(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
            String sessionId = body.get("sessionId").getAsString();
            String sql = body.get("sql").getAsString();
            int page = body.has("page") ? body.get("page").getAsInt() : 1;
            int pageSize = 100;
            int offset = (page - 1) * pageSize;

            Connection conn = SessionManager.getInstance().getConnection(sessionId);
            if (conn == null) {
                writeJson(res, 401, JsonUtil.errorResponse("Session Expired", "Session not found or expired", 401));
                return;
            }

            // Wrap user SQL in a pagination query
            String pagedSql = "SELECT * FROM (" + sql + ") __q LIMIT " + pageSize + " OFFSET " + offset;

            List<String> columns = new ArrayList<>();
            List<List<Object>> rows = new ArrayList<>();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(pagedSql)) {

                ResultSetMetaData rsMeta = rs.getMetaData();
                int colCount = rsMeta.getColumnCount();
                for (int i = 1; i <= colCount; i++) columns.add(rsMeta.getColumnName(i));

                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= colCount; i++) row.add(rs.getObject(i));
                    rows.add(row);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("columns", columns);
            data.put("rows", rows);
            data.put("page", page);
            data.put("pageSize", pageSize);
            data.put("hasMore", rows.size() == pageSize);

            writeJson(res, 200, JsonUtil.successResponse(data));

        } catch (Exception e) {
            logger.error("Query failed", e);
            writeJson(res, 400, JsonUtil.errorResponse("Query Failed", e.getMessage(), 400));
        }
    }
```

### Step 6: Add `handleDisconnect` method

```java
    /** POST /api/data-browser/disconnect  { "sessionId": "..." } */
    public void handleDisconnect(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
            String sessionId = body.get("sessionId").getAsString();
            SessionManager.getInstance().removeSession(sessionId);
            writeJson(res, 200, JsonUtil.successResponse(Map.of("status", "disconnected")));
        } catch (Exception e) {
            writeJson(res, 500, JsonUtil.errorResponse("Error", e.getMessage(), 500));
        }
    }
```

### Step 7: Commit

```bash
git add src/main/java/com/example/servlet/handler/DataBrowserHandler.java
git commit -m "feat: add DataBrowserHandler with download/connect/tables/query endpoints"
```

---

## Task 5: Wire into RouterServlet

**Files:**
- Modify: `src/main/java/com/example/servlet/RouterServlet.java`

### Step 1: Add import at top of RouterServlet

```java
import com.example.servlet.handler.DataBrowserHandler;
```

### Step 2: Add `/data-browser` to `doGet()` switch — insert before the `default:` case

```java
if ("/data-browser".equals(path)) {
    serveStaticFile(request, response, "static/data-browser.html", "text/html");
    long responseTime = System.currentTimeMillis() - startTime;
    logRequest(request, response.getStatus(), responseTime, 0);
    return;
}
```

Place this block right after the existing `/script-editor` block (around line 131).

### Step 3: Add data-browser paths to `doPost()` switch

Replace the existing `switch (path)` block in `doPost()` with:

```java
switch (path) {
    case "/api/form":
    case "/api/json":
    case "/api/upload":
    case "/api/script":
    case "/api/render":
        handleProcessorRequest(request, response);
        break;
    case "/api/data-browser/download":
        DataBrowserHandler.getInstance().handleDownload(request, response);
        break;
    case "/api/data-browser/connect":
        DataBrowserHandler.getInstance().handleConnect(request, response);
        break;
    case "/api/data-browser/tables":
        DataBrowserHandler.getInstance().handleTables(request, response);
        break;
    case "/api/data-browser/query":
        DataBrowserHandler.getInstance().handleQuery(request, response);
        break;
    case "/api/data-browser/disconnect":
        DataBrowserHandler.getInstance().handleDisconnect(request, response);
        break;
    default:
        PrintWriter out = response.getWriter();
        handleNotFound(response, out, path);
        out.flush();
        break;
}
```

### Step 4: Add endpoint to startup log in `Main.java`

In `Main.java`, after the `/script-editor` log line, add:
```java
logger.info("  - http://localhost:{}/data-browser (Database Browser)", PORT);
```

### Step 5: Commit

```bash
git add src/main/java/com/example/servlet/RouterServlet.java src/main/java/com/example/Main.java
git commit -m "feat: wire data browser routes into RouterServlet and Main startup log"
```

---

## Task 6: Data Browser HTML — Shell + Connection Panel

**Files:**
- Create: `src/main/resources/static/data-browser.html`

Build this file in two steps (per CLAUDE.md: no large files at once).

### Step 1: Create the shell with connection panel

Create `src/main/resources/static/data-browser.html` with:
- `<head>` with inline styles (dark sidebar, light main area)
- Left sidebar: DB type selector + dynamic fields + connect button + driver status badge
- Right main area: empty placeholders for table browser and SQL editor tabs
- `<script>` block: `localStorage` auto-fill on load, dynamic field rendering per DB type, `downloadDriver()` + `connect()` functions

**DB type → fields mapping (in JS):**
```js
const DB_FIELDS = {
  postgresql: [
    { id: 'url', label: 'JDBC URL', placeholder: 'jdbc:postgresql://host:5432/dbname', required: true },
    { id: 'user', label: 'Username', required: true },
    { id: 'password', label: 'Password', type: 'password', required: true }
  ],
  mysql: [
    { id: 'url', label: 'JDBC URL', placeholder: 'jdbc:mysql://host:3306/dbname', required: true },
    { id: 'user', label: 'Username', required: true },
    { id: 'password', label: 'Password', type: 'password', required: true }
  ],
  snowflake: [
    { id: 'account', label: 'Account', placeholder: 'myorg-myaccount', required: true },
    { id: 'user', label: 'Username', required: true },
    { id: 'password', label: 'Password', type: 'password', required: true },
    { id: 'warehouse', label: 'Warehouse', required: false },
    { id: 'database', label: 'Database', required: false },
    { id: 'schema', label: 'Schema', required: false },
    { id: 'role', label: 'Role', required: false }
  ]
};
```

**localStorage keys:** `db_type`, `db_url`, `db_user`, `db_account`, `db_warehouse`, `db_database`, `db_schema`, `db_role` — save on connect, restore on load.

### Step 2: Add table browser + SQL editor tabs + results grid

Add to the main content area:
- Tab bar: `[Table Browser] [SQL Editor]`
- Table browser tab: left sub-panel with schema/table tree (collapsible), right with results grid
- SQL editor tab: `<textarea>` for SQL, Run button (Cmd+Enter shortcut), results grid
- Results grid component: renders `columns` + `rows` from API, pagination controls (Prev / Page N / Next)
- JS functions: `loadTables()`, `queryTable(schema, table)`, `runQuery()`, `renderGrid(data)`

### Step 3: Commit

```bash
git add src/main/resources/static/data-browser.html
git commit -m "feat: add data browser HTML page with connection panel, table browser, and SQL editor"
```

---

## Task 7: Manual Verification

### Step 1: Build and start the server

```bash
mvn -PappRun
```

Expected in logs:
```
http://localhost:8080/data-browser (Database Browser)
```

### Step 2: Verify endpoints with curl

```bash
# Download PostgreSQL driver
curl -X POST http://localhost:8080/api/data-browser/download \
  -H "Content-Type: application/json" \
  -d '{"dbType":"postgresql"}'
# Expected: {"status":"success","data":{"status":"ready","dbType":"postgresql"},...}

# Verify JAR downloaded
ls -la extlib/
# Expected: postgresql-42.7.3.jar present
```

### Step 3: Open data browser in browser

Navigate to `http://localhost:8080/data-browser`

- Select `postgresql`, fill URL/user/pass, click Connect
- Verify schemas/tables appear in sidebar
- Click a table → rows appear in grid
- Switch to SQL Editor, type `SELECT 1`, click Run → result appears

### Step 4: Verify localStorage persistence

Refresh page → fields auto-populated from localStorage.

### Step 5: Commit any fixes and tag

```bash
git commit -m "fix: data browser post-verification fixes" # if any fixes needed
```

---

## File Summary

| Action | File |
|---|---|
| Create | `extlib/.gitkeep` |
| Modify | `.gitignore` |
| Create | `src/main/java/com/example/extlib/DriverShim.java` |
| Create | `src/main/java/com/example/extlib/ExtLibManager.java` |
| Create | `src/main/java/com/example/extlib/SessionManager.java` |
| Create | `src/main/java/com/example/servlet/handler/DataBrowserHandler.java` |
| Modify | `src/main/java/com/example/servlet/RouterServlet.java` |
| Modify | `src/main/java/com/example/Main.java` |
| Create | `src/main/resources/static/data-browser.html` |
