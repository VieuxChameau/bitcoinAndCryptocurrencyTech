package org.vieuxchameau.scroogecoin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;


class TxHandlerTest {
    private final KeyHelper keyHelper = new KeyHelper();
    private final UTXOPool utxoPool = new UTXOPool();
    private final TxHandler txHandler;
    private final Transaction firstTransaction = new Transaction();
    private final PublicKey scroogePublicKey;
    private byte[] firstTransactionHash;

    public TxHandlerTest() throws Exception {
        scroogePublicKey = getScroogePublicKey();
        firstTransaction.addOutput(42, scroogePublicKey);
        firstTransaction.addOutput(5, scroogePublicKey);
        firstTransaction.finalize();

        firstTransactionHash = firstTransaction.getHash();

        createCoin(0);
        createCoin(1);

        txHandler = new TxHandler(utxoPool);
    }

    @DisplayName("Transaction should validate all criteria")
    @Test
    public void transactionIsValid() throws Exception {
        final Transaction validTx = createValidTx();


        final boolean isValidTx = txHandler.isValidTx(validTx);


        assertThat(isValidTx).isTrue();
    }

    @DisplayName("Transaction invalid because one coin claimed is not in the ledger")
    @Test
    public void oneCoinClaimedIsNotInTheLedger() throws Exception {
        final Transaction invalidTx = new Transaction();
        invalidTx.addInput(firstTransaction.getHash(), 0);
        // Scrooge reference an invalid Tx
        invalidTx.addInput("OldCoinNotExisting".getBytes(), 1);

        invalidTx.addOutput(42, getDonaldPublicKey());
        invalidTx.addOutput(5, getDonaldPublicKey());

        invalidTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, invalidTx.getRawDataToSign(0)), 0);
        invalidTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, invalidTx.getRawDataToSign(1)), 1);


        final boolean isValidTx = txHandler.isValidTx(invalidTx);


        assertThat(isValidTx).isFalse();
    }


    @DisplayName("Transaction invalid because one input signature is not valid")
    @Test
    public void inputSignaturesShouldNotBeValid() throws Exception {
        final Transaction invalidTx = new Transaction();
        invalidTx.addInput(firstTransactionHash, 0);
        invalidTx.addInput(firstTransactionHash, 1);

        invalidTx.addOutput(42, getDonaldPublicKey());
        invalidTx.addOutput(5, getHueyPublicKey());

        invalidTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, invalidTx.getRawDataToSign(0)), 0);
        // Huey try to sign for a coin owned by SCROOGE
        invalidTx.addSignature(keyHelper.sign(KeyHelper.HUEY, invalidTx.getRawDataToSign(1)), 1);


        final boolean isValidTx = txHandler.isValidTx(invalidTx);


        assertThat(isValidTx).isFalse();
    }


    @DisplayName("Transaction invalid because one UTXO is claimed twice in transaction")
    @Test
    public void oneUTXOAreClaimedTwiceInTransaction() throws Exception {
        final Transaction invalidTx = new Transaction();
        invalidTx.addInput(firstTransactionHash, 0);
        // same output claimed as previously
        invalidTx.addInput(firstTransactionHash, 0);

        invalidTx.addOutput(42, getDonaldPublicKey());
        invalidTx.addOutput(42, getDonaldPublicKey());

        invalidTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, invalidTx.getRawDataToSign(0)), 0);
        invalidTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, invalidTx.getRawDataToSign(1)), 1);


        final boolean isValidTx = txHandler.isValidTx(invalidTx);


        assertThat(isValidTx).isFalse();
    }

    @DisplayName("Transaction invalid because one output value is negative")
    @Test
    public void oneOutputValueIsNegativeInTransaction() throws Exception {
        final Transaction invalidTx = new Transaction();
        invalidTx.addInput(firstTransactionHash, 0);
        invalidTx.addInput(firstTransactionHash, 1);

        // output value negative
        invalidTx.addOutput(-42, getDonaldPublicKey());
        invalidTx.addOutput(5, getDonaldPublicKey());

        invalidTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, invalidTx.getRawDataToSign(0)), 0);
        invalidTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, invalidTx.getRawDataToSign(1)), 1);


        final boolean isValidTx = txHandler.isValidTx(invalidTx);


        assertThat(isValidTx).isFalse();
    }


    @DisplayName("Transaction invalid because the sum of the output values is greater than the sum input values")
    @Test
    public void sumOfOutputValuesGreaterThanSumOfInputValuesInTransaction() throws Exception {
        final Transaction invalidTx = new Transaction();
        invalidTx.addInput(firstTransactionHash, 0);
        invalidTx.addInput(firstTransactionHash, 1);


        invalidTx.addOutput(43, getDonaldPublicKey());
        invalidTx.addOutput(5, getDonaldPublicKey());

        invalidTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, invalidTx.getRawDataToSign(0)), 0);
        invalidTx.addSignature(keyHelper.sign(KeyHelper.SCROOGE, invalidTx.getRawDataToSign(1)), 1);


        final boolean isValidTx = txHandler.isValidTx(invalidTx);


        assertThat(isValidTx).isFalse();
    }


    @DisplayName("Valid Tx should be added to the ledger")
    @Test
    public void shouldOnlyAddValidTransactionToLedger() throws Exception {
        final Transaction validTx = createValidTx();
        // this tx should not be valid because it try to use the coins already used in the first tx
        final Transaction invalidTx = createValidTx();


        final Transaction[] txs = new Transaction[]{validTx, invalidTx};
        final Transaction[] acceptedTransactions = txHandler.handleTxs(txs);


        assertThat(acceptedTransactions).hasSize(1).containsExactly(validTx);
        assertThat(validTx.getHash()).isNotEmpty();
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