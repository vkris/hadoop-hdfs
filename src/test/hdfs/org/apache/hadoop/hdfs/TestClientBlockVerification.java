/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdfs;

import java.util.List;

import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.fs.Path;

import org.junit.Test;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

public class TestClientBlockVerification {

  static BlockReaderTestUtil util = null;
  static final Path TEST_FILE = new Path("/test.file");
  static final int FILE_SIZE_K = 256;
  static LocatedBlock testBlock = null;

  @BeforeClass
  public static void setupCluster() throws Exception {
    final int REPLICATION_FACTOR = 1;
    util = new BlockReaderTestUtil(REPLICATION_FACTOR);
    List<LocatedBlock> blkList = util.writeFile(TEST_FILE, FILE_SIZE_K);
    testBlock = blkList.get(0);     // Use the first block to test
  }

  /**
   * Verify that if we read an entire block, we send checksumOk
   */
  @Test
  public void testBlockVerification() throws Exception {
    BlockReader reader = spy(util.getBlockReader(testBlock, 0, FILE_SIZE_K * 1024));
    util.readAndCheckEOS(reader, FILE_SIZE_K * 1024, true);
    verify(reader).checksumOk(reader.dnSock);
    reader.close();
  }

  /**
   * Test that if we do an incomplete read, we don't call checksumOk
   */
  @Test
  public void testIncompleteRead() throws Exception {
    BlockReader reader = spy(util.getBlockReader(testBlock, 0, FILE_SIZE_K * 1024));
    util.readAndCheckEOS(reader, FILE_SIZE_K / 2 * 1024, false);

    // We asked the blockreader for the whole file, and only read
    // half of it, so no checksumOk
    verify(reader, never()).checksumOk(reader.dnSock);
    reader.close();
  }

  /**
   * Test that if we ask for a half block, and read it all, we *do*
   * call checksumOk. The DN takes care of knowing whether it was
   * the whole block or not.
   */
  @Test
  public void testCompletePartialRead() throws Exception {
    // Ask for half the file
    BlockReader reader = spy(util.getBlockReader(testBlock, 0, FILE_SIZE_K * 1024 / 2));
    // And read half the file
    util.readAndCheckEOS(reader, FILE_SIZE_K * 1024 / 2, true);
    verify(reader).checksumOk(reader.dnSock);
    reader.close();
  }

  /**
   * Test various unaligned reads to make sure that we properly
   * account even when we don't start or end on a checksum boundary
   */
  @Test
  public void testUnalignedReads() throws Exception {
    int startOffsets[] = new int[] { 0, 3, 129 };
    int lengths[] = new int[] { 30, 300, 512, 513, 1025 };
    for (int startOffset : startOffsets) {
      for (int length : lengths) {
        DFSClient.LOG.info("Testing startOffset = " + startOffset + " and " +
                           " len=" + length);
        BlockReader reader = spy(util.getBlockReader(testBlock, startOffset, length));
        util.readAndCheckEOS(reader, length, true);
        verify(reader).checksumOk(reader.dnSock);
        reader.close();
      }
    }
  }


  @AfterClass
  public static void teardownCluster() throws Exception {
    util.shutdown();
  }

}
