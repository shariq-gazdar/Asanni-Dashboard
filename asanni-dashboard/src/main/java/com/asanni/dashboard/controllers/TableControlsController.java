package com.asanni.dashboard.controllers;

import com.asanni.dashboard.firebase.FirestoreService;
import com.asanni.dashboard.models.OnboardingData;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;

public class TableControlsController {

    // Loading Screen Overlay
    @FXML private StackPane loadingOverlay;

    @FXML private Label logoBadge;
    @FXML private Label avatarLabel;
    @FXML private Label navBrandLabel;
    @FXML private HBox liveBanner;
    @FXML private Label liveTitleLabel;
    @FXML private Label liveSubLabel;
    @FXML private Label liveBadgeLabel;
    @FXML private Label totalLabel;
    @FXML private Label freeLabel;
    @FXML private Label occupiedLabel;
    @FXML private Button publishBtn;
    @FXML private Button totalMinusBtn, totalPlusBtn, freeMinusBtn, freePlusBtn;

    private int totalTables = 18;
    private int freeTables = 6;

    @FXML
    public void initialize() {
        // 1. Show GIF loading screen from loadingScreen.fxml
        showLoading();

        String name = OnboardingData.getInstance().getRestaurantName();
        if (name != null && !name.isEmpty()) {
            navBrandLabel.setText(name);
            setInitials(name);
        }

        updateUI();

        // 2. Load table counts from Firestore (hides loading when finished)
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

    private void loadFromFirestore() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                FirestoreService.initialize();
                String name = OnboardingData.getInstance().getRestaurantName();
                var db = FirestoreService.getDb();

                if (db == null || name == null || name.isEmpty()) {
                    return null;
                }

                List<QueryDocumentSnapshot> docs = db.collection("restaurantOwner")
                        .whereEqualTo("name", name)
                        .get().get().getDocuments();

                if (!docs.isEmpty()) {
                    var doc = docs.get(0);
                    Long total = doc.getLong("totalTables");
                    Long available = doc.getLong("availableTables");

                    Platform.runLater(() -> {
                        if (total != null && total > 0) {
                            totalTables = total.intValue();
                        }
                        if (available != null && available >= 0) {
                            freeTables = Math.min(available.intValue(), totalTables);
                        }
                        updateUI();
                    });
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(this::hideLoading));

        task.setOnFailed(e -> {
            System.err.println("Failed to load from Firestore: " + task.getException().getMessage());
            Platform.runLater(this::hideLoading);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void setInitials(String name) {
        String initials = getInitials(name);
        if (logoBadge != null) logoBadge.setText(initials);
        if (avatarLabel != null) avatarLabel.setText(initials);
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

    /* ---------- Quick actions (left sidebar) ---------- */
    @FXML private void handlePlusFree()  { freeTables = Math.min(freeTables + 1, totalTables); updateUI(); }
    @FXML private void handleMinusFree() { freeTables = Math.max(freeTables - 1, 0);          updateUI(); }
    @FXML private void handleSetFull()   { freeTables = 0;                                   updateUI(); }

    /* ---------- Steppers ---------- */
    @FXML private void handleTotalPlus() {
        totalTables++;
        if (freeTables > totalTables) freeTables = totalTables;
        updateUI();
    }
    @FXML private void handleTotalMinus() {
        if (totalTables > 1) {
            totalTables--;
            if (freeTables > totalTables) freeTables = totalTables;
        }
        updateUI();
    }
    @FXML private void handleFreePlus()  { freeTables = Math.min(freeTables + 1, totalTables); updateUI(); }
    @FXML private void handleFreeMinus() { freeTables = Math.max(freeTables - 1, 0);          updateUI(); }

    private void updateUI() {
        int occupied = totalTables - freeTables;
        totalLabel.setText(String.valueOf(totalTables));
        freeLabel.setText(String.valueOf(freeTables));
        occupiedLabel.setText(String.valueOf(occupied));
        liveSubLabel.setText("Customers see " + freeTables + " free table" + (freeTables == 1 ? "" : "s"));
        publishBtn.setText("Publish " + freeTables + " free table" + (freeTables == 1 ? "" : "s"));

        OnboardingData.getInstance().setNumberOfTables(totalTables);

        totalMinusBtn.setDisable(totalTables <= 1);
        freeMinusBtn.setDisable(freeTables <= 0);
        freePlusBtn.setDisable(freeTables >= totalTables);
        totalPlusBtn.setDisable(false);

        updateLiveBanner();
    }

    private void updateLiveBanner() {
        if (liveBanner == null || liveTitleLabel == null || liveBadgeLabel == null) return;

        if (freeTables == 0) {
            liveBanner.getStyleClass().remove("live-banner");
            if (!liveBanner.getStyleClass().contains("full-banner")) liveBanner.getStyleClass().add("full-banner");
            liveTitleLabel.setText("Full");
            liveTitleLabel.getStyleClass().remove("live-title");
            if (!liveTitleLabel.getStyleClass().contains("full-title")) liveTitleLabel.getStyleClass().add("full-title");
            liveSubLabel.setText("No tables available");
            liveSubLabel.getStyleClass().remove("live-sub");
            if (!liveSubLabel.getStyleClass().contains("full-sub")) liveSubLabel.getStyleClass().add("full-sub");
            liveBadgeLabel.setText("● FULL");
            liveBadgeLabel.getStyleClass().remove("live-badge");
            if (!liveBadgeLabel.getStyleClass().contains("full-badge")) liveBadgeLabel.getStyleClass().add("full-badge");
        } else {
            liveBanner.getStyleClass().remove("full-banner");
            if (!liveBanner.getStyleClass().contains("live-banner")) liveBanner.getStyleClass().add("live-banner");
            liveTitleLabel.setText("Live now");
            liveTitleLabel.getStyleClass().remove("full-title");
            if (!liveTitleLabel.getStyleClass().contains("live-title")) liveTitleLabel.getStyleClass().add("live-title");
            liveSubLabel.setText("Customers see " + freeTables + " free table" + (freeTables == 1 ? "" : "s"));
            liveSubLabel.getStyleClass().remove("full-sub");
            if (!liveSubLabel.getStyleClass().contains("live-sub")) liveSubLabel.getStyleClass().add("live-sub");
            liveBadgeLabel.setText("● LIVE");
            liveBadgeLabel.getStyleClass().remove("full-badge");
            if (!liveBadgeLabel.getStyleClass().contains("live-badge")) liveBadgeLabel.getStyleClass().add("live-badge");
        }
    }

    /* ---------- Publish to Firestore ---------- */
    @FXML
    private void handlePublish() {
        publishBtn.setDisable(true);
        String originalText = publishBtn.getText();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                FirestoreService.initialize();
                String name = OnboardingData.getInstance().getRestaurantName();
                var db = FirestoreService.getDb();

                List<QueryDocumentSnapshot> docs = db.collection("restaurantOwner")
                        .whereEqualTo("name", name)
                        .get().get().getDocuments();

                boolean acceptingDiners = freeTables > 0;
                long availabilityUpdatedAt = System.currentTimeMillis();

                if (!docs.isEmpty()) {
                    docs.get(0).getReference().update(
                            "totalTables", totalTables,
                            "availableTables", freeTables,
                            "acceptingDiners", acceptingDiners,
                            "availabilityUpdatedAt", availabilityUpdatedAt
                    ).get();
                } else {
                    var map = new java.util.HashMap<String, Object>();
                    map.put("name", name);
                    map.put("totalTables", totalTables);
                    map.put("availableTables", freeTables);
                    map.put("acceptingDiners", acceptingDiners);
                    map.put("availabilityUpdatedAt", availabilityUpdatedAt);
                    db.collection("restaurantOwner").add(map).get();
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            publishBtn.setText("Published ✓");
            PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
            pause.setOnFinished(ev -> {
                publishBtn.setDisable(false);
                publishBtn.setText(originalText);
            });
            pause.play();
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            publishBtn.setDisable(false);
            publishBtn.setText("Publish failed — retry");
            System.err.println("Publish failed: " + task.getException().getMessage());
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /* ---------- Close all tables ---------- */
    @FXML
    private void handleCloseAll() {
        freeTables = 0;
        updateUI();
        handlePublish();
    }

    /* ---------- Help / nav ---------- */
    @FXML
    private void handleHelp() {
        navigateTo("/fxml/MainDashboard.fxml", "Dashboard - Asanni");
    }

    @FXML
    private void handleLogoClick() {
        navigateTo("/fxml/maindashboard.fxml", "Owner Dashboard - Asanni");
    }

    private void navigateTo(String fxmlPath, String title) {
        try {
            var resource = getClass().getResource(fxmlPath);
            if (resource == null) { System.err.println("Missing: " + fxmlPath); return; }
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