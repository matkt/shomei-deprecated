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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class WorldStateStorageProxyTest {

  @Test
  public void wrapCorrectlyPutKeyIndex() {
    final InMemoryWorldStateStorage inMemoryWorldStateStorage = new InMemoryWorldStateStorage();
    final WorldStateStorageProxy worldStateStorageProxy =
        new WorldStateStorageProxy(inMemoryWorldStateStorage);
    worldStateStorageProxy.updater().putKeyIndex(Bytes.of(3), 1L);
    assertThat(inMemoryWorldStateStorage.getLeafIndexStorage().get(Bytes.of(3))).isEqualTo(1L);
  }

  @Test
  public void wrapCorrectlyPutKeyIndexWithPrefix() {
    final InMemoryWorldStateStorage inMemoryWorldStateStorage = new InMemoryWorldStateStorage();
    final WorldStateStorageProxy worldStateStorageProxy =
        new WorldStateStorageProxy(Optional.of(Bytes.of(1)), inMemoryWorldStateStorage);
    worldStateStorageProxy.updater().putKeyIndex(Bytes.of(3), 1L);
    assertThat(inMemoryWorldStateStorage.getLeafIndexStorage()).doesNotContainKey(Bytes.of(3));
    assertThat(inMemoryWorldStateStorage.getLeafIndexStorage().get(Bytes.of(1, 3))).isEqualTo(1L);
  }

  @Test
  public void wrapCorrectlyRemoveKeyIndex() {
    final InMemoryWorldStateStorage inMemoryWorldStateStorage = new InMemoryWorldStateStorage();
    final WorldStateStorageProxy worldStateStorageProxy =
        new WorldStateStorageProxy(inMemoryWorldStateStorage);
    worldStateStorageProxy.updater().putKeyIndex(Bytes.of(3), 1L);
    assertThat(inMemoryWorldStateStorage.getLeafIndexStorage().get(Bytes.of(3))).isEqualTo(1L);
    worldStateStorageProxy.updater().removeKeyIndex(Bytes.of(3));
    assertThat(inMemoryWorldStateStorage.getLeafIndexStorage()).doesNotContainKey(Bytes.of(3));
  }

  @Test
  public void wrapCorrectlyRemoveKeyIndexWithPrefix() {
    final InMemoryWorldStateStorage inMemoryWorldStateStorage = new InMemoryWorldStateStorage();
    final WorldStateStorageProxy worldStateStorageProxy =
        new WorldStateStorageProxy(Optional.of(Bytes.of(1)), inMemoryWorldStateStorage);
    worldStateStorageProxy.updater().putKeyIndex(Bytes.of(3), 1L);
    assertThat(inMemoryWorldStateStorage.getLeafIndexStorage().get(Bytes.of(1, 3))).isEqualTo(1L);
    worldStateStorageProxy.updater().removeKeyIndex(Bytes.of(3));
    assertThat(inMemoryWorldStateStorage.getLeafIndexStorage()).doesNotContainKey(Bytes.of(1, 3));
  }

  @Test
  public void wrapCorrectlyPutTrieNode() {
    final InMemoryWorldStateStorage inMemoryWorldStateStorage = new InMemoryWorldStateStorage();
    final WorldStateStorageProxy worldStateStorageProxy =
        new WorldStateStorageProxy(inMemoryWorldStateStorage);
    worldStateStorageProxy.updater().putTrieNode(Bytes.of(1), Bytes.of(1), Bytes.of(3));
    assertThat(inMemoryWorldStateStorage.getTrieNode(Bytes.of(1), Bytes.of(1)))
        .contains(Bytes.of(3));
  }

  @Test
  public void wrapCorrectlyPutTrieNodeWithPrefix() {
    final InMemoryWorldStateStorage inMemoryWorldStateStorage = new InMemoryWorldStateStorage();
    final WorldStateStorageProxy worldStateStorageProxy =
        new WorldStateStorageProxy(Optional.of(Bytes.of(1)), inMemoryWorldStateStorage);
    worldStateStorageProxy.updater().putTrieNode(Bytes.of(1), Bytes.of(1), Bytes.of(3));
    assertThat(inMemoryWorldStateStorage.getTrieNode(Bytes.of(1), Bytes.of(1))).isEmpty();
    assertThat(inMemoryWorldStateStorage.getTrieNode(Bytes.of(1, 1), Bytes.of(1, 1)))
        .contains(Bytes.of(3));
  }
}
