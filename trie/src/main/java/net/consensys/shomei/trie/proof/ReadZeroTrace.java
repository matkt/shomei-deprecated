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

public class ReadZeroTrace implements Trace {

  private long nextFreeNode;
  public Node<Bytes> subRoot;

  public StateLeafValue leftLeaf;

  public StateLeafValue rightLeaf;

  public Proof proofLeft; // HKEY -
  public Proof proofRight; // HKEY +

  public Bytes key;

  public ReadZeroTrace(final Node<Bytes> subRoot) {
    this.subRoot = subRoot;
  }

  public long getNextFreeNode() {
    return nextFreeNode;
  }

  public void setNextFreeNode(final long nextFreeNode) {
    this.nextFreeNode = nextFreeNode;
  }

  public Node<Bytes> getSubRoot() {
    return subRoot;
  }

  public void setSubRoot(final Node<Bytes> subRoot) {
    this.subRoot = subRoot;
  }

  public StateLeafValue getLeftLeaf() {
    return leftLeaf;
  }

  public void setLeftLeaf(final StateLeafValue leftLeaf) {
    this.leftLeaf = leftLeaf;
  }

  public StateLeafValue getRightLeaf() {
    return rightLeaf;
  }

  public void setRightLeaf(final StateLeafValue rightLeaf) {
    this.rightLeaf = rightLeaf;
  }

  public Proof getProofLeft() {
    return proofLeft;
  }

  public void setProofLeft(final Proof proofLeft) {
    this.proofLeft = proofLeft;
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
}
