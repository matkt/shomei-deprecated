package net.consensys.shomei.util;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.Hash;

import java.nio.charset.StandardCharsets;

public class KeyGenerator {

    public static Bytes createDumKey(int value){
        return Hash.keccak256(Bytes.of(("KEY_" + value).getBytes(StandardCharsets.UTF_8)));
    }
}
