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
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.model.FlatLeafValue;
import net.consensys.shomei.util.bytes.FullBytes;
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

/**
 * This class encapsulates the changes that are done to transition one block to the next. This
 * includes serialization and deserialization tasks for storing this log to off-memory storage.
 *
 * <p>In this particular formulation only the "Leaves" are tracked" Future layers may track patrica
 * trie changes as well.
 */
public class TrieLogLayerConverter {

  final WorldStateStorage worldStateStorage;

  public TrieLogLayerConverter(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  public ShomeiTrieLogLayer prepareTrieLog(final RLPInput input) {

    ShomeiTrieLogLayer trieLogLayer = new ShomeiTrieLogLayer();

    input.enterList();
    trieLogLayer.setBlockHash(Hash.wrap(input.readBytes32()));

    while (!input.isEndOfCurrentList()) {
      input.enterList();

      final Address address = Address.readFrom(input);
      AccountKey accountKey = null;
      Optional<Long> accountIndex = Optional.empty();

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final TrieLogAccountValue oldAccountValue;
        if (!input.nextIsNull()) {
          final Optional<FlatLeafValue> flatLeaf =
              worldStateStorage.getFlatLeaf(HashProvider.mimc(address));
          oldAccountValue =
              flatLeaf
                  .map(value -> ZkAccount.fromEncodedBytes(address, value.getValue()))
                  .map(TrieLogAccountValue::new)
                  .orElseThrow();
          accountIndex = flatLeaf.map(FlatLeafValue::getLeafIndex);
        } else {
          oldAccountValue = null;
        }
        input.skipNext();
        final TrieLogAccountValue newAccountValue =
            nullOrValue(input, rlpInput -> prepareTrieLogAccount(oldAccountValue, rlpInput));
        final boolean isCleared = defaultOrValue(input, 0, RLPInput::readInt) == 1;
        input.leaveList();
        accountKey =
            trieLogLayer.addAccountChange(address, oldAccountValue, newAccountValue, isCleared);
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          input.enterList();
          final UInt256 slotKey = input.readUInt256Scalar();
          final UInt256 oldValue;
          if (!input.nextIsNull()) {
            oldValue =
                worldStateStorage
                    .getFlatLeaf(
                        Bytes.concatenate(
                            Bytes.wrap(Longs.toByteArray(accountIndex.orElseThrow())), slotKey))
                    .map(FlatLeafValue::getValue)
                    .map(UInt256::fromBytes)
                    .orElseThrow();
          } else {
            oldValue = null;
          }
          input.skipNext();
          final UInt256 newValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          input.skipNext(); // skip is cleared for storage level
          input.leaveList();
          trieLogLayer.addStorageChange(accountKey, slotKey, oldValue, newValue);
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

  public TrieLogAccountValue prepareTrieLogAccount(
      final TrieLogAccountValue oldValue, final RLPInput in) {
    in.enterList();

    final long nonce = in.readLongScalar();
    final Wei balance = Wei.of(in.readUInt256Scalar());
    Bytes32 storageRoot;
    FullBytes keccakCodeHash;
    Bytes32 mimcCodeHash;
    long codeSize;

    if (oldValue != null) {
      storageRoot = oldValue.getStorageRoot();
      keccakCodeHash = oldValue.getCodeHash();
      mimcCodeHash = oldValue.getMimcCodeHash();
      codeSize = oldValue.getCodeSize();
      in.skipNext();
    } else {

      if (in.nextIsNull()) {
        storageRoot = ZKTrie.EMPTY_TRIE_ROOT;
        in.skipNext();
      } else {
        storageRoot = in.readBytes32();
      }

      if (in.nextIsNull()) {
        keccakCodeHash = ZkAccount.EMPTY_KECCAK_CODE_HASH;
        mimcCodeHash = ZkAccount.EMPTY_CODE_HASH;
        codeSize = 0;
        in.skipNext();
      } else {
        final Bytes code = in.readBytes();
        keccakCodeHash = new FullBytes(HashProvider.keccak256(code));
        mimcCodeHash = HashProvider.mimc(code);
        codeSize = code.size();
      }
    }
    in.leaveList();

    return new TrieLogAccountValue(
        nonce, balance, Hash.wrap(storageRoot), keccakCodeHash, Hash.wrap(mimcCodeHash), codeSize);
  }
}
