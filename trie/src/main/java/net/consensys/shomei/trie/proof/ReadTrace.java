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

public class ReadTrace implements Trace {

  private long nextFreeNode;
  public Node<Bytes> subRoot;

  public LeafOpening leaf;

  public Proof proof;

  public Bytes key;

  public Bytes value;

  public ReadTrace(
      final long nextFreeNode,
      final Node<Bytes> subRoot,
      final LeafOpening leaf,
      final Proof proof,
      final Bytes key,
      final Bytes value) {
    this.nextFreeNode = nextFreeNode;
    this.subRoot = subRoot;
    this.leaf = leaf;
    this.proof = proof;
    this.key = key;
    this.value = value;
  }

  public ReadTrace(final Node<Bytes> subRoot) {
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

  public LeafOpening getLeaf() {
    return leaf;
  }

  public void setLeaf(final LeafOpening leaf) {
    this.leaf = leaf;
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

  public Bytes getValue() {
    return value;
  }

  public void setValue(final Bytes value) {
    this.value = value;
  }

  @Override
  public int getTransactionType() {
    return READ_TRACE_CODE;
  }

  public static ReadTrace readFrom(final RLPInput in) {
    in.enterList();
    final long newNextFreeNode = in.readLongScalar();
    final Node<Bytes> subRoot = new StoredNode<>(null, null, Hash.wrap(in.readBytes32()));
    final LeafOpening leaf = LeafOpening.readFrom(in.readBytes());
    final Proof proof = Proof.readFrom(in);
    final Bytes key = in.readBytes();
    final Bytes value = in.readBytes();
    in.leaveList();
    return new ReadTrace(newNextFreeNode, subRoot, leaf, proof, key, value);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeLongScalar(nextFreeNode);
    out.writeBytes(subRoot.getHash());
    out.writeBytes(leaf.getEncodesBytes());
    proof.writeTo(out);
    out.writeBytes(key);
    out.writeBytes(value);
    out.endList();
  }
}
