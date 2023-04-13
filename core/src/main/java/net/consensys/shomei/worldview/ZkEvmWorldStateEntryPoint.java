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

package net.consensys.shomei.worldview;

import net.consensys.shomei.exception.MissingTrieLogException;
import net.consensys.shomei.storage.WorldStateStorage;
import net.consensys.shomei.trielog.TrieLogLayer;

import java.util.Optional;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;

public class ZkEvmWorldStateEntryPoint {

  private final ZKEvmWorldState currentWorldState;

  private final WorldStateStorage worldStateStorage;

  public ZkEvmWorldStateEntryPoint(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
    this.currentWorldState = new ZKEvmWorldState(worldStateStorage);
  }

  public void moveHead(final long newBlockNumber, final Hash blockHash)
      throws MissingTrieLogException {
    while (currentWorldState.getBlockNumber() > newBlockNumber) {
      Optional<TrieLogLayer> trieLog =
          worldStateStorage.getTrieLog(blockHash).map(RLP::input).map(TrieLogLayer::readFrom);
      if (trieLog.isPresent()) {
        ZkEvmWorldStateUpdateAccumulator accumulator = currentWorldState.getAccumulator();
        accumulator.rollForward(trieLog.get());
        currentWorldState.commit(newBlockNumber, blockHash);
      } else {
        throw new MissingTrieLogException(newBlockNumber);
      }
    }
  }
}
