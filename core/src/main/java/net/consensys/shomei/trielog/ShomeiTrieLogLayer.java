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

import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

/**
 * This class encapsulates the changes that are done to transition one block to the next. This
 * includes serialization and deserialization tasks for storing this log to off-memory storage.
 *
 * <p>In this particular formulation only the "Leaves" are tracked" Future layers may track patrica
 * trie changes as well.
 */
public class ShomeiTrieLogLayer extends TrieLogLayer {

  @Override
  public Hash getAccountHash(final Address address) {
    return HashProvider.mimc(Bytes32.leftPad(address));
  }

  @Override
  public Hash getSlotHash(final UInt256 slotKey) {
    return HashProvider.mimc(new MimcSafeBytes(slotKey));
  }
}
