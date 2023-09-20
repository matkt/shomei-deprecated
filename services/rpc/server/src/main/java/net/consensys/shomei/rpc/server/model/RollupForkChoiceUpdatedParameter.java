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

package net.consensys.shomei.rpc.server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hyperledger.besu.datatypes.Hash;

public class RollupForkChoiceUpdatedParameter {

  private final String finalizedBlockNumber;

  private final String finalizedBlockHash;

  @JsonCreator
  public RollupForkChoiceUpdatedParameter(
      @JsonProperty("finalizedBlockNumber") final String finalizedBlockNumber,
      @JsonProperty("finalizedBlockHash") final String finalizedBlockHash) {
    this.finalizedBlockNumber = finalizedBlockNumber;
    this.finalizedBlockHash = finalizedBlockHash;
  }

  public long getFinalizedBlockNumber() {
    return Long.decode(finalizedBlockNumber);
  }

  public Hash getFinalizedBlockHash() {
    return Hash.fromHexString(finalizedBlockHash);
  }

  @Override
  public String toString() {
    return "RollupForkChoiceUpdatedParameter{"
        + "finalizedBlockNumber='"
        + finalizedBlockNumber
        + '\''
        + ", finalizedBlockHash='"
        + finalizedBlockHash
        + '\''
        + '}';
  }
}
