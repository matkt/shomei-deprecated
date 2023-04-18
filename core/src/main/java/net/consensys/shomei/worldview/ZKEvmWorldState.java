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

import static net.consensys.shomei.trie.ZKTrie.EMPTY_TRIE_ROOT;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.ZkValue;
import net.consensys.shomei.storage.WorldStateStorage;
import net.consensys.shomei.storage.WorldStateStorageProxy;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.storage.StorageProxy;
import net.consensys.shomei.trie.storage.StorageProxy.Updater;
import net.consensys.shomei.util.bytes.FullBytes;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZKEvmWorldState {

  private static final Logger LOG = LoggerFactory.getLogger(ZKEvmWorldState.class);

  private final ZkEvmWorldStateUpdateAccumulator accumulator;
  private Hash stateRoot;

  private long blockNumber;
  private Hash blockHash;

  private final WorldStateStorage zkEvmWorldStateStorage;

  public ZKEvmWorldState(final WorldStateStorage zkEvmWorldStateStorage) {
    this.stateRoot = zkEvmWorldStateStorage.getWorldStateRootHash().orElse(EMPTY_TRIE_ROOT);
    this.blockNumber =
        zkEvmWorldStateStorage.getWorldStateBlockNumber().orElse(0L); // -1 when not state yet
    this.blockHash = zkEvmWorldStateStorage.getWorldStateBlockHash().orElse(Hash.EMPTY);
    this.accumulator = new ZkEvmWorldStateUpdateAccumulator();
    this.zkEvmWorldStateStorage = zkEvmWorldStateStorage;
  }

  public void commit(final long blockNumber, final Hash blockHash) {
    LOG.atDebug()
        .setMessage("Commit world state for block number {} and block hash {}")
        .addArgument(blockNumber)
        .addArgument(blockHash)
        .log();
    final WorldStateStorage.WorldStateUpdater updater =
        (WorldStateStorage.WorldStateUpdater) zkEvmWorldStateStorage.updater();
    this.stateRoot = calculateRootHash(updater);
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;

    updater.setBlockHash(blockHash);
    updater.setBlockNumber(blockNumber);
    // persist
    // updater.commit();
    accumulator.reset();
  }

  private Hash calculateRootHash(final Updater updater) {
    final ZKTrie zkAccountTrie =
        loadAccountTrie(new WorldStateStorageProxy(zkEvmWorldStateStorage, updater));
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
                final WorldStateStorageProxy storageAdapter =
                    new WorldStateStorageProxy(
                        Optional.of(accountValue.getKey()), zkEvmWorldStateStorage, updater);
                final ZKTrie zkStorageTrie = loadStorageTrie(accountValue, storageAdapter);
                final Hash targetStorageRootHash =
                    Optional.ofNullable(accountValue.getUpdated())
                        .map(ZkAccount::getStorageRoot)
                        .orElse(EMPTY_TRIE_ROOT);
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

  public Hash getStateRootHash() {
    return stateRoot;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  @VisibleForTesting
  public ZkEvmWorldStateUpdateAccumulator getAccumulator() {
    return accumulator;
  }

  private ZKTrie loadAccountTrie(final StorageProxy storageProxy) {
    if (stateRoot.equals(EMPTY_TRIE_ROOT)) {
      return ZKTrie.createTrie(storageProxy);
    } else {
      return ZKTrie.loadTrie(stateRoot, new WorldStateStorageProxy(zkEvmWorldStateStorage));
    }
  }

  private ZKTrie loadStorageTrie(
      final ZkValue<Address, ZkAccount> zkAccount, final StorageProxy storageProxy) {
    if (zkAccount.getPrior() == null
        || zkAccount.getPrior().getStorageRoot().equals(EMPTY_TRIE_ROOT)) {
      return ZKTrie.createTrie(storageProxy);
    } else {
      return ZKTrie.loadTrie(zkAccount.getPrior().getStorageRoot(), storageProxy);
    }
  }
}
