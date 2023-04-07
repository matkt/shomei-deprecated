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

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.util.bytes.FullBytes;
import net.consensys.zkevm.HashProvider;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

public class ZkAccountTest {

  @Test
  public void testHashZeroAccount() {
    final ZkAccount zkAccount =
        new ZkAccount(
            Hash.ZERO,
            Address.ZERO,
            new FullBytes(Hash.ZERO),
            Hash.ZERO,
            0L,
            0L,
            Wei.ZERO,
            Hash.ZERO);

    assertThat(HashProvider.mimc(zkAccount.serializeAccount()))
        .isEqualTo(
            Bytes32.fromHexString(
                "19be98b429f6e00b8eff84a8aa617d2982421d5cde049c3e2a9b5a30a554a307"));
  }
}
