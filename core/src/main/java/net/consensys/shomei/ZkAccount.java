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

import static net.consensys.zkevm.HashProvider.keccak256;
import static net.consensys.zkevm.HashProvider.mimc;

import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.TrieLogAccountValue;
import net.consensys.shomei.util.bytes.BytesInput;
import net.consensys.shomei.util.bytes.LongConverter;
import net.consensys.shomei.util.bytes.MimcSafeBytes;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;

public class ZkAccount {

  public static final MimcSafeBytes EMPTY_KECCAK_CODE_HASH =
      new MimcSafeBytes(keccak256(Bytes.EMPTY));
  public static final Hash EMPTY_CODE_HASH = mimc(Bytes32.ZERO);

  protected AccountKey accountKey;
  protected MimcSafeBytes keccakCodeHash;
  protected Hash mimcCodeHash;

  protected long codeSize;
  protected long nonce;
  protected Wei balance;
  protected Hash storageRoot;

  public ZkAccount(
      final AccountKey accountKey,
      final MimcSafeBytes keccakCodeHash,
      final Hash mimcCodeHash,
      final long codeSize,
      final long nonce,
      final Wei balance,
      final Hash storageRoot) {
    this.accountKey = accountKey;
    this.nonce = nonce;
    this.balance = balance;
    this.storageRoot = storageRoot;
    this.keccakCodeHash = keccakCodeHash;
    this.mimcCodeHash = mimcCodeHash;
    this.codeSize = codeSize;
  }

  public ZkAccount(final AccountKey accountKey, final TrieLogAccountValue accountValue) {
    this(
        accountKey,
        accountValue.getCodeHash(),
        accountValue.getMimcCodeHash(),
        accountValue.getCodeSize(),
        accountValue.getNonce(),
        accountValue.getBalance(),
        accountValue.getStorageRoot());
  }

  public ZkAccount(final ZkAccount toCopy) {
    this(
        toCopy.accountKey,
        toCopy.keccakCodeHash,
        toCopy.mimcCodeHash,
        toCopy.codeSize,
        toCopy.nonce,
        toCopy.balance,
        toCopy.storageRoot);
  }

  public static ZkAccount fromEncodedBytes(final AccountKey accountKey, final Bytes encoded) {

    return BytesInput.readBytes(
        encoded,
        bytesInput ->
            new ZkAccount(
                accountKey,
                new MimcSafeBytes(bytesInput.readBytes32()),
                Hash.wrap(bytesInput.readBytes32()),
                bytesInput.readLong(),
                bytesInput.readLong(),
                Wei.of(bytesInput.readLong()),
                Hash.wrap(bytesInput.readBytes32())));
  }

  public Hash getHkey() {
    return accountKey.accountHash();
  }

  public Address getAddress() {
    return accountKey.address();
  }

  public long getNonce() {
    return nonce;
  }

  public Wei getBalance() {
    return balance;
  }

  public MimcSafeBytes getCodeHash() {
    return keccakCodeHash;
  }

  public Hash getMimcCodeHash() {
    return mimcCodeHash;
  }

  public long getCodeSize() {
    return codeSize;
  }

  public Hash getStorageRoot() {
    return storageRoot;
  }

  public Bytes getEncodedBytes() {
    return Bytes.concatenate(
        LongConverter.toBytes32(nonce),
        balance,
        storageRoot,
        mimcCodeHash,
        keccakCodeHash,
        LongConverter.toBytes32(codeSize));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ZkAccount zkAccount = (ZkAccount) o;
    return codeSize == zkAccount.codeSize
        && nonce == zkAccount.nonce
        && Objects.equals(accountKey, zkAccount.accountKey)
        && Objects.equals(keccakCodeHash, zkAccount.keccakCodeHash)
        && Objects.equals(mimcCodeHash, zkAccount.mimcCodeHash)
        && Objects.equals(balance, zkAccount.balance)
        && Objects.equals(storageRoot, zkAccount.storageRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        accountKey, keccakCodeHash, mimcCodeHash, codeSize, nonce, balance, storageRoot);
  }

  @Override
  public String toString() {
    return "ZkAccount{"
        + "accountKey="
        + accountKey
        + ", keccakCodeHash="
        + keccakCodeHash
        + ", mimcCodeHash="
        + mimcCodeHash
        + ", codeSize="
        + codeSize
        + ", nonce="
        + nonce
        + ", balance="
        + balance
        + ", storageRoot="
        + storageRoot
        + '}';
  }

  public static void assertCloseEnoughForDiffing(
      final ZkAccount source, final ZkAccount account, final String context) {
    if (source == null) {
      throw new IllegalStateException(context + ": source is null but target isn't");
    } else {
      if (source.nonce != account.getNonce()) {
        throw new IllegalStateException(context + ": nonces differ");
      }
      if (!Objects.equals(source.balance, account.getBalance())) {
        throw new IllegalStateException(context + ": balances differ");
      }
      if (!Objects.equals(source.storageRoot, account.getStorageRoot())) {
        throw new IllegalStateException(context + ": Storage Roots differ");
      }
    }
  }
}
