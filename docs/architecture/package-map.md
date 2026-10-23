# Package Map

Every source package under `com.example`, and what each one is for.

`Main` boots the app. `servlet/` holds the single servlet. Everything else is
grouped by function, one level deep.

```
src/main/java/com/example/
├── Main.java                           Tomcat bootstrap
│
├── servlet/
│   └── RouterServlet.java              HTTP request router
│
├── route/                              Declarative routing
│   ├── RouteRegistry.java              YAML loader and pattern matcher
│   └── RouteDispatcher.java            Reflection-based dispatch
│
├── processor/                          Per-request processors
│   ├── IRequestProcessor.java          Processor interface
│   ├── ScriptProcessor.java            JavaScript execution
│   ├── ApiScriptProcessor.java         Script-defined HTTP APIs
│   ├── FileUploadProcessor.java        Multipart upload
│   ├── TemplateProcessor.java          HTML template rendering
│   └── ModuleProcessor.java            JavaScript module CRUD
│
├── handler/                            Singleton HTTP handlers
│   ├── ApiHandler.java                 Script-backed API dispatch
│   ├── AttachmentHandler.java          Download, delete, list
│   ├── DataBrowserHandler.java         Relational browsing
│   ├── MongoHandler.java               MongoDB browsing
│   └── PropertiesHandler.java          Properties REST API
│
├── storage/                            Chunked file storage
│   ├── AttachmentStorage.java          Backend interface
│   ├── AttachmentManager.java          Coordinator and metadata cache
│   ├── LocalFileSystemStorage.java     Filesystem backend
│   ├── DatabaseAttachmentStorage.java  PostgreSQL backend
│   └── ChunkedOutputStream.java        Chunked writer
│
├── datasource/                         Database connection strategies
│   ├── DataSourceStrategy.java         Strategy interface, a ServiceLoader SPI
│   ├── DataSourceRegistry.java         Strategy registry
│   ├── PostgreSqlStrategy.java         PostgreSQL URL builder
│   ├── MySqlStrategy.java              MySQL URL builder
│   └── SnowflakeStrategy.java          Snowflake URL builder
│
├── config/                             Database config and properties
│   ├── DbConfig.java                   Connection setup
│   ├── Database.java                   jOOQ query and transact wrapper
│   ├── DbPropertiesLoader.java         Flyway plus property wiring
│   ├── PropertyRepository.java         app_properties CRUD
│   └── AppProperty.java                Java record
│
├── script/                             Rhino execution internals
│   ├── ScriptExecutor.java             Script invocation
│   ├── ScriptModuleLoader.java         require() resolution
│   └── ScriptScopeBuilder.java         Sandbox scope construction
│
├── security/
│   └── ScriptSecurityManager.java      ClassShutter policy
│
├── module/                             JavaScript module system
│   ├── ModuleManager.java              Module CRUD on the modules table
│   └── ModuleDependencyResolver.java   Dependency graph, cycle detection
│
├── record/                             Table access and business rules
│   ├── TableRecord.java                Query and write allow-listed tables
│   └── BusinessRuleRunner.java         Runs rules around each write
│
├── extlib/                             Dynamic JDBC driver loading
│   ├── ExtLibManager.java              Driver jar loading
│   ├── DriverShim.java                 Driver delegation wrapper
│   └── SessionManager.java             Connection session lifecycle
│
├── mongo/                              MongoDB by reflection, no compile dependency
│   ├── MongoExtLibManager.java         Mongo driver loading
│   ├── MongoReflectionClient.java      Reflective driver calls
│   └── MongoSessionManager.java        Mongo session lifecycle
│
├── model/                              Shared domain models, all Lombok
│   ├── Attachment.java                 File metadata
│   ├── Module.java                     Module metadata
│   ├── Route.java                      Route configuration
│   └── ProcessorResponse.java          HTTP response builder
│
├── exception/
│   ├── ScriptExecutionException.java   Script failure
│   └── StorageException.java           Storage failure
│
└── util/                               Shared utilities
    ├── PropertiesUtil.java             Property lookup with env overrides
    ├── JsonUtil.java                   Gson wrapper with InstantTypeAdapter
    ├── JsonConverter.java              Java and JavaScript value conversion
    ├── TemplateEngine.java             HTML templates
    ├── StructuredLogger.java           MDC-based logging
    ├── CorrelationIdFilter.java        Correlation ID filter
    ├── LoggingConfig.java              Programmatic Logback setup
    ├── RequestHelper.java              Request parsing
    └── ResponseHelper.java             Response writing
```

## Tests mirror this layout

Test packages match source packages. `route/RouteRegistryTest` tests
`route/RouteRegistry`. Find the tests for a class by looking in the same package
under `src/test/java`.

## Finding things by task

| Task | Start here |
|---|---|
| Routing | `route/`, `src/main/resources/routes.yml` |
| Request handling | `processor/`, `handler/` |
| File storage | `storage/AttachmentManager` |
| Database browsing | `datasource/` |
| Script sandbox | `security/ScriptSecurityManager` |
| Logging | `util/StructuredLogger` |

## Related

- [Component responsibilities](./component-responsibilities.md) — what each class owns
- [Configuration](./configuration.md) — resource files
- [Request flow](./request-flow.md) — how a request moves through these packages
- [Design patterns and principles](./design-patterns.md) — why these packages are shaped this way
