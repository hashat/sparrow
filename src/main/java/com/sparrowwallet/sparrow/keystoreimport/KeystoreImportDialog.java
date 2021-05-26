package com.sparrowwallet.sparrow.keystoreimport;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.controlsfx.tools.Borders;

import java.io.IOException;
import java.util.List;

public class KeystoreImportDialog extends Dialog<Keystore> {
    private final KeystoreImportController keystoreImportController;
    private Keystore keystore;

    public KeystoreImportDialog(Wallet wallet) {
        this(wallet, KeystoreSource.HW_USB);
    }

    public KeystoreImportDialog(Wallet wallet, KeystoreSource initialSource) {
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });

        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        try {
            FXMLLoader ksiLoader = new FXMLLoader(AppServices.class.getResource("keystoreimport/keystoreimport.fxml"));
            dialogPane.setContent(Borders.wrap(ksiLoader.load()).emptyBorder().buildAll());
            keystoreImportController = ksiLoader.getController();
            keystoreImportController.initializeView(wallet);
            keystoreImportController.selectSource(initialSource);

            final ButtonType watchOnlyButtonType = new javafx.scene.control.ButtonType(Network.get().getXpubHeader().getDisplayName() + " / Watch Only Wallet", ButtonBar.ButtonData.LEFT);
            final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogPane.getButtonTypes().addAll(watchOnlyButtonType, cancelButtonType);
            dialogPane.setPrefWidth(650);
            dialogPane.setPrefHeight(690);
            AppServices.moveToActiveWindowScreen(this);

            setResultConverter(dialogButton -> dialogButton != cancelButtonType ? getWatchOnlyKeystore() : null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<KeystoreSource> getSupportedSources() {
        return List.of(KeystoreSource.HW_USB, KeystoreSource.HW_AIRGAPPED, KeystoreSource.SW_SEED);
    }

    private Keystore getWatchOnlyKeystore() {
        this.keystore = new Keystore();
        keystore.setSource(KeystoreSource.SW_WATCH);
        keystore.setWalletModel(WalletModel.SPARROW);
        keystore.setKeyDerivation(new KeyDerivation("",""));
        return keystore;
    }

    @Subscribe
    public void keystoreImported(KeystoreImportEvent event) {
        this.keystore = event.getKeystore();
        setResult(keystore);
    }
}
