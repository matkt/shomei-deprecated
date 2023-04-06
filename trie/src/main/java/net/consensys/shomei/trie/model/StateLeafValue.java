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

import net.consensys.shomei.util.bytes.BytesInput;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;

/** Represents the raw values associated with a leaf in the flat database. */
public class StateLeafValue {

  public static final StateLeafValue HEAD =
      new StateLeafValue(0, 1, Hash.wrap(Bytes32.ZERO), Bytes32.ZERO);

  public static final StateLeafValue TAIL =
      new StateLeafValue(0, 1, Hash.wrap(Bytes32.repeat((byte) 0xff)), Bytes32.ZERO);

  private final Hash hkey;

  private Bytes hval;

  private long prevLeaf;

  private long nextLeaf;

  public StateLeafValue(final Hash hkey, final UInt256 hval) {
    this.hkey = hkey;
    this.hval = hval;
    this.prevLeaf = 0;
    this.nextLeaf = 1;
  }

  public StateLeafValue(
      final long prevLeaf, final long nextLeaf, final Hash hkey, final Bytes hval) {
    this.hkey = hkey;
    this.hval = hval;
    this.prevLeaf = prevLeaf;
    this.nextLeaf = nextLeaf;
  }

  public StateLeafValue(final StateLeafValue stateLeafValue) {
    this.hkey = stateLeafValue.hkey;
    this.hval = stateLeafValue.hval;
    this.prevLeaf = stateLeafValue.prevLeaf;
    this.nextLeaf = stateLeafValue.nextLeaf;
  }

  public long getPrevLeaf() {
    return prevLeaf;
  }

  public long getNextLeaf() {
    return nextLeaf;
  }

  public void setPrevLeaf(final long prevLeaf) {
    this.prevLeaf = prevLeaf;
  }

  public void setNextLeaf(final long nextLeaf) {
    this.nextLeaf = nextLeaf;
  }

  public Hash getHkey() {
    return hkey;
  }

  public Bytes getHval() {
    return hval;
  }

  public void setHval(Bytes hval) {
    this.hval = hval;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StateLeafValue that = (StateLeafValue) o;
    return prevLeaf == that.prevLeaf
        && nextLeaf == that.nextLeaf
        && Objects.equals(hkey, that.hkey)
        && Objects.equals(hval, that.hval);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hkey, hval, prevLeaf, nextLeaf);
  }

  public static StateLeafValue readFrom(final Bytes encodedBytes) {
    return BytesInput.readBytes(
        encodedBytes,
        bytesInput ->
            new StateLeafValue(
                bytesInput.readUInt256().toLong(),
                bytesInput.readUInt256().toLong(),
                Hash.wrap(bytesInput.readBytes32()),
                bytesInput.readBytes32()));
  }

  public Bytes getEncodesBytes() {
    return Bytes.concatenate(
        UInt256.valueOf(prevLeaf), // Prev
        UInt256.valueOf(nextLeaf), // Next ,
        hkey, // HKEY
        hval); // HVALUE
  }
}
