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
import static net.consensys.shomei.ZkAccount.EMPTY_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumAddress;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumDiggest;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumFullBytes;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.util.bytes.FullBytes;
import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

public class WorldStateTest {

  @Test
  public void testWorldStateWithAnAccount() {
    final Address address = createDumAddress(36);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    assertThat(HashProvider.mimc(zkAccount.serializeAccount()))
        .isEqualTo(
            Hash.fromHexString("ab023fb58c760f385eb5e68491287a46a51a653f3d7609b035b82a79df93f413"));

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.serializeAccount());

    assertThat(accountStateTrie.getSubRootHash())
        .isEqualTo(
            Hash.fromHexString("2485d2db988337c68fbc4236b4b0dc92f82cc7d2ac18836f90afe852842922cc"));

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("828dd273c29ec50463bd7fac90e06b04b4010b72fe880df82e299bf162046e41"));
  }

  @Test
  public void testWorldStateWithTwoAccount() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(41);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);
    final ZkAccount zkAccount2 =
        new ZkAccount(
            address2,
            EMPTY_KECCAK_CODE_HASH,
            EMPTY_CODE_HASH,
            0L,
            42,
            Wei.of(354),
            EMPTY_TRIE_ROOT);

    assertThat(HashProvider.mimc(zkAccount.serializeAccount()))
        .isEqualTo(
            Hash.fromHexString("ab023fb58c760f385eb5e68491287a46a51a653f3d7609b035b82a79df93f413"));

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.serializeAccount());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("527e480b526f4976528ef828ef90fc07b5758bf9f4e7db77e9bf5122b5935f2a"));
  }

  @Test
  public void testWorldStateWithAccountAndContract() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);
    final ZkAccount zkAccount2 =
        new ZkAccount(
            address2,
            createDumFullBytes(15),
            Hash.wrap(createDumDiggest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.serializeAccount());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("67c5b3445103ca9762dbaa16cd81c34beeb01b7d66d569bd27bd0558bf290f2f"));
  }

  @Test
  public void testWorldStateWithUpdateContractStorage() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            address2,
            createDumFullBytes(15),
            Hash.wrap(createDumDiggest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.serializeAccount());

    // Write something in the storage of B
    final Bytes zkAccount2PriorValue = zkAccount2.serializeAccount();
    final ZKTrie account2Storage = ZKTrie.createInMemoryTrie();
    final FullBytes slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.mimc(slotKey);
    final FullBytes slotValue = createDumFullBytes(18);
    account2Storage.putAndProve(slotKeyHash, slotKey, slotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2Storage.getTopRootHash()));
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(),
        zkAccount2.getAddress(),
        zkAccount2PriorValue,
        zkAccount2.serializeAccount());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("78af69680860107428206b1fc984696007e9ee7381f2d075c9be438d98f56bd3"));
  }

  @Test
  public void testWorldStateWithDeleteAccountAndStorage() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    final MutableZkAccount zkAccount2 =
        new MutableZkAccount(
            address2,
            createDumFullBytes(15),
            Hash.wrap(createDumDiggest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.serializeAccount());

    // Write something in the storage of B
    Bytes zkAccount2PriorValue = zkAccount2.serializeAccount();
    final ZKTrie account2StorageTrie = ZKTrie.createInMemoryTrie();
    final FullBytes slotKey = createDumFullBytes(14);
    final Hash slotKeyHash = HashProvider.mimc(slotKey);
    final FullBytes slotValue = createDumFullBytes(18);
    account2StorageTrie.putAndProve(slotKeyHash, slotKey, slotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2StorageTrie.getTopRootHash()));
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(),
        zkAccount2.getAddress(),
        zkAccount2PriorValue,
        zkAccount2.serializeAccount());

    // Delete account 1
    accountStateTrie.removeAndProve(zkAccount.getHkey(), zkAccount.getAddress());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("8f066c00f100e87a1d037ddc020538caa24b974da1f6e33249e97b265f262230"));

    // clean storage B
    zkAccount2PriorValue = zkAccount2.serializeAccount();
    account2StorageTrie.removeAndProve(slotKeyHash, slotKey);
    zkAccount2.setStorageRoot(Hash.wrap(account2StorageTrie.getTopRootHash()));
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(),
        zkAccount2.getAddress(),
        zkAccount2PriorValue,
        zkAccount2.serializeAccount());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("279c1a09b9b8fe9de73fdcf3b460d7be9662dd5b15d1d2176f6c0819d03dffcb"));

    // Write again, somewhere else
    zkAccount2PriorValue = zkAccount2.serializeAccount();
    final FullBytes newSlotKey = createDumFullBytes(11);
    final Hash newSlotKeyHash = HashProvider.mimc(newSlotKey);
    final FullBytes newSlotValue = createDumFullBytes(78);
    account2StorageTrie.putAndProve(newSlotKeyHash, newSlotKey, newSlotValue);
    zkAccount2.setStorageRoot(Hash.wrap(account2StorageTrie.getTopRootHash()));
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(),
        zkAccount2.getAddress(),
        zkAccount2PriorValue,
        zkAccount2.serializeAccount());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("cca1d6b6e90e8cd82082c7abab8491394eb2cceafad4c52e22cef7f8355fbe8f"));
  }

  @Test
  public void testAddAndDeleteAccounts() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);
    final Address address3 = createDumAddress(120);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    final ZkAccount zkAccount2 =
        new ZkAccount(
            address2,
            createDumFullBytes(15),
            Hash.wrap(createDumDiggest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    final ZkAccount zkAccount3 =
        new ZkAccount(
            address3,
            createDumFullBytes(85),
            Hash.wrap(createDumDiggest(54)),
            19L,
            48,
            Wei.of(9835),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    accountStateTrie.putAndProve(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.serializeAccount());
    accountStateTrie.removeAndProve(zkAccount.getHkey(), zkAccount.getAddress());
    accountStateTrie.putAndProve(
        zkAccount3.getHkey(), zkAccount3.getAddress(), zkAccount3.serializeAccount());

    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("ae6539fb01841d666941963fede04ff6bb8334c68ba22e90d1904532b64b29cb"));
  }

  @Test
  public void testRevertAddAccount() {
    final Address address = createDumAddress(36);
    final Address address2 = createDumAddress(47);

    final ZkAccount zkAccount =
        new ZkAccount(
            address, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

    final ZkAccount zkAccount2 =
        new ZkAccount(
            address2,
            createDumFullBytes(15),
            Hash.wrap(createDumDiggest(75)),
            7L,
            41,
            Wei.of(15353),
            EMPTY_TRIE_ROOT);

    ZKTrie accountStateTrie = ZKTrie.createInMemoryTrie();
    // add account
    accountStateTrie.putAndProve(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.serializeAccount());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("67c5b3445103ca9762dbaa16cd81c34beeb01b7d66d569bd27bd0558bf290f2f"));
    accountStateTrie.commit();
    // revert all addition
    accountStateTrie.removeAndProve(zkAccount.getHkey(), zkAccount.getAddress());
    accountStateTrie.decrementNextFreeNode();
    accountStateTrie.removeAndProve(zkAccount2.getHkey(), zkAccount2.getAddress());
    accountStateTrie.decrementNextFreeNode();
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("a222476ab21332f448fdf2496ce1b8442761140856b48300d1fce9c5395e4305"));
    accountStateTrie.commit();
    // add account again
    accountStateTrie.putAndProve(
        zkAccount.getHkey(), zkAccount.getAddress(), zkAccount.serializeAccount());
    accountStateTrie.putAndProve(
        zkAccount2.getHkey(), zkAccount2.getAddress(), zkAccount2.serializeAccount());
    assertThat(accountStateTrie.getTopRootHash())
        .isEqualTo(
            Hash.fromHexString("67c5b3445103ca9762dbaa16cd81c34beeb01b7d66d569bd27bd0558bf290f2f"));
  }
}
