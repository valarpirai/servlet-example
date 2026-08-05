package com.example.extlib;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionManager {

  private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
  private static final SessionManager INSTANCE = new SessionManager();
  private static final long TTL_MS = 30 * 60 * 1000L; // 30 minutes

  /**
   * Connection is mutable because switching the active database on a driver that can't do so
   * in-place (see {@link com.example.datasource.DataSourceStrategy#supportsCatalogSwitch}) requires
   * replacing it with a freshly-reconnected one, under the same sessionId.
   */
  public static final class Session {
    private final String dbType;
    private final String user;
    private final String password;
    private final Map<String, String> props;
    private volatile Connection connection;
    private final AtomicLong lastAccessed;

    private Session(
        String dbType,
        String user,
        String password,
        Map<String, String> props,
        Connection connection) {
      this.dbType = dbType;
      this.user = user;
      this.password = password;
      this.props = props;
      this.connection = connection;
      this.lastAccessed = new AtomicLong(System.currentTimeMillis());
    }

    public String dbType() {
      return dbType;
    }

    public String user() {
      return user;
    }

    public String password() {
      return password;
    }

    public Map<String, String> props() {
      return props;
    }

    public Connection connection() {
      return connection;
    }
  }

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

  public String createSession(
      String dbType,
      String user,
      String password,
      Map<String, String> props,
      Connection connection) {
    String id = UUID.randomUUID().toString();
    sessions.put(id, new Session(dbType, user, password, props, connection));
    logger.info("Session created: {}", id);
    return id;
  }

  /** Returns the session and refreshes TTL, or null if not found. */
  public Session getSession(String sessionId) {
    Session session = sessions.get(sessionId);
    if (session == null) return null;
    session.lastAccessed.set(System.currentTimeMillis());
    return session;
  }

  /** Returns connection and refreshes TTL, or null if session not found. */
  public Connection getConnection(String sessionId) {
    Session session = getSession(sessionId);
    return session == null ? null : session.connection;
  }

  /**
   * Replaces the connection for an existing session (used when switching database requires
   * reconnecting), closing the old connection. No-op (closing the new connection) if the session no
   * longer exists.
   */
  public void replaceConnection(String sessionId, Connection newConnection) {
    Session session = sessions.get(sessionId);
    if (session == null) {
      try {
        newConnection.close();
      } catch (SQLException e) {
        logger.warn("Failed to close orphaned connection: {}", sessionId, e);
      }
      return;
    }
    Connection old = session.connection;
    session.connection = newConnection;
    session.lastAccessed.set(System.currentTimeMillis());
    try {
      old.close();
    } catch (SQLException e) {
      logger.warn("Failed to close previous connection for session: {}", sessionId, e);
    }
  }

  public void removeSession(String sessionId) {
    Session session = sessions.remove(sessionId);
    if (session != null) {
      try {
        session.connection.close();
      } catch (SQLException e) {
        logger.warn("Failed to close connection for session: {}", sessionId, e);
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
              boolean expired = (now - entry.getValue().lastAccessed.get()) > TTL_MS;
              if (expired) {
                try {
                  entry.getValue().connection.close();
                } catch (SQLException e) {
                  logger.warn("Failed to close connection for session: {}", entry.getKey(), e);
                }
                logger.info("Session evicted (TTL): {}", entry.getKey());
              }
              return expired;
            });
  }
}
