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

package net.consensys.shomei.trie.node;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeFactory;

import java.util.function.Function;

public class TailNode extends LeafNode<Bytes> implements Node<Bytes> {


  private static final Bytes VALUE = Bytes.concatenate(
          UInt256.valueOf(0), // Prev 0
          UInt256.valueOf(1), // Next 1,
          Bytes32.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"), //HKEY TODO change to 0x30644e72e131a029b85045b68181585d2833e84879b9709143e1f593f0000001 when we will remove keccak
          Bytes32.ZERO); // Value

  public TailNode(final Bytes path,final NodeFactory<Bytes> nodeFactory, final Function<Bytes, Bytes> valueSerializer) {
    super(path,VALUE,
            nodeFactory, valueSerializer);
  }

  @Override
  public String print() {
    return "[TAIL LEAF]";
  }

}
