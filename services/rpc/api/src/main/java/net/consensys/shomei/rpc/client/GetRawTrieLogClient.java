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

package net.consensys.shomei.rpc.client;

import io.vertx.core.http.HttpMethod;
import net.consensys.shomei.observer.TrieLogObserver;
import net.consensys.shomei.rpc.model.SendRawTrieLogParameter;
import net.consensys.shomei.rpc.server.JsonRpcService;
import net.consensys.shomei.storage.WorldStateRepository;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetRawTrieLogClient {

  private static final String APPLICATION_JSON = "application/json";

  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcService.class);
  private static final SecureRandom RANDOM;

  static {
    try {
      RANDOM = SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private final WebClient webClient;
  private final String besuHttpHost;
  private final int besuHttpPort;

  private final WorldStateRepository worldStateRepository;

  public GetRawTrieLogClient(
      final WorldStateRepository worldStateRepository,
      final String besuHttpHost,
      final int besuHttpPort) {
    final Vertx vertx = Vertx.vertx();
    final WebClientOptions options = new WebClientOptions();
    this.webClient = WebClient.create(vertx, options);
    this.worldStateRepository = worldStateRepository;
    this.besuHttpHost = besuHttpHost;
    this.besuHttpPort = besuHttpPort;
  }

  public void getTrieLog(
      final long startBlockNumber,
      final long endBlockNumber,
      final TrieLogObserver trieLogObserver) {
    final int requestId = RANDOM.nextInt();
    JsonObject jsonRpcRequest =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", requestId)
            .put("method", "shomei_getTrieLogsByRange")
            .put("params", new JsonArray(List.of(startBlockNumber, endBlockNumber)));

    // Send the request to the JSON-RPC service
    webClient
        .request(HttpMethod.POST, besuHttpPort, besuHttpHost, "/")
        .putHeader("Content-Type", APPLICATION_JSON)
            .timeout(TimeUnit.SECONDS.toMillis(30))
        .sendJsonObject(
            jsonRpcRequest,
            response -> {
              LOG.atInfo()
                      .setMessage("response received for getTrieLogsByRange {}")
                      .addArgument(response.result())
                      .log();
              if (response.succeeded()) {
                final JsonRpcRequest responseBody =
                    response.result().bodyAsJson(JsonRpcRequest.class);
                if (responseBody.getId().equals(requestId)) {
                  try {
                    final List<TrieLogObserver.TrieLogIdentifier> trieLogIdentifiers =
                        new ArrayList<>();
                    IntStream.range(0, responseBody.getParamLength())
                        .forEach(
                            index -> {
                              SendRawTrieLogParameter param =
                                  responseBody.getRequiredParameter(
                                      index, SendRawTrieLogParameter.class);
                              worldStateRepository.saveTrieLog(
                                  param.blockNumber(), Bytes.fromHexString(param.trieLog()));
                              trieLogIdentifiers.add(param.getTrieLogIdentifier());
                            });
                    worldStateRepository.commitTrieLogStorage();
                    trieLogObserver.onTrieLogsAdded(trieLogIdentifiers);

                  } catch (RuntimeException e) {
                    LOG.error("failed to handle new TrieLog {}", e.getMessage());
                    LOG.debug("exception handling TrieLog", e);
                  }
                }
              }
            });
  }
}
