package com.example.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyRepositoryTest {

  @Mock private DbConfig dbConfig;
  @Mock private Connection conn;
  @Mock private PreparedStatement ps;
  @Mock private ResultSet rs;

  private PropertyRepository repo;

  @BeforeEach
  void setUp() throws SQLException {
    when(dbConfig.getConnection()).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
    repo = new PropertyRepository(dbConfig);
  }

  // ---- findValueByName ----

  @Test
  void findValueByName_returnsValue_whenRowExists() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true);
    when(rs.getString("value")).thenReturn("8080");

    Optional<String> result = repo.findValueByName("server.port");

    assertTrue(result.isPresent());
    assertEquals("8080", result.get());
    verify(ps).setString(1, "server.port");
  }

  @Test
  void findValueByName_returnsEmpty_whenNoRow() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(false);

    Optional<String> result = repo.findValueByName("missing.key");

    assertFalse(result.isPresent());
  }

  @Test
  void findValueByName_returnsEmptyOptional_whenValueIsNull() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true);
    when(rs.getString("value")).thenReturn(null);

    Optional<String> result = repo.findValueByName("null.valued");

    assertFalse(result.isPresent());
  }

  // ---- findAll ----

  @Test
  void findAll_returnsMappedRows() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true, true, false);

    stubFullRow(rs, 1L, "a.key", "v1", "STRING", true);

    List<AppProperty> list = repo.findAll();

    assertEquals(2, list.size());
  }

  // ---- findById ----

  @Test
  void findById_returnsProperty_whenFound() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true);
    stubFullRow(rs, 5L, "my.prop", "hello", "STRING", true);

    Optional<AppProperty> result = repo.findById(5L);

    assertTrue(result.isPresent());
    assertEquals("my.prop", result.get().name());
    assertEquals("hello", result.get().value());
  }

  @Test
  void findById_returnsEmpty_whenNotFound() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(false);

    Optional<AppProperty> result = repo.findById(99L);

    assertFalse(result.isPresent());
  }

  // ---- create ----

  @Test
  void create_insertsAndReturnsRow() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true);
    stubFullRow(rs, 10L, "new.prop", "newVal", "STRING", true);

    AppProperty created = repo.create("new.prop", "newVal", "STRING", "desc", "admin");

    assertEquals(10L, created.id());
    assertEquals("new.prop", created.name());
    verify(ps).setString(1, "new.prop");
    verify(ps).setString(2, "newVal");
  }

  @Test
  void create_defaultsTypeToString_whenNull() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true);
    stubFullRow(rs, 11L, "x", "y", "STRING", true);

    repo.create("x", "y", null, null, null);

    verify(ps).setString(3, "STRING");
  }

  // ---- update ----

  @Test
  void update_returnsUpdatedRow() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true);
    stubFullRow(rs, 3L, "some.key", "newValue", "STRING", true);

    Optional<AppProperty> result = repo.update(3L, "newValue", "updated desc", "admin");

    assertTrue(result.isPresent());
    assertEquals("newValue", result.get().value());
  }

  @Test
  void update_returnsEmpty_whenIdNotFound() throws SQLException {
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(false);

    Optional<AppProperty> result = repo.update(999L, "v", "d", "admin");

    assertFalse(result.isPresent());
  }

  // ---- setActive ----

  @Test
  void setActive_returnsTrue_whenRowUpdated() throws SQLException {
    when(ps.executeUpdate()).thenReturn(1);

    boolean updated = repo.setActive(1L, false, "admin");

    assertTrue(updated);
    verify(ps).setBoolean(1, false);
  }

  @Test
  void setActive_returnsFalse_whenRowNotFound() throws SQLException {
    when(ps.executeUpdate()).thenReturn(0);

    boolean updated = repo.setActive(999L, true, "admin");

    assertFalse(updated);
  }

  // ---- delete ----

  @Test
  void delete_returnsTrue_whenRowDeleted() throws SQLException {
    when(ps.executeUpdate()).thenReturn(1);

    assertTrue(repo.delete(1L));
  }

  @Test
  void delete_returnsFalse_whenRowNotFound() throws SQLException {
    when(ps.executeUpdate()).thenReturn(0);

    assertFalse(repo.delete(999L));
  }

  // ---- helpers ----

  private void stubFullRow(
      ResultSet rs, long id, String name, String value, String type, boolean active)
      throws SQLException {
    when(rs.getLong("id")).thenReturn(id);
    when(rs.getString("name")).thenReturn(name);
    when(rs.getString("value")).thenReturn(value);
    when(rs.getString("type")).thenReturn(type);
    when(rs.getString("description")).thenReturn("desc");
    when(rs.getBoolean("active")).thenReturn(active);
    Timestamp ts = new Timestamp(System.currentTimeMillis());
    when(rs.getTimestamp("created_at")).thenReturn(ts);
    when(rs.getTimestamp("updated_at")).thenReturn(ts);
    when(rs.getString("created_by")).thenReturn("system");
    when(rs.getString("updated_by")).thenReturn("system");
  }
}
