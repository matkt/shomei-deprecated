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

import static net.consensys.shomei.trielog.TrieLogLayer.defaultOrValue;
import static net.consensys.shomei.trielog.TrieLogLayer.nullOrValue;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.storage.WorldStateStorage;
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import java.util.Optional;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

public class TrieLogLayerConverter {

  final WorldStateStorage worldStateStorage;

  public TrieLogLayerConverter(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  public ShomeiTrieLogLayer decodeTrieLog(final RLPInput input) {

    ShomeiTrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();

    input.enterList();
    trieLogLayer.setBlockHash(Hash.wrap(input.readBytes32()));

    while (!input.isEndOfCurrentList()) {
      input.enterList();

      final Address address = Address.readFrom(input);
      final AccountKey accountKey = new AccountKey(address);
      final Optional<Bytes> newCode;
      Optional<Long> accountIndex = Optional.empty();

      if (input.nextIsNull()) {
        input.skipNext();
        newCode = Optional.empty();
      } else {
        input.enterList();
        input.skipNext(); // skip prior code not needed
        newCode = Optional.of(input.readBytes());
        input.skipNext(); // skip is cleared for code
        input.leaveList();
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final ZkAccount oldAccountValue;
        if (!input.nextIsNull()) {
          final Optional<FlattenedLeaf> flatLeaf =
              worldStateStorage.getFlatLeaf(accountKey.accountHash());
          oldAccountValue =
              flatLeaf
                  .map(value -> ZkAccount.fromEncodedBytes(accountKey, value.leafValue()))
                  .orElseThrow();
          accountIndex = flatLeaf.map(FlattenedLeaf::leafIndex);
        } else {
          oldAccountValue = null;
        }
        input.skipNext();
        final ZkAccount newAccountValue =
            nullOrValue(
                input,
                rlpInput -> prepareTrieLogAccount(accountKey, newCode, oldAccountValue, rlpInput));
        final boolean isCleared = defaultOrValue(input, 0, RLPInput::readInt) == 1;
        input.leaveList();
        trieLogLayer.addAccountChange(accountKey, oldAccountValue, newAccountValue, isCleared);
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          input.enterList();
          final StorageSlotKey storageSlotKey = new StorageSlotKey(input.readUInt256Scalar());
          final UInt256 oldValue;
          if (!input.nextIsNull()) {
            oldValue =
                worldStateStorage
                    .getFlatLeaf(
                        Bytes.concatenate(
                            Bytes.wrap(Longs.toByteArray(accountIndex.orElseThrow())),
                            storageSlotKey.slotHash()))
                    .map(FlattenedLeaf::leafValue)
                    .map(MimcSafeBytes::toUInt256)
                    .orElseThrow();
          } else {
            oldValue = null;
          }
          input.skipNext();
          final UInt256 newValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          input.skipNext(); // skip is cleared for storage level
          input.leaveList();
          trieLogLayer.addStorageChange(accountKey, storageSlotKey, oldValue, newValue);
        }
        input.leaveList();
      }
      // lenient leave list for forward compatible additions.
      input.leaveListLenient();
    }
    input.leaveListLenient();
    trieLogLayer.freeze();

    return trieLogLayer;
  }

  public ZkAccount prepareTrieLogAccount(
      final AccountKey accountKey,
      final Optional<Bytes> newCode,
      final ZkAccount oldValue,
      final RLPInput in) {
    in.enterList();

    final long nonce = in.readLongScalar();
    final Wei balance = Wei.of(in.readUInt256Scalar());
    in.skipNext(); // skip storage root (evm storage root is useless)
    in.skipNext(); // skip keccak codeHash
    in.leaveList();

    MimcSafeBytes keccakCodeHash;
    Bytes32 mimcCodeHash;
    long codeSize;

    if (newCode.isEmpty()) {
      if (oldValue == null) {
        keccakCodeHash = ZkAccount.EMPTY_KECCAK_CODE_HASH;
        mimcCodeHash = ZkAccount.EMPTY_CODE_HASH;
        codeSize = 0;
      } else {
        keccakCodeHash = oldValue.getCodeHash();
        mimcCodeHash = oldValue.getMimcCodeHash();
        codeSize = oldValue.getCodeSize();
      }
    } else {
      final Bytes code = newCode.get();
      keccakCodeHash = new MimcSafeBytes(HashProvider.keccak256(code));
      mimcCodeHash = HashProvider.mimc(code);
      codeSize = code.size();
    }

    return new ZkAccount(
        accountKey, keccakCodeHash, Hash.wrap(mimcCodeHash), codeSize, nonce, balance, null);
  }
}
