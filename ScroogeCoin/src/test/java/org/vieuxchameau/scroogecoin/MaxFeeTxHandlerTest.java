package org.vieuxchameau.scroogecoin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;

class MaxFeeTxHandlerTest {
    private final KeyHelper keyHelper = new KeyHelper();
    private final UTXOPool utxoPool = new UTXOPool();
    private final MaxFeeTxHandler txHandler;
    private final Transaction firstTransaction = new Transaction();
    private final PublicKey scroogePublicKey;
    private byte[] firstTransactionHash;

    public MaxFeeTxHandlerTest() throws Exception {
        scroogePublicKey = getScroogePublicKey();
        firstTransaction.addOutput(42, scroogePublicKey);
        firstTransaction.addOutput(5, scroogePublicKey);
        firstTransaction.finalize();

        firstTransactionHash = firstTransaction.getHash();

        createCoin(0);
        createCoin(1);

        txHandler = new MaxFeeTxHandler(utxoPool);
    }

    @DisplayName("Valid Tx should be added to the ledger")
    @Test
    public void shouldOnlyAddValidTransactionToLedger() throws Exception {
        final Transaction validTx = createValidTx();
        // this tx should not be valid because it try to use the coins already used in the first tx
        final Transaction invalidTx = createValidTx();


        final Transaction[] txs = new Transaction[]{validTx, invalidTx, createValidTx(), createValidTx(), createValidTx(), createValidTx(), createValidTx(),
                createValidTx(), createValidTx(), createValidTx()
                /*, createValidTx(), createValidTx(),
                createValidTx(), createValidTx(), createValidTx(), createValidTx(), createValidTx(),
                createValidTx(), createValidTx(), createValidTx(), createValidTx(), createValidTx(),
                createValidTx(), createValidTx(), createValidTx(), createValidTx(), createValidTx(),
                createValidTx(), createValidTx(), createValidTx(), createValidTx(), createValidTx(),
                createValidTx(), createValidTx(), createValidTx(), createValidTx(), createValidTx()*/};
        final Transaction[] acceptedTransactions = txHandler.handleTxs(txs);


       /* assertThat(acceptedTransactions).hasSize(1).containsExactly(validTx);
        assertThat(validTx.getHash()).isNotEmpty();*/
    }

    @Test
    void name() {
        double t = Integer.MIN_VALUE;
        System.out.println("t = " + t);


        double v = 0;
        final boolean b = v > t;
        System.out.println("b = " + b);
        final int compare = Double.compare(t, v);
        System.out.println("compare = " + compare);

        final double max = Double.max(t, v);
        System.out.println("max = " + max);
    }


    private Transaction createValidTx() throws Exception {
        final Transaction validTx = new Transaction();
        validTx.addInput(firstTransaction.getHash(), 0);
        validTx.addInput(firstTransaction.getHash(), 1);

        validTx.addOutput(42, getDonaldPublicKey());
        validTx.addOutput(5, getDonaldPublicKey());

        validTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, validTx.getRawDataToSign(0)), 0);
        validTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, validTx.getRawDataToSign(1)), 1);
        return validTx;
    }


    private void createCoin(int index) {
        final Transaction.Output txOut = firstTransaction.getOutput(index);

        final UTXO utxo = new UTXO(firstTransactionHash, index);
        utxoPool.addUTXO(utxo, txOut);
    }

    private PublicKey getScroogePublicKey() throws Exception {
        return keyHelper.getKeyPair(KeyHelper.SCROOGE).getPublic();
    }

    private PublicKey getDonaldPublicKey() throws Exception {
        return keyHelper.getKeyPair(KeyHelper.DONALD).getPublic();
    }

    private PublicKey getHueyPublicKey() throws Exception {
        return keyHelper.getKeyPair(KeyHelper.DONALD).getPublic();
    }

}