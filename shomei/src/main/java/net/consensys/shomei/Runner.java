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

package net.consensys.shomei;

import net.consensys.shomei.cli.option.DataStorageOption;
import net.consensys.shomei.cli.option.JsonRpcOption;
import net.consensys.shomei.cli.option.SyncOption;
import net.consensys.shomei.fullsync.FullSyncDownloader;
import net.consensys.shomei.rpc.client.GetRawTrieLogClient;
import net.consensys.shomei.rpc.server.JsonRpcService;
import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentedStorage;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import net.consensys.shomei.storage.PersistedWorldStateRepository;
import net.consensys.shomei.storage.WorldStateRepository;
import net.consensys.shomei.worldview.ZkEvmWorldStateEntryPoint;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Runner {

  private static final Logger LOG = LoggerFactory.getLogger(Runner.class);

  private final Vertx vertx;
  private final FullSyncDownloader fullSyncDownloader;
  private final JsonRpcService jsonRpcService;
  private final WorldStateRepository worldStateStorage;

  public Runner(
      final DataStorageOption dataStorageOption,
      JsonRpcOption jsonRpcOption,
      final SyncOption syncOption) {
    this.vertx = Vertx.vertx();

    worldStateStorage =
        new PersistedWorldStateRepository(
            new RocksDBSegmentedStorage(
                new RocksDBConfigurationBuilder()
                    .databaseDir(dataStorageOption.getDataStoragePath())
                    .build()));

    final ZkEvmWorldStateEntryPoint zkEvmWorldStateEntryPoint =
        new ZkEvmWorldStateEntryPoint(worldStateStorage);

    final GetRawTrieLogClient getRawTrieLog =
        new GetRawTrieLogClient(
            worldStateStorage,
            jsonRpcOption.getBesuRpcHttpHost(),
            jsonRpcOption.getBesuRHttpPort());

    fullSyncDownloader =
        new FullSyncDownloader(
            zkEvmWorldStateEntryPoint, getRawTrieLog, syncOption.getImportDelay());

    this.jsonRpcService =
        new JsonRpcService(
            jsonRpcOption.getRpcHttpHost(),
            jsonRpcOption.getRpcHttpPort(),
            fullSyncDownloader,
            worldStateStorage);
  }

  public void start() {
    vertx.deployVerticle(
        jsonRpcService,
        res -> {
          if (!res.succeeded()) {
            LOG.atError()
                .setMessage("Error occurred when starting the JSON RPC service {}")
                .addArgument(res.cause())
                .log();
          }
        });
    vertx.deployVerticle(
        fullSyncDownloader,
        res -> {
          if (!res.succeeded()) {
            LOG.atError()
                .setMessage("Error occurred when starting the block downloader {}")
                .addArgument(res.cause())
                .log();
          }
        });
  }

  public void stop() {
    worldStateStorage.close();
    vertx.close();
  }
}
