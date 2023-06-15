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

package net.consensys.shomei.rpc.server;

import java.io.IOException;
import java.util.Optional;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;

public abstract class AbstractJsonRpcExecutor {
  final JsonRpcExecutor jsonRpcExecutor;
  final RoutingContext ctx;
  final JsonRpcConfiguration jsonRpcConfiguration;

  public AbstractJsonRpcExecutor(
      final JsonRpcExecutor jsonRpcExecutor,
      final RoutingContext ctx,
      final JsonRpcConfiguration jsonRpcConfiguration) {
    this.jsonRpcExecutor = jsonRpcExecutor;
    this.ctx = ctx;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
  }

  abstract void execute() throws IOException;

  abstract String getRpcMethodName(final RoutingContext ctx);

  protected static JsonRpcResponse executeRequest(
      final JsonRpcExecutor jsonRpcExecutor,
      final JsonObject jsonRequest,
      final RoutingContext ctx) {
    final Optional<User> user = ContextKey.AUTHENTICATED_USER.extractFrom(ctx, Optional::empty);
    return jsonRpcExecutor.execute(
        user,
        null,
        null,
        () -> !ctx.response().closed(),
        jsonRequest,
        req -> req.mapTo(JsonRpcRequest.class));
  }

  protected static void handleJsonRpcError(
      final RoutingContext routingContext, final Object id, final JsonRpcError error) {
    final HttpServerResponse response = routingContext.response();
    if (!response.closed()) {
      response
          .setStatusCode(statusCodeFromError(error).code())
          .end(Json.encode(new JsonRpcErrorResponse(id, error)));
    }
  }

  private static HttpResponseStatus statusCodeFromError(final JsonRpcError error) {
    return switch (error) {
      case INVALID_REQUEST, PARSE_ERROR -> HttpResponseStatus.BAD_REQUEST;
      default -> HttpResponseStatus.OK;
    };
  }
}
