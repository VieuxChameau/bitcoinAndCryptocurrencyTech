package org.vieuxchameau.blockchain;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {

    private final UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(final UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(final Transaction tx) {
        final Set<UTXO> claimedUTXOs = new HashSet<>(tx.numInputs());
        double inputSum = 0;
        for (int i = 0; i < tx.numInputs(); i++) {
            final Transaction.Input input = tx.getInput(i);

            final UTXO claimedUTXO = new UTXO(input.prevTxHash, input.outputIndex);
            final Transaction.Output output = utxoPool.getTxOutput(claimedUTXO);
            if (output == null) { // check #1
                show("Tx invalid because coin claimed from input {0} does not exist in the utxoPool", i);
                return false;
            }

            if (claimedUTXOs.contains(claimedUTXO)) { // check #3
                show("Tx invalid because the coin claimed from input {0} has been claimed twice", i);
                return false;
            }

            final byte[] rawDataToSign = tx.getRawDataToSign(i);
            if (!Crypto.verifySignature(output.address, rawDataToSign, input.signature)) { // check #2
                show("Tx invalid because Input {0} signature is not valid", i);
                return false;
            }


            claimedUTXOs.add(claimedUTXO);
            inputSum += output.value;
        }

        double outputSum = 0;
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) { // check #4
                show("Tx invalid because the output value is negative");
                return false;
            }
            outputSum += output.value;
        }


        if (outputSum > inputSum) { // check #5
            show("Tx invalid because the sum of the output values is greater than the sum of the input value");
            return false;
        }
        return true;
    }

    private void show(final String pattern, final Object... arguments) {
        System.out.println(MessageFormat.format(pattern, arguments));
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(final Transaction[] possibleTxs) {
        final List<Transaction> acceptedTransactions = new ArrayList<>();
        for (Transaction possibleTx : possibleTxs) {
            if (!isValidTx(possibleTx)) {
                continue;
            }
            updateLedger(possibleTx);

            acceptedTransactions.add(possibleTx);
        }
        return acceptedTransactions.toArray(new Transaction[acceptedTransactions.size()]);

    }

    public UTXOPool getUTXOPool() {
        return this.utxoPool;
    }

    private void updateLedger(final Transaction validTx) {
        validTx.finalize();

        for (Transaction.Input input : validTx.getInputs()) {
            final UTXO claimedUTXO = new UTXO(input.prevTxHash, input.outputIndex);
            utxoPool.removeUTXO(claimedUTXO);
        }

        for (int i = 0; i < validTx.numOutputs(); i++) {
            final UTXO newUTXO = new UTXO(validTx.getHash(), i);
            utxoPool.addUTXO(newUTXO, validTx.getOutput(i));

        }
    }

}
