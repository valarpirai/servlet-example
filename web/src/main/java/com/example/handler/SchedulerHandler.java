package com.example.handler;

import com.example.scheduler.Scheduler;
import com.example.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON endpoint for the job scheduler's statistics. */
public class SchedulerHandler {

  private static final Logger logger = LoggerFactory.getLogger(SchedulerHandler.class);
  private static SchedulerHandler instance;

  private SchedulerHandler() {}

  public static synchronized SchedulerHandler getInstance() {
    if (instance == null) {
      instance = new SchedulerHandler();
    }
    return instance;
  }

  /**
   * GET /api/scheduler/stats. Answers {@code enabled: false} when no scheduler runs. Otherwise
   * returns this node's counters under {@code node} and the job row counts by state under {@code
   * table}.
   */
  public void handleStats(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    Scheduler scheduler = Scheduler.get();
    body.put("enabled", scheduler != null);
    int status = 200;
    if (scheduler != null) {
      body.put("node", scheduler.stats());
      try {
        body.put("table", scheduler.tableCounts());
      } catch (Exception e) {
        logger.error("Failed to count scheduled jobs", e);
        body.put("table", Map.of("error", String.valueOf(e.getMessage())));
        status = 500;
      }
    }
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    PrintWriter out = response.getWriter();
    out.print(JsonUtil.toJson(body));
    out.flush();
  }
}
