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

package net.consensys.shomei.trie;

import java.util.Optional;
import java.util.function.Function;
import net.consensys.shomei.trie.visitor.GetVisitor;
import net.consensys.shomei.trie.visitor.PutVisitor;
import net.consensys.shomei.trie.visitor.RemoveVisitor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.MerkleStorage;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;
import org.hyperledger.besu.ethereum.trie.StoredMerkleTrie;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.ethereum.trie.CompactEncoding.bytesToPath;

/**
 * A {@link MerkleTrie} that persists trie nodes to a {@link MerkleStorage} key/value store.
 *
 */
public class StoredSparseMerkleTrie extends StoredMerkleTrie<Bytes, Bytes>
    implements MerkleTrie<Bytes, Bytes> {

  private final GetVisitor<Bytes> getVisitor = new GetVisitor<>();

  public StoredSparseMerkleTrie(
      final NodeLoader nodeLoader,
      final Function<Bytes, Bytes> valueSerializer,
      final Function<Bytes, Bytes> valueDeserializer) {
    super(new StoredNodeFactory(nodeLoader, valueSerializer, valueDeserializer));
  }

  public StoredSparseMerkleTrie(
      final NodeLoader nodeLoader,
      final Bytes32 rootHash,
      final Bytes rootLocation,
      final Function<Bytes, Bytes> valueSerializer,
      final Function<Bytes, Bytes> valueDeserializer) {
    super(
        new StoredNodeFactory(nodeLoader, valueSerializer, valueDeserializer),
        rootHash,
        rootLocation);
  }

  public StoredSparseMerkleTrie(
      final NodeLoader nodeLoader,
      final Bytes32 rootHash,
      final Function<Bytes, Bytes> valueSerializer,
      final Function<Bytes, Bytes> valueDeserializer) {
    super(new StoredNodeFactory(nodeLoader, valueSerializer, valueDeserializer), rootHash);
  }

  public StoredSparseMerkleTrie(final StoredNodeFactory nodeFactory, final Bytes32 rootHash) {
    super(nodeFactory, rootHash);
  }

  /*@Override
  public void remove(final K key) {
    super.put(key, //0 leaf);
  }*/

  public Node<Bytes> getNodePath(final Bytes path) {
    checkNotNull(path);
    return root.accept(getGetVisitor(), path);
  }

  @Override
  public PathNodeVisitor<Bytes> getGetVisitor() {
    return getVisitor;
  }



  @Override
  public PathNodeVisitor<Bytes> getRemoveVisitor() {
    return new RemoveVisitor<>();
  }

  @Override
  public PathNodeVisitor<Bytes> getPutVisitor(final Bytes value) {
    return new PutVisitor<>(nodeFactory, value);
  }
}
