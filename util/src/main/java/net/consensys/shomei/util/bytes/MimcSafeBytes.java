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

package net.consensys.shomei.util.bytes;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.DelegatingBytes;
import org.apache.tuweni.units.bigints.UInt256;

public class MimcSafeBytes extends DelegatingBytes implements Bytes {

  private final Bytes32 originalValue;

  public MimcSafeBytes(final Bytes32 delegate) {
    super(convertToSafeFieldElementsSize(delegate));
    this.originalValue = delegate;
  }

  @Override
  public String toHexString() {
    return originalValue.toHexString();
  }

  public Bytes32 getOriginalValue() {
    return originalValue;
  }

  public static UInt256 toUInt256(final Bytes value) {
    return UInt256.fromBytes(reverseConvertToSafeFieldElementSize(value));
  }

  /**
   * Fhe fields elements hold on 32 bytes but do not allow to contain 32 bytes entirely. For some
   * keys, we cannot assume that it will always fit on a field element. So we need sometimes to
   * split the key Put the first half of f into the second half of msb and the second half of f into
   * the second half of lsb The rest is zero.
   *
   * @param value to format
   * @return formated value
   */
  private static Bytes convertToSafeFieldElementsSize(final Bytes32 value) {
    Bytes32 lsb = Bytes32.leftPad(value.slice(16, 16));
    Bytes32 msb = Bytes32.leftPad(value.slice(0, 16));
    return Bytes.concatenate(lsb, msb);
  }

  private static Bytes32 reverseConvertToSafeFieldElementSize(final Bytes value) {
    Bytes lsb = value.slice(0, 16);
    Bytes msb = value.slice(16, 16);
    return Bytes32.wrap(Bytes.concatenate(msb, lsb));
  }
}
