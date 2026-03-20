package com.example.servlet.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkedOutputStreamTest {

  @Test
  void testWriteSingleByte() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();
    int chunkSize = 10;

    try (ChunkedOutputStream stream = new ChunkedOutputStream(chunkSize, chunks::add)) {
      for (int i = 0; i < 5; i++) {
        stream.write(i);
      }
    }

    assertEquals(1, chunks.size());
    assertEquals(5, chunks.get(0).getLength());
    assertEquals(5, chunks.get(0).getData().length);
  }

  @Test
  void testWriteMultipleChunks() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();
    int chunkSize = 10;

    try (ChunkedOutputStream stream = new ChunkedOutputStream(chunkSize, chunks::add)) {
      byte[] data = new byte[25]; // 3 chunks: 10, 10, 5
      for (int i = 0; i < data.length; i++) {
        data[i] = (byte) i;
      }
      stream.write(data, 0, data.length);
    }

    assertEquals(3, chunks.size());
    assertEquals(10, chunks.get(0).getLength());
    assertEquals(10, chunks.get(1).getLength());
    assertEquals(5, chunks.get(2).getLength());
  }

  @Test
  void testChunkPositions() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();
    int chunkSize = 5;

    try (ChunkedOutputStream stream = new ChunkedOutputStream(chunkSize, chunks::add)) {
      stream.write(new byte[12], 0, 12); // 3 chunks
    }

    assertEquals(0, chunks.get(0).getPosition());
    assertEquals(1, chunks.get(1).getPosition());
    assertEquals(2, chunks.get(2).getPosition());
  }

  @Test
  void testTotalBytesWritten() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();

    try (ChunkedOutputStream stream = new ChunkedOutputStream(1024, chunks::add)) {
      stream.write(new byte[500], 0, 500);
      stream.write(new byte[300], 0, 300);

      assertEquals(800, stream.getTotalBytesWritten());
    }
  }

  @Test
  void testChunkCount() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();
    int chunkSize = 100;

    try (ChunkedOutputStream stream = new ChunkedOutputStream(chunkSize, chunks::add)) {
      stream.write(new byte[250], 0, 250); // 3 chunks after close
    }

    assertEquals(3, chunks.size());
  }

  @Test
  void testFlushCreatesChunk() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();

    try (ChunkedOutputStream stream = new ChunkedOutputStream(1024, chunks::add)) {
      stream.write(new byte[100], 0, 100);
      assertEquals(0, chunks.size()); // Not flushed yet

      stream.flush();
      assertEquals(1, chunks.size()); // Now flushed
      assertEquals(100, chunks.get(0).getLength());
    }
  }

  @Test
  void testEmptyFlushDoesNothing() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();

    try (ChunkedOutputStream stream = new ChunkedOutputStream(1024, chunks::add)) {
      stream.flush();
      assertEquals(0, chunks.size());
    }
  }

  @Test
  void testWriteArrayWithOffset() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();

    try (ChunkedOutputStream stream = new ChunkedOutputStream(10, chunks::add)) {
      byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
      stream.write(data, 3, 4); // Write 3,4,5,6
    }

    assertEquals(1, chunks.size());
    assertEquals(4, chunks.get(0).getLength());
    assertArrayEquals(new byte[] {3, 4, 5, 6}, chunks.get(0).getData());
  }

  @Test
  void testLargeWrite() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();
    int chunkSize = 1024;
    int totalSize = 5000;

    try (ChunkedOutputStream stream = new ChunkedOutputStream(chunkSize, chunks::add)) {
      byte[] data = new byte[totalSize];
      stream.write(data, 0, totalSize);
    }

    assertEquals(5, chunks.size()); // 1024*4 + 904
    assertEquals(
        totalSize, chunks.stream().mapToInt(ChunkedOutputStream.ChunkData::getLength).sum());
  }

  @Test
  void testChunkDataCopiesBuffer() throws IOException {
    List<ChunkedOutputStream.ChunkData> chunks = new ArrayList<>();

    try (ChunkedOutputStream stream = new ChunkedOutputStream(5, chunks::add)) {
      stream.write(new byte[] {1, 2, 3, 4, 5}, 0, 5);
      stream.write(new byte[] {6, 7}, 0, 2);
    }

    // First chunk should still contain original data
    assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, chunks.get(0).getData());
    assertArrayEquals(new byte[] {6, 7}, chunks.get(1).getData());
  }
}
