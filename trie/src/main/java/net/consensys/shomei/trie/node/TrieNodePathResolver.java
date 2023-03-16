package net.consensys.shomei.trie.node;

import net.consensys.shomei.trie.KeyIndexLoader;
import net.consensys.shomei.trie.util.PathGenerator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerkleTrie;

import java.math.BigInteger;
import java.util.Optional;

@SuppressWarnings({"DoNotInvokeMessageDigestDirectly", "unused"})
public class TrieNodePathResolver {

    private static final Bytes NEXT_FREE_NODE_PATH =Bytes.of(0);
    private static final Bytes SUB_TRIE_ROOT_PATH = Bytes.of(1);

    private final KeyIndexLoader keyIndexLoader;
    private final int trieDepth;
    private final MerkleTrie<Bytes, Bytes> trie;

    private Long nextFreeNode;

    public TrieNodePathResolver(final KeyIndexLoader keyIndexLoader, final int trieDepth, final StoredMerkleTrie<Bytes,Bytes> trie) {
        this.keyIndexLoader = keyIndexLoader;
        this.trieDepth = trieDepth;
        this.trie = trie;
    }

    public Bytes getAndIncrementNextFreeLeafPath() {
        return getLeafPath(getAndIncrementNextFreeLeafIndex());
    }

    private Long getAndIncrementNextFreeLeafIndex() {
        final long foundFreeNode = getNextFreeLeafIndex();
        nextFreeNode = foundFreeNode + 1;
        trie.putPath(getNextFreeNodePath(), formatNodeIndex(nextFreeNode));
        return foundFreeNode;
    }

    private Long getNextFreeLeafIndex() {
        if (nextFreeNode == null) {
            nextFreeNode =
                    trie.getPath(getNextFreeNodePath())
                            .map(bytes -> new BigInteger(bytes.toArrayUnsafe()).longValue())
                            .orElse(0L);
        }
        return nextFreeNode;
    }

    public Bytes geRootPath() {
        return SUB_TRIE_ROOT_PATH;
    }

    public Bytes getLeafPath(final Long nodeIndex) {
        return Bytes.concatenate(
                SUB_TRIE_ROOT_PATH,
                PathGenerator.bytesToLeafPath(nodeIndexToBytes(nodeIndex), LeafType.VALUE));
    }

    public Optional<Bytes> getLeafPath(final Bytes key) {
        return keyIndexLoader.getKeyIndex(key).map(this::getLeafPath);
    }

    public Bytes getNextFreeNodePath() {
        return Bytes.concatenate(
                NEXT_FREE_NODE_PATH, Bytes.of(LeafType.NEXT_FREE_NODE.getTerminatorPath()));
    }

    public Bytes getHeadPath() {
        return Bytes.concatenate(
                SUB_TRIE_ROOT_PATH,
                PathGenerator.bytesToLeafPath(nodeIndexToBytes(getAndIncrementNextFreeLeafIndex()), LeafType.HEAD));
    }

    public Bytes getTailPath() {
        return Bytes.concatenate(
                SUB_TRIE_ROOT_PATH,
                PathGenerator.bytesToLeafPath(nodeIndexToBytes(getAndIncrementNextFreeLeafIndex()), LeafType.TAIL));
    }

    private  Bytes nodeIndexToBytes( final long nodeIndex){
        return Bytes.fromHexString(
                String.format("%" + trieDepth + "s", Long.toBinaryString(nodeIndex))
                        .replace(' ', '0'));
    }

    private Bytes formatNodeIndex(final long nodeIndex){
        final MutableBytes32 mutableBytes32 = MutableBytes32.create();
        Bytes arrayView = Bytes.wrap(BigInteger.valueOf(nodeIndex).toByteArray());
        mutableBytes32.set(MutableBytes32.SIZE-arrayView.size(), arrayView);
        return mutableBytes32;
    }



}
