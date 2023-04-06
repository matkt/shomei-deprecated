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

package net.consensys.shomei.trie;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trie.storage.InMemoryLeafIndexManager;
import net.consensys.zkevm.HashProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.Test;

public class ZKTrieTest {

  @Test
  public void testEmptyRootHash() {

    final InMemoryLeafIndexManager inMemoryLeafIndexManager = new InMemoryLeafIndexManager();
    final Map<Bytes, Bytes> keyValueStorage = new HashMap<>();

    ZKTrie zkTrie =
        ZKTrie.createTrie(
            inMemoryLeafIndexManager,
            (location, hash) -> Optional.ofNullable(keyValueStorage.get(hash)),
            (location, hash, value) -> keyValueStorage.put(hash, value));
    zkTrie.commit();

    assertThat(keyValueStorage).isNotEmpty();
    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "89835e84ad52c14bf64fc28e57cfca5cea785d81e4df6cb723aaf3b3ef56888b"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "a222476ab21332f448fdf2496ce1b8442761140856b48300d1fce9c5395e4305"));
  }

  @Test
  public void testInsertionRootHash() {

    final InMemoryLeafIndexManager inMemoryLeafIndexManager = new InMemoryLeafIndexManager();
    final Map<Bytes, Bytes> keyValueStorage = new HashMap<>();
    ZKTrie zkTrie =
        ZKTrie.createTrie(
            inMemoryLeafIndexManager,
            (location, hash) -> Optional.ofNullable(keyValueStorage.get(hash)),
            (location, hash, value) -> keyValueStorage.put(hash, value));

    final Bytes key = createDumDiggest(58);
    final Hash hkey = HashProvider.mimc(key);

    zkTrie.putAndProve(hkey, key, createDumDiggest(42));
    zkTrie.commit();
    assertThat(keyValueStorage).isNotEmpty();
    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "a6cf0354a2b53b2dbd0f5aa59361a35f131dc3e7e7eb73287f6bc3ae39f91e59"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "bd286f0deb3d838d95426a42122ff6d2a14f5e5294b249e19056b9d1e030fed5"));
  }

  @Test
  public void testInsertionAndUpdateRootHash() {

    final ZKTrie zkTrie = ZKTrie.createInMemoryTrie();

    final Bytes32 dumValue = createDumDiggest(41);
    final Bytes32 newDumValue = createDumDiggest(42);

    final Bytes key = createDumDiggest(58);
    final Hash hkey = HashProvider.mimc(key);
    zkTrie.putAndProve(hkey, key, dumValue);

    assertThat(zkTrie.getSubRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "a6cf0354a2b53b2dbd0f5aa59361a35f131dc3e7e7eb73287f6bc3ae39f91e59"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "bd286f0deb3d838d95426a42122ff6d2a14f5e5294b249e19056b9d1e030fed5"));

    // Note : the tree should be in exactly the same state as after directly
    // inserting 42
    zkTrie.putAndProve(hkey, hkey, newDumValue);

    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "a6cf0354a2b53b2dbd0f5aa59361a35f131dc3e7e7eb73287f6bc3ae39f91e59"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "bd286f0deb3d838d95426a42122ff6d2a14f5e5294b249e19056b9d1e030fed5"));
  }

  @Test
  public void testInsertionAndDeleteRootHash() {
    final ZKTrie zkTrie = ZKTrie.createInMemoryTrie();

    final Bytes key = createDumDiggest(58);
    final Hash hkey = HashProvider.mimc(key);
    zkTrie.putAndProve(hkey, key, createDumDiggest(41));

    assertThat(zkTrie.getSubRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0x6fc4afd4f9a87ed6ed40e9829ed5c58d378848248609d1b4eb60a893011a429f"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "0xe1ec2e46125e67ca779d458c531549a86f58671163fbc3332c8bb1b0a86cbb62"));

    zkTrie.removeAndProve(hkey, key);

    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "89835e84ad52c14bf64fc28e57cfca5cea785d81e4df6cb723aaf3b3ef56888b"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "a3ff34462bfb7b99a97df2ca92e3218077a799841e73598bd632b9b65283bcc7"));
  }

  public static Bytes32 createDumDiggest(int value) {
    MutableBytes32 mutableBytes = MutableBytes32.create();
    mutableBytes.set(Bytes32.SIZE - 1, (byte) value);
    return mutableBytes;
  }
}
