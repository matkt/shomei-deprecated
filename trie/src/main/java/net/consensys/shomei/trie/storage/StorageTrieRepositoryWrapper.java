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

package net.consensys.shomei.trie.storage;

import java.nio.charset.StandardCharsets;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;

/**
 * This class serves as a wrapper for the StorageTrieRepository, providing additional functionality
 * and abstraction for interacting with the storage trie data.
 */
public class StorageTrieRepositoryWrapper extends AbstractTrieRepositoryWrapper
    implements TrieStorage {

  public static final Bytes STORAGE_PREFIX =
      Bytes.wrap("storagePrefix".getBytes(StandardCharsets.UTF_8));

  public StorageTrieRepositoryWrapper(
      final long accountLeafIndex, final TrieStorage trieStorage, final TrieUpdater updater) {
    super(createPrefix(accountLeafIndex), trieStorage, updater);
  }

  public StorageTrieRepositoryWrapper(final long accountLeafIndex, final TrieStorage trieStorage) {
    super(createPrefix(accountLeafIndex), trieStorage, trieStorage.updater());
  }

  private static Bytes createPrefix(final long accountLeafIndex) {
    return Bytes.concatenate(STORAGE_PREFIX, Bytes.wrap(Longs.toByteArray(accountLeafIndex)));
  }
}
