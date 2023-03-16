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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.primitives.Longs;
import net.consensys.shomei.trie.node.EmptyLeafNode;
import net.consensys.shomei.trie.node.TrieNodePathResolver;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.trie.CommitVisitor;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;
import org.hyperledger.besu.ethereum.trie.Proof;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.patricia.RemoveVisitor;

public class ZKTrie implements MerkleTrie<Bytes, Bytes> {

  private static final int ZK_TRIE_DEPTH = 40;


  private final TrieNodePathResolver trieNodeFinder;
  private final StoredSparseMerkleTrie state;

  public ZKTrie(final KeyIndexLoader keyIndexLoader, final NodeLoader nodeLoader) {
    this(EMPTY_TRIE_NODE_HASH, keyIndexLoader, nodeLoader);
  }

  public ZKTrie(
      final Bytes32 rootHash, final KeyIndexLoader keyIndexLoader, final NodeLoader nodeLoader) {
    this.state = new StoredSparseMerkleTrie(nodeLoader, rootHash, b -> b, b -> b);
    this.trieNodeFinder = new TrieNodePathResolver(keyIndexLoader, ZK_TRIE_DEPTH, state);
  }

  public static Node<Bytes> initWorldState(final NodeUpdater nodeUpdater) {
    // if empty we need to fill the sparse trie with zero leaves
    final StoredNodeFactory nodeFactory =
        new StoredNodeFactory((location, hash) -> Optional.empty(), a -> a, b -> b);
    Node<Bytes> defaultNode = EmptyLeafNode.instance();
    for (int i = 0; i < ZK_TRIE_DEPTH; i++) {
      nodeUpdater.store(null, defaultNode.getHash(), defaultNode.getEncodedBytes());
      defaultNode = nodeFactory.createBranch(Collections.nCopies(2, defaultNode), Optional.empty());
    }
    nodeUpdater.store(null, defaultNode.getHash(), defaultNode.getEncodedBytes());

    Node<Bytes> nextFreeNode = EmptyLeafNode.instance();
    nodeUpdater.store(null, nextFreeNode.getHash(), nextFreeNode.getEncodedBytes());

    defaultNode = nodeFactory.createBranch(List.of(nextFreeNode, defaultNode), Optional.empty());

    nodeUpdater.store(null, defaultNode.getHash(), defaultNode.getEncodedBytes());

    return defaultNode;
  }

  public void setHeadAndTail(){
    //head
    putPath(trieNodeFinder.getHeadPath(), Bytes.wrap(Longs.toByteArray(0L)));
    //tail
    putPath(trieNodeFinder.getTailPath(), Bytes.wrap(Longs.toByteArray(0L)));
  }


  @Override
  public Bytes32 getRootHash() {
    return state
        .getNodePath(trieNodeFinder.geRootPath()).getHash();
  }

  public Bytes32 getTopRootHash() {
    return state.getRootHash();
  }

  @Override
  public Optional<Bytes> get(final Bytes key) {
    // use flat database
    return trieNodeFinder.getLeafPath(key).flatMap(state::getPath);
  }

  @Override
  public Optional<Bytes> getPath(final Bytes path) {
    return Optional.empty();
  }

  @Override
  public Proof<Bytes> getValueWithProof(final Bytes key) {

    return null;
  }



  @Override
  public void put(final Bytes key, final Bytes value) {
    // Check if hash(k) exist
    // IF NOT
    // FIND HKey- thanks to flat database
    // FIND HKey+ thanks to next of HKey-
    // PUT hash(k) with HKey- for Prev and HKey+ for next
    // UPDATE HKey- with hash(k) for next
    // UPDATE HKey+ with hash(k) for prev
    Bytes nodePath = trieNodeFinder.getAndIncrementNextFreeLeafPath();
    putPath(nodePath, value);
  }

  @Override
  public void putPath(final Bytes path, final Bytes value) {
    state.putPath(path, value);
  }

  @Override
  public void put(final Bytes key, final PathNodeVisitor<Bytes> putVisitor) {
    putPath(trieNodeFinder.getAndIncrementNextFreeLeafPath(), putVisitor);
  }

  @Override
  public void putPath(final Bytes path, final PathNodeVisitor<Bytes> putVisitor) {
    state.putPath(path, putVisitor);
  }

  @Override
  public void remove(final Bytes key) {
    trieNodeFinder
        .getLeafPath(key)
        .ifPresent(
            path -> {
              state.putPath(path, Bytes.EMPTY); // TODO put 0 value leaf
            });
  }

  @Override
  public void removePath(final Bytes path, final RemoveVisitor<Bytes> removeVisitor) {
    state.putPath(path, Bytes.EMPTY); // TODO put 0 value leaf
  }

  @Override
  public void commit(final NodeUpdater nodeUpdater) {
    state.commit(nodeUpdater);
  }

  @Override
  public void commit(final NodeUpdater nodeUpdater, final CommitVisitor<Bytes> commitVisitor) {
    state.commit(nodeUpdater, commitVisitor);
  }

  @Override
  public Map<Bytes32, Bytes> entriesFrom(final Bytes32 startKeyHash, final int limit) {
    return null;
  }

  @Override
  public Map<Bytes32, Bytes> entriesFrom(final Function<Node<Bytes>, Map<Bytes32, Bytes>> handler) {
    return null;
  }

  @Override
  public void visitAll(final Consumer<Node<Bytes>> nodeConsumer) {}

  @Override
  public CompletableFuture<Void> visitAll(
      final Consumer<Node<Bytes>> nodeConsumer, final ExecutorService executorService) {
    return null;
  }

  @Override
  public void visitLeafs(final TrieIterator.LeafHandler<Bytes> handler) {}
}
