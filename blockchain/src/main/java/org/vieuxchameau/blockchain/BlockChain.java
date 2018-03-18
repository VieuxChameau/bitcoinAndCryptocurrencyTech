package org.vieuxchameau.blockchain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Block Chain should maintain only limited block nodes to satisfy the functions
 * You should not have all the blocks added to the block chain in memory
 * as it would cause a memory overflow.
 */
public class BlockChain {
    private class BlockNode {
        final Block block;
        final UTXOPool utxoPool;
        final BlockNode parent;
        final List<BlockNode> children = new ArrayList<>();
        final int height;

        private BlockNode(final Block block, final UTXOPool utxoPool, final BlockNode parent, final int height) {
            this.block = block;
            this.utxoPool = utxoPool;
            this.parent = parent;
            this.height = height;
        }
    }

    public static final int CUT_OFF_AGE = 10;
    private final TransactionPool transactionPool = new TransactionPool();
    private final Map<ByteArrayWrapper, BlockNode> blocks = new HashMap<>();
    private BlockNode maxHeightBlock;


    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(final Block genesisBlock) {
        final byte[] hash = genesisBlock.getHash();
        final BlockNode genesisNode = new BlockNode(genesisBlock, new UTXOPool(), null, 0);
        blocks.put(new ByteArrayWrapper(hash), genesisNode);
        maxHeightBlock = genesisNode;

        addCoinBaseTxToUTXOPool(genesisBlock.getCoinbase(), genesisNode.utxoPool);
    }

    /**
     * Get the maximum height block
     */
    public Block getMaxHeightBlock() {
        return maxHeightBlock.block;
    }

    /**
     * Get the UTXOPool for mining a new block on top of max height block
     */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightBlock.utxoPool;
    }

    /**
     * Get the transaction pool to mine a new block
     */
    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * <p>
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <= CUT_OFF_AGE + 1}.
     * As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block at height 2.
     *
     * @return true if block is successfully added
     */
    public boolean addBlock(final Block block) {
        final byte[] prevBlockHash = block.getPrevBlockHash();
        if (prevBlockHash == null) { // New genesis block won't be mined
            return false;
        }

        final BlockNode parentNode = blocks.get(new ByteArrayWrapper(prevBlockHash));
        if (parentNode == null) {
            return false;
        }

        if (!hasValidHeight(parentNode.height)) {
            // TODO erase ancestors from memory
            return false;
        }


        final TxHandler txHandler = new TxHandler(parentNode.utxoPool);
        if (!isValidBlock(block, txHandler)) {
            return false;
        }

        final BlockNode node = processBlock(block, parentNode, txHandler);

        blocks.put(new ByteArrayWrapper(block.getHash()), node);

        if (node.height > maxHeightBlock.height) {
            maxHeightBlock = node;
        }
        return true;
    }

    private boolean hasValidHeight(final int parentNodeHeight) {
        final int minimumHeightThreshold = maxHeightBlock.height - CUT_OFF_AGE;
        final int newBlockHeight = parentNodeHeight + 1;
        return newBlockHeight > minimumHeightThreshold;
    }

    /**
     * Remove all the block's transactions from the tx pool
     * Create a new block node
     * Add the coinbase tx to the UTXOPool
     */
    private BlockNode processBlock(final Block block, final BlockNode parentNode, final TxHandler txHandler) {
        for (Transaction transaction : block.getTransactions()) {
            transactionPool.removeTransaction(transaction.getHash());
        }

        final BlockNode node = new BlockNode(block, txHandler.getUTXOPool(), parentNode, parentNode.height + 1);

        parentNode.children.add(node);

        addCoinBaseTxToUTXOPool(block.getCoinbase(), node.utxoPool);

        return node;
    }

    private void addCoinBaseTxToUTXOPool(final Transaction coinbaseTx, final UTXOPool utxoPool) {
        final UTXO newUTXO = new UTXO(coinbaseTx.getHash(), 0);
        utxoPool.addUTXO(newUTXO, coinbaseTx.getOutput(0));
    }

    /**
     * When checking for validity of a newly received block, just checking if the transactions form a
     * valid set is enough. The set need not be a maximum possible set of transactions. Also, you
     * needn't do any proof-of-work checks.
     */
    private boolean isValidBlock(final Block block, final TxHandler txHandler) {
        final List<Transaction> transactions = block.getTransactions();
        final int nbOfTxs = transactions.size();
        final Transaction[] validTransactions = txHandler.handleTxs(transactions.toArray(new Transaction[nbOfTxs]));
        return validTransactions.length == nbOfTxs;
    }

    /**
     * Add a transaction to the transaction pool
     */
    public void addTransaction(final Transaction tx) {
        transactionPool.addTransaction(tx);
    }
}

