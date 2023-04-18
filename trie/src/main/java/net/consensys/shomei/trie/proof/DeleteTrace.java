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

import net.consensys.shomei.trie.model.LeafOpening;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.Node;

public class DeleteTrace implements Trace {

  private long newNextFreeNode;
  public Node<Bytes> oldSubRoot;
  public Node<Bytes> newSubRoot;

  // `New` correspond to the inserted leaf
  public Proof proofLeft; // HKEY -
  public Proof proofDeleted; // hash(k)
  public Proof proofRight; // HKEY +
  public Bytes key;

  // Value of the leaf opening before being modified
  public LeafOpening priorLeftLeaf;
  public LeafOpening priorDeletedLeaf;
  public LeafOpening priorRightLeaf;

  public DeleteTrace(final Node<Bytes> oldSubRoot) {
    this.oldSubRoot = oldSubRoot;
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

  public Proof getProofDeleted() {
    return proofDeleted;
  }

  public void setProofDeleted(final Proof proofDeleted) {
    this.proofDeleted = proofDeleted;
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

  public void setKey(final Bytes key) {
    this.key = key;
  }

  public LeafOpening getPriorLeftLeaf() {
    return priorLeftLeaf;
  }

  public void setPriorLeftLeaf(final LeafOpening priorLeftLeaf) {
    this.priorLeftLeaf = priorLeftLeaf;
  }

  public LeafOpening getPriorDeletedLeaf() {
    return priorDeletedLeaf;
  }

  public void setPriorDeletedLeaf(final LeafOpening priorDeletedLeaf) {
    this.priorDeletedLeaf = priorDeletedLeaf;
  }

  public LeafOpening getPriorRightLeaf() {
    return priorRightLeaf;
  }

  public void setPriorRightLeaf(final LeafOpening priorRightLeaf) {
    this.priorRightLeaf = priorRightLeaf;
  }
}
