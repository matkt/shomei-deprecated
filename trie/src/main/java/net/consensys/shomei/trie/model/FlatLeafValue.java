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

package net.consensys.shomei.trie.model;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

/** Represents the raw values associated with a leaf in the flat database. */
public class FlatLeafValue {

  public static final FlatLeafValue HEAD = new FlatLeafValue(0L, LeafOpening.HEAD.getHval());

  public static final FlatLeafValue TAIL = new FlatLeafValue(1L, LeafOpening.TAIL.getHval());

  private final Long leafIndex;

  private final Bytes value;

  public FlatLeafValue(final Long leafIndex, final Bytes value) {
    this.leafIndex = leafIndex;
    this.value = value;
  }

  public Long getLeafIndex() {
    return leafIndex;
  }

  public Bytes getValue() {
    return value;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeLongScalar(leafIndex);
    out.writeBytes(value);
    out.endList();
  }

  public static FlatLeafValue readFrom(final RLPInput in) {
    in.enterList();
    final long nonce = in.readLongScalar();
    final Bytes value = in.readBytes();
    in.leaveList();
    return new FlatLeafValue(nonce, value);
  }
}
