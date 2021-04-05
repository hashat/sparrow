package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletExportEvent;
import com.sparrowwallet.sparrow.io.*;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

import java.util.List;

public class WalletExportDialog extends Dialog<Wallet> {
    private Wallet wallet;

    public WalletExportDialog(Wallet wallet) {
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });

        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        dialogPane.setContent(stackPane);

        AnchorPane anchorPane = new AnchorPane();
        stackPane.getChildren().add(anchorPane);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setPrefHeight(400);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        anchorPane.getChildren().add(scrollPane);
        scrollPane.setFitToWidth(true);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);

        List<WalletExport> exporters;
        if(wallet.getPolicyType() == PolicyType.SINGLE) {
            exporters = List.of(new Electrum(), new SpecterDesktop(), new Sparrow());
        } else if(wallet.getPolicyType() == PolicyType.MULTI) {
            exporters = List.of(new ColdcardMultisig(), new CoboVaultMultisig(), new Electrum(), new PassportMultisig(), new SpecterDesktop(), new BlueWalletMultisig(), new Sparrow());
        } else {
            throw new UnsupportedOperationException("Cannot export wallet with policy type " + wallet.getPolicyType());
        }

        Accordion exportAccordion = new Accordion();
        for (WalletExport exporter : exporters) {
            FileWalletExportPane exportPane = new FileWalletExportPane(wallet, exporter);
            exportAccordion.getPanes().add(exportPane);
        }
        scrollPane.setContent(exportAccordion);

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(cancelButtonType);
        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(480);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? wallet : null);
    }

    @Subscribe
    public void walletExported(WalletExportEvent event) {
        wallet = event.getWallet();
        setResult(wallet);
    }
}
