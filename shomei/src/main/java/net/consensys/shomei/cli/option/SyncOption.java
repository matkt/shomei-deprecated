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

package net.consensys.shomei.cli.option;

import picocli.CommandLine;

public class SyncOption {
  /**
   * Create Sync option.
   *
   * @return the RPC option
   */
  public static SyncOption create() {
    return new SyncOption();
  }

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  static final long DEFAULT_FIRST_GENERATED_BLOCK_NUMBER = 0;

  static final long DEFAULT_MIN_CONFIRMATION = 0;

  @CommandLine.Option(
      names = {"--trace-start-block-number"},
      paramLabel = "<LONG>",
      description =
          "Lowest block number for the trace generation process. Default: ${DEFAULT-VALUE}",
      arity = "1")
  private long traceStartBlockNumber = DEFAULT_FIRST_GENERATED_BLOCK_NUMBER;

  @CommandLine.Option(
      names = {"--min-confirmations-before-importing"},
      paramLabel = "<LONG>",
      description = "Number of confirmations before importing block. Default: ${DEFAULT-VALUE}",
      arity = "1")
  private long minConfirmationsBeforeImporting = DEFAULT_MIN_CONFIRMATION;

  @CommandLine.Option(
      names = {"--import-block-number-limit"},
      paramLabel = "<LONG>",
      description =
          "Cap on the block number that can be imported, preventing the import of blocks beyond this limit.",
      arity = "1")
  private Long importBlockNumberLimit = null;

  @CommandLine.Option(
      names = {"--import-block-hash-limit"},
      paramLabel = "<LONG>",
      description = "Specific block hash as the limit for importing blocks",
      arity = "1")
  private String importBlockHashLimit = null;

  public long getTraceStartBlockNumber() {
    return traceStartBlockNumber;
  }

  public long getMinConfirmationsBeforeImporting() {
    return minConfirmationsBeforeImporting;
  }

  public Long getImportBlockNumberLimit() {
    return importBlockNumberLimit;
  }

  public String getImportBlockHashLimit() {
    return importBlockHashLimit;
  }
}
