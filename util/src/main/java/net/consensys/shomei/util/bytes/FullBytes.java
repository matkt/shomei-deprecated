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

public class FullBytes extends DelegatingBytes implements Bytes {

  private final Bytes32 originalValue;

  public FullBytes(final Bytes32 delegate) {
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

  private static Bytes convertToSafeFieldElementsSize(Bytes32 value) {
    Bytes32 lsb = Bytes32.leftPad(value.slice(16, 16));
    Bytes32 msb = Bytes32.leftPad(value.slice(0, 16));
    return Bytes.concatenate(lsb, msb);
  }
}
