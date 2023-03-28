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

import static net.consensys.shomei.util.bytes.FieldElementsUtil.convertToSafeFieldElementsSize;
import static net.consensys.zkevm.HashProvider.keccak256;
import static net.consensys.zkevm.HashProvider.mimc;

import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trielog.TrieLogAccountValue;
import net.consensys.shomei.util.bytes.BytesInput;
import net.consensys.shomei.util.bytes.LongConverter;
import net.consensys.zkevm.HashProvider;

import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;

public class ZkAccount {

  public static final Hash EMPTY_TRIE_ROOT =
      Hash.wrap(ZKTrie.createInMemoryTrie().getTopRootHash());

  public static final Hash EMPTY_KECCAK_CODE_HASH = keccak256(Bytes.EMPTY);
  public static final Hash EMPTY_CODE_HASH = mimc(Bytes32.ZERO);

  private final boolean mutable;

  private final Supplier<Hash> hkey;
  private final Address address;
  private Hash keccakCodeHash;
  private Hash mimcCodeHash;

  private long codeSize;
  private long nonce;
  private Wei balance;
  private Hash storageRoot;

  ZkAccount(
      final Address address,
      final Hash keccakCodeHash,
      final Hash mimcCodeHash,
      final long codeSize,
      final long nonce,
      final Wei balance,
      final Hash storageRoot,
      final boolean mutable) {
    this.hkey = Suppliers.memoize(() -> HashProvider.mimc(Bytes32.leftPad(address)));
    this.address = address;
    this.nonce = nonce;
    this.balance = balance;
    this.storageRoot = storageRoot;
    this.keccakCodeHash = keccakCodeHash;
    this.mimcCodeHash = mimcCodeHash;
    this.codeSize = codeSize;
    this.mutable = mutable;
  }

  public ZkAccount(
      final Hash hkey,
      final Address address,
      final Hash keccakCodeHash,
      final Hash mimcCodeHash,
      final long codeSize,
      final long nonce,
      final Wei balance,
      final Hash storageRoot,
      final boolean mutable) {
    this.hkey = Suppliers.memoize(() -> hkey);
    this.address = address;
    this.keccakCodeHash = keccakCodeHash;
    this.mimcCodeHash = mimcCodeHash;
    this.codeSize = codeSize;
    this.nonce = nonce;
    this.balance = balance;
    this.storageRoot = storageRoot;
    this.mutable = mutable;
  }

  public ZkAccount(final Hash hkey, final Address address, final TrieLogAccountValue accountValue) {
    this(
        hkey,
        address,
        accountValue.getCodeHash(),
        accountValue.getMimcCodeHash(),
        accountValue.getCodeSize(),
        accountValue.getNonce(),
        accountValue.getBalance(),
        accountValue.getStorageRoot(),
        false);
  }

  public ZkAccount(final ZkAccount toCopy, final boolean mutable) {
    this(
        toCopy.hkey.get(),
        toCopy.address,
        toCopy.keccakCodeHash,
        toCopy.mimcCodeHash,
        toCopy.codeSize,
        toCopy.nonce,
        toCopy.balance,
        toCopy.storageRoot,
        mutable);
  }

  static ZkAccount fromEncodedBytes(
      final Address address, final Bytes encoded, final boolean mutable) {

    return BytesInput.readBytes(
        encoded,
        bytesInput ->
            new ZkAccount(
                address,
                Hash.wrap(bytesInput.readBytes32()),
                Hash.wrap(bytesInput.readBytes32()),
                bytesInput.readLong(),
                bytesInput.readLong(),
                Wei.of(bytesInput.readLong()),
                Hash.wrap(bytesInput.readBytes32()),
                mutable));
  }

  public Hash getHkey() {
    return hkey.get();
  }

  public Address getAddress() {
    return address;
  }

  public long getNonce() {
    return nonce;
  }

  public Wei getBalance() {
    return balance;
  }

  public Hash getCodeHash() {
    return keccakCodeHash;
  }

  public Hash getMimcCodeHash() {
    return mimcCodeHash;
  }

  public long getCodeSize() {
    return codeSize;
  }

  public void setKeccakCodeHash(final Hash keccakCodeHash) {
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

  public Bytes serializeAccount() {
    return Bytes.concatenate(
        LongConverter.toBytes32(nonce),
        LongConverter.toBytes32(balance.toLong(ByteOrder.BIG_ENDIAN)),
        storageRoot,
        mimcCodeHash,
        convertToSafeFieldElementsSize(keccakCodeHash),
        LongConverter.toBytes32(codeSize));
  }

  public Hash getStorageRoot() {
    return storageRoot;
  }

  public void setStorageRoot(final Hash storageRoot) {
    if (!mutable) {
      throw new UnsupportedOperationException("Account is immutable");
    }
    this.storageRoot = storageRoot;
  }

  @Override
  public String toString() {
    return "ZkAccount{"
        + "mutable="
        + mutable
        + ", hkey="
        + hkey
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
      final ZkAccount source, final TrieLogAccountValue account, final String context) {
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
