package net.consensys.shomei.trie.node;

import org.apache.tuweni.bytes.Bytes;

import java.util.Arrays;

public enum LeafType {

    VALUE((byte)0x16),
    HEAD((byte)0x17),
    TAIL((byte)0x18),
    NEXT_FREE_NODE((byte)0x19),

    EMPTY((byte)0x20);

    final byte terminatorPath;

    LeafType(final byte terminatorPath) {
        this.terminatorPath = terminatorPath;
    }

    public byte getTerminatorPath() {
        return terminatorPath;
    }

    public static LeafType fromBytes(final Bytes path){
            return Arrays.stream(values())
                    .filter(leafType -> Bytes.of(leafType.getTerminatorPath())
                            .equals(path)).findFirst().orElse(EMPTY);
    }
}
