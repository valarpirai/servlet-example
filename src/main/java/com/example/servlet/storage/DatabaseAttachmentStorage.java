package com.example.servlet.storage;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.servlet.model.Attachment;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
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

  // attachments table
  private static final Table<Record> ATTACHMENTS = table("attachments");
  private static final Field<String> ATT_ID = field("id", String.class);
  private static final Field<String> ATT_FILE_NAME = field("file_name", String.class);
  private static final Field<String> ATT_CONTENT_TYPE = field("content_type", String.class);
  private static final Field<Long> ATT_SIZE_BYTES = field("size_bytes", Long.class);
  private static final Field<String> ATT_HASH = field("hash", String.class);
  private static final Field<String> ATT_STORAGE_TYPE = field("storage_type", String.class);
  private static final Field<OffsetDateTime> ATT_CREATED_AT =
      field("created_at", OffsetDateTime.class);
  private static final Field<OffsetDateTime> ATT_UPDATED_AT =
      field("updated_at", OffsetDateTime.class);

  // attachment_chunks table
  private static final Table<Record> CHUNKS = table("attachment_chunks");
  private static final Field<String> CHUNK_ATT_ID = field("attachment_id", String.class);
  private static final Field<Integer> CHUNK_POS = field("position", Integer.class);
  private static final Field<byte[]> CHUNK_DATA = field("data", byte[].class);

  private final Database database;
  private final int chunkSize;

  public DatabaseAttachmentStorage(Database database, int chunkSize) {
    this.database = database;
    this.chunkSize = chunkSize;
  }

  // ---- store ----

  @Override
  public Attachment store(Attachment attachment, InputStream inputStream) throws IOException {
    String id = UUID.randomUUID().toString();
    attachment.setId(id);
    attachment.setStorageType(getStorageType());
    attachment.setStoragePath("database:" + id);

    try {
      return database.transact(
          dsl -> {
            insertMetadataRow(dsl, attachment);

            MessageDigest digest = newSha256();
            ChunkedOutputStream chunks =
                new ChunkedOutputStream(
                    chunkSize,
                    chunkData -> {
                      insertChunk(dsl, id, chunkData);
                      digest.update(chunkData.getData(), 0, chunkData.getLength());
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
            updateSizeAndHash(dsl, id, totalBytes, hash);

            logger.info(
                "Stored attachment {} ({} bytes, {} chunks)",
                id,
                totalBytes,
                chunks.getChunkCount());
            return attachment;
          });
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Failed to store attachment in DB", e);
    }
  }

  private void insertMetadataRow(DSLContext dsl, Attachment a) {
    OffsetDateTime now = a.getCreatedAt().atOffset(ZoneOffset.UTC);
    dsl.insertInto(ATTACHMENTS)
        .columns(
            ATT_ID,
            ATT_FILE_NAME,
            ATT_CONTENT_TYPE,
            ATT_STORAGE_TYPE,
            ATT_CREATED_AT,
            ATT_UPDATED_AT)
        .values(a.getId(), a.getFileName(), a.getContentType(), getStorageType(), now, now)
        .execute();
  }

  private void insertChunk(DSLContext dsl, String attachmentId, ChunkedOutputStream.ChunkData c) {
    byte[] copy = new byte[c.getLength()];
    System.arraycopy(c.getData(), 0, copy, 0, c.getLength());
    dsl.insertInto(CHUNKS)
        .columns(CHUNK_ATT_ID, CHUNK_POS, CHUNK_DATA)
        .values(attachmentId, c.getPosition(), copy)
        .execute();
  }

  private void updateSizeAndHash(DSLContext dsl, String id, long sizeBytes, String hash) {
    dsl.update(ATTACHMENTS)
        .set(ATT_SIZE_BYTES, sizeBytes)
        .set(ATT_HASH, hash)
        .set(ATT_UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
        .where(ATT_ID.eq(id))
        .execute();
  }

  // ---- retrieve ----

  @Override
  public InputStream retrieve(String attachmentId) throws IOException {
    if (!exists(attachmentId)) {
      throw new IOException("Attachment not found: " + attachmentId);
    }
    return new DatabaseChunkedInputStream(attachmentId, database);
  }

  // ---- delete ----

  @Override
  public void delete(String attachmentId) throws IOException {
    try {
      database.query(dsl -> dsl.delete(ATTACHMENTS).where(ATT_ID.eq(attachmentId)).execute());
      logger.info("Deleted attachment {}", attachmentId);
    } catch (Exception e) {
      throw new IOException("Failed to delete attachment " + attachmentId, e);
    }
  }

  // ---- exists ----

  @Override
  public boolean exists(String attachmentId) {
    try {
      return database.query(
          dsl ->
              dsl.selectOne().from(ATTACHMENTS).where(ATT_ID.eq(attachmentId)).fetchOne() != null);
    } catch (Exception e) {
      logger.warn("Failed to check existence of attachment {}", attachmentId, e);
      return false;
    }
  }

  // ---- listAll ----

  @Override
  public List<Attachment> listAll() throws IOException {
    try {
      return database.query(
          dsl ->
              dsl.select(
                      ATT_ID,
                      ATT_FILE_NAME,
                      ATT_CONTENT_TYPE,
                      ATT_SIZE_BYTES,
                      ATT_HASH,
                      ATT_STORAGE_TYPE,
                      ATT_CREATED_AT,
                      ATT_UPDATED_AT)
                  .from(ATTACHMENTS)
                  .orderBy(ATT_CREATED_AT.desc())
                  .fetch()
                  .map(this::mapMetadataRecord));
    } catch (Exception e) {
      throw new IOException("Failed to list attachments", e);
    }
  }

  // ---- loadMetadata ----

  @Override
  public Attachment loadMetadata(String attachmentId) throws IOException {
    try {
      return database.query(
          dsl ->
              dsl.select(
                      ATT_ID,
                      ATT_FILE_NAME,
                      ATT_CONTENT_TYPE,
                      ATT_SIZE_BYTES,
                      ATT_HASH,
                      ATT_STORAGE_TYPE,
                      ATT_CREATED_AT,
                      ATT_UPDATED_AT)
                  .from(ATTACHMENTS)
                  .where(ATT_ID.eq(attachmentId))
                  .fetchOne(this::mapMetadataRecord));
    } catch (Exception e) {
      throw new IOException("Failed to load metadata for " + attachmentId, e);
    }
  }

  // ---- getStorageType ----

  @Override
  public String getStorageType() {
    return "database";
  }

  // ---- helpers ----

  private Attachment mapMetadataRecord(Record r) {
    Attachment a = new Attachment();
    a.setId(r.get(ATT_ID));
    a.setFileName(r.get(ATT_FILE_NAME));
    a.setContentType(r.get(ATT_CONTENT_TYPE));
    a.setSizeBytes(r.get(ATT_SIZE_BYTES));
    a.setHash(r.get(ATT_HASH));
    a.setStorageType(r.get(ATT_STORAGE_TYPE));
    a.setStoragePath("database:" + r.get(ATT_ID));
    OffsetDateTime createdAt = r.get(ATT_CREATED_AT);
    if (createdAt != null) a.setCreatedAt(createdAt.toInstant());
    OffsetDateTime updatedAt = r.get(ATT_UPDATED_AT);
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

    DatabaseChunkedInputStream(String attachmentId, Database database) throws IOException {
      try {
        conn = database.openConnection();
        conn.setAutoCommit(false);
        ps =
            conn.prepareStatement(
                "SELECT data FROM attachment_chunks WHERE attachment_id = ? ORDER BY position",
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
