package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class DateCell extends TreeTableCell<Entry, Entry> {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public DateCell() {
        super();
        setAlignment(Pos.CENTER_LEFT);
        setContentDisplay(ContentDisplay.RIGHT);
        getStyleClass().add("date-cell");
    }

    @Override
    protected void updateItem(Entry entry, boolean empty) {
        super.updateItem(entry, empty);

        EntryCell.applyRowStyles(this, entry);

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            if(entry instanceof UtxoEntry) {
                UtxoEntry utxoEntry = (UtxoEntry)entry;
                if(utxoEntry.getHashIndex().getHeight() <= 0) {
                    setText("Unconfirmed " + (utxoEntry.getHashIndex().getHeight() < 0 ? "Parent " : "") + (utxoEntry.isSpendable() ? "(Spendable)" : "(Not yet spendable)"));
                } else {
                    String date = DATE_FORMAT.format(utxoEntry.getHashIndex().getDate());
                    setText(date);
                    setContextMenu(new DateContextMenu(date, utxoEntry.getHashIndex()));
                }

                Tooltip tooltip = new Tooltip();
                int height = utxoEntry.getHashIndex().getHeight();
                tooltip.setText(height > 0 ? Integer.toString(height) : "Mempool");
                setTooltip(tooltip);
            }
            setGraphic(null);
        }
    }

    private static class DateContextMenu extends ContextMenu {
        public DateContextMenu(String date, BlockTransactionHashIndex reference) {
            MenuItem copyDate = new MenuItem("Copy Date");
            copyDate.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(date);
                Clipboard.getSystemClipboard().setContent(content);
            });

            MenuItem copyHeight = new MenuItem("Copy Block Height");
            copyHeight.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(reference.getHeight() > 0 ? Integer.toString(reference.getHeight()) : "Mempool");
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyDate, copyHeight);
        }
    }
}