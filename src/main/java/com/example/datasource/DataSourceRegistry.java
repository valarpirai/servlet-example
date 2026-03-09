package com.example.datasource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Singleton registry of {@link DataSourceStrategy} implementations. Pre-registers PostgreSQL,
 * MySQL, and Snowflake; new strategies can be added via {@link #register}.
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
