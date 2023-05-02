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

import net.consensys.shomei.trie.model.FlattenedLeaf;

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public interface StorageProxy {

  Optional<FlattenedLeaf> getFlatLeaf(final Bytes hkey);

  Range getNearestKeys(final Bytes hkey);

  Optional<Bytes> getTrieNode(final Bytes location, final Bytes nodeHash);

  /**
   * Returns an updater that can be used to update the storage.
   */
  Updater updater();

  interface Updater {

    void putFlatLeaf(final Bytes key, final FlattenedLeaf value);

    void putTrieNode(final Bytes location, final Bytes nodeHash, final Bytes value);

    void removeFlatLeafValue(final Bytes key);

    void commit();
  }

  class Range {
    private final Map.Entry<Bytes, FlattenedLeaf> leftNode;
    private final Optional<Map.Entry<Bytes, FlattenedLeaf>> centerNode;
    private final Map.Entry<Bytes, FlattenedLeaf> rightNode;

    public Range(
        final Map.Entry<Bytes, FlattenedLeaf> leftNode,
        final Optional<Map.Entry<Bytes, FlattenedLeaf>> centerNode,
        final Map.Entry<Bytes, FlattenedLeaf> rightNode) {
      this.leftNode = leftNode;
      this.centerNode = centerNode;
      this.rightNode = rightNode;
    }

    public Range(
        final Map.Entry<Bytes, FlattenedLeaf> leftNode,
        final Map.Entry<Bytes, FlattenedLeaf> rightNode) {
      this(leftNode, Optional.empty(), rightNode);
    }

    public Optional<Map.Entry<Bytes, FlattenedLeaf>> getCenterNode() {
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

    public FlattenedLeaf getLeftNodeValue() {
      return leftNode.getValue();
    }

    public Optional<FlattenedLeaf> getCenterNodeValue() {
      return centerNode.map(Map.Entry::getValue);
    }

    public FlattenedLeaf getRightNodeValue() {
      return rightNode.getValue();
    }
  }
}
