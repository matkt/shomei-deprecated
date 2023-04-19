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

import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.TrieLogAccountValue;
import net.consensys.shomei.util.bytes.MimcSafeBytes;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;

public class MutableZkAccount extends ZkAccount {

  public MutableZkAccount(
      final AccountKey accountKey,
      final MimcSafeBytes keccakCodeHash,
      final Hash mimcCodeHash,
      final long codeSize,
      final long nonce,
      final Wei balance,
      final Hash storageRoot) {
    super(accountKey, keccakCodeHash, mimcCodeHash, codeSize, nonce, balance, storageRoot);
  }

  public MutableZkAccount(final AccountKey accountKey, final TrieLogAccountValue accountValue) {
    super(accountKey, accountValue);
  }

  public MutableZkAccount(final ZkAccount toCopy) {
    super(toCopy);
  }

  public void setKeccakCodeHash(final MimcSafeBytes keccakCodeHash) {
    this.keccakCodeHash = keccakCodeHash;
  }

  public void setMimcCodeHash(final Hash mimcCodeHash) {
    this.mimcCodeHash = mimcCodeHash;
  }

  public void setCodeSize(final long codeSize) {
    this.codeSize = codeSize;
  }

  public void setNonce(final long nonce) {
    this.nonce = nonce;
  }

  public void setBalance(final Wei balance) {
    this.balance = balance;
  }

  public void setStorageRoot(final Hash storageRoot) {
    this.storageRoot = storageRoot;
  }
}
