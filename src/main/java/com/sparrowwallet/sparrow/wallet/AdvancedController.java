package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.DateStringConverter;
import com.sparrowwallet.sparrow.event.SettingsChangedEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

import java.net.URL;
import java.time.ZoneId;
import java.util.Date;
import java.util.ResourceBundle;

public class AdvancedController implements Initializable {
    @FXML
    private DatePicker birthDate;

    @FXML
    private Spinner<Integer> gapLimit;

    @FXML
    private Spinner<Integer> receiveChId;

    @FXML
    private Spinner<Integer> changeChId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void initializeView(Wallet wallet) {
        birthDate.setConverter(new DateStringConverter());
        if(wallet.getBirthDate() != null) {
            birthDate.setValue(wallet.getBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
        birthDate.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                wallet.setBirthDate(Date.from(newValue.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
                EventManager.get().post(new SettingsChangedEvent(wallet, SettingsChangedEvent.Type.BIRTH_DATE));
            }
        });

        gapLimit.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(Wallet.DEFAULT_LOOKAHEAD, 10000, wallet.getGapLimit()));
        gapLimit.valueProperty().addListener((observable, oldValue, newValue) -> {
            wallet.setGapLimit(newValue);
            EventManager.get().post(new SettingsChangedEvent(wallet, SettingsChangedEvent.Type.GAP_LIMIT));
        });
        
        receiveChId.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 2147483647, wallet.getReceiveChain().getPathIndex().i()));
        receiveChId.valueProperty().addListener((observable, oldValue, newValue) -> {
            int goodValue = 0;
            int otherValue = changeChId.getValue();
            if (newValue == otherValue) {
                if (oldValue < newValue) { // we were going upwards
                    goodValue = otherValue == Integer.MAX_VALUE ? 0 : otherValue + 1;
                }
                else { // we were going downwards
                    goodValue = otherValue == 0 ? Integer.MAX_VALUE : otherValue - 1;
                }
                receiveChId.getEditor().setText(Integer.toString(goodValue));
            }
            else { // all good, no collision
                goodValue = newValue;
            }
            wallet.setReceiveChId(goodValue);
            EventManager.get().post(new SettingsChangedEvent(wallet, SettingsChangedEvent.Type.WALLET_CHAINS));
        });
        
        changeChId.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 2147483647, wallet.getChangeChain().getPathIndex().i()));
        changeChId.valueProperty().addListener((observable, oldValue, newValue) -> {
            int goodValue = 1;
            int otherValue = receiveChId.getValue();
            if (newValue == otherValue) {
                if (oldValue < newValue) { // we were going upwards
                    goodValue = otherValue == Integer.MAX_VALUE ? 0 : otherValue + 1;
                }
                else { // we were going downwards
                    goodValue = otherValue == 0 ? Integer.MAX_VALUE : otherValue - 1;
                }
                changeChId.getEditor().setText(Integer.toString(goodValue));
            }
            else { // all good, no collision
                goodValue = newValue;
            }
            wallet.setChangeChId(goodValue);
            EventManager.get().post(new SettingsChangedEvent(wallet, SettingsChangedEvent.Type.WALLET_CHAINS));
        });
        
    }
}
