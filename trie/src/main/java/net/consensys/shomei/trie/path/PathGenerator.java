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

package net.consensys.shomei.trie.path;

import net.consensys.shomei.trie.node.LeafType;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class PathGenerator {

  public static Bytes bytesToLeafPath(final Bytes bytes, final LeafType leafType) {
    MutableBytes path = MutableBytes.create(bytes.size() * 2 + 1);
    int j = 0;
    for (int i = j; i < bytes.size(); j += 2) {
      byte b = bytes.get(i);
      path.set(j, (byte) (b >>> 4 & 15));
      path.set(j + 1, (byte) (b & 15));
      ++i;
    }

    path.set(j, leafType.getTerminatorPath()); // leaf ending path
    return path;
  }
}
