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
import net.consensys.shomei.rpc.error.JsonInvalidVersionMessage;
import net.consensys.shomei.rpc.error.ShomeiJsonRpcErrorResponse;
import net.consensys.shomei.storage.WorldStateRepository;
import net.consensys.shomei.trie.ZKTrie;
import net.consensys.shomei.trie.proof.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.rlp.RLP;

public class RollupGetZkEVMStateMerkleProofV0 implements JsonRpcMethod {

  final WorldStateRepository worldStateStorage;

  public RollupGetZkEVMStateMerkleProofV0(final WorldStateRepository worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  @Override
  public String getName() {
    return ShomeiRpcMethod.ROLLUP_GET_ZKEVM_STATE_MERKLE_PROOF_V0.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final RollupGetZkEvmStateV0Parameter param =
        requestContext.getRequiredParameter(0, RollupGetZkEvmStateV0Parameter.class);
    if (!IMPL_VERSION.equals(param.getZkStateManagerVersion())) {
      return new ShomeiJsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          JsonRpcError.INVALID_PARAMS,
          "UNSUPPORTED_VERSION",
          new JsonInvalidVersionMessage(param.getZkStateManagerVersion(), IMPL_VERSION));
    }
    final List<List<Trace>> traces = new ArrayList<>();
    for (long i = param.getStartBlockNumber(); i <= param.getEndBlockNumber(); i++) {
      Optional<Bytes> traceRaw = worldStateStorage.getTrace(i);
      traceRaw.ifPresent(bytes -> traces.add(Trace.deserialize(RLP.input(bytes))));
      if (traceRaw.isEmpty()) {
        return new ShomeiJsonRpcErrorResponse(
            requestContext.getRequest().getId(),
            JsonRpcError.INVALID_REQUEST,
            "BLOCK_MISSING_IN_CHAIN - block %d is missing".formatted(i));
      }
    }

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        new RollupGetZkEVMStateMerkleProofV0Response(
            worldStateStorage
                .getZkStateRootHash(param.getStartBlockNumber() - 1)
                .orElse(ZKTrie.DEFAULT_TRIE_ROOT)
                .toHexString(),
            traces,
            IMPL_VERSION));
  }
}
