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

import static net.consensys.shomei.trie.DigestGenerator.createDumDigest;
import static net.consensys.shomei.util.bytes.MimcSafeBytes.unsafeFromBytes;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.trie.proof.DeletionTrace;
import net.consensys.shomei.trie.proof.InsertionTrace;
import net.consensys.shomei.trie.proof.ReadTrace;
import net.consensys.shomei.trie.proof.ReadZeroTrace;
import net.consensys.shomei.trie.proof.Trace;
import net.consensys.shomei.trie.proof.UpdateTrace;
import net.consensys.shomei.trie.storage.InMemoryStorage;
import net.consensys.shomei.util.bytes.MimcSafeBytes;
import net.consensys.zkevm.HashProvider;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.Node;
import org.junit.Before;
import org.junit.Test;

public class TraceSerializationTest {

  private Gson gson;

  @Before
  public void setup() {
    gson =
        new GsonBuilder()
            .registerTypeAdapter(
                Node.class,
                (JsonSerializer<Node<Bytes>>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.getHash().toHexString()))
            .registerTypeAdapter(
                UInt256.class,
                (JsonSerializer<UInt256>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
            .registerTypeAdapter(
                Hash.class,
                (JsonSerializer<Hash>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
            .registerTypeAdapter(
                Bytes.class,
                (JsonSerializer<Bytes>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString()))
            .create();
  }

  @Test
  public void testEncodeAndDecodeInsertionTrace() {
    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final MimcSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(41));
    final Hash hkey = HashProvider.mimc(key);

    final InsertionTrace expectedTrace = (InsertionTrace) zkTrie.putAndProve(hkey, key, value);

    // try encode and decode
    final InsertionTrace decodedTrace =
        InsertionTrace.readFrom(RLP.input(RLP.encode(expectedTrace::writeTo)));

    assertThat(gson.toJson(decodedTrace)).isEqualTo(gson.toJson(expectedTrace));
  }

  @Test
  public void testEncodeAndDecodeUpdateTrace() {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final MimcSafeBytes<Bytes> dumValue = unsafeFromBytes(createDumDigest(41));
    final MimcSafeBytes<Bytes> newDumValue = unsafeFromBytes(createDumDigest(42));
    final Hash hkey = HashProvider.mimc(key);

    zkTrie.putAndProve(hkey, key, dumValue);

    final UpdateTrace expectedTrace = (UpdateTrace) zkTrie.putAndProve(hkey, key, newDumValue);

    // try encode and decode
    final UpdateTrace decodedTrace =
        UpdateTrace.readFrom(RLP.input(RLP.encode(expectedTrace::writeTo)));

    assertThat(gson.toJson(decodedTrace)).isEqualTo(gson.toJson(expectedTrace));
  }

  @Test
  public void testEncodeAndDecodeDeletionTrace() {
    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final MimcSafeBytes<Bytes> value = unsafeFromBytes(createDumDigest(41));
    final Hash hkey = HashProvider.mimc(key);

    zkTrie.putAndProve(hkey, key, value);

    final DeletionTrace expectedTrace = (DeletionTrace) zkTrie.removeAndProve(hkey, key);

    // try encode and decode
    final DeletionTrace decodedTrace =
        DeletionTrace.readFrom(RLP.input(RLP.encode(expectedTrace::writeTo)));

    assertThat(gson.toJson(decodedTrace)).isEqualTo(gson.toJson(expectedTrace));
  }

  @Test
  public void testEncodeAndDecodeReadTrace() {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final MimcSafeBytes<Bytes> dumValue = unsafeFromBytes(createDumDigest(41));
    final Hash hkey = HashProvider.mimc(key);

    // try read zero trace before inserting the key in the trie
    final ReadZeroTrace expectedReadZeroTrace = (ReadZeroTrace) zkTrie.readZeroAndProve(hkey, key);

    // try encode and decode
    final ReadZeroTrace decodedReadZeroTrace =
        ReadZeroTrace.readFrom(RLP.input(RLP.encode(expectedReadZeroTrace::writeTo)));

    assertThat(gson.toJson(decodedReadZeroTrace)).isEqualTo(gson.toJson(expectedReadZeroTrace));

    zkTrie.putAndProve(hkey, key, dumValue);

    // try read trace
    final ReadTrace expectedReadTrace = (ReadTrace) zkTrie.readAndProve(hkey, key);

    // try encode and decode
    final ReadTrace decodedReadTrace =
        ReadTrace.readFrom(RLP.input(RLP.encode(expectedReadTrace::writeTo)));

    assertThat(gson.toJson(decodedReadTrace)).isEqualTo(gson.toJson(expectedReadTrace));
  }

  @Test
  public void testEncodeAndDecodeListOfTraces() {

    final InMemoryStorage storage = new InMemoryStorage();
    ZKTrie zkTrie = ZKTrie.createTrie(storage);

    final MimcSafeBytes<Bytes> key = unsafeFromBytes(createDumDigest(58));
    final MimcSafeBytes<Bytes> dumValue = unsafeFromBytes(createDumDigest(41));
    final MimcSafeBytes<Bytes> newDumValue = unsafeFromBytes(createDumDigest(42));
    final Hash hkey = HashProvider.mimc(key);

    List<Trace> expectedTraces = new ArrayList<>();
    expectedTraces.add(zkTrie.putAndProve(hkey, key, dumValue));
    expectedTraces.add(zkTrie.putAndProve(hkey, key, newDumValue));

    // try encode and decode
    final List<Trace> decodedTraces = Trace.deserialize(RLP.input(Trace.serialize(expectedTraces)));

    assertThat(gson.toJson(decodedTraces)).isEqualTo(gson.toJson(expectedTraces));
  }
}
