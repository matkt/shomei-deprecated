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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

  private final EvictingPriorityBlockingQueue<TrieLogIdentifier> blockQueue =
      new EvictingPriorityBlockingQueue<>(INITIAL_SYNC_BLOCK_NUMBER_RANGE * 2);

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

  private void startFullSync() {
    LOG.atInfo().setMessage("Fullsync downloader service started").log();
    while (!completableFuture.isDone()) {
      waitingNewElementAvailable();
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

  /**
   * Estimates the distance between the local head of a Shomei and the head of the network thanks to
   * the last trielog received from Besu.
   *
   * @return The estimated distance.
   */
  public long getEstimateDistanceFromTheHead() {
    return estimateHeadBlockNumber.orElse(-1L) - zkEvmWorldStateEntryPoint.getCurrentBlockNumber();
  }

  /**
   * Checks if the current local head of shomei is too far from the head of the network
   *
   * @return `true` if the current block is too far from the head, `false` otherwise.
   */
  public boolean isTooFarFromTheHead() {
    return getEstimateHeadBlockNumber().isEmpty()
        || getEstimateDistanceFromTheHead() > INITIAL_SYNC_BLOCK_NUMBER_RANGE;
  }

  /**
   * Checks if the trie log for the next block is available in the database.
   *
   * @return `true` if the trie log for the next block is available, `false` otherwise.
   */
  public boolean isNextTrieLogAvailable() {
    return getDistanceFromNextTrieLog().orElse(-1L) == 1;
  }

  /**
   * Calculates the distance from the current local head to the next available trie log available.
   *
   * @return An optional value representing the distance in block numbers. If the next trie log
   *     block is not available, the optional value will be empty.
   */
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
  public void onTrieLogsReceived(final List<TrieLogObserver.TrieLogIdentifier> trieLogIds) {
    // we can estimate head number when besu is pushing trielog
    estimateHeadBlockNumber =
        trieLogIds.stream()
            .max(Comparator.comparingLong(TrieLogIdentifier::blockNumber))
            .map(TrieLogIdentifier::blockNumber);
    if (!isTooFarFromTheHead()) { // not save trielog sent by besu if we are too far from head
      addTrieLogs(trieLogIds);
    }
  }

  public void addTrieLogs(final List<TrieLogObserver.TrieLogIdentifier> trieLogIds) {
    trieLogIds.forEach(
        trieLogIdentifier -> {
          LOG.atDebug().setMessage("received trie log {} ").addArgument(trieLogIdentifier).log();
          blockQueue.offer(trieLogIdentifier);
        });
    notifyNewElementAvailable(); // notify the waiting thread that new trielog is available
  }

  /**
   * Checks if the trie log for the next block is available. If the trie log is not available, it
   * indicates that some trie logs are missing. In such cases, the component will calculate the
   * distance between the nearest available trie log and the current local head of Shomei. This
   * distance represents the number of trie logs that need to be retrieved to complete the missing
   * range.
   *
   * <p>If Besu returns the trie logs, they are immediately imported. Otherwise, the component waits
   * for 30 seconds before retrying to check for the trie log availability.
   *
   * <p>For example, suppose we have the trie log for block 6, and our current local head is block
   * 3. We can determine that trie logs 4 and 5 are missing. In this case, we will immediately
   * request trie logs 4 and 5 from Besu to import them along with trie log 6.
   *
   * <p>It's important to note that this scenario occurs when the client detects missing trie logs
   * and attempts to retrieve them. However, Besu can also voluntarily send trie logs, and if it
   * sends the trie log for the next block, Shomei stops waiting and instantly imports that trie
   * log.
   */
  private void waitingNewElementAvailable() {
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
        synchronized (blockQueue) {
          blockQueue.wait(
              TimeUnit.SECONDS.toMillis(30)); // waiting for the next trielog to be retrieved
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void notifyNewElementAvailable() {
    synchronized (blockQueue) {
      blockQueue.notifyAll();
    }
  }
}
