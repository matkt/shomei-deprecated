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

package net.consensys.shomei.rpc.trielog;

import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hyperledger.besu.datatypes.Hash;

public class SendRawTrieLogParameter {

  private final Long blockNumber;

  private final Hash blockHash;

  private final String trieLog;

  public Long getBlockNumber() {
    return blockNumber;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public String getTrieLog() {
    return trieLog;
  }

  public TrieLogIdentifier getTrieLogIdentifier() {
    return new TrieLogIdentifier(blockNumber, blockHash);
  }

  @JsonCreator
  public SendRawTrieLogParameter(
      @JsonProperty("blockNumber") final Long blockNumber,
      @JsonProperty("blockHash") final Hash blockHash,
      @JsonProperty("trieLog") final String trieLog) {
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.trieLog = trieLog;
  }

  @Override
  public String toString() {
    return "blockNumber=" + blockNumber + ", blockHash=" + blockHash + ", trieLog=" + trieLog;
  }
}
