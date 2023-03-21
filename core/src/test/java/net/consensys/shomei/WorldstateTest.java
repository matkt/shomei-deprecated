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

package net.consensys.shomei;

import static net.consensys.shomei.ZkAccount.EMPTY_CODE_HASH;
import static net.consensys.shomei.ZkAccount.EMPTY_KECCAK_CODE_HASH;
import static net.consensys.shomei.ZkAccount.EMPTY_STORAGE_ROOT;
import static net.consensys.zkevm.HashProvider.keccak256;
import static net.consensys.zkevm.HashProvider.mimc;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trie.ZKTrie;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("unused")
public class WorldstateTest {

  @Test
  public void testWorldStateWithAnAccount() {
    final Address address = createDumAddress(36);

    final ZkAccount zkAccount =
        new ZkAccount(
            null,
            address,
            Hash.hash(address),
            65,
            Wei.of(835),
            EMPTY_STORAGE_ROOT,
            EMPTY_KECCAK_CODE_HASH,
            EMPTY_CODE_HASH,
            0L,
            false);

    assertThat(Hash.hash(zkAccount.serializeAccount()))
        .isEqualTo(
            Hash.fromHexString("ab023fb58c760f385eb5e68491287a46a51a653f3d7609b035b82a79df93f413"));

    ZKTrie inMemoryTrie = ZKTrie.createInMemoryTrie();
    inMemoryTrie.put(
        Hash.hash(zkAccount.getWrappedAddress()), Hash.hash(zkAccount.serializeAccount()));

    assertThat(inMemoryTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("828dd273c29ec50463bd7fac90e06b04b4010b72fe880df82e299bf162046e41"));
  }

  @Test
  public void testWorldStateWithTwoAccount() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(41);

    final ZkAccount zkAccount =
        new ZkAccount(
            null,
            address,
            Hash.hash(address),
            65,
            Wei.of(835),
            EMPTY_STORAGE_ROOT,
            EMPTY_KECCAK_CODE_HASH,
            EMPTY_CODE_HASH,
            0L,
            false);
    final ZkAccount zkAccount2 =
        new ZkAccount(
            null,
            address2,
            Hash.hash(address2),
            42,
            Wei.of(354),
            EMPTY_STORAGE_ROOT,
            EMPTY_KECCAK_CODE_HASH,
            EMPTY_CODE_HASH,
            0L,
            false);

    assertThat(Hash.hash(zkAccount.serializeAccount()))
        .isEqualTo(
            Hash.fromHexString("ab023fb58c760f385eb5e68491287a46a51a653f3d7609b035b82a79df93f413"));

    ZKTrie inMemoryTrie = ZKTrie.createInMemoryTrie();
    inMemoryTrie.put(
        Hash.hash(zkAccount.getWrappedAddress()), Hash.hash(zkAccount.serializeAccount()));
    inMemoryTrie.put(
        Hash.hash(zkAccount2.getWrappedAddress()), Hash.hash(zkAccount2.serializeAccount()));

    assertThat(inMemoryTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("527e480b526f4976528ef828ef90fc07b5758bf9f4e7db77e9bf5122b5935f2a"));
  }

  @Test
  @Ignore
  public void testWorldStateWithAccountAndContract() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);

    final ZkAccount zkAccount =
        new ZkAccount(
            null,
            address,
            Hash.hash(address),
            65,
            Wei.of(835),
            EMPTY_STORAGE_ROOT,
            EMPTY_KECCAK_CODE_HASH,
            EMPTY_CODE_HASH,
            0L,
            false);
    final ZkAccount zkAccount2 =
        new ZkAccount(
            null,
            address2,
            Hash.hash(address2),
            41,
            Wei.of(15353),
            EMPTY_STORAGE_ROOT,
            Hash.wrap(keccak256(createDumDigest(15))),
            Hash.wrap(mimc(createDumDigest(15))),
            7L,
            false);

    ZKTrie inMemoryTrie = ZKTrie.createInMemoryTrie();
    inMemoryTrie.put(
        Hash.hash(zkAccount.getWrappedAddress()), Hash.hash(zkAccount.serializeAccount()));
    inMemoryTrie.put(
        Hash.hash(zkAccount2.getWrappedAddress()), Hash.hash(zkAccount2.serializeAccount()));

    assertThat(inMemoryTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("2990341de1a14455fee8441d7efca5ce7d6dcba95dc1655e1408af66f8b6dcd7"));
  }

  private Address createDumAddress(int value) {
    MutableBytes mutableBytes = MutableBytes.create(Address.SIZE);
    mutableBytes.set(0, (byte) value);
    return Address.wrap(mutableBytes);
  }

  private Bytes32 createDumDigest(int value) {
    MutableBytes32 mutableBytes = MutableBytes32.create();
    mutableBytes.set(0, (byte) value);
    return mutableBytes;
  }
}
