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
import net.consensys.shomei.trie.storage.StorageProxy.Range;
import net.consensys.shomei.trie.storage.StorageProxy.Updater;

import java.util.Map;
import java.util.Optional;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
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
  public Optional<Long> getLeafIndex(final Bytes hkey) {
    return keyIndexStorage.get(hkey.toArrayUnsafe()).map(Longs::fromByteArray);
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
              Map.entry(Hash.wrap(Bytes32.wrap(left.key())), Longs.fromByteArray(left.value())),
              Optional.of(
                  Map.entry(
                      Hash.wrap(Bytes32.wrap(middle.key())), Longs.fromByteArray(middle.value()))),
              Map.entry(Hash.wrap(Bytes32.wrap(right.key())), Longs.fromByteArray(right.value())));
        } else {
          final KeyValueStorage.KeyValuePair left = iterator.next();
          final KeyValueStorage.KeyValuePair right = iterator.next();
          return new Range(
              Map.entry(Hash.wrap(Bytes32.wrap(left.key())), Longs.fromByteArray(left.value())),
              Optional.empty(),
              Map.entry(Hash.wrap(Bytes32.wrap(right.key())), Longs.fromByteArray(right.value())));
        }
      } catch (Exception ex) {
        // close error
      }
    }
    throw new RuntimeException("not found leaf index");
  }

  @Override
  public Optional<Bytes> getTrieLog(final Hash blockHash) {
    return trieLogStorage.get(blockHash.toArrayUnsafe()).map(Bytes::wrap);
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    return trieNodeStorage.get(nodeHash.toArrayUnsafe()).map(Bytes::wrap); // TODO use location
  }

  @Override
  public Updater updater() {
    return null;
  }
}
