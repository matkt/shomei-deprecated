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

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public interface LeafIndexLoader {

  Optional<Long> getKeyIndex(Bytes key);

  Range getNearestKeys(Bytes key);

  class Range {
    private final Map.Entry<Bytes, UInt256> leftNode;
    private final Map.Entry<Bytes, UInt256> rightNode;

    public Range(
        final Map.Entry<Bytes, UInt256> leftNode, final Map.Entry<Bytes, UInt256> rightNode) {
      this.leftNode = leftNode;
      this.rightNode = rightNode;
    }

    public Bytes getLeftNodeKey() {
      return leftNode.getKey();
    }

    public Bytes getRightNodeKey() {
      return rightNode.getKey();
    }

    public UInt256 getLeftNodeIndex() {
      return leftNode.getValue();
    }

    public UInt256 getRightNodeIndex() {
      return rightNode.getValue();
    }
  }
}
