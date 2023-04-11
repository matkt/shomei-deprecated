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
import static net.consensys.shomei.trie.StoredSparseMerkleTrie.EMPTY_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumAddress;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trielog.TrieLogAccountValue;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.util.bytes.FullBytes;
import net.consensys.shomei.worldview.ZKEvmWorldState;
import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

public class RollingTests {

  private static final Address ACCOUNT_1 = createDumAddress(36);
  private static final Address ACCOUNT_2 = createDumAddress(47);

  private static final ZkAccount ZK_ACCOUNT =
      new ZkAccount(
          ACCOUNT_1, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

  private static final ZkAccount ZK_ACCOUNT_2 =
      new ZkAccount(
          ACCOUNT_2, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

  @Test
  public void rollingForwardOneAccount() {

    ZKTrie accountStateTrieOne = ZKTrie.createInMemoryTrie();

    accountStateTrieOne.putAndProve(
        ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.getAddress(), ZK_ACCOUNT.serializeAccount());

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString("11aed727a707f2f1962e399bd4787153ba0e69b7224e8eecf4d1e4e6a8e8dafd"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardUpdatingAccount() {

    ZKTrie accountStateTrieOne = ZKTrie.createInMemoryTrie();

    MutableZkAccount accountUpdated = new MutableZkAccount(ZK_ACCOUNT);
    accountStateTrieOne.putAndProve(
        ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.getAddress(), ZK_ACCOUNT.serializeAccount());
    accountUpdated.setBalance(Wei.of(100));
    accountStateTrieOne.putAndProve(
        accountUpdated.getHkey(),
        accountUpdated.getAddress(),
        ZK_ACCOUNT.serializeAccount(),
        accountUpdated.serializeAccount());
    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString(
                "0x271a0e17054a194a6a1e227ddfa4bec3f22c55a0b061c5056a089bba1ae24ec9"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(
        ACCOUNT_1, new TrieLogAccountValue(ZK_ACCOUNT), new TrieLogAccountValue(accountUpdated));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash())
        .isEqualTo(
            Hash.fromHexString("11aed727a707f2f1962e399bd4787153ba0e69b7224e8eecf4d1e4e6a8e8dafd"));
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardUpdatingAccountWithSeveralTrieLogs() {

    ZKTrie accountStateTrieOne = ZKTrie.createInMemoryTrie();

    MutableZkAccount accountUpdated = new MutableZkAccount(ZK_ACCOUNT);
    accountStateTrieOne.putAndProve(
        ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.getAddress(), ZK_ACCOUNT.serializeAccount());
    accountUpdated.setBalance(Wei.of(100));
    accountStateTrieOne.putAndProve(
        accountUpdated.getHkey(),
        accountUpdated.getAddress(),
        ZK_ACCOUNT.serializeAccount(),
        accountUpdated.serializeAccount());
    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString(
                "0x271a0e17054a194a6a1e227ddfa4bec3f22c55a0b061c5056a089bba1ae24ec9"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(
        ACCOUNT_1, new TrieLogAccountValue(ZK_ACCOUNT), new TrieLogAccountValue(accountUpdated));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardTwoAccounts() {

    ZKTrie accountStateTrieOne = ZKTrie.createInMemoryTrie();

    accountStateTrieOne.putAndProve(
        ZK_ACCOUNT_2.getHkey(),
        ZK_ACCOUNT_2.getAddress(),
        ZK_ACCOUNT_2
            .serializeAccount()); // respect the order of hkey because they are in the same batch
    accountStateTrieOne.putAndProve(
        ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.getAddress(), ZK_ACCOUNT.serializeAccount());

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_2, null, new TrieLogAccountValue(ZK_ACCOUNT_2));
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardContractWithStorage() {
    ZKTrie accountStateTrieOne = ZKTrie.createInMemoryTrie();

    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT_2);

    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));

    final ZKTrie contractStorageTrie = ZKTrie.createInMemoryTrie();
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    // add contract
    accountStateTrieOne.putAndProve(
        contract.getHkey(),
        contract.getAddress(),
        contract
            .serializeAccount()); // respect the order of hkey because they are in the same batch
    // add simple account
    accountStateTrieOne.putAndProve(
        ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.getAddress(), ZK_ACCOUNT.serializeAccount());

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_2, null, new TrieLogAccountValue(contract));
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));
    trieLogLayer.addStorageChange(
        ACCOUNT_2,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingBackwardAccountCreation() {
    TrieLogLayer trieLog = new TrieLogLayer();
    trieLog.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // rollforward and adding an account
    zkEvmWorldState.getAccumulator().rollForward(trieLog);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isNotEqualTo(EMPTY_TRIE_ROOT);

    // rollbackward and reverting an account
    zkEvmWorldState.getAccumulator().rollBack(trieLog);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardTwoAccountsCreation() {
    TrieLogLayer trieLog = new TrieLogLayer();
    trieLog.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));
    trieLog.addAccountChange(ACCOUNT_2, null, new TrieLogAccountValue(ZK_ACCOUNT_2));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // rollforward and adding an account
    zkEvmWorldState.getAccumulator().rollForward(trieLog);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isNotEqualTo(EMPTY_TRIE_ROOT);

    // rollbackward and reverting an account
    zkEvmWorldState.getAccumulator().rollBack(trieLog);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardUpdatingAccount() {

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    MutableZkAccount account1 = new MutableZkAccount(ZK_ACCOUNT);
    account1.setBalance(Wei.of(100));
    trieLogLayer2.addAccountChange(
        ACCOUNT_1, new TrieLogAccountValue(ZK_ACCOUNT), new TrieLogAccountValue(account1));

    // roll forward account creation
    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    final Hash rootHashBeforeUpdate = zkEvmWorldState.getRootHash();

    // roll forward account update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isNotEqualTo(rootHashBeforeUpdate);

    // roll backward account update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(rootHashBeforeUpdate);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardAccountDeletion() {

    // roll forward
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(ACCOUNT_1, new TrieLogAccountValue(ZK_ACCOUNT), null);

    // roll forward account creation
    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    final Hash rootHashBeforeUpdate = zkEvmWorldState.getRootHash();

    // roll forward account deletion
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isNotEqualTo(rootHashBeforeUpdate);
    assertThat(zkEvmWorldState.getRootHash()).isNotEqualTo(EMPTY_TRIE_ROOT);

    // roll backward account update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(rootHashBeforeUpdate);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardAccountStorageDeletion() {

    // create contract with storage
    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT);
    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));
    final ZKTrie contractStorageTrie = ZKTrie.createInMemoryTrie();
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(contract.getAddress(), null, new TrieLogAccountValue(contract));
    trieLogLayer.addStorageChange(
        contract.getAddress(),
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));

    // remove slot
    final MutableZkAccount updatedContract = new MutableZkAccount(contract);
    contractStorageTrie.removeAndProve(storageKeyHash, storageKey);
    updatedContract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(
        updatedContract.getAddress(),
        new TrieLogAccountValue(contract),
        new TrieLogAccountValue(updatedContract));
    trieLogLayer2.addStorageChange(
        updatedContract.getAddress(),
        UInt256.fromBytes(storageKey.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()),
        null);

    // roll forward account creation
    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    final Hash rootHashBeforeUpdate = zkEvmWorldState.getRootHash();

    // roll forward storage update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isNotEqualTo(rootHashBeforeUpdate);

    // roll backward storage update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(rootHashBeforeUpdate);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardAccountStorageUpdate() {

    // create contract with storage
    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT);
    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));
    final ZKTrie contractStorageTrie = ZKTrie.createInMemoryTrie();
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(contract.getAddress(), null, new TrieLogAccountValue(contract));
    trieLogLayer.addStorageChange(
        contract.getAddress(),
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));

    // update slot
    final MutableZkAccount updatedContract = new MutableZkAccount(contract);
    final FullBytes updatedStorageValue = new FullBytes(UInt256.valueOf(19));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue, updatedStorageValue);
    updatedContract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(
        updatedContract.getAddress(),
        new TrieLogAccountValue(contract),
        new TrieLogAccountValue(updatedContract));
    trieLogLayer2.addStorageChange(
        contract.getAddress(),
        UInt256.fromBytes(storageKey.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()),
        UInt256.fromBytes(updatedStorageValue.getOriginalValue()));

    // roll forward account creation
    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(EMPTY_TRIE_ROOT, Hash.EMPTY);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(null);
    final Hash rootHashBeforeUpdate = zkEvmWorldState.getRootHash();

    // roll forward storage update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isNotEqualTo(rootHashBeforeUpdate);

    // roll backward storage update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(rootHashBeforeUpdate);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(null);
    assertThat(zkEvmWorldState.getRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }
}
