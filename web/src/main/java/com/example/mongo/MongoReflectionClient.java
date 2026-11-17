package com.example.mongo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Drives the MongoDB Java driver purely via reflection against classes loaded from a URLClassLoader
 * (see MongoExtLibManager), so no compile-time dependency on com.mongodb.* or org.bson.* is needed.
 * MongoDB has no JDK-standard interface like java.sql.Driver to shim against, so every driver call
 * here goes through Class.forName + Method.invoke.
 */
public class MongoReflectionClient {

  private final ClassLoader loader;

  public MongoReflectionClient(ClassLoader loader) {
    this.loader = loader;
  }

  /** Creates a MongoClient from a connection string. Returns the raw client Object. */
  public Object connect(String connectionString) throws ReflectiveOperationException {
    Class<?> mongoClientsClass = Class.forName("com.mongodb.client.MongoClients", true, loader);
    Method create = mongoClientsClass.getMethod("create", String.class);
    return invoke(create, null, connectionString);
  }

  /** Lists database names visible to the given client. */
  public List<String> listDatabaseNames(Object client) throws ReflectiveOperationException {
    Method method = client.getClass().getMethod("listDatabaseNames");
    Object iterable = invoke(method, client);
    return drainStringIterable(iterable);
  }

  /** Closes the client, releasing its connection pool. */
  public void close(Object client) throws ReflectiveOperationException {
    Method method = client.getClass().getMethod("close");
    invoke(method, client);
  }

  /** Gets a MongoDatabase handle by name. */
  public Object getDatabase(Object client, String databaseName)
      throws ReflectiveOperationException {
    Method method = client.getClass().getMethod("getDatabase", String.class);
    return invoke(method, client, databaseName);
  }

  /** Lists collection names in the given database. */
  public List<String> listCollectionNames(Object database) throws ReflectiveOperationException {
    Method method = database.getClass().getMethod("listCollectionNames");
    Object iterable = invoke(method, database);
    return drainStringIterable(iterable);
  }

  /** Gets a MongoCollection<Document> handle by name. */
  public Object getCollection(Object database, String collectionName)
      throws ReflectiveOperationException {
    Class<?> documentClass = Class.forName("org.bson.Document", true, loader);
    Method method = database.getClass().getMethod("getCollection", String.class, Class.class);
    return invoke(method, database, collectionName, documentClass);
  }

  /**
   * Runs a find on the collection with an optional JSON filter (empty/blank means match all),
   * returning each result document as a JSON string.
   */
  public List<String> find(Object collection, String filterJson, int limit)
      throws ReflectiveOperationException {
    Class<?> documentClass = Class.forName("org.bson.Document", true, loader);
    Object filter =
        (filterJson == null || filterJson.isBlank())
            ? documentClass.getDeclaredConstructor().newInstance()
            : invoke(documentClass.getMethod("parse", String.class), null, filterJson);

    Class<?> bsonIface = Class.forName("org.bson.conversions.Bson", true, loader);
    Method findMethod = collection.getClass().getMethod("find", bsonIface);
    Object findIterable = invoke(findMethod, collection, filter);

    Class<?> findIterableClass = Class.forName("com.mongodb.client.FindIterable", true, loader);
    Object limited = invoke(findIterableClass.getMethod("limit", int.class), findIterable, limit);

    Class<?> mongoIterableClass = Class.forName("com.mongodb.client.MongoIterable", true, loader);
    Object cursor = invoke(mongoIterableClass.getMethod("iterator"), limited);

    Method toJsonMethod = documentClass.getMethod("toJson");
    Iterator<?> iterator = (Iterator<?>) cursor;
    List<String> results = new ArrayList<>();
    while (iterator.hasNext()) {
      Object document = iterator.next();
      results.add((String) invoke(toJsonMethod, document));
    }
    return results;
  }

  private List<String> drainStringIterable(Object mongoIterable)
      throws ReflectiveOperationException {
    Class<?> iterableIface = Class.forName("com.mongodb.client.MongoIterable", true, loader);
    Method iteratorMethod = iterableIface.getMethod("iterator");
    Object cursor = invoke(iteratorMethod, mongoIterable);

    Iterator<?> iterator = (Iterator<?>) cursor;
    List<String> result = new ArrayList<>();
    while (iterator.hasNext()) {
      result.add((String) iterator.next());
    }
    return result;
  }

  /** Unwraps InvocationTargetException so callers see the real driver-thrown cause. */
  private Object invoke(Method method, Object target, Object... args)
      throws ReflectiveOperationException {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) throw runtimeException;
      if (cause instanceof Error error) throw error;
      throw new RuntimeException(cause);
    }
  }
}
