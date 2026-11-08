package com.asanni.dashboard.controllers;

import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class LoadingScreenController {

    @FXML private Circle spinnerCircle;
    @FXML private Label loadingLabel;

    @FXML
    public void initialize() {
        // Create rotation animation for spinner
        RotateTransition rotate = new RotateTransition(Duration.seconds(1), spinnerCircle);
        rotate.setByAngle(360);
        rotate.setCycleCount(RotateTransition.INDEFINITE);
        rotate.setFromAngle(0);
        rotate.play();
    }

    public void setLoadingText(String text) {
        if (loadingLabel != null) {
            loadingLabel.setText(text);
        }
    }
}
