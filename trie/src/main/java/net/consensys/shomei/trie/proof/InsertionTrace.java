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

package net.consensys.shomei.trie.proof;

import net.consensys.shomei.trie.model.StateLeafValue;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.Node;

public class InsertionTrace implements Trace {

  private long newNextFreeNode;
  public Node<Bytes> oldSubRoot;
  public Node<Bytes> newSubRoot;

  // `New` correspond to the inserted leaf
  public Proof proofLeft; // HKEY -
  public Proof proofNew; // hash(k)
  public Proof proofRight; // HKEY +
  public Bytes key;
  public Bytes value;

  // Value of the leaf opening before being modified
  public StateLeafValue priorLeftLeaf;
  public StateLeafValue priorRightLeaf;

  public InsertionTrace(final Node<Bytes> oldSubRoot) {
    this.oldSubRoot = oldSubRoot;
  }

  public void setKey(final Bytes key) {
    this.key = key;
  }

  public void setValue(final Bytes value) {
    this.value = value;
  }

  public long getNewNextFreeNode() {
    return newNextFreeNode;
  }

  public void setNewNextFreeNode(final long newNextFreeNode) {
    this.newNextFreeNode = newNextFreeNode;
  }

  public Node<Bytes> getOldSubRoot() {
    return oldSubRoot;
  }

  public Node<Bytes> getNewSubRoot() {
    return newSubRoot;
  }

  public void setNewSubRoot(final Node<Bytes> newSubRoot) {
    this.newSubRoot = newSubRoot;
  }

  public Proof getProofLeft() {
    return proofLeft;
  }

  public void setProofLeft(final Proof proofLeft) {
    this.proofLeft = proofLeft;
  }

  public Proof getProofNew() {
    return proofNew;
  }

  public void setProofNew(final Proof proofNew) {
    this.proofNew = proofNew;
  }

  public Proof getProofRight() {
    return proofRight;
  }

  public void setProofRight(final Proof proofRight) {
    this.proofRight = proofRight;
  }

  public Bytes getKey() {
    return key;
  }

  public Bytes getValue() {
    return value;
  }

  public StateLeafValue getPriorLeftLeaf() {
    return priorLeftLeaf;
  }

  public StateLeafValue getPriorRightLeaf() {
    return priorRightLeaf;
  }

  public void setPriorLeftLeaf(final StateLeafValue priorLeftLeaf) {
    this.priorLeftLeaf = priorLeftLeaf;
  }

  public void setPriorRightLeaf(final StateLeafValue priorRightLeaf) {
    this.priorRightLeaf = priorRightLeaf;
  }

  public void load() {
    oldSubRoot.getHash();
    newSubRoot.getHash();
    proofLeft.load();
    proofNew.load();
    proofRight.load();
  }
}
