package com.example.servlet.storage;

import com.example.config.DbConfig;
import com.example.servlet.model.Attachment;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores attachments in PostgreSQL using two tables:
 *
 * <ul>
 *   <li>{@code attachments} – metadata row per file
 *   <li>{@code attachment_chunks} – binary content split into fixed-size BYTEA rows
 * </ul>
 *
 * <p>Memory guarantee: only one chunk (default 1 MB) is held in the JVM heap at a time.
 */
public class DatabaseAttachmentStorage implements AttachmentStorage {

  private static final Logger logger = LoggerFactory.getLogger(DatabaseAttachmentStorage.class);

  private final DbConfig dbConfig;
  private final int chunkSize;

  public DatabaseAttachmentStorage(DbConfig dbConfig, int chunkSize) {
    this.dbConfig = dbConfig;
    this.chunkSize = chunkSize;
  }

  // ---- store ----

  @Override
  public Attachment store(Attachment attachment, InputStream inputStream) throws IOException {
    String id = UUID.randomUUID().toString();
    attachment.setId(id);
    attachment.setStorageType(getStorageType());
    attachment.setStoragePath("database:" + id);

    try (Connection conn = dbConfig.getConnection()) {
      conn.setAutoCommit(false);
      try {
        insertMetadataRow(conn, attachment);

        MessageDigest digest = newSha256();
        ChunkedOutputStream chunks =
            new ChunkedOutputStream(
                chunkSize,
                chunkData -> {
                  try {
                    insertChunk(conn, id, chunkData);
                    digest.update(chunkData.getData(), 0, chunkData.getLength());
                  } catch (SQLException e) {
                    throw new RuntimeException("Failed to insert chunk", e);
                  }
                });

        byte[] buf = new byte[8192];
        int n;
        while ((n = inputStream.read(buf)) != -1) {
          chunks.write(buf, 0, n);
        }
        chunks.close();

        long totalBytes = chunks.getTotalBytesWritten();
        String hash = HexFormat.of().formatHex(digest.digest());
        attachment.setSizeBytes(totalBytes);
        attachment.setHash(hash);
        updateSizeAndHash(conn, id, totalBytes, hash);

        conn.commit();
        logger.info(
            "Stored attachment {} ({} bytes, {} chunks)", id, totalBytes, chunks.getChunkCount());
        return attachment;

      } catch (Exception e) {
        conn.rollback();
        throw new IOException("Failed to store attachment in DB", e);
      }
    } catch (SQLException e) {
      throw new IOException("DB connection error during store", e);
    }
  }

  private void insertMetadataRow(Connection conn, Attachment a) throws SQLException {
    String sql =
        "INSERT INTO attachments (id, file_name, content_type, storage_type, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, a.getId());
      ps.setString(2, a.getFileName());
      ps.setString(3, a.getContentType());
      ps.setString(4, getStorageType());
      Timestamp now = Timestamp.from(a.getCreatedAt());
      ps.setTimestamp(5, now);
      ps.setTimestamp(6, now);
      ps.executeUpdate();
    }
  }

  private void insertChunk(Connection conn, String attachmentId, ChunkedOutputStream.ChunkData c)
      throws SQLException {
    String sql = "INSERT INTO attachment_chunks (attachment_id, position, data) VALUES (?, ?, ?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, attachmentId);
      ps.setInt(2, c.getPosition());
      byte[] copy = new byte[c.getLength()];
      System.arraycopy(c.getData(), 0, copy, 0, c.getLength());
      ps.setBytes(3, copy);
      ps.executeUpdate();
    }
  }

  private void updateSizeAndHash(Connection conn, String id, long sizeBytes, String hash)
      throws SQLException {
    String sql = "UPDATE attachments SET size_bytes = ?, hash = ?, updated_at = NOW() WHERE id = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, sizeBytes);
      ps.setString(2, hash);
      ps.setString(3, id);
      ps.executeUpdate();
    }
  }

  // ---- retrieve ----

  @Override
  public InputStream retrieve(String attachmentId) throws IOException {
    if (!exists(attachmentId)) {
      throw new IOException("Attachment not found: " + attachmentId);
    }
    return new DatabaseChunkedInputStream(attachmentId, dbConfig);
  }

  // ---- delete ----

  @Override
  public void delete(String attachmentId) throws IOException {
    String sql = "DELETE FROM attachments WHERE id = ?";
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, attachmentId);
      ps.executeUpdate();
      logger.info("Deleted attachment {}", attachmentId);
    } catch (SQLException e) {
      throw new IOException("Failed to delete attachment " + attachmentId, e);
    }
  }

  // ---- exists ----

  @Override
  public boolean exists(String attachmentId) {
    String sql = "SELECT 1 FROM attachments WHERE id = ?";
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, attachmentId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      logger.warn("Failed to check existence of attachment {}", attachmentId, e);
      return false;
    }
  }

  // ---- listAll ----

  @Override
  public List<Attachment> listAll() throws IOException {
    String sql =
        "SELECT id, file_name, content_type, size_bytes, hash, storage_type,"
            + " created_at, updated_at FROM attachments ORDER BY created_at DESC";
    List<Attachment> list = new ArrayList<>();
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        list.add(mapMetadataRow(rs));
      }
    } catch (SQLException e) {
      throw new IOException("Failed to list attachments", e);
    }
    return list;
  }

  // ---- loadMetadata ----

  @Override
  public Attachment loadMetadata(String attachmentId) throws IOException {
    String sql =
        "SELECT id, file_name, content_type, size_bytes, hash, storage_type,"
            + " created_at, updated_at FROM attachments WHERE id = ?";
    try (Connection conn = dbConfig.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, attachmentId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? mapMetadataRow(rs) : null;
      }
    } catch (SQLException e) {
      throw new IOException("Failed to load metadata for " + attachmentId, e);
    }
  }

  // ---- getStorageType ----

  @Override
  public String getStorageType() {
    return "database";
  }

  // ---- helpers ----

  private Attachment mapMetadataRow(ResultSet rs) throws SQLException {
    Attachment a = new Attachment();
    a.setId(rs.getString("id"));
    a.setFileName(rs.getString("file_name"));
    a.setContentType(rs.getString("content_type"));
    a.setSizeBytes(rs.getLong("size_bytes"));
    a.setHash(rs.getString("hash"));
    a.setStorageType(rs.getString("storage_type"));
    a.setStoragePath("database:" + a.getId());
    Timestamp createdAt = rs.getTimestamp("created_at");
    if (createdAt != null) a.setCreatedAt(createdAt.toInstant());
    Timestamp updatedAt = rs.getTimestamp("updated_at");
    if (updatedAt != null) a.setUpdatedAt(updatedAt.toInstant());
    return a;
  }

  private static MessageDigest newSha256() throws IOException {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-256 not available", e);
    }
  }

  // ---- Inner InputStream that cursors through attachment_chunks ----

  private static class DatabaseChunkedInputStream extends InputStream {

    private final Connection conn;
    private final PreparedStatement ps;
    private final ResultSet rs;
    private byte[] currentChunk;
    private int offset;
    private boolean exhausted;

    DatabaseChunkedInputStream(String attachmentId, DbConfig dbConfig) throws IOException {
      try {
        conn = dbConfig.getConnection();
        conn.setAutoCommit(false);
        ps =
            conn.prepareStatement(
                "SELECT data FROM attachment_chunks" + " WHERE attachment_id = ? ORDER BY position",
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY);
        ps.setFetchSize(1);
        ps.setString(1, attachmentId);
        rs = ps.executeQuery();
      } catch (SQLException e) {
        close();
        throw new IOException("Failed to open DB stream for " + attachmentId, e);
      }
    }

    @Override
    public int read() throws IOException {
      if (exhausted) return -1;
      if (currentChunk == null || offset >= currentChunk.length) {
        if (!advance()) return -1;
      }
      return currentChunk[offset++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (exhausted) return -1;
      if (currentChunk == null || offset >= currentChunk.length) {
        if (!advance()) return -1;
      }
      int available = currentChunk.length - offset;
      int toRead = Math.min(len, available);
      System.arraycopy(currentChunk, offset, b, off, toRead);
      offset += toRead;
      return toRead;
    }

    private boolean advance() throws IOException {
      try {
        if (!rs.next()) {
          exhausted = true;
          return false;
        }
        currentChunk = rs.getBytes("data");
        offset = 0;
        return true;
      } catch (SQLException e) {
        throw new IOException("Failed to advance to next chunk", e);
      }
    }

    @Override
    public void close() throws IOException {
      if (rs != null)
        try {
          rs.close();
        } catch (SQLException ignored) {
        }
      if (ps != null)
        try {
          ps.close();
        } catch (SQLException ignored) {
        }
      if (conn != null)
        try {
          conn.close();
        } catch (SQLException ignored) {
        }
    }
  }
}
