package com.example.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC-backed repository for the app_properties table. */
public class PropertyRepository {

  private final DbConfig dbConfig;

  public PropertyRepository(DbConfig dbConfig) {
    this.dbConfig = dbConfig;
  }

  /** Fetch the value of a single active property by its dot-notation name. */
  public Optional<String> findValueByName(String name) throws SQLException {
    String sql = "SELECT value FROM app_properties WHERE name = ? AND active = TRUE";
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.ofNullable(rs.getString("value"));
        }
        return Optional.empty();
      }
    }
  }

  /** Return all property rows ordered by name. */
  public List<AppProperty> findAll() throws SQLException {
    String sql =
        "SELECT id, name, value, type, description, active, created_at, updated_at,"
            + " created_by, updated_by FROM app_properties ORDER BY name";
    List<AppProperty> result = new ArrayList<>();
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        result.add(mapRow(rs));
      }
    }
    return result;
  }

  /** Find a single row by primary key. */
  public Optional<AppProperty> findById(long id) throws SQLException {
    String sql =
        "SELECT id, name, value, type, description, active, created_at, updated_at,"
            + " created_by, updated_by FROM app_properties WHERE id = ?";
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
      }
    }
  }

  /** Insert a new property row and return it with the generated id. */
  public AppProperty create(
      String name, String value, String type, String description, String actor)
      throws SQLException {
    String sql =
        "INSERT INTO app_properties (name, value, type, description, created_by, updated_by)"
            + " VALUES (?, ?, ?, ?, ?, ?)"
            + " RETURNING id, name, value, type, description, active,"
            + "           created_at, updated_at, created_by, updated_by";
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, name);
      ps.setString(2, value);
      ps.setString(3, type != null ? type : "STRING");
      ps.setString(4, description);
      String by = actor != null ? actor : "system";
      ps.setString(5, by);
      ps.setString(6, by);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return mapRow(rs);
        }
        throw new SQLException("INSERT did not return a row");
      }
    }
  }

  /** Update value and description of an existing property. */
  public Optional<AppProperty> update(long id, String value, String description, String actor)
      throws SQLException {
    String sql =
        "UPDATE app_properties"
            + " SET value = ?, description = ?, updated_by = ?, updated_at = NOW()"
            + " WHERE id = ?"
            + " RETURNING id, name, value, type, description, active,"
            + "           created_at, updated_at, created_by, updated_by";
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, value);
      ps.setString(2, description);
      ps.setString(3, actor != null ? actor : "system");
      ps.setLong(4, id);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
      }
    }
  }

  /** Toggle the active flag of a property. */
  public boolean setActive(long id, boolean active, String actor) throws SQLException {
    String sql =
        "UPDATE app_properties SET active = ?, updated_by = ?, updated_at = NOW() WHERE id = ?";
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setBoolean(1, active);
      ps.setString(2, actor != null ? actor : "system");
      ps.setLong(3, id);
      return ps.executeUpdate() > 0;
    }
  }

  /** Hard-delete a property row. */
  public boolean delete(long id) throws SQLException {
    String sql = "DELETE FROM app_properties WHERE id = ?";
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, id);
      return ps.executeUpdate() > 0;
    }
  }

  private AppProperty mapRow(ResultSet rs) throws SQLException {
    Timestamp createdAt = rs.getTimestamp("created_at");
    Timestamp updatedAt = rs.getTimestamp("updated_at");
    return new AppProperty(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("value"),
        rs.getString("type"),
        rs.getString("description"),
        rs.getBoolean("active"),
        createdAt != null ? createdAt.toInstant() : null,
        updatedAt != null ? updatedAt.toInstant() : null,
        rs.getString("created_by"),
        rs.getString("updated_by"));
  }
}
