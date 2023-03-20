package net.consensys.shomei.util;

import net.consensys.shomei.trie.LeafStorage;
import net.consensys.shomei.trie.model.StateLeafValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLeafStorage implements LeafStorage {

    final TreeMap<Bytes32, UInt256> flatDB =
            new TreeMap<>(Comparator.naturalOrder());

    final ConcurrentHashMap<Bytes,Bytes> leafInMemoryStorage = new ConcurrentHashMap<>();

    @Override
    public Optional<Long> getKeyIndex(final Bytes key) {
        return Optional.ofNullable(flatDB.get(key)).map(UInt256::toLong);
    }

    @Override
    public void putKeyIndex(final Bytes key, final Long index) {
        flatDB.put(Bytes32.wrap(key), UInt256.valueOf(index));
    }

    @Override
    public Range getNearestKeys(final Bytes key) {
        final Iterator<Map.Entry<Bytes32, UInt256>> iterator = flatDB.entrySet().iterator();
        Map.Entry<Bytes32, UInt256> next = Map.entry(Bytes32.ZERO, UInt256.ZERO);
        Map.Entry<Bytes32, UInt256> nearest =  next;
        while (iterator.hasNext() && next.getKey().compareTo(key)<=0){
            nearest = next;
            next = iterator.next();
        }
        return new Range(nearest, next);
    }

}
