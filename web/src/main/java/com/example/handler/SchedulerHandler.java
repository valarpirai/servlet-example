package com.example.handler;

import com.example.scheduler.Scheduler;
import com.example.view.ResponseHelper;
import com.example.view.SchedulerStatsView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    Scheduler scheduler = Scheduler.get();
    if (scheduler == null) {
      ResponseHelper.sendJson(response, 200, SchedulerStatsView.disabled());
      return;
    }
    Object node = scheduler.stats();
    int status = 200;
    Map<String, Object> body;
    try {
      body = SchedulerStatsView.enabled(node, scheduler.tableCounts());
    } catch (Exception e) {
      logger.error("Failed to count scheduled jobs", e);
      body = SchedulerStatsView.tableFailed(node, e.getMessage());
      status = 500;
    }
    ResponseHelper.sendJson(response, status, body);
  }
}
