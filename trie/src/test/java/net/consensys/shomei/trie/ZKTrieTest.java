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

import static net.consensys.shomei.util.KeyGenerator.createDumKey;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.Node;
import org.junit.Ignore;
import org.junit.Test;

public class ZKTrieTest {

  @Test
  public void testEmptyRootHash() {

    final Map<Bytes, Bytes> keyValueStorage = new HashMap<>();
    Node<Bytes> rootHash =
        ZKTrie.initWorldState((location, hash, value) -> keyValueStorage.put(hash, value));
    ZKTrie zkTrie =
        new ZKTrie(
            rootHash.getHash(),
            key -> Optional.empty(),
            (location, hash) -> Optional.ofNullable(keyValueStorage.get(hash)));
    zkTrie.setHeadAndTail();
    zkTrie.commit((location, hash, value) -> {
      keyValueStorage.put(hash, value);
    });

    assertThat(keyValueStorage).isNotEmpty();
    assertThat(zkTrie.getRootHash())
            .isEqualTo(
                    Bytes.fromHexString(
                            "89835e84ad52c14bf64fc28e57cfca5cea785d81e4df6cb723aaf3b3ef56888b"));
    assertThat(zkTrie.getTopRootHash())
        .isEqualTo(
            Bytes.fromHexString(
                "a222476ab21332f448fdf2496ce1b8442761140856b48300d1fce9c5395e4305"));
  }

  @Test
  @Ignore //TODO need to implement put
  public void testInsertionRootHash() {

    final Map<Bytes, Bytes> keyValueStorage = new HashMap<>();
    Node<Bytes> rootHash =
            ZKTrie.initWorldState((location, hash, value) -> {
              keyValueStorage.put(hash, value);
            });
    ZKTrie zkTrie =
            new ZKTrie(
                    rootHash.getHash(),
                    key -> Optional.empty(),
                    (location, hash) -> Optional.ofNullable(keyValueStorage.get(hash)));
    zkTrie.setHeadAndTail();
    zkTrie.put(createDumKey(58), createDumKey(42));
    zkTrie.commit((location, hash, value) -> keyValueStorage.put(hash, value));
    assertThat(keyValueStorage).isNotEmpty();
    assertThat(zkTrie.getRootHash())
            .isEqualTo(
                    Bytes.fromHexString(
                            "7a7658514516cf30456fe16730d186cce37c930edc4c0a91c85298db428a3f97"));
    assertThat(zkTrie.getTopRootHash())
            .isEqualTo(
                    Bytes.fromHexString(
                            "29d47648b374fb18e31fe50dc4ca65bdea4f1cd7664075f4ef52da18138304ab"));
  }
}
