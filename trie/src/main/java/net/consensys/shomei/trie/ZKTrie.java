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

import static com.google.common.base.Preconditions.checkArgument;

import net.consensys.shomei.trie.StoredSparseMerkleTrie.GetAndProve;
import net.consensys.shomei.trie.model.StateLeafValue;
import net.consensys.shomei.trie.node.EmptyLeafNode;
import net.consensys.shomei.trie.proof.DeleteTrace;
import net.consensys.shomei.trie.proof.EmptyTrace;
import net.consensys.shomei.trie.proof.InsertionTrace;
import net.consensys.shomei.trie.proof.Proof;
import net.consensys.shomei.trie.proof.ReadTrace;
import net.consensys.shomei.trie.proof.ReadZeroTrace;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trie.proof.UpdateTrace;
import net.consensys.shomei.trie.storage.StorageProxy;
import net.consensys.shomei.trie.storage.StorageProxy.Range;
import net.consensys.zkevm.HashProvider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;

public class ZKTrie {

  private static final int ZK_TRIE_DEPTH = 40;

  private final PathResolver pathResolver;
  private final StoredSparseMerkleTrie state;
  private final StorageProxy worldStateStorage;

  private final StorageProxy.Updater updater;

  private ZKTrie(final Bytes32 rootHash, final StorageProxy worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
    this.updater = worldStateStorage.updater();
    this.state =
        new StoredSparseMerkleTrie(worldStateStorage::getTrieNode, rootHash, b -> b, b -> b);
    this.pathResolver = new PathResolver(ZK_TRIE_DEPTH, state);
  }

  public static ZKTrie createTrie(final StorageProxy worldStateStorage) {
    StorageProxy.Updater updater = worldStateStorage.updater();
    final ZKTrie trie =
        new ZKTrie(ZKTrie.initWorldState(updater::putTrieNode).getHash(), worldStateStorage);
    trie.setHeadAndTail();
    // updater.commit();
    return trie;
  }

  public static ZKTrie loadTrie(final Bytes32 rootHash, final StorageProxy worldStateStorage) {
    return new ZKTrie(rootHash, worldStateStorage);
  }

  private static Node<Bytes> initWorldState(final NodeUpdater nodeUpdater) {
    // if empty we need to fill the sparse trie with zero leaves
    final StoredNodeFactory nodeFactory =
        new StoredNodeFactory((location, hash) -> Optional.empty(), a -> a, b -> b);
    Node<Bytes> defaultNode = EmptyLeafNode.instance();
    for (int i = 0; i < ZK_TRIE_DEPTH; i++) {
      nodeUpdater.store(null, defaultNode.getHash(), defaultNode.getEncodedBytes());
      defaultNode = nodeFactory.createBranch(Collections.nCopies(2, defaultNode), Optional.empty());
    }
    nodeUpdater.store(null, defaultNode.getHash(), defaultNode.getEncodedBytes());

    // create root node
    defaultNode =
        nodeFactory.createBranch(List.of(EmptyLeafNode.instance(), defaultNode), Optional.empty());

    nodeUpdater.store(null, defaultNode.getHash(), defaultNode.getEncodedBytes());

    return defaultNode;
  }

  public void setHeadAndTail() {
    // head
    final Long headIndex = pathResolver.getNextFreeLeafNodeIndex();
    updater.putKeyIndex(StateLeafValue.HEAD.getHkey(), headIndex);
    state.put(pathResolver.getLeafPath(headIndex), StateLeafValue.HEAD.getEncodesBytes());
    pathResolver.incrementNextFreeLeafNodeIndex();
    // tail
    final Long tailIndex = pathResolver.getNextFreeLeafNodeIndex();
    updater.putKeyIndex(StateLeafValue.TAIL.getHkey(), tailIndex);
    state.put(pathResolver.getLeafPath(tailIndex), StateLeafValue.TAIL.getEncodesBytes());
    pathResolver.incrementNextFreeLeafNodeIndex();
  }

  public Node<Bytes> getSubRootNode() {
    return state.getNode(pathResolver.geRootPath());
  }

  public Node<Bytes> getTopRootNode() {
    return state.root;
  }

  public Bytes32 getSubRootHash() {
    return getSubRootNode().getHash();
  }

  public Bytes32 getTopRootHash() {
    return state.getRootHash();
  }

  public Optional<Bytes> get(final Hash hkey) {
    return worldStateStorage.getLeafIndex(hkey).map(pathResolver::getLeafPath).flatMap(this::get);
  }

  public Trace readZeroAndProve(final Hash hkey, final Bytes key) {
    return readAndProve(hkey, key, null);
  }

  public Trace readAndProve(final Hash hkey, final Bytes key, final Bytes value) {
    // GET the openings HKEY-,  hash(k) , HKEY+
    final Range nearestKeys = worldStateStorage.getNearestKeys(hkey);
    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeKey().isEmpty()) {
      // INIT trace
      final ReadZeroTrace readZeroTrace = new ReadZeroTrace(getSubRootNode());

      // GET path of HKey-
      final Bytes leftLeafPath = pathResolver.getLeafPath(nearestKeys.getLeftNodeIndex());
      // GET path of HKey+
      final Bytes rightLeafPath = pathResolver.getLeafPath(nearestKeys.getRightNodeIndex());

      final GetAndProve leftData = state.getAndProve(leftLeafPath);
      final GetAndProve rightData = state.getAndProve(rightLeafPath);

      readZeroTrace.setKey(key);
      readZeroTrace.setNextFreeNode(pathResolver.getNextFreeLeafNodeIndex());
      readZeroTrace.setLeftLeaf(leftData.nodeValue().map(StateLeafValue::readFrom).orElseThrow());
      readZeroTrace.setRightLeaf(rightData.nodeValue().map(StateLeafValue::readFrom).orElseThrow());
      readZeroTrace.setProofLeft(new Proof(nearestKeys.getLeftNodeIndex(), leftData.proof()));
      readZeroTrace.setProofRight(new Proof(nearestKeys.getRightNodeIndex(), rightData.proof()));

      return readZeroTrace;
    } else {
      // INIT trace
      final ReadTrace readTrace = new ReadTrace(getSubRootNode());

      // GET path of hash(k)
      final Bytes leafPath =
          pathResolver.getLeafPath(nearestKeys.getCenterNodeIndex().orElseThrow());
      // READ hash(k)
      final GetAndProve data = state.getAndProve(leafPath);

      readTrace.setKey(key);
      readTrace.setValue(value);
      readTrace.setNextFreeNode(pathResolver.getNextFreeLeafNodeIndex());
      readTrace.setLeaf(data.nodeValue().map(StateLeafValue::readFrom).orElseThrow());
      readTrace.setProof(new Proof(nearestKeys.getCenterNodeIndex().orElseThrow(), data.proof()));

      return readTrace;
    }
  }

  private Optional<Bytes> get(final Bytes path) {
    return state.get(path);
  }

  public Trace putAndProve(final Hash hKey, final Bytes key, final Bytes newValue) {
    return putAndProve(hKey, key, null, newValue);
  }

  public Trace putAndProve(
      final Hash hKey, final Bytes key, final Bytes priorValue, final Bytes newValue) {
    checkArgument(hKey.size() == Bytes32.SIZE);

    // GET the openings HKEY-,  hash(k) , HKEY+
    final Range nearestKeys = worldStateStorage.getNearestKeys(hKey);

    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeKey().isEmpty()) {

      // INIT trace
      final InsertionTrace insertionTrace = new InsertionTrace(getSubRootNode());

      // GET path of HKey-
      final Bytes leftLeafPath = pathResolver.getLeafPath(nearestKeys.getLeftNodeIndex());
      // GET path of HKey+
      final Bytes rightLeafPath = pathResolver.getLeafPath(nearestKeys.getRightNodeIndex());

      // FIND next free node
      final long nextFreeNode = pathResolver.getNextFreeLeafNodeIndex();

      // UPDATE HKey- with hash(k) for next
      final StateLeafValue priorLeftLeaf =
          get(leftLeafPath).map(StateLeafValue::readFrom).orElseThrow();

      final StateLeafValue newLeftLeaf = new StateLeafValue(priorLeftLeaf);
      newLeftLeaf.setNextLeaf(nextFreeNode);

      final List<Node<Bytes>> leftSiblings =
          state.putAndProve(leftLeafPath, newLeftLeaf.getEncodesBytes());

      // PUT hash(k) with HKey- for Prev and HKey+ for next
      final Bytes leafPathToAdd = pathResolver.getLeafPath(nextFreeNode);
      updater.putKeyIndex(hKey, nextFreeNode);
      final StateLeafValue newLeafValue =
          new StateLeafValue(
              nearestKeys.getLeftNodeIndex(),
              nearestKeys.getRightNodeIndex(),
              hKey,
              HashProvider.mimc(newValue));

      final List<Node<Bytes>> centerSiblings =
          state.putAndProve(leafPathToAdd, newLeafValue.getEncodesBytes());

      // UPDATE HKey+ with hash(k) for prev
      final StateLeafValue priorRightLeaf =
          get(rightLeafPath).map(StateLeafValue::readFrom).orElseThrow();
      final StateLeafValue newRightLeaf = new StateLeafValue(priorRightLeaf);
      newRightLeaf.setPrevLeaf(nextFreeNode);

      final List<Node<Bytes>> rightSiblings =
          state.putAndProve(rightLeafPath, newRightLeaf.getEncodesBytes());

      // UPDATE next free node
      pathResolver.incrementNextFreeLeafNodeIndex();

      insertionTrace.setKey(key);
      insertionTrace.setValue(newValue);
      insertionTrace.setPriorLeftLeaf(priorLeftLeaf);
      insertionTrace.setPriorRightLeaf(priorRightLeaf);
      insertionTrace.setProofLeft(new Proof(nearestKeys.getLeftNodeIndex(), leftSiblings));
      insertionTrace.setProofNew(new Proof(nextFreeNode, centerSiblings));
      insertionTrace.setProofRight(new Proof(nearestKeys.getRightNodeIndex(), rightSiblings));
      insertionTrace.setNewNextFreeNode(pathResolver.getNextFreeLeafNodeIndex());
      insertionTrace.setNewSubRoot(getSubRootNode());

      return insertionTrace;

    } else {

      // INIT trace
      final UpdateTrace updateTrace = new UpdateTrace(getSubRootNode());

      final Bytes leafPathToUpdate =
          pathResolver.getLeafPath(nearestKeys.getCenterNodeIndex().orElseThrow());

      // RETRIEVE OLD VALUE
      final StateLeafValue priorUpdatedLeaf =
          get(leafPathToUpdate).map(StateLeafValue::readFrom).orElseThrow();
      final StateLeafValue newUpdatedLeaf = new StateLeafValue(priorUpdatedLeaf);
      newUpdatedLeaf.setHval(HashProvider.mimc(newValue));

      final List<Node<Bytes>> siblings =
          state.putAndProve(leafPathToUpdate, newUpdatedLeaf.getEncodesBytes());

      updateTrace.setKey(key);
      updateTrace.setNewValue(newValue);
      updateTrace.setOldValue(priorValue);
      updateTrace.setNewValue(newValue);
      updateTrace.setPriorUpdatedLeaf(priorUpdatedLeaf);
      updateTrace.setProof(new Proof(nearestKeys.getCenterNodeIndex().orElseThrow(), siblings));
      updateTrace.setNewNextFreeNode(pathResolver.getNextFreeLeafNodeIndex());
      updateTrace.setNewSubRoot(getSubRootNode());

      return updateTrace;
    }
  }

  public Trace removeAndProve(final Hash hkey, final Bytes key) {
    checkArgument(hkey.size() == Bytes32.SIZE);

    // GET the openings HKEY-,  hash(k) , HKEY+
    final Range nearestKeys = worldStateStorage.getNearestKeys(hkey);

    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeIndex().isPresent()) {

      // INIT trace
      final DeleteTrace deleteTrace = new DeleteTrace(getSubRootNode());

      final Long leftLeafIndex = nearestKeys.getLeftNodeIndex();
      final Long rightLeafIndex = nearestKeys.getRightNodeIndex();

      // UPDATE HKey- with HKey+ for next
      final Bytes leftLeafPath = pathResolver.getLeafPath(leftLeafIndex);
      final StateLeafValue priorLeftLeaf =
          get(leftLeafPath).map(StateLeafValue::readFrom).orElseThrow();
      final StateLeafValue newLeftLeaf = new StateLeafValue(priorLeftLeaf);
      newLeftLeaf.setNextLeaf(rightLeafIndex);

      final List<Node<Bytes>> leftSiblings =
          state.putAndProve(leftLeafPath, newLeftLeaf.getEncodesBytes());

      // REMOVE hash(k)
      final Bytes leafPathToDelete =
          pathResolver.getLeafPath(nearestKeys.getCenterNodeIndex().orElseThrow());
      final StateLeafValue priorDeletedLeaf =
          get(leafPathToDelete).map(StateLeafValue::readFrom).orElseThrow();
      updater.removeKeyIndex(hkey);
      final List<Node<Bytes>> centerSiblings = state.removeAndProve(leafPathToDelete);

      // UPDATE HKey+ with HKey- for prev
      final Bytes rightLeafPath = pathResolver.getLeafPath(rightLeafIndex);
      final StateLeafValue priorRightLeaf =
          get(rightLeafPath).map(StateLeafValue::readFrom).orElseThrow();
      final StateLeafValue newRightLeaf = new StateLeafValue(priorRightLeaf);
      newRightLeaf.setPrevLeaf(leftLeafIndex);

      final List<Node<Bytes>> rightSiblings =
          state.putAndProve(rightLeafPath, newRightLeaf.getEncodesBytes());

      deleteTrace.setKey(key);
      deleteTrace.setPriorLeftLeaf(priorLeftLeaf);
      deleteTrace.setPriorDeletedLeaf(priorDeletedLeaf);
      deleteTrace.setPriorRightLeaf(priorRightLeaf);
      deleteTrace.setProofLeft(new Proof(nearestKeys.getLeftNodeIndex(), leftSiblings));
      deleteTrace.setProofDeleted(
          new Proof(nearestKeys.getCenterNodeIndex().orElseThrow(), centerSiblings));
      deleteTrace.setProofRight(new Proof(nearestKeys.getRightNodeIndex(), rightSiblings));
      deleteTrace.setNewNextFreeNode(pathResolver.getNextFreeLeafNodeIndex());
      deleteTrace.setNewSubRoot(getSubRootNode());

      return deleteTrace;
    }

    return new EmptyTrace();
  }

  public void decrementNextFreeNode() {
    pathResolver.decrementNextFreeLeafNodeIndex();
  }

  public void commit() {
    state.commit(updater::putTrieNode);
    // updater.commit();
  }
}
