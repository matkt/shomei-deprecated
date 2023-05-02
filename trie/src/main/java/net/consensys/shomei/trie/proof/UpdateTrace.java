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
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StoredNode;

public class UpdateTrace implements Trace {

  private long newNextFreeNode;
  public Node<Bytes> oldSubRoot;
  public Node<Bytes> newSubRoot;

  public Proof proof;

  public Bytes key;
  public Bytes oldValue;
  public Bytes newValue;

  // Value of the leaf opening before being modified
  public LeafOpening priorUpdatedLeaf;

  public UpdateTrace(
      final long newNextFreeNode,
      final Node<Bytes> oldSubRoot,
      final Node<Bytes> newSubRoot,
      final Proof proof,
      final Bytes key,
      final Bytes oldValue,
      final Bytes newValue,
      final LeafOpening priorUpdatedLeaf) {
    this.newNextFreeNode = newNextFreeNode;
    this.oldSubRoot = oldSubRoot;
    this.newSubRoot = newSubRoot;
    this.proof = proof;
    this.key = key;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.priorUpdatedLeaf = priorUpdatedLeaf;
  }

  public UpdateTrace(final Node<Bytes> oldSubRoot) {
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

  public Proof getProof() {
    return proof;
  }

  public void setProof(final Proof proof) {
    this.proof = proof;
  }

  public Bytes getKey() {
    return key;
  }

  public void setKey(final Bytes key) {
    this.key = key;
  }

  public Bytes getOldValue() {
    return oldValue;
  }

  public void setOldValue(final Bytes oldValue) {
    this.oldValue = oldValue;
  }

  public Bytes getNewValue() {
    return newValue;
  }

  public void setNewValue(final Bytes newValue) {
    this.newValue = newValue;
  }

  public LeafOpening getPriorUpdatedLeaf() {
    return priorUpdatedLeaf;
  }

  public void setPriorUpdatedLeaf(final LeafOpening priorUpdatedLeaf) {
    this.priorUpdatedLeaf = priorUpdatedLeaf;
  }

  @Override
  public int getTransactionType() {
    return UPDATE_TRACE_CODE;
  }

  public static UpdateTrace readFrom(final RLPInput in) {
    in.enterList();
    final long newNextFreeNode = in.readLongScalar();
    final Node<Bytes> oldSubRoot = new StoredNode<>(null, null, Hash.wrap(in.readBytes32()));
    final Node<Bytes> newSubRoot = new StoredNode<>(null, null, Hash.wrap(in.readBytes32()));
    final Proof proof = Proof.readFrom(in);
    final Bytes key = in.readBytes();
    final Bytes oldValue = in.readBytes();
    final Bytes newValue = in.readBytes();
    final LeafOpening priorUpdatedLeaf = LeafOpening.readFrom(in.readBytes());
    in.leaveList();
    return new UpdateTrace(
        newNextFreeNode, oldSubRoot, newSubRoot, proof, key, oldValue, newValue, priorUpdatedLeaf);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeLongScalar(newNextFreeNode);
    out.writeBytes(oldSubRoot.getHash());
    out.writeBytes(newSubRoot.getHash());
    proof.writeTo(out);
    out.writeBytes(key);
    out.writeBytes(oldValue);
    out.writeBytes(newValue);
    out.writeBytes(priorUpdatedLeaf.getEncodesBytes());
    out.endList();
  }
}
