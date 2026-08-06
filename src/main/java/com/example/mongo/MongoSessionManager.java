package com.example.mongo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds active MongoDB connections. Mirrors com.example.extlib.SessionManager but stores the client
 * as a raw Object (a reflectively-created com.mongodb.client.MongoClient instance) since there is
 * no compile-time type for it.
 */
public class MongoSessionManager {

  private static final Logger logger = LoggerFactory.getLogger(MongoSessionManager.class);
  private static final MongoSessionManager INSTANCE = new MongoSessionManager();
  private static final long TTL_MS = 30 * 60 * 1000L; // 30 minutes

  public static final class Session {
    private final Object client;
    private final MongoReflectionClient reflectionClient;
    private final AtomicLong lastAccessed;

    private Session(Object client, MongoReflectionClient reflectionClient) {
      this.client = client;
      this.reflectionClient = reflectionClient;
      this.lastAccessed = new AtomicLong(System.currentTimeMillis());
    }

    public Object client() {
      return client;
    }

    public MongoReflectionClient reflectionClient() {
      return reflectionClient;
    }
  }

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleaner =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "mongo-session-cleaner");
            t.setDaemon(true);
            return t;
          });

  private MongoSessionManager() {
    cleaner.scheduleAtFixedRate(this::evictExpired, 5, 5, TimeUnit.MINUTES);
  }

  public static MongoSessionManager getInstance() {
    return INSTANCE;
  }

  public String createSession(Object client, MongoReflectionClient reflectionClient) {
    String id = UUID.randomUUID().toString();
    sessions.put(id, new Session(client, reflectionClient));
    logger.info("Mongo session created: {}", id);
    return id;
  }

  /** Returns the session and refreshes TTL, or null if not found. */
  public Session getSession(String sessionId) {
    Session session = sessions.get(sessionId);
    if (session == null) return null;
    session.lastAccessed.set(System.currentTimeMillis());
    return session;
  }

  public void removeSession(String sessionId) {
    Session session = sessions.remove(sessionId);
    if (session != null) {
      closeQuietly(session);
      logger.info("Mongo session removed: {}", sessionId);
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
                closeQuietly(entry.getValue());
                logger.info("Mongo session evicted (TTL): {}", entry.getKey());
              }
              return expired;
            });
  }

  private void closeQuietly(Session session) {
    try {
      session.reflectionClient.close(session.client);
    } catch (ReflectiveOperationException e) {
      logger.warn("Failed to close MongoDB client", e);
    }
  }
}
