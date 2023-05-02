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

package net.consensys.shomei.storage;

import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames;
import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentedStorage;
import net.consensys.shomei.trie.model.FlattenedLeaf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.storage.BidirectionalIterator;
import services.storage.KeyValueStorage;
import services.storage.KeyValueStorageTransaction;

public class PersistedWorldStateStorage
    implements WorldStateStorage, WorldStateStorage.WorldStateUpdater {
  record UpdateableStorage(
      KeyValueStorage storage, AtomicReference<KeyValueStorageTransaction> txRef) {
    UpdateableStorage(KeyValueStorage storage) {
      this(storage, new AtomicReference<>(storage.startTransaction()));
    }

    void commit() {
      txRef.getAndUpdate(
          tx -> {
            tx.commit();
            return storage.startTransaction();
          });
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(PersistedWorldStateStorage.class);
  private final UpdateableStorage flatLeafStorage;
  private final UpdateableStorage trieLogStorage;
  private final UpdateableStorage trieNodeStorage;
  private final UpdateableStorage traceStorage;

  static final String ZK_STATE_ROOT_PREFIX = "zkStateRoot";

  public PersistedWorldStateStorage(final RocksDBSegmentedStorage storage) {
    this.flatLeafStorage =
        new UpdateableStorage(
            storage.getKeyValueStorageForSegment(
                SegmentNames.ZK_LEAF_INDEX.getSegmentIdentifier()));
    this.trieLogStorage =
        new UpdateableStorage(
            storage.getKeyValueStorageForSegment(SegmentNames.ZK_TRIE_LOG.getSegmentIdentifier()));
    this.trieNodeStorage =
        new UpdateableStorage(
            storage.getKeyValueStorageForSegment(SegmentNames.ZK_TRIE_NODE.getSegmentIdentifier()));
    this.traceStorage =
        new UpdateableStorage(
            storage.getKeyValueStorageForSegment(SegmentNames.ZK_TRACE.getSegmentIdentifier()));
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    return flatLeafStorage
        .txRef
        .get()
        .get(hkey.toArrayUnsafe())
        .map(Bytes::wrap)
        .map(RLP::input)
        .map(FlattenedLeaf::readFrom);
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    // call nearest key of rocksdb
    final Optional<BidirectionalIterator<KeyValueStorage.KeyValuePair>> nearestTo =
        flatLeafStorage.txRef.get().getNearestTo(hkey.toArrayUnsafe());
    if (nearestTo.isPresent()) {
      try (var iterator = nearestTo.get()) {
        final KeyValueStorage.KeyValuePair next = iterator.current();
        if (Bytes.wrap(next.key()).equals(hkey)) {
          final KeyValueStorage.KeyValuePair left = iterator.previous();
          final KeyValueStorage.KeyValuePair middle = iterator.next();
          final KeyValueStorage.KeyValuePair right = iterator.next();
          return new Range(
              Map.entry(
                  Hash.wrap(Bytes32.wrap(left.key())),
                  FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(left.value())))),
              Optional.of(
                  Map.entry(
                      Hash.wrap(Bytes32.wrap(middle.key())),
                      FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(middle.value()))))),
              Map.entry(
                  Hash.wrap(Bytes32.wrap(right.key())),
                  FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(right.value())))));
        } else {
          final KeyValueStorage.KeyValuePair left = iterator.next();
          final KeyValueStorage.KeyValuePair right = iterator.next();
          return new Range(
              Map.entry(
                  Hash.wrap(Bytes32.wrap(left.key())),
                  FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(left.value())))),
              Optional.empty(),
              Map.entry(
                  Hash.wrap(Bytes32.wrap(right.key())),
                  FlattenedLeaf.readFrom(RLP.input(Bytes.wrap(right.value())))));
        }
      } catch (Exception ex) {
        // close error
      }
    }
    throw new RuntimeException("not found leaf index");
  }

  @Override
  public Optional<Bytes> getTrieLog(final long blockNumber) {
    return trieLogStorage.txRef.get().get(Longs.toByteArray(blockNumber)).map(Bytes::wrap);
  }

  @Override
  public Optional<Bytes> getZkStateRootHash(final long blockNumber) {
    return traceStorage
        .txRef
        .get()
        .get((ZK_STATE_ROOT_PREFIX + blockNumber).getBytes(StandardCharsets.UTF_8))
        .map(Bytes::wrap);
  }

  @Override
  public Optional<Bytes> getTrace(final long blockNumber) {
    return traceStorage.txRef.get().get(Longs.toByteArray(blockNumber)).map(Bytes::wrap);
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    // TODO use location
    return trieNodeStorage.txRef.get().get(nodeHash.toArrayUnsafe()).map(Bytes::wrap);
  }

  @Override
  public Optional<Hash> getWorldStateRootHash() {
    return trieNodeStorage
        .txRef
        .get()
        .get(WORLD_STATE_ROOT_HASH_KEY)
        .map(Bytes32::wrap)
        .map(Hash::wrap);
  }

  @Override
  public Optional<Hash> getWorldStateBlockHash() {
    return trieNodeStorage.txRef.get().get(WORLD_BLOCK_HASH_KEY).map(Bytes32::wrap).map(Hash::wrap);
  }

  @Override
  public Optional<Long> getWorldStateBlockNumber() {
    return trieNodeStorage.txRef.get().get(WORLD_BLOCK_NUMBER_KEY).map(Longs::fromByteArray);
  }

  @Override
  public WorldStateUpdater updater() {
    return this;
  }

  public void close() {
    try {
      flatLeafStorage.storage.close();
      trieLogStorage.storage.close();
      trieNodeStorage.storage.close();
      traceStorage.storage.close();
    } catch (IOException e) {
      LOG.error("Failed to close storage", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void setBlockHash(final Hash blockHash) {
    trieNodeStorage.txRef.get().put(WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
  }

  @Override
  public void setBlockNumber(final long blockNumber) {
    trieNodeStorage.txRef.get().put(WORLD_BLOCK_NUMBER_KEY, Longs.toByteArray(blockNumber));
  }

  @Override
  public void saveTrieLog(final long blockNumber, final Bytes rawTrieLogLayer) {
    trieLogStorage.txRef.get().put(Longs.toByteArray(blockNumber), rawTrieLogLayer.toArrayUnsafe());
  }

  @Override
  public void saveZkStateRootHash(final long blockNumber, final Bytes stateRoot) {
    traceStorage
        .txRef
        .get()
        .put(
            (ZK_STATE_ROOT_PREFIX + blockNumber).getBytes(StandardCharsets.UTF_8),
            stateRoot.toArrayUnsafe());
  }

  @Override
  public void saveTrace(final long blockNumber, final Bytes rawTrace) {
    traceStorage.txRef.get().put(Longs.toByteArray(blockNumber), rawTrace.toArrayUnsafe());
  }

  @Override
  public void putFlatLeaf(final Bytes key, final FlattenedLeaf value) {
    flatLeafStorage
        .txRef
        .get()
        .put(key.toArrayUnsafe(), RLP.encode(value::writeTo).toArrayUnsafe());
  }

  @Override
  public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
    trieNodeStorage.txRef.get().put(nodeHash.toArrayUnsafe(), value.toArrayUnsafe());
  }

  @Override
  public void removeFlatLeafValue(final Bytes key) {
    flatLeafStorage.txRef.get().remove(key.toArrayUnsafe());
  }

  @Override
  public void commit() {
    flatLeafStorage.commit();
    trieLogStorage.commit();
    trieNodeStorage.commit();
    traceStorage.commit();
  }
}
