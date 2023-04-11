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

public class ReadTrace implements Trace {

  private long nextFreeNode;
  public Node<Bytes> subRoot;

  public StateLeafValue leaf;

  public Proof proof;

  public Bytes key;

  public Bytes value;

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

  public StateLeafValue getLeaf() {
    return leaf;
  }

  public void setLeaf(final StateLeafValue leaf) {
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
}
