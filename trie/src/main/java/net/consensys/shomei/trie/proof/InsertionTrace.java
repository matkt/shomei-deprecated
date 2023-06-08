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

public class InsertionTrace implements Trace {

  private Bytes location;
  private long newNextFreeNode;
  public Node<Bytes> oldSubRoot;
  public Node<Bytes> newSubRoot;

  // `New` correspond to the inserted leaf
  public Proof leftProof; // HKEY -
  public Proof newProof; // hash(k)
  public Proof rightProof; // HKEY +
  public Bytes key;
  public Bytes value;

  // Value of the leaf opening before being modified
  public LeafOpening priorLeftLeaf;
  public LeafOpening priorRightLeaf;

  public InsertionTrace(
      final Bytes location,
      final long newNextFreeNode,
      final Node<Bytes> oldSubRoot,
      final Node<Bytes> newSubRoot,
      final Proof leftProof,
      final Proof newProof,
      final Proof rightProof,
      final Bytes key,
      final Bytes value,
      final LeafOpening priorLeftLeaf,
      final LeafOpening priorRightLeaf) {
    this.location = location;
    this.newNextFreeNode = newNextFreeNode;
    this.oldSubRoot = oldSubRoot;
    this.newSubRoot = newSubRoot;
    this.leftProof = leftProof;
    this.newProof = newProof;
    this.rightProof = rightProof;
    this.key = key;
    this.value = value;
    this.priorLeftLeaf = priorLeftLeaf;
    this.priorRightLeaf = priorRightLeaf;
  }

  @Override
  public Bytes getLocation() {
    return location;
  }

  @Override
  public void setLocation(final Bytes location) {
    this.location = location;
  }

  public long getNewNextFreeNode() {
    return newNextFreeNode;
  }

  public Node<Bytes> getOldSubRoot() {
    return oldSubRoot;
  }

  public Node<Bytes> getNewSubRoot() {
    return newSubRoot;
  }

  public Proof getLeftProof() {
    return leftProof;
  }

  public Proof getNewProof() {
    return newProof;
  }

  public Proof getRightProof() {
    return rightProof;
  }

  public Bytes getKey() {
    return key;
  }

  public Bytes getValue() {
    return value;
  }

  public LeafOpening getPriorLeftLeaf() {
    return priorLeftLeaf;
  }

  public LeafOpening getPriorRightLeaf() {
    return priorRightLeaf;
  }

  @Override
  public int getType() {
    return INSERTION_TRACE_CODE;
  }

  public static InsertionTrace readFrom(final RLPInput in) {
    in.enterList();
    final Bytes location;
    if (in.nextIsNull()) {
      location = Bytes.EMPTY;
      in.skipNext();
    } else {
      location = in.readBytes();
    }
    final long newNextFreeNode = in.readLongScalar();
    final Node<Bytes> oldSubRoot = new StoredNode<>(null, null, Hash.wrap(in.readBytes32()));
    final Node<Bytes> newSubRoot = new StoredNode<>(null, null, Hash.wrap(in.readBytes32()));
    final Proof leftProof = Proof.readFrom(in);
    final Proof newProof = Proof.readFrom(in);
    final Proof rightProof = Proof.readFrom(in);
    final Bytes key = in.readBytes();
    final Bytes value = in.readBytes();
    final LeafOpening priorLeftLeaf = LeafOpening.readFrom(in.readBytes());
    final LeafOpening priorRightLeaf = LeafOpening.readFrom(in.readBytes());
    in.leaveList();
    return new InsertionTrace(
        location,
        newNextFreeNode,
        oldSubRoot,
        newSubRoot,
        leftProof,
        newProof,
        rightProof,
        key,
        value,
        priorLeftLeaf,
        priorRightLeaf);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeBytes(location);
    out.writeLongScalar(newNextFreeNode);
    out.writeBytes(oldSubRoot.getHash());
    out.writeBytes(newSubRoot.getHash());
    leftProof.writeTo(out);
    newProof.writeTo(out);
    rightProof.writeTo(out);
    out.writeBytes(key);
    out.writeBytes(value);
    out.writeBytes(priorLeftLeaf.getEncodesBytes());
    out.writeBytes(priorRightLeaf.getEncodesBytes());
    out.endList();
  }
}
