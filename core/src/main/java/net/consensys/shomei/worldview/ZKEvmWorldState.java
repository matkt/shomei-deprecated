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

import static net.consensys.shomei.storage.WorldStateStorageProxy.createAccountProxy;
import static net.consensys.shomei.storage.WorldStateStorageProxy.createStorageProxy;
import static net.consensys.shomei.trie.ZKTrie.EMPTY_TRIE_ROOT;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.ZkValue;
import net.consensys.shomei.storage.WorldStateStorage;
import net.consensys.shomei.storage.WorldStateStorageProxy;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trie.storage.StorageProxy;
import net.consensys.shomei.trie.storage.StorageProxy.Updater;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.StorageSlotKey;
import net.consensys.shomei.util.bytes.FullBytes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZKEvmWorldState {

  private static final Logger LOG = LoggerFactory.getLogger(ZKEvmWorldState.class);

  private final ZkEvmWorldStateUpdateAccumulator accumulator;
  private Hash stateRoot;

  private List<Trace> lastTraces; // TODO remove and save it in rocksdb

  private long blockNumber;
  private Hash blockHash;

  private final WorldStateStorage zkEvmWorldStateStorage;

  public ZKEvmWorldState(final WorldStateStorage zkEvmWorldStateStorage) {
    this.stateRoot = zkEvmWorldStateStorage.getWorldStateRootHash().orElse(EMPTY_TRIE_ROOT);
    this.lastTraces = new ArrayList<>();
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
    final State state = generateNewState(updater);
    this.stateRoot = state.stateRoot;
    this.lastTraces = state.traces;
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;

    updater.setBlockHash(blockHash);
    updater.setBlockNumber(blockNumber);

    // persist
    // updater.commit();
    accumulator.reset();
  }

  record State(Hash stateRoot, List<Trace> traces) {}

  private State generateNewState(final Updater updater) {
    final ZKTrie zkAccountTrie =
        loadAccountTrie(createAccountProxy(zkEvmWorldStateStorage, updater));
    final List<Trace> traces = updateAccounts(zkAccountTrie, updater);
    zkAccountTrie.commit();
    return new State(Hash.wrap(zkAccountTrie.getTopRootHash()), traces);
  }

  public List<Trace> updateAccounts(final ZKTrie zkAccountTrie, final Updater updater) {
    final List<Trace> traces = new ArrayList<>();
    accumulator.getAccountsToUpdate().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            (entry) -> {
              final AccountKey accountKey = entry.getKey();
              final ZkValue<ZkAccount> accountValue = entry.getValue();
              traces.addAll(updateAccount(accountKey, accountValue, zkAccountTrie, updater));
            });
    return traces;
  }

  public List<Trace> updateAccount(
      final AccountKey accountKey,
      final ZkValue<ZkAccount> accountValue,
      final ZKTrie zkAccountTrie,
      final Updater updater) {
    final List<Trace> traces = new ArrayList<>();

    // check read and read zero for rollfoward
    if (accountValue.isRollforward()) {
      if (!accountValue
          .isCleared()) { // not trace the read account because we will selfdetruct the contract
        if (accountValue.getPrior() == null && accountValue.getUpdated() == null) {
          // read zero
          traces.add(
              zkAccountTrie.readZeroAndProve(accountKey.accountHash(), accountKey.address()));
        } else if (Objects.equals(accountValue.getPrior(), accountValue.getUpdated())) {
          // read non zero
          traces.add(
              zkAccountTrie.readAndProve(
                  accountKey.accountHash(),
                  accountKey.address(),
                  accountValue.getPrior().serializeAccount()));
        }
      }
      // only read if the contract already exist
      if (accountValue.getPrior() != null) {
        traces.addAll(readSlots(accountKey, accountValue, updater));
      }
    }

    // check if remove needed
    if (accountValue.getPrior() == null
        || accountValue.getUpdated() == null
        || accountValue.isCleared()) {
      if (!accountValue.isRollforward()) {
        zkAccountTrie.decrementNextFreeNode();
      }
      if (accountValue.getPrior() != null) {
        traces.add(zkAccountTrie.removeAndProve(accountKey.accountHash(), accountKey.address()));
        // TODO remove all slots from storage : if we not do that the rollback will not work
        // get index leaf from DB and delete all keys starting by HKEY+index in trie branch
      }
    }

    // check selfdestruct
    if (!accountValue.isRollforward()
        && accountValue.isCleared()) { // decrement again in case of selfdestruct
      zkAccountTrie.decrementNextFreeNode();
      zkAccountTrie.removeAndProve(accountKey.accountHash(), accountKey.address());
      // TODO remove all slots from storage : if we not do that the rollback will not work
      // get index leaf from DB and delete all keys starting by HKEY+index in trie branch
    }

    // check update and insert
    if (!Objects.equals(accountValue.getPrior(), accountValue.getUpdated())
        || accountValue.isCleared()) {
      // update account or create
      if (accountValue.getUpdated() != null) {
        traces.add(
            zkAccountTrie.putAndProve(
                accountValue.getUpdated().getHkey(),
                accountKey.address(),
                accountValue.getPrior() == null ? null : accountValue.getPrior().serializeAccount(),
                accountValue.getUpdated().serializeAccount()));
        // update slots of the account
        traces.addAll(updateSlots(accountKey, accountValue, updater));
      }
    }
    return traces;
  }

  public List<Trace> readSlots(
      final AccountKey accountKey, final ZkValue<ZkAccount> accountValue, final Updater updater) {
    final List<Trace> traces = new ArrayList<>();
    final Map<StorageSlotKey, ZkValue<UInt256>> storageToRead =
        accumulator.getStorageToUpdate().get(accountKey);
    if (storageToRead != null) {
      // load the account storage trie
      final WorldStateStorageProxy storageAdapter =
          createStorageProxy(accountKey, zkEvmWorldStateStorage, updater);
      final ZKTrie zkStorageTrie = loadStorageTrie(accountValue, false, storageAdapter);
      storageToRead.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              (storageEntry) -> {
                final StorageSlotKey storageSlotKey = storageEntry.getKey();
                final ZkValue<UInt256> storageValue = storageEntry.getValue();
                if (storageValue.getPrior() == null) {
                  if (storageValue.getUpdated() == null) {
                    // read zero
                    traces.add(
                        zkStorageTrie.readZeroAndProve(
                            storageSlotKey.slotHash(),
                            new FullBytes(storageSlotKey.slotKey().orElseThrow())));
                  }
                } else if (Objects.equals(storageValue.getPrior(), storageValue.getUpdated())
                    || accountValue.isCleared()) {
                  // read non zero
                  traces.add(
                      zkStorageTrie.readAndProve(
                          storageSlotKey.slotHash(),
                          new FullBytes(storageSlotKey.slotKey().orElseThrow()),
                          new FullBytes(storageValue.getPrior())));
                }
              });
    }
    return traces;
  }

  public List<Trace> updateSlots(
      final AccountKey accountKey, final ZkValue<ZkAccount> accountValue, final Updater updater) {
    final List<Trace> traces = new ArrayList<>();
    final Map<StorageSlotKey, ZkValue<UInt256>> storageToUpdate =
        accumulator.getStorageToUpdate().get(accountKey);
    if (storageToUpdate != null) {
      // load the account storage trie
      final WorldStateStorageProxy storageAdapter =
          createStorageProxy(accountKey, zkEvmWorldStateStorage, updater);
      final ZKTrie zkStorageTrie =
          loadStorageTrie(accountValue, accountValue.isCleared(), storageAdapter);
      final Hash targetStorageRootHash =
          Optional.ofNullable(accountValue.getUpdated())
              .map(ZkAccount::getStorageRoot)
              .orElse(EMPTY_TRIE_ROOT);
      storageToUpdate.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              (storageEntry) -> {
                final StorageSlotKey storageSlotKey = storageEntry.getKey();
                final ZkValue<UInt256> storageValue = storageEntry.getValue();
                traces.addAll(
                    updateSlot(accountValue, storageSlotKey, storageValue, zkStorageTrie));
              });
      if (!zkStorageTrie.getTopRootHash().equals(targetStorageRootHash)) {
        throw new RuntimeException("invalid trie log");
      }
      zkStorageTrie.commit();
    }
    return traces;
  }

  public List<Trace> updateSlot(
      final ZkValue<ZkAccount> accountValue,
      final StorageSlotKey storageSlotKey,
      final ZkValue<UInt256> storageValue,
      final ZKTrie zkStorageTrie) {
    final List<Trace> traces = new ArrayList<>();

    // check remove needed (not needed in case of selfdestruct contract)
    if ((storageValue.getPrior() == null || storageValue.getUpdated() == null)
        && !accountValue.isCleared()) {
      if (!storageValue.isRollforward()) {
        zkStorageTrie.decrementNextFreeNode();
      }
      if (storageValue.getPrior() != null) {
        traces.add(
            zkStorageTrie.removeAndProve(
                storageSlotKey.slotHash(), new FullBytes(storageSlotKey.slotKey().orElseThrow())));
      }
    }
    // check update and insert
    if (!Objects.equals(storageValue.getPrior(), storageValue.getUpdated())
        || accountValue.isCleared()) {
      // update account or create
      if (storageValue.getUpdated() != null) {
        traces.add(
            zkStorageTrie.putAndProve(
                storageSlotKey.slotHash(),
                new FullBytes(storageSlotKey.slotKey().orElseThrow()),
                storageValue.getPrior() == null ? null : new FullBytes(storageValue.getPrior()),
                new FullBytes(storageValue.getUpdated())));
      }
    }
    return traces;
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

  public List<Trace> getLastTraces() {
    return lastTraces;
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
      final ZkValue<ZkAccount> zkAccount,
      final boolean forceNewTrie,
      final StorageProxy storageProxy) {
    if (zkAccount.getPrior() == null // new contract
        || zkAccount
            .getPrior()
            .getStorageRoot()
            .equals(EMPTY_TRIE_ROOT) // first write in the storage
        || forceNewTrie) { // recreate contract
      return ZKTrie.createTrie(storageProxy);
    } else {
      return ZKTrie.loadTrie(zkAccount.getPrior().getStorageRoot(), storageProxy);
    }
  }
}
