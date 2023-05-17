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

import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.rpc.client.GetRawTrieLogClient;
import net.consensys.shomei.worldview.ZkEvmWorldStateEntryPoint;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.vertx.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The FullSyncDownloader class is responsible for downloading data during a full synchronization
 * process. It retrieves trie logs and associated data from a remote Besu node, stores them and
 * import them.
 */
@SuppressWarnings("FutureReturnValueIgnored")
public class FullSyncDownloader extends AbstractVerticle implements TrieLogObserver {

  private static final Logger LOG = LoggerFactory.getLogger(FullSyncDownloader.class);
  private static final int INITIAL_SYNC_BLOCK_NUMBER_RANGE = 500;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private final Queue<TrieLogIdentifier> blockQueue = new PriorityBlockingQueue<>();

  private final ZkEvmWorldStateEntryPoint zkEvmWorldStateEntryPoint;

  private final GetRawTrieLogClient getRawTrieLog;

  private CompletableFuture<Void> completableFuture;
  private Optional<Long> estimateHeadBlockNumber = Optional.empty();

  public FullSyncDownloader(
      final ZkEvmWorldStateEntryPoint zkEvmWorldStateEntryPoint,
      final GetRawTrieLogClient getRawTrieLog) {
    this.zkEvmWorldStateEntryPoint = zkEvmWorldStateEntryPoint;
    this.getRawTrieLog = getRawTrieLog;
  }

  @Override
  public void start() {
    LOG.atInfo().setMessage("Starting fullsync downloader service").log();
    completableFuture = new CompletableFuture<>();
    executor.execute(this::startFullSync);
  }

  private synchronized void startFullSync() {
    LOG.atInfo().setMessage("Fullsync downloader service started").log();
    while (!completableFuture.isDone()) {
      while (blockQueue.isEmpty() || !isNextTrieLogAvailable()) {
        try {
          // ask for trielog to Besu
          final long missingTrieLogsNumber =
              getDistanceFromNextTrieLog()
                  .filter(dist -> dist <= INITIAL_SYNC_BLOCK_NUMBER_RANGE)
                  .orElse((long) INITIAL_SYNC_BLOCK_NUMBER_RANGE);
          final long startBlockNumber = zkEvmWorldStateEntryPoint.getCurrentBlockNumber() + 1;
          final long endBlockNumber =
              zkEvmWorldStateEntryPoint.getCurrentBlockNumber() + missingTrieLogsNumber;
          getRawTrieLog
              .getTrieLog(startBlockNumber, endBlockNumber)
              .whenComplete(
                  (trieLogIdentifiers, throwable) -> {
                    if (throwable == null) {
                      addTrieLogs(trieLogIdentifiers);
                    }
                  });
          wait(TimeUnit.SECONDS.toMillis(30)); // waiting for the next trielog to be retrieved
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      importTrieLog();
    }
  }

  public void importTrieLog() {
    if (isNextTrieLogAvailable()) {
      final TrieLogObserver.TrieLogIdentifier trieLogId = blockQueue.poll();
      try {
        final boolean tooFarFromTheHead = isTooFarFromTheHead();
        zkEvmWorldStateEntryPoint.importBlock(
            Objects.requireNonNull(trieLogId), !tooFarFromTheHead);
        if (zkEvmWorldStateEntryPoint
            .getCurrentBlockHash()
            .equals(Objects.requireNonNull(trieLogId.blockHash()))) {
          if (tooFarFromTheHead) {
            if (trieLogId.blockNumber() % INITIAL_SYNC_BLOCK_NUMBER_RANGE == 0) {
              LOG.atInfo()
                  .setMessage("Block import progress: {}:{}")
                  .addArgument(trieLogId.blockNumber())
                  .addArgument(trieLogId.blockHash())
                  .log();
            }
          } else {
            LOG.atInfo()
                .setMessage("Imported block {} ({})")
                .addArgument(trieLogId.blockNumber())
                .addArgument(trieLogId.blockHash())
                .log();
          }
        } else {
          throw new RuntimeException(
              "failed to import block %d".formatted(trieLogId.blockNumber()));
        }
      } catch (Exception e) {
        LOG.atError()
            .setMessage("Exception during import block {} ({}) : {}")
            .addArgument(trieLogId.blockNumber())
            .addArgument(trieLogId.blockHash())
            .addArgument(e.getMessage())
            .log();
      }
    } else if (!blockQueue.isEmpty()) {
      blockQueue.remove(); // remove deprecated trielog
    }
  }

  @Override
  public void stop() throws Exception {
    LOG.atInfo().setMessage("Fullsync downloader service stopped").log();
    completableFuture.complete(null);
    super.stop();
  }

  public long getEstimateDistanceFromTheHead() {
    return estimateHeadBlockNumber.orElse(-1L) - zkEvmWorldStateEntryPoint.getCurrentBlockNumber();
  }

  public boolean isTooFarFromTheHead() {
    return getEstimateHeadBlockNumber().isEmpty()
        || getEstimateDistanceFromTheHead() > INITIAL_SYNC_BLOCK_NUMBER_RANGE;
  }

  public boolean isNextTrieLogAvailable() {
    return getDistanceFromNextTrieLog().orElse(-1L) == 1;
  }

  public Optional<Long> getDistanceFromNextTrieLog() {
    return blockQueue.isEmpty()
        ? Optional.empty()
        : Optional.of(
            blockQueue.element().blockNumber() - zkEvmWorldStateEntryPoint.getCurrentBlockNumber());
  }

  public Optional<Long> getEstimateHeadBlockNumber() {
    return estimateHeadBlockNumber;
  }

  @Override
  public synchronized void onTrieLogsReceived(
      final List<TrieLogObserver.TrieLogIdentifier> trieLogIds) {
    // we can estimate head number when besu is pushing trielog
    estimateHeadBlockNumber =
        trieLogIds.stream()
            .max(Comparator.comparingLong(TrieLogIdentifier::blockNumber))
            .map(TrieLogIdentifier::blockNumber);
    if (!isTooFarFromTheHead()) { // not save trielog sent by besu if we are too far from head
      addTrieLogs(trieLogIds);
    }
  }

  public synchronized void addTrieLogs(final List<TrieLogObserver.TrieLogIdentifier> trieLogIds) {
    trieLogIds.forEach(
        trieLogIdentifier -> {
          LOG.atDebug().setMessage("received trie log {} ").addArgument(trieLogIdentifier).log();
          blockQueue.offer(trieLogIdentifier);
          notifyAll();
        });
  }
}
