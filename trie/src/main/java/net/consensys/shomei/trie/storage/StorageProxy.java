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

import net.consensys.shomei.trie.model.FlatLeafValue;

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public interface StorageProxy {

  Optional<FlatLeafValue> getFlatLeaf(final Bytes hkey);

  Range getNearestKeys(final Bytes hkey);

  Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash);

  Updater updater();

  interface Updater {

    void putFlatLeaf(final Bytes key, final FlatLeafValue value);

    void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value);

    void removeFlatLeafValue(final Bytes key);
  }

  class Range {
    private final Map.Entry<Bytes, FlatLeafValue> leftNode;
    private final Optional<Map.Entry<Bytes, FlatLeafValue>> centerNode;
    private final Map.Entry<Bytes, FlatLeafValue> rightNode;

    public Range(
        final Map.Entry<Bytes, FlatLeafValue> leftNode,
        final Optional<Map.Entry<Bytes, FlatLeafValue>> centerNode,
        final Map.Entry<Bytes, FlatLeafValue> rightNode) {
      this.leftNode = leftNode;
      this.centerNode = centerNode;
      this.rightNode = rightNode;
    }

    public Range(
        final Map.Entry<Bytes, FlatLeafValue> leftNode,
        final Map.Entry<Bytes, FlatLeafValue> rightNode) {
      this(leftNode, Optional.empty(), rightNode);
    }

    public Optional<Map.Entry<Bytes, FlatLeafValue>> getCenterNode() {
      return centerNode;
    }

    public Bytes getLeftNodeKey() {
      return leftNode.getKey();
    }

    public Optional<Bytes> getCenterNodeKey() {
      return centerNode.map(Map.Entry::getKey);
    }

    public Bytes getRightNodeKey() {
      return rightNode.getKey();
    }

    public FlatLeafValue getLeftNodeValue() {
      return leftNode.getValue();
    }

    public Optional<FlatLeafValue> getCenterNodeValue() {
      return centerNode.map(Map.Entry::getValue);
    }

    public FlatLeafValue getRightNodeValue() {
      return rightNode.getValue();
    }
  }
}
