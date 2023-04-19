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
import static net.consensys.shomei.util.TestFixtureGenerator.ZK_ACCOUNT_2;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.exception.MissingTrieLogException;
import net.consensys.shomei.storage.InMemoryWorldStateStorage;
import net.consensys.shomei.storage.WorldStateStorageProxy;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trielog.ShomeiTrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.shomei.worldview.ZkEvmWorldStateEntryPoint;
import net.consensys.zkevm.HashProvider;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogLayer;
import org.hyperledger.besu.ethereum.bonsai.trielog.ZkTrieLogFactoryImpl;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.junit.Test;

public class TrieLogShippingTests {

  @Test
  public void testTrielogShippingWithNewContractUpdate() throws MissingTrieLogException {

    ZKTrie accountStateTrieOne =
        ZKTrie.createTrie(new WorldStateStorageProxy(new InMemoryWorldStateStorage()));

    // add contract with storage
    final MutableZkAccount contract = new MutableZkAccount(ZK_ACCOUNT_2);

    final MimcSafeBytes storageKey = new MimcSafeBytes(UInt256.valueOf(14));
    final Hash storageKeyHash = HashProvider.mimc(storageKey);
    final MimcSafeBytes storageValue = new MimcSafeBytes(UInt256.valueOf(12));

    final ZKTrie contractStorageTrie =
        ZKTrie.createTrie(
            new WorldStateStorageProxy(
                Optional.of(contract.getAddress()), new InMemoryWorldStateStorage()));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, storageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));

    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    Hash topRootHashBeforeUpdate = Hash.wrap(accountStateTrieOne.getTopRootHash());

    // change storage
    final MimcSafeBytes newStorageValue = new MimcSafeBytes(UInt256.valueOf(22));
    contractStorageTrie.putAndProve(storageKeyHash, storageKey, newStorageValue);
    contract.setStorageRoot(Hash.wrap(contractStorageTrie.getTopRootHash()));
    accountStateTrieOne.putAndProve(
        contract.getHkey(), contract.getAddress(), contract.getEncodedBytes());

    Hash topRootHashAfterUpdate = Hash.wrap(accountStateTrieOne.getTopRootHash());

    // simulate trielog from Besu before update
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(
        ZK_ACCOUNT_2.getAddress(),
        null,
        new StateTrieAccountValue(
            contract.nonce,
            contract.balance,
            Hash.wrap(
                Bytes32.random()), // change storage root to simulate evm storage root sent by Besu
            Hash.wrap(contract.keccakCodeHash.getOriginalValue())));
    trieLogLayer.setBlockHash(Hash.wrap(Bytes32.random()));
    trieLogLayer.addStorageChange(
        ZK_ACCOUNT_2.getAddress(),
        new BonsaiWorldStateUpdateAccumulator.StorageSlotKey(
            UInt256.fromBytes(storageKey.getOriginalValue())),
        null,
        UInt256.fromBytes(storageValue.getOriginalValue()));

    // simulate trielog from Besu after update
    TrieLogLayer trieLogLayer2 = new TrieLogLayer();
    trieLogLayer2.addAccountChange(
        ZK_ACCOUNT_2.getAddress(),
        new StateTrieAccountValue(
            contract.nonce,
            contract.balance,
            Hash.wrap(
                Bytes32.random()), // change storage root to simulate evm storage root sent by Besu
            Hash.wrap(
                contract.keccakCodeHash.getOriginalValue())), // get update of the first trielog
        new StateTrieAccountValue(
            contract.nonce,
            contract.balance,
            Hash.wrap(
                Bytes32.random()), // change storage root to simulate evm storage root sent by Besu
            Hash.wrap(contract.keccakCodeHash.getOriginalValue())));
    trieLogLayer2.setBlockHash(Hash.wrap(Bytes32.random()));
    trieLogLayer2.addStorageChange(
        ZK_ACCOUNT_2.getAddress(),
        new BonsaiWorldStateUpdateAccumulator.StorageSlotKey(
            UInt256.fromBytes(storageKey.getOriginalValue())),
        UInt256.fromBytes(storageValue.getOriginalValue()),
        UInt256.fromBytes(newStorageValue.getOriginalValue()));

    ZkTrieLogFactoryImpl zkTrieLogFactory = new ZkTrieLogFactoryImpl();

    // init the worldstate entrypoint with empty worldstate
    InMemoryWorldStateStorage storage = new InMemoryWorldStateStorage();
    ZkEvmWorldStateEntryPoint evmWorldStateEntryPoint = new ZkEvmWorldStateEntryPoint(storage);
    assertThat(evmWorldStateEntryPoint.getCurrentRootHash()).isEqualTo(EMPTY_TRIE_ROOT);

    // decode trielog from Besu
    ShomeiTrieLogLayer decodedLayer =
        new TrieLogLayerConverter(storage)
            .decodeTrieLog(RLP.input(Bytes.wrap(zkTrieLogFactory.serialize(trieLogLayer))));

    // move head with the new trielog
    evmWorldStateEntryPoint.moveHead(0, decodedLayer);
    assertThat(evmWorldStateEntryPoint.getCurrentRootHash()).isEqualTo(topRootHashBeforeUpdate);

    // decode second trielog from Besu
    ShomeiTrieLogLayer decodedLayer2 =
        new TrieLogLayerConverter(storage)
            .decodeTrieLog(RLP.input(Bytes.wrap(zkTrieLogFactory.serialize(trieLogLayer2))));

    // move head with the second trielog
    evmWorldStateEntryPoint.moveHead(1, decodedLayer2);
    assertThat(evmWorldStateEntryPoint.getCurrentRootHash()).isEqualTo(topRootHashAfterUpdate);
  }
}
