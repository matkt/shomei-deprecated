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

package net.consensys.shomei.proof;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.trie.StoredSparseMerkleTrie.GetAndProve;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.storage.StorageTrieRepositoryWrapper;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.worldview.ZkEvmWorldState;

import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.Proof;

public class WorldStateProofProvider {

  private final ZkEvmWorldState zkEvmWorldState;

  public WorldStateProofProvider(final ZkEvmWorldState zkEvmWorldState) {
    this.zkEvmWorldState = zkEvmWorldState;
  }

  public Optional<WorldStateProof> getAccountProof(
      final AccountKey accountKey, final List<StorageSlotKey> accountStorageKeys) {

    final ZKTrie accountTrie =
        ZKTrie.loadTrie(
            zkEvmWorldState.getStateRootHash(), zkEvmWorldState.getZkEvmWorldStateStorage());
    final Optional<Long> maybeAccountLeafIndex = accountTrie.getLeafIndex(accountKey.accountHash());
    final Optional<GetAndProve> accountProof =
        maybeAccountLeafIndex.map(accountTrie::getValueAndMerkleProof);

    return accountProof
        .flatMap(GetAndProve::nodeValue)
        .map(
            bytes -> {
              ZkAccount zkAccount = ZkAccount.fromEncodedBytes(accountKey, bytes);
              final SortedMap<UInt256, Proof<Bytes>> storageProofs =
                  getStorageProofs(zkAccount, maybeAccountLeafIndex.get(), accountStorageKeys);
              return new WorldStateProof(
                  zkAccount,
                  new Proof<>(
                      accountProof.get().nodeValue(),
                      accountProof.get().proof().stream()
                          .map(Node::getEncodedBytes)
                          .collect(Collectors.toList())),
                  storageProofs);
            });
  }

  private SortedMap<UInt256, Proof<Bytes>> getStorageProofs(
      final ZkAccount account,
      final Long accountIndex,
      final List<StorageSlotKey> accountStorageKeys) {
    final ZKTrie storageTrie =
        ZKTrie.loadTrie(
            account.getStorageRoot(),
            new StorageTrieRepositoryWrapper(
                accountIndex, zkEvmWorldState.getZkEvmWorldStateStorage()));
    final NavigableMap<UInt256, Proof<Bytes>> storageProofs = new TreeMap<>();
    accountStorageKeys.forEach(
        key -> {
          storageTrie
              .getLeafIndex(key.slotHash())
              .ifPresent(
                  slotLeafIndex -> {
                    final GetAndProve valueAndMerkleProof =
                        storageTrie.getValueAndMerkleProof(slotLeafIndex);
                    storageProofs.put(
                        key.slotKey().getOriginalUnsafeValue(),
                        new Proof<>(
                            valueAndMerkleProof.nodeValue(),
                            valueAndMerkleProof.proof().stream()
                                .map(Node::getEncodedBytes)
                                .collect(Collectors.toList())));
                  });
        });
    return storageProofs;
  }
}
