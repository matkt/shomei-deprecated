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

import static net.consensys.shomei.trie.ZKTrie.EMPTY_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.ZK_ACCOUNT;
import static net.consensys.shomei.util.TestFixtureGenerator.ZK_ACCOUNT_2;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.InMemoryWorldStateStorage;
import net.consensys.shomei.storage.WorldStateStorageProxy;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.ShomeiTrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.util.bytes.FullBytes;
import net.consensys.shomei.worldview.ZKEvmWorldState;
import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

public class RollingBackwardTests {

  @Test
  public void rollingBackwardAccountCreation() {
    TrieLogLayer trieLog = new ShomeiTrieLogLayer();
    trieLog.addAccountChange(ZK_ACCOUNT.getAddress(), null, ZK_ACCOUNT);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // rollforward and adding an account
    zkEvmWorldState.getAccumulator().rollForward(trieLog);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isNotEqualTo(EMPTY_TRIE_ROOT);

    // rollbackward and reverting an account
    zkEvmWorldState.getAccumulator().rollBack(trieLog);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardTwoAccountsCreation() {
    TrieLogLayer trieLog = new ShomeiTrieLogLayer();
    trieLog.addAccountChange(ZK_ACCOUNT.getAddress(), null, ZK_ACCOUNT);
    trieLog.addAccountChange(ZK_ACCOUNT_2.getAddress(), null, ZK_ACCOUNT_2);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // rollforward and adding an account
    zkEvmWorldState.getAccumulator().rollForward(trieLog);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isNotEqualTo(EMPTY_TRIE_ROOT);

    // rollbackward and reverting an account
    zkEvmWorldState.getAccumulator().rollBack(trieLog);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardUpdatingAccount() {

    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    trieLogLayer.addAccountChange(ZK_ACCOUNT.getAddress(), null, ZK_ACCOUNT);

    TrieLogLayer trieLogLayer2 = new ShomeiTrieLogLayer();
    MutableZkAccount account1 = new MutableZkAccount(ZK_ACCOUNT);
    account1.setBalance(Wei.of(100));
    trieLogLayer2.addAccountChange(ZK_ACCOUNT.getAddress(), ZK_ACCOUNT, account1);

    // roll forward account creation
    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    final Hash rootHashBeforeUpdate = zkEvmWorldState.getStateRootHash();

    // roll forward account update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isNotEqualTo(rootHashBeforeUpdate);

    // roll backward account update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashBeforeUpdate);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardAccountDeletion() {

    // roll forward
    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    trieLogLayer.addAccountChange(ZK_ACCOUNT.getAddress(), null, ZK_ACCOUNT);

    TrieLogLayer trieLogLayer2 = new ShomeiTrieLogLayer();
    trieLogLayer2.addAccountChange(ZK_ACCOUNT.getAddress(), ZK_ACCOUNT, null);

    // roll forward account creation
    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    final Hash rootHashBeforeUpdate = zkEvmWorldState.getStateRootHash();

    // roll forward account deletion
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isNotEqualTo(rootHashBeforeUpdate);
    assertThat(zkEvmWorldState.getStateRootHash()).isNotEqualTo(EMPTY_TRIE_ROOT);

    // roll backward account update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashBeforeUpdate);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardAccountStorageDeletion() {

    // create contract with storage
    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT);
    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));
    final ZKTrie contractStorageTrie =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    AccountKey accountKey = trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));

    // remove slot
    final MutableZkAccount updatedContract = new MutableZkAccount(contract);
    contractStorageTrie.removeAndProve(storageKeyHash, storageKey);
    updatedContract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer2 = new ShomeiTrieLogLayer();
    AccountKey updateAccountKey =
        trieLogLayer2.addAccountChange(updatedContract.getAddress(), contract, updatedContract);
    trieLogLayer2.addStorageChange(
        updateAccountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()),
        null);

    // roll forward account creation
    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    final Hash rootHashBeforeUpdate = zkEvmWorldState.getStateRootHash();

    // roll forward storage update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isNotEqualTo(rootHashBeforeUpdate);

    // roll backward storage update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashBeforeUpdate);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }

  @Test
  public void rollingBackwardAccountStorageUpdate() {

    // create contract with storage
    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT);
    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));
    final ZKTrie contractStorageTrie =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    AccountKey contractAccountKey =
        trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        contractAccountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));

    // update slot
    final MutableZkAccount updatedContract = new MutableZkAccount(contract);
    final FullBytes updatedStorageValue = new FullBytes(UInt256.valueOf(19));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, updatedStorageValue);
    updatedContract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    TrieLogLayer trieLogLayer2 = new ShomeiTrieLogLayer();
    AccountKey updatedContractAccountKey =
        trieLogLayer2.addAccountChange(updatedContract.getAddress(), contract, updatedContract);
    trieLogLayer2.addStorageChange(
        updatedContractAccountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()),
        UInt256.fromBytes(updatedStorageValue.getOriginalValue()));

    // roll forward account creation
    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    final Hash rootHashBeforeUpdate = zkEvmWorldState.getStateRootHash();

    // roll forward storage update
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isNotEqualTo(rootHashBeforeUpdate);

    // roll backward storage update
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(rootHashBeforeUpdate);

    // roll backward account creation
    zkEvmWorldState.getAccumulator().rollBack(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
  }
}
