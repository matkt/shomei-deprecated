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

import net.consensys.shomei.ZkAccount;
import net.consensys.shomei.exception.MissingTrieLogException;
import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.storage.WorldStateStorage;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trielog.AccountKey;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.TrieLogLayerConverter;
import net.consensys.shomei.util.bytes.MimcSafeBytes;

import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.Node;

public class ZkEvmWorldStateEntryPoint implements TrieLogObserver {

  private final ZKEvmWorldState currentWorldState;

  private final TrieLogLayerConverter trieLogLayerConverter;

  private final WorldStateStorage worldStateStorage;

  public ZkEvmWorldStateEntryPoint(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
    this.currentWorldState = new ZKEvmWorldState(worldStateStorage);
    this.trieLogLayerConverter = new TrieLogLayerConverter(worldStateStorage);
    TrieLogLayer trieLogLayer = new TrieLogLayer();
    trieLogLayer.addAccountChange(
        MimcSafeBytes.safeAddress(
            Address.fromHexString("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")),
        null,
        new ZkAccount(
            new AccountKey(Address.fromHexString("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")),
            0L,
            Wei.fromHexString("0xad78ebc5ac6200000"),
            ZKTrie.EMPTY_TRIE_ROOT,
            ZkAccount.EMPTY_CODE_HASH,
            ZkAccount.EMPTY_KECCAK_CODE_HASH,
            0L),
        false);
    currentWorldState.getAccumulator().rollForward(trieLogLayer);
    currentWorldState.commit(0, Hash.EMPTY);
  }

  public void moveHead(final long newBlockNumber, final Hash blockHash)
      throws MissingTrieLogException {
    while (currentWorldState.getBlockNumber() < newBlockNumber) {
      Optional<TrieLogLayer> trieLog =
          worldStateStorage
              .getTrieLog(blockHash)
              .map(RLP::input)
              .map(trieLogLayerConverter::decodeTrieLog);
      if (trieLog.isPresent()) {
        moveHead(newBlockNumber, trieLog.get());
      } else {
        throw new MissingTrieLogException(newBlockNumber);
      }
    }
  }

  public void moveHead(final long newBlockNumber, final TrieLogLayer trieLogLayer) {
    currentWorldState.getAccumulator().rollForward(trieLogLayer);
    currentWorldState.commit(newBlockNumber, trieLogLayer.getBlockHash());
  }

  public Hash getCurrentRootHash() {
    return currentWorldState.getStateRootHash();
  }

  static int block = 1;

  @Override
  public void onTrieLogAdded(final Hash blockHash) {
    // receive trie log
    System.out.println("receive trie log " + blockHash);
    try {
      moveHead(++block, blockHash);
    } catch (MissingTrieLogException e) {
      throw new RuntimeException(e);
    }
    // TODO use Jackson
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(
                Node.class,
                (JsonSerializer<Node<Bytes>>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.getHash().toHexString()))
            .registerTypeAdapter(
                UInt256.class,
                (JsonSerializer<UInt256>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
            .registerTypeAdapter(
                Hash.class,
                (JsonSerializer<Hash>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
            .registerTypeAdapter(
                Bytes.class,
                (JsonSerializer<Bytes>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
            .create();
    System.out.println(gson.toJson(currentWorldState.getLastTraces()));
  }
}
