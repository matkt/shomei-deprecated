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
import static net.consensys.shomei.trie.ZKTrie.EMPTY_TRIE_ROOT;
import static net.consensys.shomei.util.TestFixtureGenerator.createDumAddress;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.storage.InMemoryWorldStateStorage;
import net.consensys.shomei.storage.WorldStateStorageProxy;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.ShomeiTrieLogLayer;
import net.consensys.shomei.trielog.TrieLogAccountValue;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.util.bytes.FullBytes;
import net.consensys.shomei.worldview.ZKEvmWorldState;
import net.consensys.zkevm.HashProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.Node;
import org.junit.Before;
import org.junit.Test;

public class RollingProofTests {

  private static final Address ACCOUNT_1 = createDumAddress(36);
  private static final Address ACCOUNT_2 = createDumAddress(47);

  private static final ZkAccount ZK_ACCOUNT =
      new ZkAccount(
          ACCOUNT_1, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

  private static final ZkAccount ZK_ACCOUNT_2 =
      new ZkAccount(
          ACCOUNT_2, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

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
  public void rollingForwardOneAccount() {
    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    Trace expectedTrace =
        accountStateTrieOne.putAndProve(
            ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.getAddress(), ZK_ACCOUNT.serializeAccount());

    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces()))
        .isEqualTo(gson.toJson(List.of(expectedTrace)));
  }

  @Test
  public void rollingForwardUpdatingAccount() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    MutableZkAccount accountUpdated = new MutableZkAccount(ZK_ACCOUNT);
    accountStateTrieOne.putAndProve(
        ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.getAddress(), ZK_ACCOUNT.serializeAccount());
    accountUpdated.setBalance(Wei.of(100));

    Trace expectedTrace =
        accountStateTrieOne.putAndProve(
            accountUpdated.getHkey(),
            accountUpdated.getAddress(),
            ZK_ACCOUNT.serializeAccount(),
            accountUpdated.serializeAccount());

    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    TrieLogLayer trieLogLayer2 = new ShomeiTrieLogLayer();
    trieLogLayer2.addAccountChange(
        ACCOUNT_1, new TrieLogAccountValue(ZK_ACCOUNT), new TrieLogAccountValue(accountUpdated));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces()))
        .isEqualTo(gson.toJson(List.of(expectedTrace)));
  }

  @Test
  public void rollingForwardTwoAccounts() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            ZK_ACCOUNT_2.getHkey(),
            ZK_ACCOUNT_2.getAddress(),
            ZK_ACCOUNT_2
                .serializeAccount())); // respect the order of hkey because they are in the same
    // batch
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.getAddress(), ZK_ACCOUNT.serializeAccount()));

    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    trieLogLayer.addAccountChange(ACCOUNT_2, null, new TrieLogAccountValue(ZK_ACCOUNT_2));
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
  }

  @Test
  public void rollingForwardContractWithStorage() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    List<Trace> expectedTraces = new ArrayList<>();
    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT_2);

    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));

    final ZKTrie contractStorageTrie =
        ZKTrie.createTrie(
            new WorldStateStorageProxy(
                Optional.of(contract.getAddress()), new InMemoryWorldStateStorage()));
    expectedTraces.add(contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue));
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    // add contract
    expectedTraces.add(
        0,
        accountStateTrieOne.putAndProve(
            contract.getHkey(),
            contract.getAddress(),
            contract.serializeAccount())); // respect the order of hkey because they are in the same
    // batch
    // add simple account
    expectedTraces.add(
        accountStateTrieOne.putAndProve(
            ZK_ACCOUNT.getHkey(), ZK_ACCOUNT.getAddress(), ZK_ACCOUNT.serializeAccount()));

    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    final AccountKey accountKey2 =
        trieLogLayer.addAccountChange(ACCOUNT_2, null, new TrieLogAccountValue(contract));
    trieLogLayer.addAccountChange(ACCOUNT_1, null, new TrieLogAccountValue(ZK_ACCOUNT));
    trieLogLayer.addStorageChange(
        accountKey2,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);
    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
  }

  @Test
  public void rollingForwardContractWithStorageWithReadNonZero() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT_2);

    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));

    final ZKTrie contractStorageTrie =
        ZKTrie.createTrie(
            new WorldStateStorageProxy(
                Optional.of(contract.getAddress()), new InMemoryWorldStateStorage()));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    // add contract
    accountStateTrieOne.putAndProve(
        contract.getHkey(),
        contract.getAddress(),
        contract
            .serializeAccount()); // respect the order of hkey because they are in the same batch

    // read non zero slot of the contract
    List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(
        accountStateTrieOne.readAndProve(
            contract.getHkey(), contract.getAddress(), contract.serializeAccount()));
    expectedTraces.add(contractStorageTrie.readAndProve(storageKeyHash, storageKey, storageValue));

    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    final AccountKey accountKey2 =
        trieLogLayer.addAccountChange(
            contract.getAddress(), null, new TrieLogAccountValue(contract));
    trieLogLayer.addStorageChange(
        accountKey2,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));

    TrieLogLayer trieLogLayer2 = new ShomeiTrieLogLayer();
    trieLogLayer2.addAccountChange(
        contract.getAddress(),
        new TrieLogAccountValue(contract),
        new TrieLogAccountValue(contract));
    trieLogLayer2.addStorageChange(
        accountKey2,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()));

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
    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT_2);

    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));

    final ZKTrie contractStorageTrie =
        ZKTrie.createTrie(
            new WorldStateStorageProxy(
                Optional.of(contract.getAddress()), new InMemoryWorldStateStorage()));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));
    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.serializeAccount());

    final List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(contractStorageTrie.readAndProve(storageKeyHash, storageKey, storageValue));
    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeAndProve(contract.getHkey(), contract.getAddress()));
    // recreate contract
    final ZKTrie contractStorageTrieNew =
        ZKTrie.createTrie(
            new WorldStateStorageProxy(
                Optional.of(contract.getAddress()), new InMemoryWorldStateStorage()));
    expectedTraces.add(
        contractStorageTrieNew.putAndProve(storageKeyHash, storageKey, storageValue));
    contract.setStorageRoot(Hash.wrap(contractStorageTrieNew.getTopRootHash()));
    expectedTraces.add(
        expectedTraces.size() - 1,
        accountStateTrieOne.putAndProve(
            contract.getHkey(), contract.getAddress(), contract.serializeAccount()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // create account with the rolling
    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    AccountKey accountKey =
        trieLogLayer.addAccountChange(ACCOUNT_2, null, new TrieLogAccountValue(contract));
    trieLogLayer.addStorageChange(
        accountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new ShomeiTrieLogLayer();
    accountKey =
        trieLogLayer2.addAccountChange(
            ACCOUNT_2, new TrieLogAccountValue(contract), new TrieLogAccountValue(contract), true);
    trieLogLayer2.addStorageChange(
        accountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()));
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
    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT_2);

    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));

    final ZKTrie contractStorageTrie =
        ZKTrie.createTrie(
            new WorldStateStorageProxy(
                Optional.of(contract.getAddress()), new InMemoryWorldStateStorage()));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));
    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.serializeAccount());

    final List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(contractStorageTrie.readAndProve(storageKeyHash, storageKey, storageValue));
    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeAndProve(contract.getHkey(), contract.getAddress()));
    // recreate contract
    final MutableZkAccount contractNew = new MutableZkAccount(ZK_ACCOUNT_2);
    final FullBytes storageValueNew = new FullBytes(UInt256.valueOf(13));
    final ZKTrie contractStorageTrieNew =
        ZKTrie.createTrie(
            new WorldStateStorageProxy(
                Optional.of(contractNew.getAddress()), new InMemoryWorldStateStorage()));
    expectedTraces.add(
        contractStorageTrieNew.putAndProve(storageKeyHash, storageKey, storageValueNew));
    contractNew.setStorageRoot(Hash.wrap(contractStorageTrieNew.getTopRootHash()));
    expectedTraces.add(
        expectedTraces.size() - 1,
        accountStateTrieOne.putAndProve(
            contractNew.getHkey(), contractNew.getAddress(), contractNew.serializeAccount()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // create account with the rolling
    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    AccountKey accountKey =
        trieLogLayer.addAccountChange(ACCOUNT_2, null, new TrieLogAccountValue(contract));
    trieLogLayer.addStorageChange(
        accountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new ShomeiTrieLogLayer();
    accountKey =
        trieLogLayer2.addAccountChange(
            ACCOUNT_2,
            new TrieLogAccountValue(contract),
            new TrieLogAccountValue(contractNew),
            true);
    trieLogLayer2.addStorageChange(
        accountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()),
        UInt256.fromBytes(storageValueNew.getOriginalValue()));
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);

    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }

  @Test
  public void rollingForwardAccountSelfDestructWithDifferentStorageKeyAndValue() {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    // create contract with storage
    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT_2);

    final FullBytes storageKey = new FullBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final FullBytes storageValue = new FullBytes(UInt256.valueOf(12));

    final ZKTrie contractStorageTrie =
        ZKTrie.createTrie(
            new WorldStateStorageProxy(
                Optional.of(contract.getAddress()), new InMemoryWorldStateStorage()));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));
    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.serializeAccount());

    final List<Trace> expectedTraces = new ArrayList<>();
    // read slot before selfdestruct
    expectedTraces.add(contractStorageTrie.readAndProve(storageKeyHash, storageKey, storageValue));
    // selfdestruct contract
    expectedTraces.add(
        accountStateTrieOne.removeAndProve(contract.getHkey(), contract.getAddress()));
    // recreate contract
    final MutableZkAccount contractNew = new MutableZkAccount(ZK_ACCOUNT_2);
    final FullBytes storageKeyNew = new FullBytes(UInt256.valueOf(146));
    final Hash storageKeyHashNew = HashProvider.mimc(storageKeyNew);
    final FullBytes storageValueNew = new FullBytes(UInt256.valueOf(13));
    final ZKTrie contractStorageTrieNew =
        ZKTrie.createTrie(
            new WorldStateStorageProxy(
                Optional.of(contractNew.getAddress()), new InMemoryWorldStateStorage()));
    expectedTraces.add(
        contractStorageTrieNew.putAndProve(storageKeyHashNew, storageKeyNew, storageValueNew));
    contractNew.setStorageRoot(Hash.wrap(contractStorageTrieNew.getTopRootHash()));
    expectedTraces.add(
        expectedTraces.size() - 1,
        accountStateTrieOne.putAndProve(
            contractNew.getHkey(), contractNew.getAddress(), contractNew.serializeAccount()));

    Hash topRootHash = Hash.wrap(accountStateTrieOne.getTopRootHash());

    ZKEvmWorldState zkEvmWorldState = new ZKEvmWorldState(new InMemoryWorldStateStorage());
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // create account with the rolling
    TrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();
    AccountKey accountKey =
        trieLogLayer.addAccountChange(ACCOUNT_2, null, new TrieLogAccountValue(contract));
    trieLogLayer.addStorageChange(
        accountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer);
    zkEvmWorldState.commit(0L, null);

    // delete and recreate the contract in the same batch
    TrieLogLayer trieLogLayer2 = new ShomeiTrieLogLayer();
    accountKey =
        trieLogLayer2.addAccountChange(
            ACCOUNT_2,
            new TrieLogAccountValue(contract),
            new TrieLogAccountValue(contractNew),
            true);
    trieLogLayer2.addStorageChange(
        accountKey,
        UInt256.fromBytes(storageKey.getOriginalValue()),
        UInt256.fromBytes(storageValue.getOriginalValue()),
        null);
    trieLogLayer2.addStorageChange(
        accountKey,
        UInt256.fromBytes(storageKeyNew.getOriginalValue()),
        null,
        UInt256.fromBytes(storageValueNew.getOriginalValue()));
    zkEvmWorldState.getAccumulator().rollForward(trieLogLayer2);
    zkEvmWorldState.commit(0L, null);

    assertThat(gson.toJson(zkEvmWorldState.getLastTraces())).isEqualTo(gson.toJson(expectedTraces));
    assertThat(zkEvmWorldState.getStateRootHash()).isEqualTo(topRootHash);
  }
}
