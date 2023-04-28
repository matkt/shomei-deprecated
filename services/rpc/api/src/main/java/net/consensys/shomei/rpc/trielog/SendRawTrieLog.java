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

import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.rpc.ShomeiRpcMethod;
import net.consensys.shomei.storage.WorldStateStorage;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class SendRawTrieLog implements JsonRpcMethod {

  final TrieLogObserver trieLogObserver;

  final WorldStateStorage worldStateStorage;

  public SendRawTrieLog(
      final TrieLogObserver trieLogObserver, final WorldStateStorage worldStateStorage) {
    this.trieLogObserver = trieLogObserver;
    this.worldStateStorage = worldStateStorage;
  }

  @Override
  public String getName() {
    return ShomeiRpcMethod.STATE_SEND_RAW_TRIE_LOG.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final SendRawTrieLogParameter param =
        requestContext.getRequiredParameter(0, SendRawTrieLogParameter.class);
    try {
      final WorldStateStorage.WorldStateUpdater updater =
          (WorldStateStorage.WorldStateUpdater) worldStateStorage.updater();
      updater.saveTrieLog(param.getBlockHash(), Bytes.fromHexString(param.getTrieLog()));
      // updater.commit(); //TODO commit
      trieLogObserver.onTrieLogAdded(param.getTrieLogIdentifier());
    } catch (Exception e) {
      e.printStackTrace(System.out);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
