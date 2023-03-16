package net.consensys.shomei.trie.visitor;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.NullNode;

public class CommitVisitor<V> extends org.hyperledger.besu.ethereum.trie.CommitVisitor<V> {
    public CommitVisitor(final NodeUpdater nodeUpdater) {
        super(nodeUpdater);
    }

    @Override
    public void maybeStoreNode(final Bytes location, final Node<V> node) {
        this.nodeUpdater.store(location, node.getHash(), node.getEncodedBytes());
    }

    @Override
    public void visit(final Bytes location, final NullNode<V> node) {
        if(!node.getEncodedBytes().isEmpty()) {
            this.nodeUpdater.store(location, node.getHash(), node.getEncodedBytes());
        }
    }

}
