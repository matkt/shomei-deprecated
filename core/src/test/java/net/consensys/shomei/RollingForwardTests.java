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
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountOne;
import static net.consensys.shomei.util.TestFixtureGenerator.getAccountTwo;
import static net.consensys.shomei.util.TestFixtureGenerator.getContractStorageTrie;
import static net.consensys.shomei.util.bytes.MimcSafeBytes.safeUInt256;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.InMemoryWorldStateStorage;
import net.consensys.shomei.storage.WorldStateStorageProxy;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.shomei.worldview.ZKEvmWorldState;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.Node;
import org.junit.Before;
import org.junit.Test;

public class RollingForwardTests {

  private Gson gson;

  @Before
  public void setup() {
    gson =
        new GsonBuilder()
            .registerTypeAdapter(
                Node.class,
                (JsonSerializer<Node<Bytes>>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.getHash().toHexString()))
            .registerTypeAdapter(
                UInt256.class,
                (JsonSerializer<UInt256>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
            .registerTypeAdapter(
                Hash.class,
                (JsonSerializer<Hash>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
            .registerTypeAdapter(
                Bytes.class,
                (JsonSerializer<Bytes>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
            .create();
  }

  @Test
  public void rollingForwardZeroReadAccount() {

    MutableZkAccount account = getAccountOne();
    MutableZkAccount missingAccount = getAccountTwo();

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    final List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(
        accountStateTrieOne.readAndProve(missingAccount.getHkey(), missingAccount.getAddress()));
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            account.getHkey(), account.getAddress(), account.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString("11aed727a707f2f1962e399bd4787153ba0e69b7224e8eecf4d1e4e6a8e8dafd"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);
    trieLogLayer.addAccountChange(missingAccount.getAddress(), null, null);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
  }

  @Test
  public void rollingForwardOneAccount() {

    MutableZkAccount account = getAccountOne();

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    Trace expectedTrace =
        accountStateTrieOne.putAndProve(
            account.getHkey(), account.getAddress(), account.getEncodedBytes());

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString("11aed727a707f2f1962e399bd4787153ba0e69b7224e8eecf4d1e4e6a8e8dafd"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces()))
        .isEqualTo(gson.toJson(List.of(expectedTrace)));
  }

  @Test
  public void rollingForwardUpdatingAccount() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    MutableZkAccount account = getAccountOne();
    accountStateTrieOne.putAndProve(
        account.getHkey(), account.getAddress(), account.getEncodedBytes());

    // update account
    MutableZkAccount accountUpdated = new MutableZkAccount(account);
    accountUpdated.setBalance(Wei.of(100));

    Trace expectedTrace =
        accountStateTrieOne.putAndProve(
            accountUpdated.getHkey(),
            accountUpdated.getAddress(),
            accountUpdated.getEncodedBytes());

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());
    assertThat(topRootHash)
        .isEqualTo(
            Hash.fromHexString(
                "0x271a0e17054a194a6a1e227ddfa4bec3f22c55a0b061c5056a089bba1ae24ec9"));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(account.getAddress(), account, accountUpdated);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash())
        .isEqualTo(
            Hash.fromHexString("11aed727a707f2f1962e399bd4787153ba0e69b7224e8eecf4d1e4e6a8e8dafd"));

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces()))
        .isEqualTo(gson.toJson(List.of(expectedTrace)));
  }

  @Test
  public void rollingForwardTwoAccounts() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    List<Trace> expectedTraces = new ArrayList<>();
    MutableZkAccount account = getAccountTwo();
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            account.getHkey(),
            account.getAddress(),
            account.getEncodedBytes())); // respect the order of hkey because they are in the same
    // batch
    MutableZkAccount secondAccount = getAccountOne();
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            secondAccount.getHkey(), secondAccount.getAddress(), secondAccount.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(secondAccount.getAddress(), null, secondAccount);
    trieLogLayer.addAccountChange(account.getAddress(), null, account);

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
  }

  @Test
  public void rollingForwardContractWithStorage() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    List<Trace> expectedTraces = new ArrayList<>();

    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    expectedTraces.add(
        contractStorageTrie.putAndProve(
            storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue));
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    // add contract
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            contract.getHkey(),
            contract.getAddress(),
            contract.getEncodedBytes())); // respect the order of hkey because they are in the same

    // add simple account
    MutableZkAccount simpleAccount = getAccountOne();
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            simpleAccount.getHkey(), simpleAccount.getAddress(), simpleAccount.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    final AccountKey contractAccountKey =
        trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addAccountChange(simpleAccount.getAddress(), null, simpleAccount);
    trieLogLayer.addStorageChange(
        contractAccountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
  }

  @Test
  public void rollingForwardContractWithStorageWithReadNonZero() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    // add contract
    accountStateTrieOne.putAndProve(
        contract.getHkey(),
        contract.getAddress(),
        contract.getEncodedBytes()); // respect the order of hkey because they are in the same batch

    // read non zero slot of the contract
    List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(accountStateTrieOne.readAndProve(contract.getHkey(), contract.getAddress()));
    expectedTraces.add(
        contractStorageTrie.readAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey()));

    TrieLogLayer trieLogLayer = new TrieLogLayer();
    final AccountKey accountKey2 =
        trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey2,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());

    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(contract.getAddress(), contract, contract);
    trieLogLayer2.addStorageChange(
        accountKey2,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
  }

  @Test
  public void rollingForwardAccountSelfDestructWithStorage() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    // create contract with storage
    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    final List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(
        contractStorageTrie.readAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey()));

    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeAndProve(contract.getHkey(), contract.getAddress()));

    // recreate contract
    ZKTrie newContractStorageTrie = getContractStorageTrie(contract);
    expectedTraces.add(
        newContractStorageTrie.putAndProve(
            storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue));
    contract.setStorageRoot(Hash.wrap(newContractStorageTrie.getTopRootHash()));
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            contract.getHkey(), contract.getAddress(), contract.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // create account with the rolling
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    AccountKey accountKey = trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    accountKey = trieLogLayer2.addAccountChange(contract.getAddress(), contract, contract, true);
    trieLogLayer2.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue(),
        slotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);

    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardAccountSelfDestructWithDifferentStorageValue() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    // create contract with storage
    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(
        contractStorageTrie.readAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey()));
    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeAndProve(contract.getHkey(), contract.getAddress()));

    // recreate contract
    MutableZkAccount updatedContract = getAccountTwo();
    ZKTrie newContractStorageTrie = getContractStorageTrie(updatedContract);
    MimcSafeBytes<UInt256> newSlotValue = safeUInt256(UInt256.valueOf(13));
    expectedTraces.add(
        newContractStorageTrie.putAndProve(
            storageSlotKey.slotHash(), storageSlotKey.slotKey(), newSlotValue));
    updatedContract.setStorageRoot(Hash.wrap(newContractStorageTrie.getTopRootHash()));
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            updatedContract.getHkey(),
            updatedContract.getAddress(),
            updatedContract.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // create account with the rolling
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    AccountKey accountKey = trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    accountKey =
        trieLogLayer2.addAccountChange(contract.getAddress(), contract, updatedContract, true);
    trieLogLayer2.addStorageChange(
        accountKey,
        storageSlotKey,
        slotValue.getOriginalUnsafeValue(),
        newSlotValue.getOriginalUnsafeValue(),
        true);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);

    System.out.println(gson.toJson(expectedTraces));
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardAccountSelfDestructWithDifferentStorageKeyAndValue() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    // create contract with storage
    MutableZkAccount contract = getAccountTwo();
    StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.valueOf(14));
    MimcSafeBytes<UInt256> slotValue = safeUInt256(UInt256.valueOf(12));
    ZKTrie contractStorageTrie = getContractStorageTrie(contract);
    contractStorageTrie.putAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey(), slotValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(
        contractStorageTrie.readAndProve(storageSlotKey.slotHash(), storageSlotKey.slotKey()));
    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeAndProve(contract.getHkey(), contract.getAddress()));
    // recreate contract

    MutableZkAccount updatedContract = getAccountTwo();
    ZKTrie newContractStorageTrie = getContractStorageTrie(updatedContract);
    StorageSlotKey newStorageSlotKey = new StorageSlotKey(UInt256.valueOf(146));
    MimcSafeBytes<UInt256> newSlotValue = safeUInt256(UInt256.valueOf(13));
    expectedTraces.add(
        newContractStorageTrie.putAndProve(
            newStorageSlotKey.slotHash(), newStorageSlotKey.slotKey(), newSlotValue));
    updatedContract.setStorageRoot(Hash.wrap(newContractStorageTrie.getTopRootHash()));
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            updatedContract.getHkey(),
            updatedContract.getAddress(),
            updatedContract.getEncodedBytes()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // create account with the rolling
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    AccountKey accountKey = trieLogLayer.addAccountChange(contract.getAddress(), null, contract);
    trieLogLayer.addStorageChange(
        accountKey,
        storageSlotKey.slotKey().getOriginalUnsafeValue(),
        null,
        slotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    accountKey =
        trieLogLayer2.addAccountChange(contract.getAddress(), contract, updatedContract, true);
    trieLogLayer2.addStorageChange(
        accountKey, storageSlotKey, slotValue.getOriginalUnsafeValue(), null, true);
    trieLogLayer2.addStorageChange(
        accountKey, newStorageSlotKey, null, newSlotValue.getOriginalUnsafeValue());
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);

    System.out.println(gson.toJson(expectedTraces));
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }
}
