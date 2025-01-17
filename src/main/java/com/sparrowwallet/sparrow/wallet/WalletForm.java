package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ServerType;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class WalletForm {
    private static final Logger log = LoggerFactory.getLogger(WalletForm.class);

    private final Storage storage;
    protected Wallet wallet;
    private Wallet savedPastWallet;

    private WalletTransactionsEntry walletTransactionsEntry;
    private WalletUtxosEntry walletUtxosEntry;
    private final List<NodeEntry> accountEntries = new ArrayList<>();
    private final List<Set<WalletNode>> walletTransactionNodes = new ArrayList<>();

    public WalletForm(Storage storage, Wallet currentWallet, Wallet backupWallet) {
        this(storage, currentWallet, backupWallet, true);
    }

    public WalletForm(Storage storage, Wallet currentWallet, Wallet backupWallet, boolean refreshHistory) {
        this.storage = storage;
        this.wallet = currentWallet;

        //Unencrypted wallets load before isConnected is true, waiting for the ConnectionEvent to refresh history - save the backup for this event
        savedPastWallet = backupWallet;

        if(refreshHistory && wallet.isValid()) {
            ElectrumServer.addCalculatedScriptHashes(wallet);
            refreshHistory(AppServices.getCurrentBlockHeight(), backupWallet);
        }
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Storage getStorage() {
        return storage;
    }

    public File getWalletFile() {
        return storage.getWalletFile();
    }

    public void setWallet(Wallet wallet) {
        throw new UnsupportedOperationException("Only SettingsWalletForm supports setWallet");
    }

    public void revert() {
        throw new UnsupportedOperationException("Only SettingsWalletForm supports revert");
    }

    public void save() throws IOException {
        storage.saveWallet(wallet);
    }

    public void saveAndRefresh() throws IOException {
        Wallet pastWallet = wallet.copy();
        storage.backupTempWallet();
        wallet.clearHistory();
        save();
        refreshHistory(AppServices.getCurrentBlockHeight(), pastWallet);
    }

    public void saveBackup() throws IOException {
        storage.backupWallet();
    }

    public void deleteBackups() {
        storage.deleteBackups();
    }

    public void refreshHistory(Integer blockHeight, Wallet pastWallet) {
        refreshHistory(blockHeight, pastWallet, null);
    }

    public void refreshHistory(Integer blockHeight, Wallet pastWallet, WalletNode node) {
        Wallet previousWallet = wallet.copy();
        if(wallet.isValid() && AppServices.isConnected()) {
            log.debug(node == null ? wallet.getName() + " refreshing full wallet history" : wallet.getName() + " requesting node wallet history for " + node.getDerivationPath());
            ElectrumServer.TransactionHistoryService historyService = new ElectrumServer.TransactionHistoryService(wallet, getWalletTransactionNodes(node));
            historyService.setOnSucceeded(workerStateEvent -> {
                if(historyService.getValue()) {
                    EventManager.get().post(new WalletHistoryFinishedEvent(wallet));
                    updateWallet(blockHeight, pastWallet, previousWallet);
                }
            });
            historyService.setOnFailed(workerStateEvent -> {
                if(AppServices.isConnected()) {
                    log.error("Error retrieving wallet history", workerStateEvent.getSource().getException());
                } else {
                    log.debug("Disconnected while retrieving wallet history", workerStateEvent.getSource().getException());
                }

                EventManager.get().post(new WalletHistoryFailedEvent(wallet, workerStateEvent.getSource().getException()));
            });

            EventManager.get().post(new WalletHistoryStartedEvent(wallet, node));
            historyService.start();
        }
    }

    private void updateWallet(Integer blockHeight, Wallet pastWallet, Wallet previousWallet) {
        if(blockHeight != null) {
            wallet.setStoredBlockHeight(blockHeight);
        }

        boolean labelsChanged = false;
        if(pastWallet != null) {
            labelsChanged = copyLabels(pastWallet);
        }

        notifyIfChanged(blockHeight, previousWallet, labelsChanged);
    }

    private boolean copyLabels(Wallet pastWallet) {
        boolean changed = wallet.getRNode().copyLabels(pastWallet.getRNode());// Hashat> old: wallet.getNode(wallet.getReceiveChain()).copyLabels(pastWallet.getNode(pastWallet.getReceiveChain()));
        changed |= wallet.getCNode().copyLabels(pastWallet.getCNode());// Hashat> old: wallet.getNode(wallet.getChangeChain()).copyLabels(pastWallet.getNode(pastWallet.getChangeChain()));

        for(Map.Entry<Sha256Hash, BlockTransaction> txEntry : wallet.getTransactions().entrySet()) {
            BlockTransaction pastBlockTransaction = pastWallet.getTransactions().get(txEntry.getKey());
            if(pastBlockTransaction != null && txEntry.getValue() != null && txEntry.getValue().getLabel() == null && pastBlockTransaction.getLabel() != null) {
                txEntry.getValue().setLabel(pastBlockTransaction.getLabel());
                changed = true;
            }
        }

        storage.deleteTempBackups();
        return changed;
    }

    private void notifyIfChanged(Integer blockHeight, Wallet previousWallet, boolean labelsChanged) {
        List<WalletNode> historyChangedNodes = new ArrayList<>();
        historyChangedNodes.addAll(getHistoryChangedNodes(previousWallet.getRNode().getChildren(), wallet.getRNode().getChildren()));
        historyChangedNodes.addAll(getHistoryChangedNodes(previousWallet.getCNode().getChildren(), wallet.getCNode().getChildren()));
        boolean changed = labelsChanged;
        if(!historyChangedNodes.isEmpty()) {
            Platform.runLater(() -> EventManager.get().post(new WalletHistoryChangedEvent(wallet, storage, historyChangedNodes)));
            changed = true;
        }

        if(blockHeight != null && !blockHeight.equals(previousWallet.getStoredBlockHeight())) {
            Platform.runLater(() -> EventManager.get().post(new WalletBlockHeightChangedEvent(wallet, blockHeight)));
            changed = true;
        }

        if(changed) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    private List<WalletNode> getHistoryChangedNodes(Set<WalletNode> previousNodes, Set<WalletNode> currentNodes) {
        List<WalletNode> changedNodes = new ArrayList<>();
        for(WalletNode currentNode : currentNodes) {
            Optional<WalletNode> optPreviousNode = previousNodes.stream().filter(node -> node.equals(currentNode)).findFirst();
            if(optPreviousNode.isPresent()) {
                WalletNode previousNode = optPreviousNode.get();
                if(!currentNode.getTransactionOutputs().equals(previousNode.getTransactionOutputs())) {
                    changedNodes.add(currentNode);
                }
            } else {
                changedNodes.add(currentNode);
            }
        }

        return changedNodes;
    }

    public void addWalletTransactionNodes(Set<WalletNode> transactionNodes) {
        walletTransactionNodes.add(transactionNodes);
    }

    private Set<WalletNode> getWalletTransactionNodes(WalletNode walletNode) {
        if(walletNode == null) {
            return null;
        }

        Set<WalletNode> allNodes = new LinkedHashSet<>();
        for(Set<WalletNode> nodes : walletTransactionNodes) {
            if(nodes.contains(walletNode)) {
                allNodes.addAll(nodes);
            }
        }

        return allNodes.isEmpty() ? Set.of(walletNode) : allNodes;
    }

    public NodeEntry getNodeEntry(KeyPurpose keyPurpose) {
        NodeEntry purposeEntry;
        Optional<NodeEntry> optionalPurposeEntry = accountEntries.stream().filter(entry -> entry.getNode().getKeyPurpose().equals(keyPurpose)).findFirst();
        if(optionalPurposeEntry.isPresent()) {
            purposeEntry = optionalPurposeEntry.get();
        } else {
            WalletNode purposeNode = getWallet().getNode(keyPurpose);
            purposeEntry = new NodeEntry(getWallet(), purposeNode);
            accountEntries.add(purposeEntry);
        }

        return purposeEntry;
    }

    public NodeEntry getFreshNodeEntry(KeyPurpose keyPurpose, NodeEntry currentEntry) {
        NodeEntry rootEntry = getNodeEntry(keyPurpose);
        WalletNode freshNode = getWallet().getFreshNode(keyPurpose, currentEntry == null ? null : currentEntry.getNode());

        for(Entry childEntry : rootEntry.getChildren()) {
            NodeEntry nodeEntry = (NodeEntry)childEntry;
            if(nodeEntry.getNode().equals(freshNode)) {
                return nodeEntry;
            }
        }

        NodeEntry freshEntry = new NodeEntry(getWallet(), freshNode);
        rootEntry.getChildren().add(freshEntry);
        return freshEntry;
    }

    public WalletTransactionsEntry getWalletTransactionsEntry() {
        if(walletTransactionsEntry == null) {
            walletTransactionsEntry = new WalletTransactionsEntry(wallet);
        }

        return walletTransactionsEntry;
    }

    public WalletUtxosEntry getWalletUtxosEntry() {
        if(walletUtxosEntry == null) {
            walletUtxosEntry = new WalletUtxosEntry(wallet);
        }

        return walletUtxosEntry;
    }

    @Subscribe
    public void walletDataChanged(WalletDataChangedEvent event) {
        if(event.getWallet().equals(wallet)) {
            backgroundSaveWallet();
        }
    }

    private void backgroundSaveWallet() {
        try {
            save();
        } catch (IOException e) {
            //Background save failed
            log.error("Background wallet save failed", e);
        }
    }

    @Subscribe
    public void walletSettingsChanged(WalletSettingsChangedEvent event) {
        if(event.getWalletFile().equals(storage.getWalletFile())) {
            wallet = event.getWallet();

            if(event instanceof WalletAddressesChangedEvent) {
                walletTransactionsEntry = null;
                walletUtxosEntry = null;
                accountEntries.clear();
                EventManager.get().post(new WalletNodesChangedEvent(wallet));

                //It is necessary to save the past wallet because the actual copying of the past labels only occurs on a later ConnectionEvent with bwt
                if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
                    savedPastWallet = event.getPastWallet();
                }

                //Clear the cache - we will need to fetch everything again
                AppServices.clearTransactionHistoryCache(wallet);
                refreshHistory(AppServices.getCurrentBlockHeight(), event.getPastWallet());
            }
        }
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        //Check if wallet is valid to avoid saving wallets in initial setup
        if(wallet.isValid()) {
            updateWallet(event.getHeight(), null, wallet.copy());
        }
    }

    @Subscribe
    public void connected(ConnectionEvent event) {
        refreshHistory(event.getBlockHeight(), savedPastWallet);
        savedPastWallet = null;
    }

    @Subscribe
    public void walletNodeHistoryChanged(WalletNodeHistoryChangedEvent event) {
        if(wallet.isValid()) {
            WalletNode walletNode = event.getWalletNode(wallet);
            if(walletNode != null) {
                log.debug(wallet.getName() + " history event for node " + walletNode + " (" + event.getScriptHash() + ")");
                refreshHistory(AppServices.getCurrentBlockHeight(), null, walletNode);
            }
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWalletFile().equals(storage.getWalletFile())) {
            for(WalletNode changedNode : event.getHistoryChangedNodes()) {
                if(changedNode.getLabel() != null && !changedNode.getLabel().isEmpty()) {
                    List<Entry> changedLabelEntries = new ArrayList<>();
                    for(BlockTransactionHashIndex receivedRef : changedNode.getTransactionOutputs()) {
                        BlockTransaction blockTransaction = wallet.getTransactions().get(receivedRef.getHash());
                        if(blockTransaction != null && (blockTransaction.getLabel() == null || blockTransaction.getLabel().isEmpty())) {
                            blockTransaction.setLabel(changedNode.getLabel());
                            changedLabelEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
                        }
                    }

                    if(!changedLabelEntries.isEmpty()) {
                        Platform.runLater(() -> EventManager.get().post(new WalletEntryLabelsChangedEvent(event.getWallet(), changedLabelEntries)));
                    }
                }
            }
        }
    }

    @Subscribe
    public void walletLabelsChanged(WalletEntryLabelsChangedEvent event) {
        if(event.getWallet() == wallet) {
            List<Entry> labelChangedEntries = new ArrayList<>();
            for(Entry entry : event.getEntries()) {
                if(entry.getLabel() != null && !entry.getLabel().isEmpty()) {
                    if(entry instanceof TransactionEntry) {
                        TransactionEntry transactionEntry = (TransactionEntry)entry;
                        List<KeyPurpose> keyPurposes = List.of(wallet.getReceiveChain(), wallet.getChangeChain());
                        for(KeyPurpose keyPurpose : keyPurposes) {
                            for(WalletNode childNode : wallet.getNode(keyPurpose).getChildren()) {
                                for(BlockTransactionHashIndex receivedRef : childNode.getTransactionOutputs()) {
                                    if(receivedRef.getHash().equals(transactionEntry.getBlockTransaction().getHash())) {
                                        if(receivedRef.getLabel() == null || receivedRef.getLabel().isEmpty()) {
                                            receivedRef.setLabel(entry.getLabel() + (keyPurpose.equals(wallet.getChangeChain()) ? " (change)" : " (received)"));
                                            labelChangedEntries.add(new HashIndexEntry(event.getWallet(), receivedRef, HashIndexEntry.Type.OUTPUT, keyPurpose));
                                        }
                                        if(childNode.getLabel() == null || childNode.getLabel().isEmpty()) {
                                            childNode.setLabel(entry.getLabel());
                                            labelChangedEntries.add(new NodeEntry(event.getWallet(), childNode));
                                        }
                                    }
                                    if(receivedRef.isSpent() && receivedRef.getSpentBy().getHash().equals(transactionEntry.getBlockTransaction().getHash()) && (receivedRef.getSpentBy().getLabel() == null || receivedRef.getSpentBy().getLabel().isEmpty())) {
                                        receivedRef.getSpentBy().setLabel(entry.getLabel() + " (input)");
                                        labelChangedEntries.add(new HashIndexEntry(event.getWallet(), receivedRef.getSpentBy(), HashIndexEntry.Type.INPUT, keyPurpose));
                                    }
                                }
                            }
                        }
                    }
                    if(entry instanceof NodeEntry) {
                        NodeEntry nodeEntry = (NodeEntry)entry;
                        for(BlockTransactionHashIndex receivedRef : nodeEntry.getNode().getTransactionOutputs()) {
                            BlockTransaction blockTransaction = event.getWallet().getTransactions().get(receivedRef.getHash());
                            if(blockTransaction.getLabel() == null || blockTransaction.getLabel().isEmpty()) {
                                blockTransaction.setLabel(entry.getLabel());
                                labelChangedEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
                            }
                        }
                    }
                    if(entry instanceof HashIndexEntry) {
                        HashIndexEntry hashIndexEntry = (HashIndexEntry)entry;
                        BlockTransaction blockTransaction = hashIndexEntry.getBlockTransaction();
                        if(blockTransaction.getLabel() == null || blockTransaction.getLabel().isEmpty()) {
                            blockTransaction.setLabel(entry.getLabel());
                            labelChangedEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
                        }
                    }
                }
            }

            if(!labelChangedEntries.isEmpty()) {
                Platform.runLater(() -> EventManager.get().post(new WalletEntryLabelsChangedEvent(wallet, labelChangedEntries)));
            }
        }
    }

    @Subscribe
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        for(WalletTabData tabData : event.getClosedWalletTabData()) {
            if(tabData.getWalletForm() == this) {
                AppServices.clearTransactionHistoryCache(wallet);
                EventManager.get().unregister(this);
            }
        }
    }

    @Subscribe
    public void hideEmptyUsedAddressesStatusChanged(HideEmptyUsedAddressesStatusEvent event) {
        accountEntries.clear();
        EventManager.get().post(new WalletAddressesStatusEvent(wallet));
    }
}
