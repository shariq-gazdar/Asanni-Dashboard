package com.asanni.dashboard.controllers;

import com.asanni.dashboard.models.OnboardingData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class PlanSelectController {

    @FXML
    private Button helpButton;

    @FXML
    private Button profileButton;

    @FXML
    private TextField restaurantNameField;

    @FXML
    private TextField totalTablesField;

    @FXML
    private void handleHelp(ActionEvent event) {
        System.out.println("Help button clicked");
    }

    @FXML
    private void handleProfile(ActionEvent event) {
        System.out.println("Profile button clicked");
    }

    @FXML
    private void handleChooseEssential(ActionEvent event) {
        OnboardingData.getInstance().setSelectedPlan("essential");
        navigateToSignup();
    }

    @FXML
    private void handleChooseFeatured(ActionEvent event) {
        OnboardingData.getInstance().setSelectedPlan("featured");
        navigateToSignup();
    }

    @FXML
    private void handleContinueWithFeatured(ActionEvent event) {
        OnboardingData.getInstance().setSelectedPlan("featured");
        navigateToSignup();
    }

    @FXML
    private void handleQuickLogin(ActionEvent event) {
        String restaurantName = restaurantNameField.getText();
        String tablesText = totalTablesField.getText();

        if (restaurantName == null || restaurantName.trim().isEmpty()) {
            System.err.println("Restaurant name is required");
            return;
        }

        try {
            int totalTables = Integer.parseInt(tablesText.trim());
            if (totalTables <= 0) {
                System.err.println("Total tables must be a positive number");
                return;
            }

            OnboardingData data = OnboardingData.getInstance();
            data.setRestaurantName(restaurantName);
            data.setNumberOfTables(totalTables);
            data.setSelectedPlan("featured");

            navigateToMainDashboard();
        } catch (NumberFormatException e) {
            System.err.println("Invalid number for total tables");
        }
    }

    private void navigateToMainDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/maindashboard.fxml")
            );

            Parent root = loader.load();
            Scene scene = new Scene(root, 1220, 840);

            Stage stage = (Stage) helpButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Owner Dashboard - Asanni");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void navigateToSignup() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/signup.fxml")
            );

            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);

            Stage stage = (Stage) helpButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Create Account - Asanni");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}