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
                "21bf7e28464bf26302be46623b706eacf89b08134665b8f425d437f13218091b"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "2e7942bb21022172cbad3ffc38d1c59e998f1ab6ab52feb15345d04bbf859f14"));
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
                "1b8c9d905e0561b9ae850cf04ba39ff166f7b7306ea348b87f3f682a45cc82c1"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "186cb83c254c5f6985f7929e02cfd5f19e6c953c4f01bc2af9e0276f75184d6b"));
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
                "1b8c9d905e0561b9ae850cf04ba39ff166f7b7306ea348b87f3f682a45cc82c1"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "186cb83c254c5f6985f7929e02cfd5f19e6c953c4f01bc2af9e0276f75184d6b"));

    // Note : the tree should be in exactly the same state as after directly
    // inserting 42
    zkTrie.putAndProve(hkey, hkey, newDumValue);

    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "1b8c9d905e0561b9ae850cf04ba39ff166f7b7306ea348b87f3f682a45cc82c1"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "186cb83c254c5f6985f7929e02cfd5f19e6c953c4f01bc2af9e0276f75184d6b"));
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
                "21bf7e28464bf26302be46623b706eacf89b08134665b8f425d437f13218091b"));
    assertThat(zkTrie.getTopRootHash())
        .isNotEqualTo(
            Bytes.fromHexString(
                "2844b523cac49f6c5205b7b44065a741fd5c4ba8481c0ebee1d06ecf33f5ef40"));

    zkTrie.removeAndProve(hkey, key);

    assertThat(zkTrie.getSubRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "21bf7e28464bf26302be46623b706eacf89b08134665b8f425d437f13218091b"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "2844b523cac49f6c5205b7b44065a741fd5c4ba8481c0ebee1d06ecf33f5ef40"));
  }

  public static Bytes32 createDumDiggest(int value) {
    MutableBytes32 mutableBytes = MutableBytes32.create();
    mutableBytes.set(Bytes32.SIZE - 1, (byte) value);
    return mutableBytes;
  }
}
