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

package net.consensys.shomei.fullsync.rules;

import java.util.Optional;

import org.hyperledger.besu.datatypes.Hash;

public class FullSyncRules {

  private boolean isTraceGenerationEnabled;
  private long traceStartBlockNumber;
  private long minConfirmationsBeforeImporting;
  private Optional<Long> blockNumberImportLimit;
  private Optional<Hash> blockHashImportLimit;

  public FullSyncRules(
      final boolean isTraceGenerationEnabled,
      final long traceStartBlockNumber,
      final long minConfirmationsBeforeImporting,
      final Optional<Long> blockNumberImportLimit,
      final Optional<Hash> blockHashImportLimit) {
    this.isTraceGenerationEnabled = isTraceGenerationEnabled;
    this.traceStartBlockNumber = traceStartBlockNumber;
    this.minConfirmationsBeforeImporting = minConfirmationsBeforeImporting;
    this.blockNumberImportLimit = blockNumberImportLimit;
    this.blockHashImportLimit = blockHashImportLimit;
  }

  public FullSyncRules(
      final boolean isTraceGenerationEnabled,
      final long traceStartBlockNumber,
      final long minConfirmationsBeforeImporting) {
    this.isTraceGenerationEnabled = isTraceGenerationEnabled;
    this.traceStartBlockNumber = traceStartBlockNumber;
    this.minConfirmationsBeforeImporting = minConfirmationsBeforeImporting;
    this.blockNumberImportLimit = Optional.empty();
    this.blockHashImportLimit = Optional.empty();
  }

  public boolean isTraceGenerationEnabled() {
    return isTraceGenerationEnabled;
  }

  public long getTraceStartBlockNumber() {
    return traceStartBlockNumber;
  }

  public long getMinConfirmationsBeforeImporting() {
    return minConfirmationsBeforeImporting;
  }

  public Optional<Long> getBlockNumberImportLimit() {
    return blockNumberImportLimit;
  }

  public Optional<Hash> getBlockHashImportLimit() {
    return blockHashImportLimit;
  }

  public void setTraceStartBlockNumber(final long traceStartBlockNumber) {
    this.traceStartBlockNumber = traceStartBlockNumber;
  }

  public void setMinConfirmationsBeforeImporting(final long minConfirmationsBeforeImporting) {
    this.minConfirmationsBeforeImporting = minConfirmationsBeforeImporting;
  }

  public void setBlockNumberImportLimit(final Optional<Long> blockNumberImportLimit) {
    this.blockNumberImportLimit = blockNumberImportLimit;
  }

  public void setBlockHashImportLimit(final Optional<Hash> blockHashImportLimit) {
    this.blockHashImportLimit = blockHashImportLimit;
  }
}
