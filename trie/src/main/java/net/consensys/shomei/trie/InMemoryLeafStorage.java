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

package net.consensys.shomei.trie;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class InMemoryLeafStorage implements LeafStorage {

  final TreeMap<Bytes32, UInt256> flatDB = new TreeMap<>(Comparator.naturalOrder());

  @Override
  public Optional<Long> getKeyIndex(final Bytes key) {
    return Optional.ofNullable(flatDB.get(key)).map(UInt256::toLong);
  }

  @Override
  public void putKeyIndex(final Bytes key, final Long index) {
    flatDB.put(Bytes32.wrap(key), UInt256.valueOf(index));
  }

  @Override
  public Range getNearestKeys(final Bytes key) {
    final Iterator<Map.Entry<Bytes32, UInt256>> iterator = flatDB.entrySet().iterator();
    Map.Entry<Bytes32, UInt256> next = Map.entry(Bytes32.ZERO, UInt256.ZERO);
    Map.Entry<Bytes32, UInt256> nearest = next;
    while (iterator.hasNext() && next.getKey().compareTo(key) <= 0) {
      nearest = next;
      next = iterator.next();
    }
    return new Range(nearest, next);
  }
}
