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

import org.apache.tuweni.bytes.Bytes;

/**
 * This class serves as a wrapper for the AccountTrieRepository, providing additional functionality
 * and abstraction for interacting with the account trie data.
 */
public class AccountTrieRepositoryWrapper extends AbstractTrieRepositoryWrapper
    implements TrieStorage {

  public static final Bytes ACCOUNT_PREFIX =
      Bytes.wrap("accountPrefix".getBytes(StandardCharsets.UTF_8));

  public AccountTrieRepositoryWrapper(final TrieStorage trieStorage, final TrieUpdater updater) {
    super(ACCOUNT_PREFIX, trieStorage, updater);
  }

  public AccountTrieRepositoryWrapper(final TrieStorage trieStorage) {
    super(ACCOUNT_PREFIX, trieStorage, trieStorage.updater());
  }
}
