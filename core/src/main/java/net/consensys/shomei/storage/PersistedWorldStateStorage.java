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

import java.util.Map;
import java.util.Optional;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import services.storage.BidirectionalIterator;
import services.storage.KeyValueStorage;
import services.storage.SnappableKeyValueStorage;

public class PersistedWorldStateStorage implements WorldStateStorage {

  private final SnappableKeyValueStorage keyIndexStorage;

  private final SnappableKeyValueStorage trieLogStorage;

  private final SnappableKeyValueStorage trieNodeStorage;

  public PersistedWorldStateStorage(final RocksDBSegmentedStorage storage) {
    this.keyIndexStorage =
        storage.getKeyValueStorageForSegment(SegmentNames.ZK_LEAF_INDEX.getSegmentIdentifier());
    this.trieLogStorage =
        storage.getKeyValueStorageForSegment(SegmentNames.ZK_TRIE_LOG.getSegmentIdentifier());
    this.trieNodeStorage =
        storage.getKeyValueStorageForSegment(SegmentNames.ZK_TRIE_NODE.getSegmentIdentifier());
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    return keyIndexStorage
        .get(hkey.toArrayUnsafe())
        .map(Bytes::wrap)
        .map(RLP::input)
        .map(FlattenedLeaf::readFrom);
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    // call nearest key of rocksdb
    final Optional<BidirectionalIterator<KeyValueStorage.KeyValuePair>> nearestTo =
        keyIndexStorage.getNearestTo(hkey.toArrayUnsafe());
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
    return trieLogStorage.get(Longs.toByteArray(blockNumber)).map(Bytes::wrap);
  }

  @Override
  public Optional<Bytes> getTrace(final long blockNumber) {
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    return trieNodeStorage.get(nodeHash.toArrayUnsafe()).map(Bytes::wrap); // TODO use location
  }

  @Override
  public Optional<Hash> getWorldStateRootHash() {
    return trieNodeStorage.get(WORLD_STATE_ROOT_HASH_KEY).map(Bytes32::wrap).map(Hash::wrap);
  }

  @Override
  public Optional<Hash> getWorldStateBlockHash() {
    return trieNodeStorage.get(WORLD_BLOCK_HASH_KEY).map(Bytes32::wrap).map(Hash::wrap);
  }

  @Override
  public Optional<Long> getWorldStateBlockNumber() {
    return trieNodeStorage.get(WORLD_BLOCK_NUMBER_KEY).map(Longs::fromByteArray);
  }

  @Override
  public Updater updater() {
    return null;
  }
}
