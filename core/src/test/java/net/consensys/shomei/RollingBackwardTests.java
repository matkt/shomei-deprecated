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

import static net.consensys.shomei.trie.ZKTrie.DEFAULT_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountOne;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountTwo;
import static net.consensys.shomei.util.TestFixtureGenerator.getContractStorageTrie;
import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeUInt256;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.InMemoryStorageProvider;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

public class RollingBackwardTests {

  private ZkEvmWorldState inMemoryWorldState() {
    InMemoryStorageProvider inMemoryStorageProvider = new InMemoryStorageProvider();
    return new ZkEvmWorldState(
        inMemoryStorageProvider.getWorldStateStorage(), inMemoryStorageProvider.getTraceManager());
  }

  @Test
  public void rollingBackwardAccountCreation() {

    MutableZkAccount account = getAccountOne();

    TrieLogLayer trieLog = new TrieLogLayer();
    trieLog.addAccountChange(account.getAddress(), null, account);

    ZkEvmWorldState zkEvmWorldState = inMemoryWorldState();
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // rollforward and adding an account
    zkEvmWorldState.getAccumulator().rollForward(trieLog);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterAddingAccount = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterAddingAccount).isNotEqualTo(DEFAULT_TRIE_ROOT);

    // rollbackward and reverting an account
    zkEvmWorldState.getAccumulator().rollBack(trieLog);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // rollforward and adding an account again
    zkEvmWorldState.getAccumulator().rollForward(trieLog);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAddingAccount);
  }

  @Test
  public void rollingBackwardTwoAccountsCreation() {

    MutableZkAccount account = getAccountOne();
    MutableZkAccount accountTwo = getAccountTwo();

    TrieLogLayer trieLog = new TrieLogLayer();
    trieLog.addAccountChange(account.getAddress(), null, account);
    trieLog.addAccountChange(accountTwo.getAddress(), null, accountTwo);

    ZkEvmWorldState zkEvmWorldState = inMemoryWorldState();
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // rollforward and adding two accounts
    zkEvmWorldState.getAccumulator().rollForward(trieLog);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterAddingAccount = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterAddingAccount).isNotEqualTo(DEFAULT_TRIE_ROOT);

    // rollbackward and reverting two accounts
    zkEvmWorldState.getAccumulator().rollBack(trieLog);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // rollforward and adding two accounts again
    zkEvmWorldState.getAccumulator().rollForward(trieLog);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAddingAccount);
  }

  @Test
  public void rollingBackwardUpdatingAccount() {

    MutableZkAccount account = getAccountOne();

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    MutableZkAccount accountOneUpdated = new MutableZkAccount(account);
    accountOneUpdated.setBalance(Wei.of(100));
    trieLogLayer2.addAccountChange(account.getAddress(), account, accountOneUpdated);

    ZkEvmWorldState zkEvmWorldState = inMemoryWorldState();
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account creation
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterAccountCreation = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterAccountCreation).isNotEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashBeforeAfterAccountUpdate = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashBeforeAfterAccountUpdate).isNotEqualTo(DEFAULT_TRIE_ROOT);
    assertThat(rootHashBeforeAfterAccountUpdate).isNotEqualTo(rootHashAfterAccountCreation);

    // roll backward account update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountCreation);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account creation again
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountCreation);

    // roll forward account update again
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashBeforeAfterAccountUpdate);
  }

  @Test
  public void rollingBackwardAccountDeletion() {

    MutableZkAccount account = getAccountOne();

    // roll forward
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(account.getAddress(), account, null);

    ZkEvmWorldState zkEvmWorldState = inMemoryWorldState();
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account creation
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterAccountCreation = zkEvmWorldState.getStateRootHash();

    // roll forward account deletion
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterDeletion = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterDeletion).isNotEqualTo(rootHashAfterAccountCreation);
    assertThat(rootHashAfterDeletion).isNotEqualTo(DEFAULT_TRIE_ROOT);

    // roll backward account update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountCreation);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account creation
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountCreation);

    // roll forward account deletion
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterDeletion);
  }

  @Test
  public void rollingBackwardAccountStorageDeletion() {

    // create contract with storage
    MutableZkAccount contract = getAccountOne();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putWithTrace(
        storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    AccountKey accountKey = trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());

    // remove slot
    final MutableZkAccount updatedContract = new MutableZkAccount(contract);
    contractStorageTrie.removeWithTrace(storageSlotKey.slotHash(), storageSlotKey.slotKey());
    updatedContract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    AccountKey updateAccountKey =
        trieLogLayer2.addAccountChange(updatedContract.getAddress(), contract, updatedContract);
    trieLogLayer2.addStorageChange(
        updateAccountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue(),
        null);

    ZkEvmWorldState zkEvmWorldState = inMemoryWorldState();
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account creation
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterAccountCreation = zkEvmWorldState.getStateRootHash();

    // roll forward storage update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterUpdate = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterUpdate).isNotEqualTo(rootHashAfterAccountCreation);

    // roll backward storage update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountCreation);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account creation again
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountCreation);

    // roll forward storage update again
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterUpdate);
  }

  @Test
  public void rollingBackwardAccountStorageUpdate() {

    // create contract with storage
    MutableZkAccount contract = getAccountOne();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putWithTrace(
        storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    AccountKey contractAccountKey =
        trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        contractAccountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());

    // update slot
    final MutableZkAccount updatedContract = new MutableZkAccount(contract);
    final MimcSafeBytes<UInt256> updatedStorageValue = safeUInt256(UInt256.valueOf(19));
    contractStorageTrie.putWithTrace(
        storageSlotKey.slotHash(), storageSlotKey.slotKey(), updatedStorageValue);
    updatedContract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    AccountKey updatedContractAccountKey =
        trieLogLayer2.addAccountChange(updatedContract.getAddress(), contract, updatedContract);
    trieLogLayer2.addStorageChange(
        updatedContractAccountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue(),
        updatedStorageValue.getOriginalUnsafeValue());

    ZkEvmWorldState zkEvmWorldState = inMemoryWorldState();
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account creation
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterAccountCreation = zkEvmWorldState.getStateRootHash();

    // roll forward storage update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterUpdate = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterUpdate).isNotEqualTo(rootHashAfterAccountCreation);

    // roll backward storage update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountCreation);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account creation again
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountCreation);

    // roll forward storage update again
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterUpdate);
  }

  @Test
  public void rollingBackwardRecreateAccount() {

    MutableZkAccount account = getAccountOne();

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    MutableZkAccount accountOneUpdated = new MutableZkAccount(account);
    accountOneUpdated.setBalance(Wei.of(100));
    trieLogLayer2.addAccountChange(account.getAddress(), account, accountOneUpdated);

    TrieLogLayer trieLogLayer3 = new TrieLogLayer();
    trieLogLayer3.addAccountChange(
        account.getAddress(), accountOneUpdated, accountOneUpdated, true);

    TrieLogLayer trieLogLayer4 = new TrieLogLayer();
    MutableZkAccount accountOneUpdatedSecond = new MutableZkAccount(account);
    accountOneUpdatedSecond.setBalance(Wei.of(101));
    trieLogLayer4.addAccountChange(
        account.getAddress(), accountOneUpdated, accountOneUpdatedSecond);

    ZkEvmWorldState zkEvmWorldState = inMemoryWorldState();
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account creation
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterAccountCreation = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterAccountCreation).isNotEqualTo(DEFAULT_TRIE_ROOT);

    // roll forward account update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterAccountUpdate = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterAccountUpdate).isNotEqualTo(DEFAULT_TRIE_ROOT);
    assertThat(rootHashAfterAccountUpdate).isNotEqualTo(rootHashAfterAccountCreation);

    // roll forward account update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer3);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterRecreate = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterRecreate).isNotEqualTo(DEFAULT_TRIE_ROOT);
    assertThat(rootHashAfterRecreate).isNotEqualTo(rootHashAfterAccountCreation);
    assertThat(rootHashAfterRecreate).isNotEqualTo(rootHashAfterAccountUpdate);

    // roll forward account update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer4);
    zkEvmWorldState.commit(0L, null, false);
    final Hash rootHashAfterUpdateSecond = zkEvmWorldState.getStateRootHash();
    assertThat(rootHashAfterUpdateSecond).isNotEqualTo(DEFAULT_TRIE_ROOT);
    assertThat(rootHashAfterUpdateSecond).isNotEqualTo(rootHashAfterAccountCreation);
    assertThat(rootHashAfterUpdateSecond).isNotEqualTo(rootHashAfterAccountUpdate);
    assertThat(rootHashAfterUpdateSecond).isNotEqualTo(rootHashAfterRecreate);

    // roll backward account update second
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer4);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterRecreate);

    // roll backward account recreate
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer3);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountUpdate);

    // roll backward account update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashAfterAccountCreation);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(0L, null, false);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(DEFAULT_TRIE_ROOT);
  }
}
