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

import static java.lang.String.format;
import static net.consensys.shomei.trie.node.LeafType.fromBytes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import net.consensys.shomei.trie.node.BranchNode;
import net.consensys.shomei.trie.node.EmptyLeafNode;
import net.consensys.shomei.trie.node.HeadNode;
import net.consensys.shomei.trie.node.LeafNode;
import net.consensys.shomei.trie.node.LeafType;
import net.consensys.shomei.trie.node.NextFreeNode;
import net.consensys.shomei.trie.node.TailNode;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeFactory;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.StoredNode;
import org.hyperledger.besu.plugin.data.Hash;

@SuppressWarnings("unused")
public class StoredNodeFactory implements NodeFactory<Bytes> {

  @SuppressWarnings("rawtypes")
  public static final NullNode NULL_NODE = NullNode.instance();

  @SuppressWarnings("rawtypes")
  private static final int NB_CHILD = 2;

  private final NodeLoader nodeLoader;
  private final Function<Bytes, Bytes> valueSerializer;
  private final Function<Bytes, Bytes> valueDeserializer;

  public StoredNodeFactory(
      final NodeLoader nodeLoader,
      final Function<Bytes, Bytes> valueSerializer,
      final Function<Bytes, Bytes> valueDeserializer) {
    this.nodeLoader = nodeLoader;
    this.valueSerializer = valueSerializer;
    this.valueDeserializer = valueDeserializer;
  }

  @Override
  public Node<Bytes> createExtension(final Bytes path, final Node<Bytes> child) {
    throw new UnsupportedOperationException("cannot create extension in the sparse merkle trie");
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node<Bytes> createBranch(
      final byte leftIndex, final Node<Bytes> left, final byte rightIndex, final Node<Bytes> right) {
    assert (leftIndex <= NB_CHILD);
    assert (rightIndex <= NB_CHILD);
    assert (leftIndex != rightIndex);

    final ArrayList<Node<Bytes>> children =
        new ArrayList<>(Collections.nCopies(NB_CHILD, (Node<Bytes>) NULL_NODE));

    if (leftIndex == NB_CHILD) {
      children.set(rightIndex, right);
      return createBranch(children, left.getValue());
    } else if (rightIndex == NB_CHILD) {
      children.set(leftIndex, left);
      return createBranch(children, right.getValue());
    } else {
      children.set(leftIndex, left);
      children.set(rightIndex, right);
      return createBranch(children, Optional.empty());
    }
  }

  @Override
  public Node<Bytes> createBranch(final List<Node<Bytes>> children, final Optional<Bytes> value) {
    return handleNewNode(new BranchNode<>(children, value, this, valueSerializer));
  }

  @Override
  public Node<Bytes> createLeaf(final Bytes path, final Bytes value) {
    if(fromBytes(path).equals(LeafType.HEAD)){
      return handleNewNode(new HeadNode(path, this, valueSerializer));
    } else if(fromBytes(path).equals(LeafType.TAIL)){
      return handleNewNode(new TailNode(path, this, valueSerializer));
    }else if(fromBytes(path).equals(LeafType.NEXT_FREE_NODE)){
      return handleNewNode(new NextFreeNode<>(path, value, this, valueSerializer));
    }
    return handleNewNode(new LeafNode<>(path, value, this, valueSerializer));
  }

  private Node<Bytes> handleNewNode(final Node<Bytes> node) {
    node.markDirty();
    return node;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<Node<Bytes>> retrieve(final Bytes location, final Bytes32 hash)
      throws MerkleTrieException {
    return nodeLoader
        .getNode(location, hash)
        .map(
            encodedBytes -> {
              final Node<Bytes> node =
                  decode(
                      location, encodedBytes, () -> format("Invalid RLP value for hash %s", hash));

              return node;
            });
  }

  public Node<Bytes> decode(final Bytes location, final Bytes rlp) {
    return decode(location, rlp, () -> String.format("Failed to decode value %s", rlp.toString()));
  }

  private Node<Bytes> decode(
      final Bytes location, final Bytes input, final Supplier<String> errMessage) {

    int type =
        input.size() == Hash.SIZE * 2
            ? 1
            : 2; // a leaf will only be bigger (leaf opening) or smaller (zero leaf)

    switch (type) {
      case 1 -> {
        return decodeBranch(location, input, errMessage);
      }
      case 2 -> {
        return decodeLeaf(location, input, errMessage);
      }
      default -> throw new MerkleTrieException(
          errMessage.get() + format(": invalid node %s", type));
    }
  }

  @SuppressWarnings("unchecked")
  protected BranchNode<Bytes> decodeBranch(
      final Bytes location, final Bytes input, final Supplier<String> errMessage) {
    final ArrayList<Node<Bytes>> children = new ArrayList<>(NB_CHILD);
    final int nbChilds = input.size() / Hash.SIZE;
    for (int i = 0; i < nbChilds; i++) {
      final Bytes32 childHash = Bytes32.wrap(input.slice(i*Bytes32.SIZE, Hash.SIZE));
      children.add(
          new StoredNode<>(
              this,
              location == null ? null : Bytes.concatenate(location, Bytes.of((byte) i)),
              childHash));
    }
    return new BranchNode<>(location, children, Optional.empty(), this, valueSerializer);
  }

  protected Node<Bytes> decodeLeaf(
      final Bytes location, final Bytes input, final Supplier<String> errMessage) {
    if (input.equals(Bytes32.ZERO)) {
      return EmptyLeafNode.instance();
    } else {
      return new LeafNode<>(Bytes.EMPTY, input, this, valueSerializer);
    }
  }

}
