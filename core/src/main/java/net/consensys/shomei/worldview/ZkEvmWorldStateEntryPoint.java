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

package net.consensys.shomei.worldview;

import net.consensys.shomei.exception.MissingTrieLogException;
import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.storage.WorldStateStorage;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;

import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZkEvmWorldStateEntryPoint implements TrieLogObserver {

  private static final Logger LOG = LoggerFactory.getLogger(ZkEvmWorldStateEntryPoint.class);

  private final ZKEvmWorldState currentWorldState;

  private final TrieLogLayerConverter trieLogLayerConverter;

  private final WorldStateStorage worldStateStorage;

  private final Queue<Hash> blockQueue = new ConcurrentLinkedQueue<>();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private volatile boolean isProcessing = false;

  public ZkEvmWorldStateEntryPoint(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
    this.currentWorldState = new ZKEvmWorldState(worldStateStorage);
    this.trieLogLayerConverter = new TrieLogLayerConverter(worldStateStorage);
  }

  public void importBlock(final long newBlockNumber, final Hash blockHash)
      throws MissingTrieLogException {
    if (currentWorldState.getBlockNumber() <= newBlockNumber) {
      Optional<TrieLogLayer> trieLog =
          worldStateStorage
              .getTrieLog(blockHash)
              .map(RLP::input)
              .map(trieLogLayerConverter::decodeTrieLog);
      if (trieLog.isPresent()) {
        applyTrieLog(newBlockNumber, trieLog.get());
      } else {
        throw new MissingTrieLogException(newBlockNumber);
      }
    } else {
      throw new RuntimeException(
          "block parent is missing : trying to import "
              + newBlockNumber
              + " but the head is "
              + currentWorldState.getBlockNumber()
              + " ");
    }
  }

  public void applyTrieLog(final long newBlockNumber, final TrieLogLayer trieLogLayer) {
    currentWorldState.getAccumulator().rollForward(trieLogLayer);
    currentWorldState.commit(newBlockNumber, trieLogLayer.getBlockHash());
  }

  public Hash getCurrentRootHash() {
    return currentWorldState.getStateRootHash();
  }

  static int blockNumber = 0;

  @Override
  public synchronized void onTrieLogAdded(final Hash blockHash) {
    // receive trie log
    LOG.atDebug().setMessage("receive trie log {} ").addArgument(blockHash).log();
    blockQueue.offer(blockHash);
    if (!isProcessing) {
      isProcessing = true;
      executor.execute(this::processQueue);
    }
  }

  public void processQueue() {
    while (!blockQueue.isEmpty()) {
      long start = System.nanoTime();
      final Hash blockHash = blockQueue.poll();
      try {
        importBlock(blockNumber, blockHash);
        if (currentWorldState.getBlockHash().equals(Objects.requireNonNull(blockHash))) {
          LOG.atInfo()
              .setMessage("Imported block {} ({})")
              .addArgument(blockNumber)
              .addArgument(blockHash)
              .log();
          blockNumber++;
        } else {
          LOG.atError()
              .setMessage("Failed to import block {} ({})")
              .addArgument(blockNumber)
              .addArgument(blockHash)
              .log();
        }
      } catch (Exception e) {
        LOG.atError()
                .setMessage("Failed to import block {} ({})")
                .addArgument(blockNumber)
                .addArgument(blockHash)
                .log();
        throw new RuntimeException(e);
      }
      // TODO use Jackson
      gson.toJson(currentWorldState.getLastTraces());
      LOG.atInfo()
          .setMessage("Generated trace for block {} in {} ms")
          .addArgument(blockHash)
          .addArgument(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
          .log();
    }
    isProcessing = false;
  }

  Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(
              Node.class,
              (JsonSerializer<Node<Bytes>>)
                  (src, typeOfSrc, context) -> new JsonPrimitive(src.getHash().toHexString()))
          .registerTypeAdapter(
              UInt256.class,
              (JsonSerializer<UInt256>)
                  (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
          .registerTypeAdapter(
              Hash.class,
              (JsonSerializer<Hash>)
                  (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
          .registerTypeAdapter(
              Bytes.class,
              (JsonSerializer<Bytes>)
                  (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
          .create();
}
