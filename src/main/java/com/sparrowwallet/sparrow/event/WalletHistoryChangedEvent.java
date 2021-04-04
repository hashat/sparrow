package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.io.Storage;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is posted by WalletForm once the history of the wallet has been refreshed, and new transactions detected
 *
 */
public class WalletHistoryChangedEvent extends WalletChangedEvent {
    private final Storage storage;
    private final List<WalletNode> historyChangedNodes;

    public WalletHistoryChangedEvent(Wallet wallet, Storage storage, List<WalletNode> historyChangedNodes) {
        super(wallet);
        this.storage = storage;
        this.historyChangedNodes = historyChangedNodes;
    }

    public File getWalletFile() {
        return storage.getWalletFile();
    }

    public List<WalletNode> getHistoryChangedNodes() {
        return historyChangedNodes;
    }

    public List<WalletNode> getReceiveNodes() {
        return getWallet().getRNode().getChildren().stream().filter(historyChangedNodes::contains).collect(Collectors.toList());
    }

    public List<WalletNode> getChangeNodes() {
        return getWallet().getCNode().getChildren().stream().filter(historyChangedNodes::contains).collect(Collectors.toList());
    }
}
