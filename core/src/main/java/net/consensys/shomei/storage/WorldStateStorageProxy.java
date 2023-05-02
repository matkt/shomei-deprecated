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

import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.storage.StorageProxy;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;

public class WorldStateStorageProxy implements StorageProxy {

  private final Function<Bytes, Bytes> keySerializer;

  private final Function<Bytes, Bytes> keyDeserializer;
  private final WorldStateStorage worldStateStorage;

  private final Optional<StorageProxy.Updater> updater;

  public static WorldStateStorageProxy createAccountProxy(
      final WorldStateStorage worldStateStorage, final StorageProxy.Updater updater) {
    return new WorldStateStorageProxy(Optional.empty(), worldStateStorage, updater);
  }

  public static WorldStateStorageProxy createStorageProxy(
      final long accountLeafIndex,
      final WorldStateStorage worldStateStorage,
      final StorageProxy.Updater updater) {
    return new WorldStateStorageProxy(
        Optional.of(Bytes.wrap(Longs.toByteArray(accountLeafIndex))), worldStateStorage, updater);
  }

  @VisibleForTesting
  public WorldStateStorageProxy(final WorldStateStorage worldStateStorage) {
    this(Optional.empty(), worldStateStorage, null);
  }

  @VisibleForTesting
  public WorldStateStorageProxy(
      final Optional<Bytes> keyPrefix, final WorldStateStorage worldStateStorage) {
    this(keyPrefix, worldStateStorage, null);
  }

  private WorldStateStorageProxy(
      final Optional<Bytes> keyPrefix,
      final WorldStateStorage worldStateStorage,
      final StorageProxy.Updater updater) {
    this.keySerializer =
        input -> keyPrefix.map(prefS -> Bytes.concatenate(prefS, input)).orElse(input);
    this.keyDeserializer =
        input -> keyPrefix.map(prefD -> input.slice(0, prefD.size())).orElse(input);
    this.worldStateStorage = worldStateStorage;
    this.updater = Optional.ofNullable(updater);
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    return worldStateStorage.getFlatLeaf(keySerializer.apply(hkey));
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    Range nearestKeys = worldStateStorage.getNearestKeys(keySerializer.apply(hkey));
    final Map.Entry<Bytes, FlattenedLeaf> left =
        Map.entry(
            keyDeserializer.apply(nearestKeys.getLeftNodeKey()), nearestKeys.getLeftNodeValue());
    final Optional<Map.Entry<Bytes, FlattenedLeaf>> center =
        nearestKeys
            .getCenterNode()
            .map(
                centerNode ->
                    Map.entry(keyDeserializer.apply(centerNode.getKey()), centerNode.getValue()));
    final Map.Entry<Bytes, FlattenedLeaf> right =
        Map.entry(
            keyDeserializer.apply(nearestKeys.getRightNodeKey()), nearestKeys.getRightNodeValue());
    return new Range(left, center, right);
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    return worldStateStorage.getTrieNode(
        keySerializer.apply(location), keySerializer.apply(nodeHash));
  }

  @Override
  public Updater updater() {
    return new Updater(keySerializer, updater.orElseGet(worldStateStorage::updater));
  }

  public static class Updater implements StorageProxy.Updater {

    private final Function<Bytes, Bytes> keySerializer;
    final StorageProxy.Updater updater;

    public Updater(final Function<Bytes, Bytes> keySerializer, final StorageProxy.Updater updater) {
      this.keySerializer = keySerializer;
      this.updater = updater;
    }

    @Override
    public void putFlatLeaf(final Bytes hkey, final FlattenedLeaf value) {
      this.updater.putFlatLeaf(keySerializer.apply(hkey), value);
    }

    @Override
    public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
      this.updater.putTrieNode(
          location == null ? null : keySerializer.apply(location),
          keySerializer.apply(nodeHash),
          value);
    }

    @Override
    public void removeFlatLeafValue(final Bytes hkey) {
      this.updater.removeFlatLeafValue(keySerializer.apply(hkey));
    }

    @Override
    public void commit() {}
  }
}
