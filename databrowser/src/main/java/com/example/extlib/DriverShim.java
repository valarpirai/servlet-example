package com.example.extlib;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Wraps a JDBC Driver loaded from an external URLClassLoader so that DriverManager (which filters
 * by system classloader) can use it.
 */
public class DriverShim implements Driver {

  private final Driver delegate;

  public DriverShim(Driver delegate) {
    this.delegate = delegate;
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    return delegate.connect(url, info);
  }

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    return delegate.acceptsURL(url);
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return delegate.getPropertyInfo(url, info);
  }

  @Override
  public int getMajorVersion() {
    return delegate.getMajorVersion();
  }

  @Override
  public int getMinorVersion() {
    return delegate.getMinorVersion();
  }

  @Override
  public boolean jdbcCompliant() {
    return delegate.jdbcCompliant();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return delegate.getParentLogger();
  }
}
