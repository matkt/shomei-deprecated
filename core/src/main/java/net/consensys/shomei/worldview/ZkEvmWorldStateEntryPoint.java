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
import net.consensys.shomei.observer.TrieLogObserver.TrieLogIdentifier;
import net.consensys.shomei.storage.WorldStateRepository;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;

public class ZkEvmWorldStateEntryPoint {

  private final ZKEvmWorldState currentWorldState;

  private final TrieLogLayerConverter trieLogLayerConverter;

  private final WorldStateRepository worldStateStorage;

  public ZkEvmWorldStateEntryPoint(final WorldStateRepository worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
    this.currentWorldState = new ZKEvmWorldState(worldStateStorage);
    this.trieLogLayerConverter = new TrieLogLayerConverter(worldStateStorage);
  }

  public void importBlock(final TrieLogIdentifier trieLogId, final boolean generateTrace)
      throws MissingTrieLogException {
    Optional<TrieLogLayer> trieLog =
        worldStateStorage
            .getTrieLog(trieLogId.blockNumber())
            .map(RLP::input)
            .map(trieLogLayerConverter::decodeTrieLog);
    if (trieLog.isPresent()) {
      applyTrieLog(trieLogId.blockNumber(), generateTrace, trieLog.get());
    } else {
      throw new MissingTrieLogException(trieLogId.blockNumber());
    }
  }

  @VisibleForTesting
  public void applyTrieLog(
      final long newBlockNumber, final boolean generateTrace, final TrieLogLayer trieLogLayer) {
    currentWorldState.getAccumulator().rollForward(trieLogLayer);
    currentWorldState.commit(newBlockNumber, trieLogLayer.getBlockHash(), generateTrace);
  }

  public Hash getCurrentRootHash() {
    return currentWorldState.getStateRootHash();
  }

  public Hash getCurrentBlockHash() {
    return currentWorldState.getBlockHash();
  }

  public long getCurrentBlockNumber() {
    return currentWorldState.getBlockNumber();
  }
}
