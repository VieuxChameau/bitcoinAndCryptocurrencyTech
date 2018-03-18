package org.vieuxchameau.consensusFromTrust;

import java.util.*;
import java.util.stream.Collectors;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {

    private final double p_graph;
    private final double p_malicious;
    private final double p_txDistribution;
    private final int numRounds;
    private Set<Transaction> initialProposalTxs;
    private boolean[] followees;
    private int currentRound = 0;
    private Map<Integer, FolloweeContext> receivedTxsBySender = new HashMap<>();
    private final Set<Integer> maliciousNodes = new HashSet<>();


    /**
     * @param p_graph          the pairwise connectivity probability of the random graph: e.g. {.1, .2, .3}
     * @param p_malicious      the probability that a node will be set to be malicious: e.g {.15, .30, .45}
     * @param p_txDistribution the probability that each of the initial valid transactions will be communicated: e.g. {.01, .05, .10}
     * @param numRounds        the number of rounds in the simulation e.g. {10, 20}
     */
    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
    }

    public void setFollowees(boolean[] followees) {
        this.followees = followees;
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.initialProposalTxs = pendingTransactions;
    }

    public Set<Transaction> sendToFollowers() {
        return initialProposalTxs;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        final Map<Integer, List<Candidate>> candidatesBySender = candidates.stream().collect(Collectors.groupingBy(c -> c.sender));
        if (isFirstRound()) {
            initContextByFollowee(candidatesBySender);
            currentRound++;
            return;
        }


        for (Map.Entry<Integer, List<Candidate>> candidatesOfSender : candidatesBySender.entrySet()) {
            final List<Transaction> candidateTxsForRound = candidatesOfSender.getValue()
                    .stream()
                    .map(candidate -> candidate.tx)
                    .collect(Collectors.toList());
            inspectCandidates(candidatesOfSender.getKey(), candidateTxsForRound);
        }

        currentRound++;
    }

    private void inspectCandidates(final Integer sender, final List<Transaction> candidateTxsForRound) {
        final FolloweeContext context = receivedTxsBySender.get(sender);

        if (context == null || isMaliciousNode(sender)) { // The sender is not a followee or it is but it is a malicious one
            maliciousNodes.add(sender);
            return;
        }

        // All the verified transactions should be received again
        if (!candidateTxsForRound.containsAll(context.verifiedTxs)) {
            maliciousNodes.add(sender);
            return;
        }
        // Remove all the verified transactions from the candidates
        candidateTxsForRound.removeIf(context.verifiedTxs::contains);

        // Remove the unverified transactions of the previous round from the candidates
        final Set<Transaction> newlyVerifiedTxs = new HashSet<>(context.unverifiedTxs.size());
        boolean isMalicious = false;
        final Iterator<Transaction> it = context.unverifiedTxs.iterator();
        while (it.hasNext()) {
            final Transaction unverifiedTx = it.next();
            // if an unverified transaction received from the previous round is received again we assume it is now verified
            if (candidateTxsForRound.contains(unverifiedTx)) {
                candidateTxsForRound.remove(unverifiedTx);
                newlyVerifiedTxs.add(unverifiedTx);
                it.remove();
            } else {
                isMalicious = true;
                break;
            }
        }

        // A node is supposed to send all the previously sent transactions
        if (isMalicious || !context.unverifiedTxs.isEmpty()) {
            maliciousNodes.add(sender);
            return;
        }

        initialProposalTxs.addAll(newlyVerifiedTxs);
        context.addToVerifiedTxs(newlyVerifiedTxs);
        context.addToUnverifiedTxs(candidateTxsForRound);
    }

    private boolean isMaliciousNode(final Integer sender) {
        return maliciousNodes.contains(sender);
    }


    private void initContextByFollowee(Map<Integer, List<Candidate>> candidatesBySender) {
        for (int i = 0; i < followees.length; i++) {
            final boolean isFollowee = followees[i];
            if (isFollowee) {
                final FolloweeContext followeeContext = new FolloweeContext();
                receivedTxsBySender.put(i, followeeContext);

                final List<Candidate> candidates = candidatesBySender.get(i);
                if (candidates != null && !candidates.isEmpty()) {
                    followeeContext.addToUnverifiedTxs(candidates.stream()
                            .map(candidate -> candidate.tx)
                            .collect(Collectors.toList()));
                }
            }
        }
    }

    private boolean isFirstRound() {
        return currentRound == 0;
    }


    private class FolloweeContext {
        private final Set<Transaction> verifiedTxs = new HashSet<>();
        private final Set<Transaction> unverifiedTxs = new HashSet<>();


        public void addToUnverifiedTxs(Collection<Transaction> transactions) {
            unverifiedTxs.addAll(transactions);
        }

        public void addToVerifiedTxs(final Collection<Transaction> transactions) {
            this.verifiedTxs.addAll(transactions);
        }
    }
}
