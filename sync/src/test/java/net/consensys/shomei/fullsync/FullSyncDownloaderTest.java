/*
 * Copyright ConsenSys Software Inc., 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package net.consensys.shomei.fullsync;

import static net.consensys.shomei.fullsync.TrieLogBlockingQueue.INITIAL_SYNC_BLOCK_NUMBER_RANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;
import net.consensys.shomei.rpc.client.GetRawTrieLogClient;
import net.consensys.shomei.storage.ZkWorldStateArchive;

import java.util.List;
import java.util.Optional;

import org.hyperledger.besu.datatypes.Hash;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FullSyncDownloaderTest {

  @Mock ZkWorldStateArchive zkWorldStateArchive;

  private FullSyncDownloader fullSyncDownloader;
  private TrieLogBlockingQueue blockingQueue;

  @Before
  public void setup() {
    blockingQueue =
        spy(
            new TrieLogBlockingQueue(
                INITIAL_SYNC_BLOCK_NUMBER_RANGE * 2,
                zkWorldStateArchive::getCurrentBlockNumber,
                Optional::empty,
                aLong -> {
                  try {
                    fullSyncDownloader.stop(); // force stop the downloader
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));
    fullSyncDownloader =
        new FullSyncDownloader(
            blockingQueue, zkWorldStateArchive, Mockito.mock(GetRawTrieLogClient.class), 2, 0);
    doThrow(new RuntimeException()).when(blockingQueue).startWaiting();
  }

  @Test
  public void testNotTriggerImportWhenTrieLogMissing() throws Exception {
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, Mockito.never())
        .importBlock(Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean());
  }

  @Test
  public void testNotTriggerTraceGenerationBeforeFirstBlockNumber() throws Exception {
    fullSyncDownloader =
        new FullSyncDownloader(
            blockingQueue, zkWorldStateArchive, Mockito.mock(GetRawTrieLogClient.class), 1, 0);
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, times(1))
        .importBlock(Mockito.any(TrieLogIdentifier.class), eq(true));
  }

  @Test
  public void testTriggerTraceGenerationAfterFirstBlockNumber() throws Exception {
    fullSyncDownloader =
        new FullSyncDownloader(
            blockingQueue, zkWorldStateArchive, Mockito.mock(GetRawTrieLogClient.class), 2, 0);
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, times(1))
        .importBlock(Mockito.any(TrieLogIdentifier.class), eq(false));
  }

  @Test
  public void testTriggerImportWhenTrieLogAvailableFromTrieLogShipping() throws Exception {
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, times(1))
        .importBlock(Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean());
  }

  @Test
  public void testNotTriggerImportWhenTrieLogAvailableButAlreadyImported() throws Exception {
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(0L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, never())
        .importBlock(Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean());
  }

  @Test
  public void testNotTriggerImportWhenTrieLogTooFarFromHead() throws Exception {
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(500L, Hash.EMPTY, true));
    fullSyncDownloader.addTrieLogs(trieLogIdentifiers);
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    fullSyncDownloader.startFullSync();
    Mockito.verify(zkWorldStateArchive, never())
        .importBlock(Mockito.any(TrieLogIdentifier.class), Mockito.anyBoolean());
  }

  @Test
  public void testGetEstimateDistanceFromTheHead() {
    assertThat(fullSyncDownloader.getEstimateDistanceFromTheBesuHead()).isEqualTo(-1);
    List<TrieLogIdentifier> trieLogIdentifiers =
        List.of(new TrieLogIdentifier(500L, Hash.EMPTY, true));
    fullSyncDownloader.onNewBesuHeadReceived(trieLogIdentifiers);
    assertThat(fullSyncDownloader.getEstimateDistanceFromTheBesuHead()).isEqualTo(500L);
  }

  @Test
  public void onlyUpdateWithHigherHead() {
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).isEmpty();
    fullSyncDownloader.onNewBesuHeadReceived(
        List.of(new TrieLogIdentifier(501L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(501L);
    fullSyncDownloader.onNewBesuHeadReceived(List.of(new TrieLogIdentifier(2L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(501L);
  }

  @Test
  public void getEstimateHeadBlockNumber() {
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).isEmpty();
    fullSyncDownloader.onNewBesuHeadReceived(List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(1L);
    fullSyncDownloader.onNewBesuHeadReceived(List.of(new TrieLogIdentifier(2L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(2L);
  }

  @Test
  public void onTrieLogsReceivedUpdateEstimateHead() {
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).isEmpty();
    fullSyncDownloader.onNewBesuHeadReceived(List.of(new TrieLogIdentifier(1L, Hash.EMPTY, true)));
    assertThat(fullSyncDownloader.getEstimateBesuHeadBlockNumber()).contains(1L);
  }
}
