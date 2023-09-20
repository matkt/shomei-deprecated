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

package net.consensys.shomei.rpc.server.method;

import static net.consensys.shomei.rpc.server.ShomeiRpcMethod.ROLLUP_FORK_CHOICE_UPDATED;

import net.consensys.shomei.fullsync.FullSyncDownloader;
import net.consensys.shomei.rpc.server.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.rpc.server.model.RollupForkChoiceUpdatedParameter;
import net.consensys.shomei.storage.ZkWorldStateArchive;

import java.util.Optional;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class RollupForkChoiceUpdated implements JsonRpcMethod {

  private final ZkWorldStateArchive zkWorldStateArchive;

  private final FullSyncDownloader fullSyncDownloader;

  public RollupForkChoiceUpdated(
      final ZkWorldStateArchive zkWorldStateArchive, final FullSyncDownloader fullSyncDownloader) {
    this.zkWorldStateArchive = zkWorldStateArchive;
    this.fullSyncDownloader = fullSyncDownloader;
  }

  @Override
  public String getName() {
    return ROLLUP_FORK_CHOICE_UPDATED.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final RollupForkChoiceUpdatedParameter param =
        requestContext.getRequiredParameter(0, RollupForkChoiceUpdatedParameter.class);
    if (fullSyncDownloader
        .getFullSyncRules()
        .getBlockNumberImportLimit()
        .map(limit -> limit >= param.getFinalizedBlockNumber())
        .orElse(false)) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          JsonRpcError.INVALID_PARAMS,
          "You cannot set a limit %d lower than the existing one %s ."
              .formatted(
                  param.getFinalizedBlockNumber(),
                  fullSyncDownloader.getFullSyncRules().getBlockNumberImportLimit()));
    }
    if (param.getFinalizedBlockNumber() < zkWorldStateArchive.getCurrentBlockNumber()) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          JsonRpcError.INVALID_PARAMS,
          "You cannot set a limit %d lower than the current shomei head %s ."
              .formatted(
                  param.getFinalizedBlockNumber(), zkWorldStateArchive.getCurrentBlockNumber()));
    }
    // update full sync rules
    fullSyncDownloader
        .getFullSyncRules()
        .setBlockNumberImportLimit(Optional.of(param.getFinalizedBlockNumber()));
    fullSyncDownloader
        .getFullSyncRules()
        .setBlockHashImportLimit(Optional.of(param.getFinalizedBlockHash()));
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
