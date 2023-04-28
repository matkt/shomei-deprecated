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

package net.consensys.shomei.rpc.rollup;

import static net.consensys.shomei.rpc.ShomeiVersion.IMPL_VERSION;

import net.consensys.shomei.rpc.ShomeiRpcMethod;
import net.consensys.shomei.storage.WorldStateStorage;
import net.consensys.shomei.trie.proof.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.rlp.RLP;

public class RollupGetZkEVMStateMerkleProofV0 implements JsonRpcMethod {

  final WorldStateStorage worldStateStorage;

  public RollupGetZkEVMStateMerkleProofV0(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  @Override
  public String getName() {
    return ShomeiRpcMethod.ROLLUP_GET_ZKEVM_STATE_MERKLE_PROOF_V0.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 3) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    final long startBlockNumber = requestContext.getRequiredParameter(0, Long.class);
    final long endBlockNumber = requestContext.getRequiredParameter(1, Long.class);
    final String zkStateManagerVersion = requestContext.getRequiredParameter(2, String.class);
    System.out.println(zkStateManagerVersion + " " + IMPL_VERSION);
    if (!IMPL_VERSION.equals(zkStateManagerVersion)) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          JsonRpcError.INVALID_REQUEST); // TODO return UNSUPPORTED_VERSION
    }
    final List<List<Trace>> traces = new ArrayList<>();
    for (long i = startBlockNumber; i <= endBlockNumber; i++) {
      Optional<Bytes> traceRaw = worldStateStorage.getTrace(i);
      traceRaw.ifPresent(bytes -> traces.add(Trace.deserialize(RLP.input(bytes))));
      if (traceRaw.isEmpty()) {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(),
            JsonRpcError.INVALID_REQUEST); // TODO return BLOCK_MISSING_IN_CHAIN
      }
    }

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), traces);
  }
}
