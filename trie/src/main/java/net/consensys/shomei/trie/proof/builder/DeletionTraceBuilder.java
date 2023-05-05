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

package net.consensys.shomei.trie.proof.builder;

import net.consensys.shomei.trie.model.LeafOpening;
import net.consensys.shomei.trie.proof.DeletionTrace;
import net.consensys.shomei.trie.proof.Proof;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.Node;

public final class DeletionTraceBuilder {
  private Bytes location = Bytes.EMPTY;
  private long newNextFreeNode;
  private Node<Bytes> oldSubRoot;
  private Node<Bytes> newSubRoot;
  private Proof leftProof;
  private Proof deletedProof;
  private Proof rightProof;
  private Bytes key;
  private LeafOpening priorLeftLeaf;
  private LeafOpening priorDeletedLeaf;
  private LeafOpening priorRightLeaf;

  private DeletionTraceBuilder() {}

  public static DeletionTraceBuilder aDeletionTrace() {
    return new DeletionTraceBuilder();
  }

  public DeletionTraceBuilder withLocation(Bytes location) {
    this.location = location;
    return this;
  }

  public DeletionTraceBuilder withNewNextFreeNode(long newNextFreeNode) {
    this.newNextFreeNode = newNextFreeNode;
    return this;
  }

  public DeletionTraceBuilder withOldSubRoot(Node<Bytes> oldSubRoot) {
    this.oldSubRoot = oldSubRoot;
    return this;
  }

  public DeletionTraceBuilder withNewSubRoot(Node<Bytes> newSubRoot) {
    this.newSubRoot = newSubRoot;
    return this;
  }

  public DeletionTraceBuilder withLeftProof(Proof leftProof) {
    this.leftProof = leftProof;
    return this;
  }

  public DeletionTraceBuilder withDeletedProof(Proof deletedProof) {
    this.deletedProof = deletedProof;
    return this;
  }

  public DeletionTraceBuilder withRightProof(Proof rightProof) {
    this.rightProof = rightProof;
    return this;
  }

  public DeletionTraceBuilder withKey(Bytes key) {
    this.key = key;
    return this;
  }

  public DeletionTraceBuilder withPriorLeftLeaf(LeafOpening priorLeftLeaf) {
    this.priorLeftLeaf = priorLeftLeaf;
    return this;
  }

  public DeletionTraceBuilder withPriorDeletedLeaf(LeafOpening priorDeletedLeaf) {
    this.priorDeletedLeaf = priorDeletedLeaf;
    return this;
  }

  public DeletionTraceBuilder withPriorRightLeaf(LeafOpening priorRightLeaf) {
    this.priorRightLeaf = priorRightLeaf;
    return this;
  }

  public DeletionTrace build() {
    return new DeletionTrace(
        location,
        newNextFreeNode,
        oldSubRoot,
        newSubRoot,
        leftProof,
        deletedProof,
        rightProof,
        key,
        priorLeftLeaf,
        priorDeletedLeaf,
        priorRightLeaf);
  }
}
