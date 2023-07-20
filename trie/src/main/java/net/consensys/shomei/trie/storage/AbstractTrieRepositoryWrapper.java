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

package net.consensys.shomei.trie.storage;

import net.consensys.shomei.trie.model.FlattenedLeaf;

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * This class serves as a wrapper for the TrieRepositoryWrapper, providing additional functionality
 * and abstraction for interacting with the trie data.
 */
public abstract class AbstractTrieRepositoryWrapper implements TrieStorage {

  private final Bytes prefix;

  private final TrieStorage trieStorage;

  private final TrieStorage.TrieUpdater updater;

  public AbstractTrieRepositoryWrapper(
      final Bytes prefix, final TrieStorage trieStorage, final TrieUpdater updater) {
    this.prefix = prefix;
    this.trieStorage = trieStorage;
    this.updater = updater;
  }

  @Override
  public Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey) {
    return trieStorage.getFlatLeaf(getPrefixedKey(hkey));
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    Range nearestKeys = trieStorage.getNearestKeys(getPrefixedKey(hkey));
    final Map.Entry<Bytes, FlattenedLeaf> left =
        Map.entry(removePrefix(nearestKeys.getLeftNodeKey()), nearestKeys.getLeftNodeValue());
    final Optional<Map.Entry<Bytes, FlattenedLeaf>> center =
        nearestKeys
            .getCenterNode()
            .map(centerNode -> Map.entry(removePrefix(centerNode.getKey()), centerNode.getValue()));
    final Map.Entry<Bytes, FlattenedLeaf> right =
        Map.entry(removePrefix(nearestKeys.getRightNodeKey()), nearestKeys.getRightNodeValue());
    return new Range(left, center, right);
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    return trieStorage.getTrieNode(getPrefixedKey(location), getPrefixedKey(nodeHash));
  }

  @Override
  public TrieUpdater updater() {
    return new StorageUpdater(updater);
  }

  public class StorageUpdater implements TrieUpdater {

    private final TrieUpdater updater;

    public StorageUpdater(final TrieUpdater updater) {

      this.updater = updater;
    }

    @Override
    public void putFlatLeaf(final Bytes hkey, final FlattenedLeaf value) {
      updater.putFlatLeaf(getPrefixedKey(hkey), value);
    }

    @Override
    public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
      updater.putTrieNode(getPrefixedKey(location), getPrefixedKey(nodeHash), value);
    }

    @Override
    public void removeFlatLeafValue(final Bytes hkey) {
      updater.removeFlatLeafValue(getPrefixedKey(hkey));
    }

    @Override
    public void commit() {
      updater.commit();
    }
  }

  private Bytes getPrefixedKey(final Bytes key) {
    if (key == null) {
      return null;
    }
    return Bytes.concatenate(prefix, key);
  }

  private Bytes removePrefix(final Bytes key) {
    return key.slice(0, prefix.size());
  }
}
