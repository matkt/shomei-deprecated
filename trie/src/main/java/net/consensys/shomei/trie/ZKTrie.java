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
import net.consensys.shomei.trie.model.StateLeafValue;
import net.consensys.shomei.trie.node.EmptyLeafNode;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
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


  private final PathResolver pathResolver;
  private final StoredSparseMerkleTrie state;
  private final LeafStorage leafStorage;

  public ZKTrie(final LeafStorage leafStorage, final NodeLoader nodeLoader) {
    this(EMPTY_TRIE_NODE_HASH, leafStorage, nodeLoader);
  }

  public ZKTrie(
          final Bytes32 rootHash, final LeafStorage leafStorage, final NodeLoader nodeLoader) {
    this.leafStorage = leafStorage;
    this.state = new StoredSparseMerkleTrie(nodeLoader, rootHash, b -> b, b -> b);
    this.pathResolver = new PathResolver(ZK_TRIE_DEPTH, state);
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
    final Long headIndex = pathResolver.getAndIncrementNextFreeLeafIndex();
    leafStorage.putKeyIndex(StateLeafValue.HEAD.getHkey(), headIndex);
    putPath(pathResolver.getLeafPath(headIndex), StateLeafValue.HEAD.getEncodesBytes());
    //tail
    final Long tailIndex = pathResolver.getAndIncrementNextFreeLeafIndex();
    leafStorage.putKeyIndex(StateLeafValue.TAIL.getHkey(), tailIndex);
    putPath(pathResolver.getLeafPath(tailIndex), StateLeafValue.TAIL.getEncodesBytes());
  }


  @Override
  public Bytes32 getRootHash() {
    return state
        .getNodePath(pathResolver.geRootPath()).getHash();
  }

  public Bytes32 getTopRootHash() {
    return state.getRootHash();
  }

  @Override
  public Optional<Bytes> get(final Bytes key) {
    // use flat database
    return leafStorage.getKeyIndex(key).map(pathResolver::getLeafPath).flatMap(state::getPath);
  }

  @Override
  public Optional<Bytes>  getPath(final Bytes path) {
    return state.getPath(path);
  }

  @Override
  public void put(final Bytes key, final Bytes value) {
    final LeafStorage.Range nearestKeys = leafStorage.getNearestKeys(key); // find HKey- and HKey+
    // Check if hash(k) exist
    if(!nearestKeys.getLeftNodeKey().equals(key)){
      // HKey-
      final Bytes nearestKeyPath = pathResolver.getLeafPath(nearestKeys.getLeftNodeIndex().toLong());
      // HKey+
      final Bytes nearestNextKeyPath = pathResolver.getLeafPath(nearestKeys.getRightNodeIndex().toLong());

      // PUT hash(k) with HKey- for Prev and HKey+ for next
      final long nextFreeNode = pathResolver.getAndIncrementNextFreeLeafIndex();
      final Bytes newKeyPath = pathResolver.getLeafPath(nextFreeNode);
      leafStorage.putKeyIndex(key,nextFreeNode);
      final StateLeafValue newKey = new StateLeafValue(
              nearestKeys.getLeftNodeIndex(), nearestKeys.getRightNodeIndex(),Bytes32.wrap(key), value);
      putPath(newKeyPath, newKey.getEncodesBytes());
     
      // UPDATE HKey- with hash(k) for next
      final StateLeafValue leftKey = getPath(nearestKeyPath).map(StateLeafValue::readFrom).orElseThrow();
      leftKey.setNextLeaf(UInt256.valueOf(nextFreeNode));
      putPath(nearestKeyPath, leftKey.getEncodesBytes());

      // UPDATE HKey+ with hash(k) for prev
      final StateLeafValue rightKey = getPath(nearestNextKeyPath).map(StateLeafValue::readFrom).orElseThrow();
      rightKey.setPrevLeaf(UInt256.valueOf(nextFreeNode));
      putPath(nearestNextKeyPath, rightKey.getEncodesBytes());
    }else {
      final Bytes updatedKeyPath = pathResolver.getLeafPath(nearestKeys.getLeftNodeIndex().toLong());
      final StateLeafValue updatedKey = getPath(updatedKeyPath).map(StateLeafValue::readFrom).orElseThrow();
      updatedKey.setValue(value);
      putPath(updatedKeyPath, updatedKey.getEncodesBytes());
    }
  }

  @Override
  public void putPath(final Bytes path, final Bytes value) {
    state.putPath(path, value);
  }

  @Override
  public void remove(final Bytes key) {

    final LeafStorage.Range nearestKeys = leafStorage.getNearestKeys(key); // find HKey- and HKey+
    // Check if hash(k) exist
    if(nearestKeys.getLeftNodeKey().equals(key)){
      // hash(k)
      final Bytes keyPathToDelete = pathResolver.getLeafPath(nearestKeys.getLeftNodeIndex().toLong());
      final StateLeafValue keyToDelete = getPath(keyPathToDelete).map(StateLeafValue::readFrom).orElseThrow();

      // HKey-
      final Bytes prevKeyPath = pathResolver.getLeafPath(keyToDelete.getPrevLeaf().toLong());
      final StateLeafValue prevKey = getPath(prevKeyPath).map(StateLeafValue::readFrom).orElseThrow();
      prevKey.setNextLeaf(keyToDelete.getNextLeaf());
      putPath(prevKeyPath, prevKey.getEncodesBytes());

      // HKey+
      final Bytes nextKeyPath = pathResolver.getLeafPath(keyToDelete.getNextLeaf().toLong());
      final StateLeafValue nextKey = getPath(nextKeyPath).map(StateLeafValue::readFrom).orElseThrow();
      nextKey.setPrevLeaf(keyToDelete.getPrevLeaf());
      putPath(nextKeyPath, nextKey.getEncodesBytes());

      // remove hash(k)
      removePath(keyPathToDelete, null); //TODO Clean this remove part
    }
  }

  @Override
  public void removePath(final Bytes path, final RemoveVisitor<Bytes> removeVisitor) {
    state.removePath(path,  (RemoveVisitor<Bytes>) state.getRemoveVisitor());
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
  public void put(final Bytes key, final PathNodeVisitor<Bytes> putVisitor) {

  }

  @Override
  public void putPath(final Bytes path, final PathNodeVisitor<Bytes> putVisitor) {

  }


  @Override
  public Proof<Bytes> getValueWithProof(final Bytes key) {
    return null;
  }


  @Override
  public void visitLeafs(final TrieIterator.LeafHandler<Bytes> handler) {}
}
