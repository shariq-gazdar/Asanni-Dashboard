package com.asanni.dashboard.controllers;

import com.asanni.dashboard.firebase.FirestoreService;
import com.asanni.dashboard.models.OnboardingData;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class WaitingControlController {

    // Loading Screen Overlay
    @FXML private StackPane loadingOverlay;

    @FXML private Label logoBadge;
    @FXML private Label avatarLabel;
    @FXML private Label navBrandLabel;
    @FXML private Label statusHeadingLabel;
    @FXML private Label statusSubLabel;
    @FXML private VBox tipCard;
    @FXML private Label tipTextLabel;
    @FXML private StackPane heroBanner;
    @FXML private StackPane heroCircle;
    @FXML private Label heroPillBadge;
    @FXML private Label heroHeadingLabel;
    @FXML private Label heroSubtitleLabel;
    @FXML private VBox waitTimeInputBox;
    @FXML private TextField waitTimeTextField;
    @FXML private Label previewRestaurantLabel;
    @FXML private Label updatedTimeLabel;
    @FXML private Label customerStatusBadge;
    @FXML private VBox reopenButtonBox;
    @FXML private Button reopenButton;

    @FXML
    public void initialize() {
        // 1. Show GIF loading screen from loadingScreen.fxml
        showLoading();

        String name = OnboardingData.getInstance().getRestaurantName();
        if (name != null && !name.isEmpty()) {
            navBrandLabel.setText(name);
            previewRestaurantLabel.setText(name);
            setInitials(name);
        }

        // 2. Load acceptingDiners status from Firestore (hides loading when finished)
        loadFromFirestore();
    }

    private void showLoading() {
        if (loadingOverlay != null) {
            try {
                var resource = getClass().getResource("/fxml/loadingScreen.fxml");
                if (resource == null) {
                    resource = getClass().getResource("/fxml/LoadingScreen.fxml");
                }
                if (resource != null) {
                    Parent loadingRoot = FXMLLoader.load(resource);
                    loadingOverlay.getChildren().setAll(loadingRoot);
                }
            } catch (IOException e) {
                System.err.println("Could not load loadingScreen.fxml: " + e.getMessage());
            }
            loadingOverlay.setVisible(true);
            loadingOverlay.setManaged(true);
            loadingOverlay.toFront();
        }
    }

    private void hideLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(false);
            loadingOverlay.setManaged(false);
        }
    }

    private void setInitials(String name) {
        String initials = getInitials(name);
        if (logoBadge != null) logoBadge.setText(initials);
        if (avatarLabel != null) avatarLabel.setText(initials);
    }

    private void loadFromFirestore() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                FirestoreService.initialize();
                String name = OnboardingData.getInstance().getRestaurantName();
                var db = FirestoreService.getDb();

                if (db == null || name == null || name.isEmpty()) {
                    Platform.runLater(() -> updateUI(false, 0L));
                    return null;
                }

                List<QueryDocumentSnapshot> docs = db.collection("restaurantOwner")
                        .whereEqualTo("name", name)
                        .get().get().getDocuments();

                if (!docs.isEmpty()) {
                    var doc = docs.get(0);
                    Boolean accepting = doc.getBoolean("acceptingDiners");
                    Long waitMinutes = doc.getLong("waitMinutes");

                    Platform.runLater(() -> {
                        updateUI(accepting != null ? accepting : false, waitMinutes != null ? waitMinutes : 0L);
                    });
                } else {
                    Platform.runLater(() -> updateUI(false, 0L));
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(this::hideLoading));

        task.setOnFailed(e -> {
            System.err.println("Failed to load from Firestore: " + task.getException().getMessage());
            Platform.runLater(() -> {
                updateUI(false, 0L);
                hideLoading();
            });
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void updateUI(boolean acceptingDiners, long waitMinutes) {
        if (acceptingDiners) {
            setGreenTheme();
        } else {
            setRedTheme(waitMinutes);
        }
    }

    private void setGreenTheme() {
        heroBanner.getStyleClass().remove("closed-hero-banner");
        if (!heroBanner.getStyleClass().contains("open-hero-banner")) heroBanner.getStyleClass().add("open-hero-banner");
        heroCircle.getStyleClass().remove("closed-hero-circle");
        if (!heroCircle.getStyleClass().contains("open-hero-circle")) heroCircle.getStyleClass().add("open-hero-circle");

        heroPillBadge.getStyleClass().remove("closed-pill-badge");
        if (!heroPillBadge.getStyleClass().contains("open-pill-badge")) heroPillBadge.getStyleClass().add("open-pill-badge");
        heroPillBadge.setText("●   NO WAITING TIME");

        heroHeadingLabel.getStyleClass().remove("closed-main-heading");
        if (!heroHeadingLabel.getStyleClass().contains("open-main-heading")) heroHeadingLabel.getStyleClass().add("open-main-heading");
        heroHeadingLabel.setText("Customers can walk in now.");
        heroSubtitleLabel.setText("Your profile shows as available with no waiting time.");

        statusHeadingLabel.setText("Accepting diners");
        statusSubLabel.setText("Customers see you as available right now.");
        tipCard.setVisible(false);
        tipCard.setManaged(false);

        customerStatusBadge.getStyleClass().remove("no-tables-badge");
        if (!customerStatusBadge.getStyleClass().contains("available-badge")) customerStatusBadge.getStyleClass().add("available-badge");
        customerStatusBadge.setText("●  Available");

        waitTimeInputBox.setVisible(false);
        waitTimeInputBox.setManaged(false);
        reopenButtonBox.setVisible(false);
        reopenButtonBox.setManaged(false);
    }

    private void setRedTheme(long waitMinutes) {
        heroBanner.getStyleClass().remove("open-hero-banner");
        if (!heroBanner.getStyleClass().contains("closed-hero-banner")) heroBanner.getStyleClass().add("closed-hero-banner");
        heroCircle.getStyleClass().remove("open-hero-circle");
        if (!heroCircle.getStyleClass().contains("closed-hero-circle")) heroCircle.getStyleClass().add("closed-hero-circle");

        heroPillBadge.getStyleClass().remove("open-pill-badge");
        if (!heroPillBadge.getStyleClass().contains("closed-pill-badge")) heroPillBadge.getStyleClass().add("closed-pill-badge");
        heroPillBadge.setText("●   ALL TABLES CLOSED");

        heroHeadingLabel.getStyleClass().remove("open-main-heading");
        if (!heroHeadingLabel.getStyleClass().contains("closed-main-heading")) heroHeadingLabel.getStyleClass().add("closed-main-heading");
        heroHeadingLabel.setText("Customers see you as unavailable.");
        heroSubtitleLabel.setText("Your profile stays visible, but live discovery shows \"No tables right now.\"");

        statusHeadingLabel.setText("All tables closed");
        statusSubLabel.setText("Customers see you as unavailable until tables reopen.");
        tipCard.setVisible(true);
        tipCard.setManaged(true);
        tipTextLabel.setText("If you expect to reopen soon, publish a waiting time instead.");

        customerStatusBadge.getStyleClass().remove("available-badge");
        if (!customerStatusBadge.getStyleClass().contains("no-tables-badge")) customerStatusBadge.getStyleClass().add("no-tables-badge");
        customerStatusBadge.setText("●  No tables");

        waitTimeInputBox.setVisible(true);
        waitTimeInputBox.setManaged(true);
        if (waitMinutes > 0) {
            waitTimeTextField.setText(String.valueOf(waitMinutes));
        }
        reopenButtonBox.setVisible(true);
        reopenButtonBox.setManaged(true);
    }

    private String getInitials(String name) {
        String[] words = name.trim().split("\\s+");
        if (words.length >= 2) {
            return (words[0].charAt(0) + "" + words[words.length - 1].charAt(0)).toUpperCase();
        } else if (words.length == 1 && words[0].length() >= 2) {
            return (words[0].charAt(0) + "" + words[0].charAt(1)).toUpperCase();
        }
        return "AS";
    }

    @FXML
    private void handleReopenTableControls() {
        navigateTo("/fxml/TableControls.fxml", "Table Controls - Asanni");
    }

    @FXML
    private void handlePublishWaitTime() {
        String input = waitTimeTextField.getText();
        if (input == null || input.trim().isEmpty()) {
            return;
        }

        try {
            long minutes = Long.parseLong(input.trim());
            publishWaitTimeToFirestore(minutes);
        } catch (NumberFormatException e) {
            System.err.println("Invalid wait time: " + input);
        }
    }

    private void publishWaitTimeToFirestore(long minutes) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                FirestoreService.initialize();
                String name = OnboardingData.getInstance().getRestaurantName();
                var db = FirestoreService.getDb();
                if (db == null) return null;

                List<QueryDocumentSnapshot> docs = db.collection("restaurantOwner")
                        .whereEqualTo("name", name)
                        .get().get().getDocuments();

                if (!docs.isEmpty()) {
                    docs.get(0).getReference().update(
                            "waitMinutes", minutes
                    ).get();
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            updatedTimeLabel.setText("Updated just now");
        }));

        task.setOnFailed(e -> Platform.runLater(() ->
                System.err.println("Could not update wait time: " + task.getException().getMessage())
        ));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handlePreviewClick() {
        navigateTo("/fxml/featuredlisting.fxml", "Featured Listing - Asanni");
    }

    @FXML
    private void handleHelp() {
        navigateTo("/fxml/maindashboard.fxml", "Owner Dashboard - Asanni");
    }

    @FXML
    private void handleLogoClick() {
        navigateTo("/fxml/maindashboard.fxml", "Owner Dashboard - Asanni");
    }

    private void navigateTo(String fxmlPath, String title) {
        try {
            var resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                System.err.println("FXML file not found: " + fxmlPath);
                return;
            }
            Parent root = FXMLLoader.load(resource);
            Stage stage = (Stage) navBrandLabel.getScene().getWindow();
            stage.setScene(new Scene(root, 1220, 840));
            stage.setTitle(title);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}