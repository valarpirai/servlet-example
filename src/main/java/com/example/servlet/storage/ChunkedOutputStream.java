package com.example.servlet.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Output stream that writes data in fixed-size chunks.
 * Based on Glide's AttachmentChunkOutputStream.
 *
 * Memory-efficient: Only holds one chunk (1MB) in memory at a time.
 * For a 500MB file, max heap usage = 1MB, not 500MB.
 */
public class ChunkedOutputStream extends OutputStream {

  private final byte[] buffer;
  private final Consumer<ChunkData> chunkWriter;
  private int offset;
  private int position;
  private long totalBytesWritten;

  public ChunkedOutputStream(int chunkSize, Consumer<ChunkData> chunkWriter) {
    this.buffer = new byte[chunkSize];
    this.chunkWriter = chunkWriter;
    this.offset = 0;
    this.position = 0;
    this.totalBytesWritten = 0;
  }

  @Override
  public void write(int b) throws IOException {
    buffer[offset] = (byte) b;
    offset++;
    totalBytesWritten++;

    if (offset == buffer.length) {
      flush();
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    int remaining = len;
    int srcOffset = off;

    while (remaining > 0) {
      int available = buffer.length - offset;
      int toWrite = Math.min(remaining, available);

      System.arraycopy(b, srcOffset, buffer, offset, toWrite);
      offset += toWrite;
      srcOffset += toWrite;
      remaining -= toWrite;
      totalBytesWritten += toWrite;

      if (offset == buffer.length) {
        flush();
      }
    }
  }

  @Override
  public void flush() throws IOException {
    if (offset == 0) {
      return;
    }

    // Create chunk data (only current buffer, not entire file)
    byte[] chunkData = new byte[offset];
    System.arraycopy(buffer, 0, chunkData, 0, offset);

    ChunkData chunk = new ChunkData(position, chunkData, offset);
    chunkWriter.accept(chunk);

    // Reset buffer for reuse
    offset = 0;
    position++;
  }

  @Override
  public void close() throws IOException {
    // Flush remaining data
    if (offset > 0) {
      flush();
    }
  }

  public long getTotalBytesWritten() {
    return totalBytesWritten;
  }

  public int getChunkCount() {
    return position;
  }

  /** Represents a single chunk of data */
  public static class ChunkData {
    private final int position;
    private final byte[] data;
    private final int length;

    public ChunkData(int position, byte[] data, int length) {
      this.position = position;
      this.data = data;
      this.length = length;
    }

    public int getPosition() {
      return position;
    }

    public byte[] getData() {
      return data;
    }

    public int getLength() {
      return length;
    }
  }
}
