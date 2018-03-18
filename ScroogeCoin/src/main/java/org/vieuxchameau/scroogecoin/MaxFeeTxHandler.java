package org.vieuxchameau.scroogecoin;

import java.util.*;

public class MaxFeeTxHandler {
    private final UTXOPool ledger;
    private double maxTxsFee = Double.MIN_VALUE;
    private List<TransactionWrapper> bestTxSet;

    public MaxFeeTxHandler(UTXOPool ledger) {
        this.ledger = new UTXOPool(ledger);
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
            final byte[] rawDataToSign = tx.getRawDataToSign(i);
            final Transaction.Input input = tx.getInput(i);

            final UTXO claimedUTXO = new UTXO(input.prevTxHash, input.outputIndex);
            final Transaction.Output output = ledger.getTxOutput(claimedUTXO);
            if (output == null) { // check #1
                return false;
            }

            if (claimedUTXOs.contains(claimedUTXO)) { // check #3
                return false;
            }
            if (!Crypto.verifySignature(output.address, rawDataToSign, input.signature)) { // check #2
                return false;
            }


            claimedUTXOs.add(claimedUTXO);
            inputSum += output.value;
        }

        double outputSum = 0;
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) { // check #4
                return false;
            }
            outputSum += output.value;
        }

        if (outputSum > inputSum) { // check #5
            return false;
        }
        return true;
    }

    private boolean isValidTx(final TransactionWrapper tx, final UTXOPool oneLedger) {
        final Set<UTXO> claimedUTXOs = new HashSet<>(tx.inputWrappers.size());
        double inputSum = 0;
        for (InputWrapper inputWrapper : tx.inputWrappers) {
            final UTXO claimedUTXO = inputWrapper.claimedUTXO;
            final Transaction.Output output = oneLedger.getTxOutput(claimedUTXO);
            if (output == null) { // check #1
                return false;
            }

            if (!Crypto.verifySignature(output.address, inputWrapper.rawDataToSign, inputWrapper.input.signature)) { // check #2
                return false;
            }

            if (claimedUTXOs.contains(claimedUTXO)) { // check #3
                return false;
            }

            claimedUTXOs.add(claimedUTXO);
            inputSum += output.value;

        }
        return !(tx.outSum > inputSum);
    }


    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(final Transaction[] possibleTxs) {
        bestTxSet = Collections.emptyList();
        maxTxsFee = Integer.MIN_VALUE;

        List<TransactionWrapper> txs = new ArrayList<>(possibleTxs.length);
        for (Transaction tx : possibleTxs) {
            double outputSum = 0;
            for (Transaction.Output output : tx.getOutputs()) {
                if (output.value < 0) { // check #4
                    outputSum = -1;
                    break;
                }
                outputSum += output.value;
            }
            if (outputSum != -1) {
                txs.add(new TransactionWrapper(tx, outputSum));
            }
        }

        if (possibleTxs.length > 5) {
            evaluate(txs);
        } else {
            generatePermutations(txs.size(), txs);
        }

        Transaction[] bestSet = new Transaction[bestTxSet.size()];
        int i = 0;
        for (TransactionWrapper transaction : bestTxSet) {
            updateLedger(transaction, ledger);
            bestSet[i] = transaction.tx;
            ++i;
        }

        return bestSet;
    }


    /**
     * Generate all the permutation of the txs using Heap's algorithm
     */
    private void generatePermutations(final int n, final List<TransactionWrapper> txs) {
        if (n == 1) {
            evaluate(txs);
        } else {
            final boolean isEven = n % 2 == 0;
            for (int i = 0; i < n - 1; i++) {
                generatePermutations(n - 1, txs);
                Collections.swap(txs, isEven ? i : 0, n - 1);
            }
            generatePermutations(n - 1, txs);
        }
    }


    private void evaluate(final List<TransactionWrapper> possibleTxs) {
        final UTXOPool oneLedger = new UTXOPool(ledger);
        double txsFee = 0;
        final List<TransactionWrapper> acceptedTransactions = new ArrayList<>(possibleTxs.size());
        for (TransactionWrapper possibleTx : possibleTxs) {
            if (!isValidTx(possibleTx, oneLedger)) {
                continue;
            }
            txsFee += getTransactionFee(possibleTx, oneLedger);
            updateLedger(possibleTx, oneLedger);

            acceptedTransactions.add(possibleTx);
        }

        if (txsFee > maxTxsFee) {
            bestTxSet = acceptedTransactions;
            maxTxsFee = txsFee;
        }
    }

    private double getTransactionFee(final TransactionWrapper possibleTx, final UTXOPool oneLedger) {
        double txFee = 0;
        for (InputWrapper input : possibleTx.inputWrappers) {

            final Transaction.Output output = oneLedger.getTxOutput(input.claimedUTXO);
            txFee += output.value;
        }
        txFee -= possibleTx.outSum;
        return txFee;
    }

    private void updateLedger(final TransactionWrapper validTx, final UTXOPool oneLedger) {
        // TODO only once ?
        //validTx.tx.finalize();

        for (InputWrapper input : validTx.inputWrappers) {
            oneLedger.removeUTXO(input.claimedUTXO);
        }

        for (int i = 0; i < validTx.tx.numOutputs(); i++) {
            final UTXO newUTXO = new UTXO(validTx.tx.getHash(), i);
            oneLedger.addUTXO(newUTXO, validTx.tx.getOutput(i));
        }
    }


    private class TransactionWrapper {
        private final Transaction tx;
        private final double outSum;
        private final List<InputWrapper> inputWrappers;

        private TransactionWrapper(final Transaction tx, double outSum) {
            this.tx = tx;
            this.outSum = outSum;


            inputWrappers = new ArrayList<>(tx.numInputs());
            for (int i = 0; i < tx.numInputs(); i++) {
                final byte[] rawDataToSign = tx.getRawDataToSign(i);
                final Transaction.Input input = tx.getInput(i);

                final UTXO claimedUTXO = new UTXO(input.prevTxHash, input.outputIndex);
                inputWrappers.add(new InputWrapper(input, rawDataToSign, claimedUTXO));
            }

            tx.finalize();
        }


    }

    private class InputWrapper {
        private final Transaction.Input input;
        private final byte[] rawDataToSign;
        private final UTXO claimedUTXO;

        private InputWrapper(final Transaction.Input input, byte[] rawDataToSign, UTXO claimedUTXO) {
            this.input = input;

            this.rawDataToSign = rawDataToSign;
            this.claimedUTXO = claimedUTXO;
        }

    }
}
