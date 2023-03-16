package net.consensys.shomei.trie.util;

import net.consensys.shomei.trie.node.LeafType;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class PathGenerator {

    public static Bytes bytesToLeafPath(final Bytes bytes, final LeafType leafType) {
        MutableBytes path = MutableBytes.create(bytes.size() * 2 + 1);
        int j = 0;
        for(int i = j; i < bytes.size(); j += 2) {
            byte b = bytes.get(i);
            path.set(j, (byte)(b >>> 4 & 15));
            path.set(j + 1, (byte)(b & 15));
            ++i;
        }

        path.set(j, leafType.getTerminatorPath()); // leaf ending path
        return path;
    }

}
