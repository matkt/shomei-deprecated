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

package net.consensys.shomei.services.storage.rocksdb;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import services.storage.KeyValueStorage;
import services.storage.KeyValueStorage.KeyValuePair;
import services.storage.StorageException;

/** The type Segmented key value storage adapter. */
public class SegmentedStorageAdapter {

  private final KeyValueStorage segmentHandle;

  /**
   * Instantiates a new Segmented key value storage adapter.
   *
   * @param segment the segment
   * @param storage the storage
   */
  public SegmentedStorageAdapter(
      final RocksDBSegmentIdentifier segment, final RocksDBSegmentedStorage storage) {
    segmentHandle = storage.getKeyValueStorageForSegment(segment);
  }

  public void clear() {
    segmentHandle.truncate();
  }

  public boolean containsKey(final byte[] key) throws StorageException {
    return segmentHandle.containsKey(key);
  }

  public Optional<byte[]> get(final byte[] key) throws StorageException {
    return segmentHandle.get(key);
  }

  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    return segmentHandle.getAllKeysThat(returnCondition);
  }

  public Set<byte[]> getAllValuesFromKeysThat(final Predicate<byte[]> returnCondition) {
    return segmentHandle.getAllValuesFromKeysThat(returnCondition);
  }

  public Stream<KeyValuePair> stream() {
    return segmentHandle.stream();
  }

  public Stream<byte[]> streamKeys() {
    return segmentHandle.streamKeys();
  }

  public boolean tryDelete(final byte[] key) {
    return segmentHandle.tryDelete(key);
  }

  public void close() throws IOException {
    segmentHandle.close();
  }
}
