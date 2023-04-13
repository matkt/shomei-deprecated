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

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class InMemoryStorage implements StorageProxy, StorageProxy.Updater {

  private final TreeMap<Bytes, Long> leafIndexStorage = new TreeMap<>(Comparator.naturalOrder());
  private final Map<Bytes, Bytes> trieNodeStorage = new ConcurrentHashMap<>();

  public Map<Bytes, Bytes> getTrieNodeStorage() {
    return trieNodeStorage;
  }

  @Override
  public Optional<Long> getLeafIndex(final Bytes hkey) {
    return Optional.ofNullable(leafIndexStorage.get(hkey));
  }

  @Override
  public Range getNearestKeys(final Bytes hkey) {
    final Iterator<Map.Entry<Bytes, Long>> iterator = leafIndexStorage.entrySet().iterator();
    Map.Entry<Bytes, Long> next = Map.entry(Bytes32.ZERO, 0L);
    Map.Entry<Bytes, Long> left = next;
    Optional<Map.Entry<Bytes, Long>> maybeMiddle = Optional.empty();
    int compKeyResult;
    while (iterator.hasNext() && (compKeyResult = next.getKey().compareTo(hkey)) <= 0) {
      if (compKeyResult == 0) {
        maybeMiddle = Optional.of(next);
      } else {
        left = next;
      }
      next = iterator.next();
    }
    return new Range(
        Map.entry(left.getKey(), left.getValue()),
        maybeMiddle.map(middle -> Map.entry(middle.getKey(), middle.getValue())),
        Map.entry(next.getKey(), next.getValue()));
  }

  @Override
  public Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash) {
    return Optional.ofNullable(trieNodeStorage.get(nodeHash));
  }

  @Override
  public void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value) {
    trieNodeStorage.put(nodeHash, value);
  }

  @Override
  public Updater updater() {
    return this;
  }

  @Override
  public void putKeyIndex(final Bytes key, final Long index) {
    leafIndexStorage.put(key, index);
  }

  @Override
  public void removeKeyIndex(final Bytes key) {
    leafIndexStorage.remove(key);
  }
}
