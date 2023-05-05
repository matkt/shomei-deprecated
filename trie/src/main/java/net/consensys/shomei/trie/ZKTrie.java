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
import net.consensys.shomei.trie.model.FlattenedLeaf;
import net.consensys.shomei.trie.model.LeafOpening;
import net.consensys.shomei.trie.node.EmptyLeafNode;
import net.consensys.shomei.trie.path.PathResolver;
import net.consensys.shomei.trie.proof.DeletionTrace;
import net.consensys.shomei.trie.proof.EmptyTrace;
import net.consensys.shomei.trie.proof.InsertionTrace;
import net.consensys.shomei.trie.proof.Proof;
import net.consensys.shomei.trie.proof.ReadTrace;
import net.consensys.shomei.trie.proof.ReadZeroTrace;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trie.proof.UpdateTrace;
import net.consensys.shomei.trie.storage.InMemoryStorage;
import net.consensys.shomei.trie.storage.StorageProxy;
import net.consensys.shomei.trie.storage.StorageProxy.Range;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
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

  public static final Hash EMPTY_TRIE_ROOT =
      Hash.wrap(ZKTrie.createTrie(new InMemoryStorage()).getTopRootHash());

  public static final Hash EMPTY_TRIE_SUB_ROOT =
      Hash.wrap(ZKTrie.createTrie(new InMemoryStorage()).getSubRootHash());

  private static final int ZK_TRIE_DEPTH = 40;

  private final PathResolver pathResolver;
  private final StoredSparseMerkleTrie state;
  private final StorageProxy worldStateStorage;

  private final StorageProxy.Updater updater;

  private ZKTrie(final Bytes32 rootHash, final StorageProxy worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
    this.updater = worldStateStorage.updater();
    this.state = new StoredSparseMerkleTrie(worldStateStorage::getTrieNode, rootHash, b -> b);
    this.pathResolver = new PathResolver(ZK_TRIE_DEPTH, state);
  }

  public static ZKTrie createTrie(final StorageProxy worldStateStorage) {
    StorageProxy.Updater updater = worldStateStorage.updater();
    final ZKTrie trie =
        new ZKTrie(ZKTrie.initWorldState(updater::putTrieNode).getHash(), worldStateStorage);
    trie.setHeadAndTail();
    return trie;
  }

  public static ZKTrie loadTrie(final Bytes32 rootHash, final StorageProxy worldStateStorage) {
    return new ZKTrie(rootHash, worldStateStorage);
  }

  private static Node<Bytes> initWorldState(final NodeUpdater nodeUpdater) {
    // if empty we need to fill the sparse trie with zero leaves
    final StoredNodeFactory nodeFactory =
        new StoredNodeFactory((location, hash) -> Optional.empty(), a -> a);
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
    updater.putFlatLeaf(LeafOpening.HEAD.getHkey(), FlattenedLeaf.HEAD);
    state.put(pathResolver.getLeafPath(headIndex), LeafOpening.HEAD.getEncodesBytes());
    pathResolver.incrementNextFreeLeafNodeIndex();
    // tail
    final Long tailIndex = pathResolver.getNextFreeLeafNodeIndex();
    updater.putFlatLeaf(LeafOpening.TAIL.getHkey(), FlattenedLeaf.TAIL);
    state.put(pathResolver.getLeafPath(tailIndex), LeafOpening.TAIL.getEncodesBytes());
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

  public long getNextFreeNode() {
    return pathResolver.getNextFreeLeafNodeIndex();
  }

  public Optional<Long> getLeafIndex(final Hash hkey) {
    return worldStateStorage.getFlatLeaf(hkey).map(FlattenedLeaf::leafIndex);
  }

  public Optional<Bytes> get(final Hash hkey) {
    return worldStateStorage
        .getFlatLeaf(hkey)
        .map(FlattenedLeaf::leafIndex)
        .map(pathResolver::getLeafPath)
        .flatMap(this::get);
  }

  public Trace readAndProve(final Hash hkey, final Bytes key) {
    // GET the openings HKEY-,  hash(k) , HKEY+
    final Range nearestKeys = worldStateStorage.getNearestKeys(hkey);
    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeKey().isEmpty()) {
      // INIT trace
      final ReadZeroTrace readZeroTrace = new ReadZeroTrace(getSubRootNode());

      // GET path of HKey-
      final Bytes leftLeafPath =
          pathResolver.getLeafPath(nearestKeys.getLeftNodeValue().leafIndex());
      // GET path of HKey+
      final Bytes rightLeafPath =
          pathResolver.getLeafPath(nearestKeys.getRightNodeValue().leafIndex());

      final GetAndProve leftData = state.getAndProve(leftLeafPath);
      final GetAndProve rightData = state.getAndProve(rightLeafPath);

      readZeroTrace.setKey(key);
      readZeroTrace.setNextFreeNode(pathResolver.getNextFreeLeafNodeIndex());
      readZeroTrace.setLeftLeaf(leftData.nodeValue().map(LeafOpening::readFrom).orElseThrow());
      readZeroTrace.setRightLeaf(rightData.nodeValue().map(LeafOpening::readFrom).orElseThrow());
      readZeroTrace.setLeftProof(
          new Proof(nearestKeys.getLeftNodeValue().leafIndex(), leftData.proof()));
      readZeroTrace.setRightProof(
          new Proof(nearestKeys.getRightNodeValue().leafIndex(), rightData.proof()));

      return readZeroTrace;
    } else {
      // INIT trace
      final ReadTrace readTrace = new ReadTrace(getSubRootNode());

      final FlattenedLeaf currentFlatLeafValue = nearestKeys.getCenterNodeValue().orElseThrow();

      // GET path of hash(k)
      final Bytes leafPath = pathResolver.getLeafPath(currentFlatLeafValue.leafIndex());
      // READ hash(k)
      final GetAndProve data = state.getAndProve(leafPath);

      readTrace.setKey(key);
      readTrace.setValue(currentFlatLeafValue.leafValue());
      readTrace.setNextFreeNode(pathResolver.getNextFreeLeafNodeIndex());
      readTrace.setLeaf(data.nodeValue().map(LeafOpening::readFrom).orElseThrow());
      readTrace.setProof(new Proof(currentFlatLeafValue.leafIndex(), data.proof()));

      return readTrace;
    }
  }

  private Optional<Bytes> get(final Bytes path) {
    return state.get(path);
  }

  public Trace putAndProve(
      final Hash hKey,
      final MimcSafeBytes<? extends Bytes> key,
      final MimcSafeBytes<? extends Bytes> newValue) {
    checkArgument(hKey.size() == Bytes32.SIZE);

    // GET the openings HKEY-,  hash(k) , HKEY+
    final Range nearestKeys = worldStateStorage.getNearestKeys(hKey);

    // CHECK if hash(k) exist
    if (nearestKeys.getCenterNodeKey().isEmpty()) {

      // INIT trace
      final InsertionTrace insertionTrace = new InsertionTrace(getSubRootNode());

      // GET path of HKey-
      final Bytes leftLeafPath =
          pathResolver.getLeafPath(nearestKeys.getLeftNodeValue().leafIndex());
      // GET path of HKey+
      final Bytes rightLeafPath =
          pathResolver.getLeafPath(nearestKeys.getRightNodeValue().leafIndex());

      // FIND next free node
      final long nextFreeNode = pathResolver.getNextFreeLeafNodeIndex();

      // UPDATE HKey- with hash(k) for next
      final LeafOpening priorLeftLeaf = get(leftLeafPath).map(LeafOpening::readFrom).orElseThrow();

      final LeafOpening newLeftLeaf = new LeafOpening(priorLeftLeaf);
      newLeftLeaf.setNextLeaf(nextFreeNode);

      final List<Node<Bytes>> leftSiblings =
          state.putAndProve(leftLeafPath, newLeftLeaf.getEncodesBytes());

      // PUT hash(k) with HKey- for Prev and HKey+ for next
      final Bytes leafPathToAdd = pathResolver.getLeafPath(nextFreeNode);
      updater.putFlatLeaf(hKey, new FlattenedLeaf(nextFreeNode, newValue.getOriginalUnsafeValue()));
      final LeafOpening newLeafValue =
          new LeafOpening(
              nearestKeys.getLeftNodeValue().leafIndex(),
              nearestKeys.getRightNodeValue().leafIndex(),
              hKey,
              HashProvider.mimc(newValue));

      final List<Node<Bytes>> centerSiblings =
          state.putAndProve(leafPathToAdd, newLeafValue.getEncodesBytes());

      // UPDATE HKey+ with hash(k) for prev
      final LeafOpening priorRightLeaf =
          get(rightLeafPath).map(LeafOpening::readFrom).orElseThrow();
      final LeafOpening newRightLeaf = new LeafOpening(priorRightLeaf);
      newRightLeaf.setPrevLeaf(nextFreeNode);

      final List<Node<Bytes>> rightSiblings =
          state.putAndProve(rightLeafPath, newRightLeaf.getEncodesBytes());

      // UPDATE next free node
      pathResolver.incrementNextFreeLeafNodeIndex();

      insertionTrace.setKey(key);
      insertionTrace.setValue(newValue.getOriginalUnsafeValue());
      insertionTrace.setPriorLeftLeaf(priorLeftLeaf);
      insertionTrace.setPriorRightLeaf(priorRightLeaf);
      insertionTrace.setLeftProof(
          new Proof(nearestKeys.getLeftNodeValue().leafIndex(), leftSiblings));
      insertionTrace.setNewProof(new Proof(nextFreeNode, centerSiblings));
      insertionTrace.setRightProof(
          new Proof(nearestKeys.getRightNodeValue().leafIndex(), rightSiblings));
      insertionTrace.setNewNextFreeNode(pathResolver.getNextFreeLeafNodeIndex());
      insertionTrace.setNewSubRoot(getSubRootNode());

      return insertionTrace;

    } else {

      // INIT trace
      final UpdateTrace updateTrace = new UpdateTrace(getSubRootNode());

      final FlattenedLeaf currentFlatLeafValue = nearestKeys.getCenterNodeValue().orElseThrow();

      final Bytes leafPathToUpdate = pathResolver.getLeafPath(currentFlatLeafValue.leafIndex());
      updater.putFlatLeaf(
          hKey,
          new FlattenedLeaf(currentFlatLeafValue.leafIndex(), newValue.getOriginalUnsafeValue()));

      // RETRIEVE OLD VALUE
      final LeafOpening priorUpdatedLeaf =
          get(leafPathToUpdate).map(LeafOpening::readFrom).orElseThrow();
      final LeafOpening newUpdatedLeaf = new LeafOpening(priorUpdatedLeaf);
      newUpdatedLeaf.setHval(HashProvider.mimc(newValue));

      final List<Node<Bytes>> siblings =
          state.putAndProve(leafPathToUpdate, newUpdatedLeaf.getEncodesBytes());

      updateTrace.setKey(key);
      updateTrace.setOldValue(currentFlatLeafValue.leafValue());
      updateTrace.setNewValue(newValue.getOriginalUnsafeValue());
      updateTrace.setPriorUpdatedLeaf(priorUpdatedLeaf);
      updateTrace.setProof(new Proof(currentFlatLeafValue.leafIndex(), siblings));
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
    if (nearestKeys.getCenterNode().isPresent()) {

      // INIT trace
      final DeletionTrace deleteTrace = new DeletionTrace(getSubRootNode());

      final Long leftLeafIndex = nearestKeys.getLeftNodeValue().leafIndex();
      final Long rightLeafIndex = nearestKeys.getRightNodeValue().leafIndex();

      // UPDATE HKey- with HKey+ for next
      final Bytes leftLeafPath = pathResolver.getLeafPath(leftLeafIndex);
      final LeafOpening priorLeftLeaf = get(leftLeafPath).map(LeafOpening::readFrom).orElseThrow();
      final LeafOpening newLeftLeaf = new LeafOpening(priorLeftLeaf);
      newLeftLeaf.setNextLeaf(rightLeafIndex);

      final List<Node<Bytes>> leftSiblings =
          state.putAndProve(leftLeafPath, newLeftLeaf.getEncodesBytes());

      // REMOVE hash(k)
      final Bytes leafPathToDelete =
          pathResolver.getLeafPath(nearestKeys.getCenterNodeValue().orElseThrow().leafIndex());
      final LeafOpening priorDeletedLeaf =
          get(leafPathToDelete).map(LeafOpening::readFrom).orElseThrow();
      updater.removeFlatLeafValue(hkey);
      final List<Node<Bytes>> centerSiblings = state.removeAndProve(leafPathToDelete);

      // UPDATE HKey+ with HKey- for prev
      final Bytes rightLeafPath = pathResolver.getLeafPath(rightLeafIndex);
      final LeafOpening priorRightLeaf =
          get(rightLeafPath).map(LeafOpening::readFrom).orElseThrow();
      final LeafOpening newRightLeaf = new LeafOpening(priorRightLeaf);
      newRightLeaf.setPrevLeaf(leftLeafIndex);

      final List<Node<Bytes>> rightSiblings =
          state.putAndProve(rightLeafPath, newRightLeaf.getEncodesBytes());

      deleteTrace.setKey(key);
      deleteTrace.setPriorLeftLeaf(priorLeftLeaf);
      deleteTrace.setPriorDeletedLeaf(priorDeletedLeaf);
      deleteTrace.setPriorRightLeaf(priorRightLeaf);
      deleteTrace.setLeftProof(new Proof(nearestKeys.getLeftNodeValue().leafIndex(), leftSiblings));
      deleteTrace.setDeletedProof(
          new Proof(nearestKeys.getCenterNodeValue().orElseThrow().leafIndex(), centerSiblings));
      deleteTrace.setRightProof(
          new Proof(nearestKeys.getRightNodeValue().leafIndex(), rightSiblings));
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
  }
}
