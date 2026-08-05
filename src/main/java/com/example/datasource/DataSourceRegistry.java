package com.example.datasource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Singleton registry of {@link DataSourceStrategy} implementations. Strategies are discovered via
 * {@link ServiceLoader} from {@code META-INF/services/com.example.datasource.DataSourceStrategy};
 * new strategies can be added there without touching this class. Manual registration via {@link
 * #register} is also supported and takes precedence over discovered strategies of the same type.
 */
public class DataSourceRegistry {

  private static final DataSourceRegistry INSTANCE = new DataSourceRegistry();

  private final Map<String, DataSourceStrategy> strategies = new LinkedHashMap<>();

  private DataSourceRegistry() {
    ServiceLoader.load(DataSourceStrategy.class).forEach(this::register);
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
