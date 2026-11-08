package com.asanni.dashboard.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label messageLabel;

    @FXML
    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please enter your email and password.");
            return;
        }

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            messageLabel.setText("Please enter a valid email address.");
            return;
        }

        // Firebase authentication will be added next.
        messageLabel.setText(
                "Form validated. Firebase authentication is not connected yet."
        );
    }

    @FXML
    private void openSignup() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/signup.fxml")
            );

            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);

            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Create Account - Asanni");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            messageLabel.setText("Error loading signup page: " + e.getMessage());
        }
    }
}
