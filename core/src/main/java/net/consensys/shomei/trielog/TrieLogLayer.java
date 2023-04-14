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

package net.consensys.shomei.trielog;

import static com.google.common.base.Preconditions.checkState;

import net.consensys.shomei.ZkValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

/**
 * This class encapsulates the changes that are done to transition one block to the next. This
 * includes serialization and deserialization tasks for storing this log to off-memory storage.
 *
 * <p>In this particular formulation only the "Leaves" are tracked" Future layers may track patrica
 * trie changes as well.
 */
public class TrieLogLayer {

  private Hash blockHash;
  private final Map<AccountKey, ZkValue<TrieLogAccountValue>> accounts;
  private final Map<AccountKey, Map<StorageSlotKey, ZkValue<UInt256>>> storage;

  private boolean frozen = false;

  public TrieLogLayer() {
    this.accounts = new HashMap<>();
    this.storage = new HashMap<>();
  }

  /** Locks the layer so no new changes can be added; */
  void freeze() {
    frozen = true; // The code never bothered me anyway
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public void setBlockHash(final Hash blockHash) {
    checkState(!frozen, "Layer is Frozen");
    this.blockHash = blockHash;
  }

  public AccountKey addAccountChange(
      final Address address,
      final TrieLogAccountValue oldValue,
      final TrieLogAccountValue newValue,
      final boolean isCleared) {
    checkState(!frozen, "Layer is Frozen");
    final AccountKey accountKey = new AccountKey(getAccountHash(address), address);
    accounts.put(accountKey, new ZkValue<>(oldValue, newValue, isCleared));
    return accountKey;
  }

  public AccountKey addAccountChange(
      final Address address,
      final TrieLogAccountValue oldValue,
      final TrieLogAccountValue newValue) {
    return addAccountChange(address, oldValue, newValue, false);
  }

  public void addStorageChange(
      final AccountKey accountKey,
      final UInt256 slotKey,
      final UInt256 oldValue,
      final UInt256 newValue,
      final boolean isCleared) {
    checkState(!frozen, "Layer is Frozen");
    storage
        .computeIfAbsent(accountKey, a -> new TreeMap<>())
        .put(
            new StorageSlotKey(getSlotHash(slotKey), slotKey),
            new ZkValue<>(oldValue, newValue, isCleared));
  }

  public void addStorageChange(
      final AccountKey accountKey,
      final UInt256 storageKey,
      final UInt256 oldValue,
      final UInt256 newValue) {
    addStorageChange(accountKey, storageKey, oldValue, newValue, false);
  }

  public static TrieLogLayer fromBytes(final TrieLogLayer newLayer, final byte[] bytes) {
    return readFrom(newLayer, new BytesValueRLPInput(Bytes.wrap(bytes), false));
  }

  public static TrieLogLayer readFrom(final TrieLogLayer newLayer, final RLPInput input) {
    input.enterList();
    newLayer.blockHash = Hash.wrap(input.readBytes32());

    while (!input.isEndOfCurrentList()) {
      input.enterList();

      final Address address = Address.readFrom(input);
      AccountKey accountKey = null;

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final TrieLogAccountValue oldValue = nullOrValue(input, TrieLogAccountValue::readFrom);
        final TrieLogAccountValue newValue = nullOrValue(input, TrieLogAccountValue::readFrom);
        final boolean isCleared = defaultOrValue(input, 0, RLPInput::readInt) == 1;
        input.leaveList();
        accountKey = newLayer.addAccountChange(address, oldValue, newValue, isCleared);
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          input.enterList();
          final UInt256 slotKey = input.readUInt256Scalar();
          final UInt256 oldValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final UInt256 newValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          input.leaveList();
          newLayer.addStorageChange(accountKey, slotKey, oldValue, newValue);
        }
        input.leaveList();
      }

      // lenient leave list for forward compatible additions.
      input.leaveListLenient();
    }
    input.leaveListLenient();
    newLayer.freeze();

    return newLayer;
  }

  public void writeTo(final RLPOutput output) {
    freeze();

    final Set<AccountKey> accountKeys = new TreeSet<>();
    accountKeys.addAll(accounts.keySet().stream().toList());
    accountKeys.addAll(storage.keySet().stream().toList());

    output.startList(); // container
    output.writeBytes(blockHash);

    for (final AccountKey accountKey : accountKeys) {
      output.startList(); // this change
      output.writeBytes(accountKey.address());

      final ZkValue<TrieLogAccountValue> accountChange = accounts.get(accountKey);
      if (accountChange == null || accountChange.isUnchanged()) {
        output.writeNull();
      } else {
        accountChange.writeRlp(output, (o, sta) -> sta.writeTo(o));
      }

      final Map<StorageSlotKey, ZkValue<UInt256>> storageChanges = storage.get(accountKey);
      if (storageChanges == null) {
        output.writeNull();
      } else {
        output.startList();
        for (final Map.Entry<StorageSlotKey, ZkValue<UInt256>> storageChangeEntry :
            storageChanges.entrySet()) {
          output.startList();
          output.writeUInt256Scalar(storageChangeEntry.getKey().slotKey().orElseThrow());
          storageChangeEntry.getValue().writeInnerRlp(output, RLPOutput::writeUInt256Scalar);
          output.endList();
        }
        output.endList();
      }

      output.endList(); // this change
    }
    output.endList(); // container
  }

  public Stream<Map.Entry<AccountKey, ZkValue<TrieLogAccountValue>>> streamAccountChanges() {
    return accounts.entrySet().stream();
  }

  public Stream<Map.Entry<AccountKey, Map<StorageSlotKey, ZkValue<UInt256>>>>
      streamStorageChanges() {
    return storage.entrySet().stream();
  }

  public boolean hasStorageChanges(final AccountKey accountKey) {
    return storage.containsKey(accountKey);
  }

  public Stream<Map.Entry<StorageSlotKey, ZkValue<UInt256>>> streamStorageChanges(
      final AccountKey accountKey) {
    return storage.getOrDefault(accountKey, Map.of()).entrySet().stream();
  }

  private static <T> T nullOrValue(final RLPInput input, final Function<RLPInput, T> reader) {
    if (input.nextIsNull()) {
      input.skipNext();
      return null;
    } else {
      return reader.apply(input);
    }
  }

  private static <T> T defaultOrValue(
      final RLPInput input, final T defaultValue, final Function<RLPInput, T> reader) {
    final T value = nullOrValue(input, reader);
    return nullOrValue(input, reader) == null ? defaultValue : value;
  }

  Optional<UInt256> getPriorStorage(
      final AccountKey accountKey, final StorageSlotKey storageSlotKey) {
    return Optional.ofNullable(storage.get(accountKey))
        .map(i -> i.get(storageSlotKey))
        .map(ZkValue::getPrior);
  }

  Optional<UInt256> getStorage(final AccountKey accountKey, final StorageSlotKey storageSlotKey) {
    return Optional.ofNullable(storage.get(accountKey))
        .map(i -> i.get(storageSlotKey))
        .map(ZkValue::getUpdated);
  }

  public Optional<TrieLogAccountValue> getPriorAccount(final AccountKey accountKey) {
    return Optional.ofNullable(accounts.get(accountKey)).map(ZkValue::getPrior);
  }

  public Optional<TrieLogAccountValue> getAccount(final AccountKey accountKey) {
    return Optional.ofNullable(accounts.get(accountKey)).map(ZkValue::getUpdated);
  }

  public Hash getAccountHash(final Address address) {
    return Hash.hash(address);
  }

  public Hash getSlotHash(final UInt256 slotKey) {
    return Hash.hash(slotKey);
  }

  public String dump() {
    final StringBuilder sb = new StringBuilder();
    sb.append("TrieLogLayer{" + "blockHash=").append(blockHash).append(frozen).append('}');
    sb.append("accounts\n");
    for (final Map.Entry<AccountKey, ZkValue<TrieLogAccountValue>> account : accounts.entrySet()) {
      sb.append(" : ").append(account.getKey()).append("\n");
      if (Objects.equals(account.getValue().getPrior(), account.getValue().getUpdated())) {
        sb.append("   = ").append(account.getValue().getUpdated()).append("\n");
      } else {
        sb.append("   - ").append(account.getValue().getPrior()).append("\n");
        sb.append("   + ").append(account.getValue().getUpdated()).append("\n");
      }
    }
    sb.append("Storage").append("\n");
    for (final Map.Entry<AccountKey, Map<StorageSlotKey, ZkValue<UInt256>>> storage :
        storage.entrySet()) {
      sb.append(" : ").append(storage.getKey()).append("\n");
      for (final Map.Entry<StorageSlotKey, ZkValue<UInt256>> slot : storage.getValue().entrySet()) {
        final UInt256 originalValue = slot.getValue().getPrior();
        final UInt256 updatedValue = slot.getValue().getUpdated();
        sb.append("   : ").append(slot.getKey()).append("\n");
        if (Objects.equals(originalValue, updatedValue)) {
          sb.append("     = ")
              .append((originalValue == null) ? "null" : originalValue.toShortHexString())
              .append("\n");
        } else {
          sb.append("     - ")
              .append((originalValue == null) ? "null" : originalValue.toShortHexString())
              .append("\n");
          sb.append("     + ")
              .append((updatedValue == null) ? "null" : updatedValue.toShortHexString())
              .append("\n");
        }
      }
    }
    return sb.toString();
  }
}
