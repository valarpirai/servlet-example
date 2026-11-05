package com.example.scheduler;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.processor.ApiScriptProcessor;
import com.example.util.PropertiesUtil;
import java.net.InetAddress;
import java.time.Clock;

/** Builds the application's scheduler from {@code app_properties} and the application database. */
public final class SchedulerBootstrap {

  private SchedulerBootstrap() {}

  /**
   * Creates the scheduler without starting it.
   *
   * @return the scheduler, or null when {@code scheduler.enabled} is false or the database is not
   *     ready
   */
  public static Scheduler create() {
    if (!PropertiesUtil.getBoolean("scheduler.enabled", true)) {
      return null;
    }
    Database database = DbPropertiesLoader.getDatabase();
    if (database == null) {
      return null;
    }
    ApiScriptProcessor processor = new ApiScriptProcessor();
    HandlerRegistry handlers = new HandlerRegistry(name -> new ScriptJobHandler(processor, name));
    handlers.register("hello-world", new HelloWorldJobHandler());
    return new Scheduler(
        new SqlJobRepository(database),
        handlers,
        Clock.systemUTC(),
        PropertiesUtil.getString("scheduler.nodeId", hostname()),
        SchedulerConfig.fromProperties());
  }

  private static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "local";
    }
  }
}
