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

package net.consensys.shomei.worldview;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.ZkValue;
import net.consensys.shomei.storage.WorldStateStorage;
import net.consensys.shomei.storage.WorldStateStorageProxy;
import net.consensys.shomei.trie.StoredSparseMerkleTrie;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.util.bytes.FullBytes;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZKEvmWorldState {

  private static final Logger LOG = LoggerFactory.getLogger(ZKEvmWorldState.class);

  private final ZkEvmWorldStateUpdateAccumulator accumulator;
  private Hash rootHash;
  private Hash blockHash;

  private final WorldStateStorage zkEvmWorldStateStorage;

  public ZKEvmWorldState(
      final Hash rootHash, final Hash blockHash, final WorldStateStorage zkEvmWorldStateStorage) {
    this.rootHash = rootHash; // read from database
    this.blockHash = blockHash; // read from database
    this.accumulator = new ZkEvmWorldStateUpdateAccumulator();
    this.zkEvmWorldStateStorage = zkEvmWorldStateStorage;
  }

  public void commit(final BlockHeader blockHeader) {
    final Optional<BlockHeader> maybeBlockHeader = Optional.ofNullable(blockHeader);
    LOG.atDebug().setMessage("Commit world state for block {}").addArgument(maybeBlockHeader).log();
    rootHash = calculateRootHash();
    blockHash = maybeBlockHeader.map(BlockHeader::getHash).orElse(null);
    accumulator.reset();
    // persist

    // TODO: sup brah
  }

  private Hash calculateRootHash() {
    final ZKTrie zkAccountTrie = loadAccountTrie();
    accumulator.getAccountsToUpdate().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            (entry) -> {
              final Hash hkey = entry.getKey();
              final ZkValue<Address, ZkAccount> accountValue = entry.getValue();
              final Map<Hash, ZkValue<UInt256, UInt256>> storageToUpdate =
                  accumulator.getStorageToUpdate().get(hkey);
              if (storageToUpdate != null) {
                // load the account storage trie
                final ZKTrie zkStorageTrie = loadStorageTrie(accountValue);
                final Hash targetStorageRootHash =
                    Optional.ofNullable(accountValue.getUpdated())
                        .map(ZkAccount::getStorageRoot)
                        .orElse(StoredSparseMerkleTrie.EMPTY_TRIE_ROOT);
                storageToUpdate.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                        (storageEntry) -> {
                          final Hash slotKeyHash = storageEntry.getKey();
                          final ZkValue<UInt256, UInt256> storageValue = storageEntry.getValue();
                          // check read and read zero for rollfoward
                          if (storageValue.isRollforward()) {
                            if (storageValue.getPrior() == null
                                && storageValue.getUpdated() == null) {
                              // read zero
                              zkStorageTrie.readZeroAndProve(
                                  slotKeyHash, new FullBytes(storageValue.getKey()));
                            } else if (Objects.equals(
                                storageValue.getPrior(), storageValue.getUpdated())) {
                              // read non zero
                              zkAccountTrie.readAndProve(
                                  slotKeyHash,
                                  new FullBytes(storageValue.getKey()),
                                  new FullBytes(storageValue.getPrior()));
                            }
                          }
                          // check remove, update and insert
                          if (!storageValue.isRollforward() // rollbackward
                              && (storageValue.getUpdated() == null
                                  || storageValue.getPrior() == null)) {
                            zkStorageTrie.decrementNextFreeNode();
                          }
                          if (storageValue.getUpdated() == null) {
                            zkStorageTrie.removeAndProve(
                                slotKeyHash, new FullBytes(storageValue.getKey()));
                          } else {
                            zkStorageTrie.putAndProve(
                                slotKeyHash,
                                new FullBytes(storageValue.getKey()),
                                storageValue.getPrior() == null
                                    ? null
                                    : new FullBytes(storageValue.getPrior()),
                                storageValue.getUpdated() == null
                                    ? null
                                    : new FullBytes(storageValue.getUpdated()));
                          }
                        });
                if (!zkStorageTrie.getTopRootHash().equals(targetStorageRootHash)) {
                  throw new RuntimeException("invalid trie log");
                }
                zkStorageTrie.commit();
              }
              // check read and read zero for rollfoward
              if (accountValue.isRollforward()) {
                if (accountValue.getPrior() == null && accountValue.getUpdated() == null) {
                  // read zero
                  zkAccountTrie.readZeroAndProve(hkey, accountValue.getKey());
                } else if (Objects.equals(accountValue.getPrior(), accountValue.getUpdated())) {
                  // read non zero
                  zkAccountTrie.readAndProve(
                      hkey, accountValue.getKey(), accountValue.getPrior().serializeAccount());
                }
              }
              // check remove, update and insert
              if (!accountValue.isRollforward()
                  && (accountValue.getUpdated() == null || accountValue.getPrior() == null)) {
                zkAccountTrie.decrementNextFreeNode();
              }
              if (accountValue.getUpdated() == null) {
                zkAccountTrie.removeAndProve(
                    accountValue.getPrior().getHkey(), accountValue.getKey());
              } else {
                zkAccountTrie.putAndProve(
                    accountValue.getUpdated().getHkey(),
                    accountValue.getKey(),
                    accountValue.getPrior() == null
                        ? null
                        : accountValue.getPrior().serializeAccount(),
                    accountValue.getUpdated().serializeAccount());
              }
            });
    zkAccountTrie.commit();
    return Hash.wrap(zkAccountTrie.getTopRootHash());
  }

  public Hash getRootHash() {
    return rootHash;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  @VisibleForTesting
  public ZkEvmWorldStateUpdateAccumulator getAccumulator() {
    return accumulator;
  }

  private ZKTrie loadAccountTrie() {
    if (rootHash.equals(StoredSparseMerkleTrie.EMPTY_TRIE_ROOT)) {
      return ZKTrie.createTrie(new WorldStateStorageProxy(zkEvmWorldStateStorage));
    } else {
      return ZKTrie.loadTrie(rootHash, new WorldStateStorageProxy(zkEvmWorldStateStorage));
    }
  }

  private ZKTrie loadStorageTrie(final ZkValue<Address, ZkAccount> zkAccount) {
    final WorldStateStorageProxy storageAdapter =
        new WorldStateStorageProxy(Optional.of(zkAccount.getKey()), zkEvmWorldStateStorage);
    if (zkAccount.getPrior() == null
        || zkAccount.getPrior().getStorageRoot().equals(StoredSparseMerkleTrie.EMPTY_TRIE_ROOT)) {
      return ZKTrie.createTrie(storageAdapter);
    } else {
      return ZKTrie.loadTrie(zkAccount.getPrior().getStorageRoot(), storageAdapter);
    }
  }
}
