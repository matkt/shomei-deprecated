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

package net.consensys.shomei.trie.storage;

import java.util.Optional;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;

public class RocksdbLeafIndexManager implements LeafIndexManager {

  TreeMap<Bytes, Long> flatDB; // use rocksdb transaction

  public RocksdbLeafIndexManager(final TreeMap<Bytes, Long> flatDB) {
    this.flatDB = flatDB; // start rocksdb transaction
  }

  @Override
  public Optional<Long> getKeyIndex(final Hash key) {
    return Optional.ofNullable(flatDB.get(wrapKey(key)));
  }

  @Override
  public void putKeyIndex(final Hash key, final Long index) {
    // update rocksdb transaction
    flatDB.put(wrapKey(key), index);
  }

  @Override
  public void removeKeyIndex(final Hash key) {
    // update rocksdb transaction
    flatDB.remove(wrapKey(key));
  }

  @Override
  public Range getNearestKeys(final Hash key) {
    // call nearest key of rocksdb
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void commit() {
    // commit rocksdb transaction
  }

  public Bytes wrapKey(final Hash key) {
    return key;
  }

  public Hash unwrapKey(final Bytes key) {
    return Hash.wrap(Bytes32.wrap(key));
  }
}
