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

package net.consensys.shomei.util;

import static net.consensys.shomei.ZkAccount.EMPTY_CODE_HASH;
import static net.consensys.shomei.ZkAccount.EMPTY_KECCAK_CODE_HASH;
import static net.consensys.shomei.trie.ZKTrie.EMPTY_TRIE_ROOT;

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.util.bytes.FullBytes;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

public class TestFixtureGenerator {

  private static final Address ACCOUNT_1 = createDumAddress(36);
  private static final Address ACCOUNT_2 = createDumAddress(47);

  public static final ZkAccount ZK_ACCOUNT =
      new ZkAccount(
          ACCOUNT_1, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

  public static final ZkAccount ZK_ACCOUNT_2 =
      new ZkAccount(
          ACCOUNT_2, EMPTY_KECCAK_CODE_HASH, EMPTY_CODE_HASH, 0L, 65, Wei.of(835), EMPTY_TRIE_ROOT);

  public static Address createDumAddress(int value) {
    MutableBytes mutableBytes = MutableBytes.create(Address.SIZE);
    mutableBytes.set(0, (byte) value);
    return Address.wrap(mutableBytes);
  }

  public static Bytes32 createDumDigest(int value) {
    MutableBytes32 mutableBytes = MutableBytes32.create();
    mutableBytes.set(Bytes32.SIZE - 1, (byte) value);
    return mutableBytes;
  }

  public static FullBytes createDumFullBytes(int value) {
    MutableBytes32 mutableBytes = MutableBytes32.create();
    mutableBytes.set(0, (byte) value);
    return new FullBytes(mutableBytes);
  }
}
