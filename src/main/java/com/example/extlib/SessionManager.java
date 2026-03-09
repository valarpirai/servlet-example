package com.example.extlib;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionManager {

  private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
  private static final SessionManager INSTANCE = new SessionManager();
  private static final long TTL_MS = 30 * 60 * 1000L; // 30 minutes

  private record Session(Connection connection, long[] lastAccessed) {}

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleaner =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "session-cleaner");
            t.setDaemon(true);
            return t;
          });

  private SessionManager() {
    cleaner.scheduleAtFixedRate(this::evictExpired, 5, 5, TimeUnit.MINUTES);
  }

  public static SessionManager getInstance() {
    return INSTANCE;
  }

  public String createSession(Connection connection) {
    String id = UUID.randomUUID().toString();
    sessions.put(id, new Session(connection, new long[] {System.currentTimeMillis()}));
    logger.info("Session created: {}", id);
    return id;
  }

  /** Returns connection and refreshes TTL, or null if session not found. */
  public Connection getConnection(String sessionId) {
    Session session = sessions.get(sessionId);
    if (session == null) return null;
    session.lastAccessed()[0] = System.currentTimeMillis();
    return session.connection();
  }

  public void removeSession(String sessionId) {
    Session session = sessions.remove(sessionId);
    if (session != null) {
      try {
        session.connection().close();
      } catch (SQLException ignored) {
      }
      logger.info("Session removed: {}", sessionId);
    }
  }

  private void evictExpired() {
    long now = System.currentTimeMillis();
    sessions
        .entrySet()
        .removeIf(
            entry -> {
              boolean expired = (now - entry.getValue().lastAccessed()[0]) > TTL_MS;
              if (expired) {
                try {
                  entry.getValue().connection().close();
                } catch (SQLException ignored) {
                }
                logger.info("Session evicted (TTL): {}", entry.getKey());
              }
              return expired;
            });
  }
}
