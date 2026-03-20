package com.example.servlet.storage;

import com.example.servlet.util.PropertiesUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local filesystem storage implementation.
 * Stores files in chunks to avoid memory issues with large files.
 *
 * Directory structure:
 * attachments/
 *   {attachmentId}/
 *     metadata.json
 *     chunk_0
 *     chunk_1
 *     ...
 */
public class LocalFileSystemStorage implements AttachmentStorage {

  private static final Logger logger = LoggerFactory.getLogger(LocalFileSystemStorage.class);
  private static final int CHUNK_SIZE =
      PropertiesUtil.getInt("storage.chunkSize", 1048576); // 1MB default
  private final Path baseDirectory;

  public LocalFileSystemStorage() {
    String basePath = PropertiesUtil.getString("storage.filesystem.path", "attachments");
    this.baseDirectory = Paths.get(basePath);

    try {
      Files.createDirectories(baseDirectory);
      logger.info("Attachment storage initialized at: {}", baseDirectory.toAbsolutePath());
    } catch (IOException e) {
      throw new RuntimeException("Failed to initialize attachment storage", e);
    }
  }

  @Override
  public Attachment store(Attachment attachment, InputStream inputStream) throws IOException {
    String attachmentId = UUID.randomUUID().toString();
    attachment.setId(attachmentId);
    attachment.setStorageType(getStorageType());

    Path attachmentDir = baseDirectory.resolve(attachmentId);
    Files.createDirectories(attachmentDir);

    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-256 not available", e);
    }

    // Write chunks using ChunkedOutputStream
    try (ChunkedOutputStream chunkOutput =
        new ChunkedOutputStream(
            CHUNK_SIZE,
            chunkData -> {
              try {
                writeChunk(attachmentDir, chunkData);
                digest.update(chunkData.getData(), 0, chunkData.getLength());
              } catch (IOException e) {
                throw new RuntimeException("Failed to write chunk", e);
              }
            })) {

      // Stream from input to chunked output
      // Only CHUNK_SIZE bytes in memory at any time
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        chunkOutput.write(buffer, 0, bytesRead);
      }

      chunkOutput.flush();

      // Set metadata
      attachment.setSizeBytes(chunkOutput.getTotalBytesWritten());
      attachment.setHash(HexFormat.of().formatHex(digest.digest()));
      attachment.setStoragePath(attachmentDir.toString());

      logger.info(
          "Stored attachment {} ({} bytes, {} chunks)",
          attachmentId,
          attachment.getSizeBytes(),
          chunkOutput.getChunkCount());

      return attachment;
    } catch (RuntimeException e) {
      // Cleanup on failure
      deleteDirectory(attachmentDir.toFile());
      throw new IOException("Failed to store attachment", e.getCause());
    }
  }

  @Override
  public InputStream retrieve(String attachmentId) throws IOException {
    Path attachmentDir = baseDirectory.resolve(attachmentId);

    if (!Files.exists(attachmentDir)) {
      throw new IOException("Attachment not found: " + attachmentId);
    }

    // Return ChunkedInputStream that reads one chunk at a time
    return new ChunkedInputStream(attachmentDir, CHUNK_SIZE);
  }

  @Override
  public void delete(String attachmentId) throws IOException {
    Path attachmentDir = baseDirectory.resolve(attachmentId);

    if (Files.exists(attachmentDir)) {
      deleteDirectory(attachmentDir.toFile());
      logger.info("Deleted attachment {}", attachmentId);
    }
  }

  @Override
  public boolean exists(String attachmentId) {
    Path attachmentDir = baseDirectory.resolve(attachmentId);
    return Files.exists(attachmentDir);
  }

  @Override
  public String getStorageType() {
    return "filesystem";
  }

  private void writeChunk(Path attachmentDir, ChunkedOutputStream.ChunkData chunk)
      throws IOException {
    Path chunkFile = attachmentDir.resolve("chunk_" + chunk.getPosition());

    try (FileOutputStream fos = new FileOutputStream(chunkFile.toFile())) {
      fos.write(chunk.getData(), 0, chunk.getLength());
    }
  }

  private void deleteDirectory(File directory) {
    if (directory.exists()) {
      File[] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            deleteDirectory(file);
          } else {
            file.delete();
          }
        }
      }
      directory.delete();
    }
  }

  /** InputStream that reads chunks one at a time (memory-efficient) */
  private static class ChunkedInputStream extends InputStream {
    private final Path attachmentDir;
    private final int chunkSize;
    private int currentChunkIndex;
    private FileInputStream currentChunkStream;
    private int currentChunkBytesRead;

    public ChunkedInputStream(Path attachmentDir, int chunkSize) {
      this.attachmentDir = attachmentDir;
      this.chunkSize = chunkSize;
      this.currentChunkIndex = 0;
      this.currentChunkBytesRead = 0;
    }

    @Override
    public int read() throws IOException {
      if (currentChunkStream == null) {
        if (!openNextChunk()) {
          return -1; // EOF
        }
      }

      int b = currentChunkStream.read();

      if (b == -1) {
        // Current chunk exhausted, try next
        closeCurrentChunk();
        if (!openNextChunk()) {
          return -1; // EOF
        }
        b = currentChunkStream.read();
      }

      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (currentChunkStream == null) {
        if (!openNextChunk()) {
          return -1; // EOF
        }
      }

      int totalRead = 0;

      while (totalRead < len) {
        int bytesRead = currentChunkStream.read(b, off + totalRead, len - totalRead);

        if (bytesRead == -1) {
          // Current chunk exhausted
          closeCurrentChunk();
          if (!openNextChunk()) {
            break; // No more chunks
          }
        } else {
          totalRead += bytesRead;
        }
      }

      return totalRead > 0 ? totalRead : -1;
    }

    private boolean openNextChunk() throws IOException {
      Path chunkFile = attachmentDir.resolve("chunk_" + currentChunkIndex);

      if (!Files.exists(chunkFile)) {
        return false; // No more chunks
      }

      currentChunkStream = new FileInputStream(chunkFile.toFile());
      currentChunkIndex++;
      currentChunkBytesRead = 0;
      return true;
    }

    private void closeCurrentChunk() throws IOException {
      if (currentChunkStream != null) {
        currentChunkStream.close();
        currentChunkStream = null;
      }
    }

    @Override
    public void close() throws IOException {
      closeCurrentChunk();
    }
  }
}
