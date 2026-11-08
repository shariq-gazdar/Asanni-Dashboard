package com.asanni.dashboard.controllers;

import com.asanni.dashboard.firebase.FirestoreService;
import com.asanni.dashboard.models.OnboardingData;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainDashboardController {

    // Loading Screen Overlay
    @FXML private StackPane loadingOverlay;

    // Top Navbar & Banner
    @FXML private Label logoBadge;
    @FXML private Label navBrandLabel;
    @FXML private Label headerTitleLabel;
    @FXML private Label avatarLabel;

    // Sidebar Navigation Cards
    @FXML private HBox navDashboard;
    @FXML private HBox navTables;
    @FXML private HBox navPreview;
    @FXML private HBox navWaitingControl;
    @FXML private HBox navSettings;

    // Metric Labels
    @FXML private Label totalTablesLabel;
    @FXML private Label freeTablesLabel;
    @FXML private Label occupiedTablesLabel;

    // Action Labels
    @FXML private Label updateTablesActionLabel;
    @FXML private Label waitTimeActionLabel;

    // Service Pulse
    @FXML private Label profileViewsLabel;
    @FXML private HBox pulseBarsContainer;

    // State variables
    private int totalTables;
    private int freeTables;
    private int occupiedTables;
    private final int[] waitTimeOptions = {0, 10, 15, 25, 30, 45};
    private int currentWaitIndex = 0;

    // Hourly views corresponding to the 10 Service Pulse bars
    private final int[] slotViews = {14, 21, 16, 32, 39, 25, 284, 34, 22, 17};

    private final OnboardingData onboardingData;

    public MainDashboardController() {
        this.onboardingData = OnboardingData.getInstance();
    }

    @FXML
    public void initialize() {
        // 1. Show GIF loading screen from loadingScreen.fxml
        showLoading();

        // 2. Set active nav & initial local UI state
        setActiveNav(navDashboard);
        loadOnboardingData();

        // 3. Fetch live data from Firestore (hideLoading() is called when this finishes)
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

    private void loadOnboardingData() {
        loadFromOnboardingData();
        updateTableMetricsUI();
        setupPulseBarClicks();
        setInitials();
    }

    private void loadFromOnboardingData() {
        totalTables = onboardingData.getNumberOfTables();
        freeTables = totalTables;
        occupiedTables = 0;

        String restaurantName = onboardingData.getRestaurantName();
        String area = onboardingData.getArea();
        setRestaurantInfo(restaurantName, area);
    }

    private void loadFromFirestore() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                FirestoreService.initialize();
                String name = onboardingData.getRestaurantName();
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
                    Long waitMinutes = doc.getLong("waitMinutes");
                    String area = doc.getString("areaId");

                    Platform.runLater(() -> {
                        if (total != null && total > 0) {
                            totalTables = total.intValue();
                            onboardingData.setNumberOfTables(totalTables);
                        }
                        if (available != null && available >= 0) {
                            freeTables = Math.min(available.intValue(), totalTables);
                        } else {
                            freeTables = totalTables;
                        }
                        occupiedTables = Math.max(0, totalTables - freeTables);
                        updateTableMetricsUI();

                        if (waitMinutes != null && waitMinutes > 0 && waitTimeActionLabel != null) {
                            waitTimeActionLabel.setText("Wait time: ~" + waitMinutes + " mins");
                        }
                        if ((onboardingData.getArea() == null || onboardingData.getArea().isEmpty()) && area != null) {
                            setRestaurantInfo(name, area);
                        }
                    });
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(this::hideLoading));

        task.setOnFailed(e -> {
            System.err.println("Failed to load dashboard data from Firestore: " + task.getException().getMessage());
            Platform.runLater(this::hideLoading);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void setInitials() {
        String name = onboardingData.getRestaurantName();
        if (name != null && !name.isEmpty()) {
            String initials = getInitials(name);
            if (logoBadge != null) logoBadge.setText(initials);
            if (avatarLabel != null) avatarLabel.setText(initials);
        }
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

    private void setupPulseBarClicks() {
        if (pulseBarsContainer == null) return;
        List<Node> bars = pulseBarsContainer.getChildren();
        for (int i = 0; i < bars.size(); i++) {
            final int index = i;
            Node bar = bars.get(i);
            bar.setOnMouseClicked(event -> {
                for (Node b : bars) {
                    b.getStyleClass().remove("pulse-bar-active");
                }
                bar.getStyleClass().add("pulse-bar-active");
                if (index < slotViews.length) {
                    profileViewsLabel.setText(String.valueOf(slotViews[index]));
                }
            });
        }
    }

    public void setRestaurantInfo(String name, String area) {
        if (name == null || name.isEmpty()) name = "Restaurant";
        String title = (area != null && !area.isEmpty()) ? name + " · " + area : name;
        if (navBrandLabel != null) navBrandLabel.setText(title);
        if (headerTitleLabel != null) headerTitleLabel.setText(title);
    }

    @FXML
    private void handleUpdateTables() {
         navigateTo("/fxml/tableControls.fxml", "Table Control - Asanni");
    }

    @FXML
    private void handleSetWaitTime() {
          navigateTo("/fxml/WaitingControl.fxml", "Waiting Control - Asanni");
    }

    private void updateTableMetricsUI() {
        if (totalTablesLabel != null) totalTablesLabel.setText(String.valueOf(totalTables));
        if (freeTablesLabel != null) freeTablesLabel.setText(String.valueOf(freeTables));
        if (occupiedTablesLabel != null) occupiedTablesLabel.setText(String.valueOf(occupiedTables));
    }

    // ==================== SIDEBAR NAVIGATION HANDLERS ====================

    @FXML
    private void handleNavDashboard() {
        setActiveNav(navDashboard);
    }

    @FXML
    private void handleNavTables() {
        navigateTo("/fxml/TableControls.fxml", "Table Controls - Asanni");
    }

    @FXML
    private void handleNavPreview() {
        navigateTo("/fxml/customerView.fxml", "Customer Preview - Asanni");
    }

    @FXML
    private void handleNavWaitingControl() {
        navigateTo("/fxml/WaitingControl.fxml", "Waiting Control - Asanni");
    }

    @FXML
    private void handleNavSettings() {
           navigateTo("/fxml/editDetails.fxml", "Edit Details - Asanni");
    }

    @FXML
    private void handleHelp() {
        navigateTo("/fxml/login.fxml", "Sign In - Asanni");
    }

    @FXML
    private void handleLogoClick() {
        // Already on dashboard
    }

    private void setActiveNav(HBox selectedCard) {
        List<HBox> navCards = new ArrayList<>();
        if (navDashboard != null) navCards.add(navDashboard);
        if (navTables != null) navCards.add(navTables);
        if (navPreview != null) navCards.add(navPreview);
        if (navWaitingControl != null) navCards.add(navWaitingControl);
        if (navSettings != null) navCards.add(navSettings);

        for (HBox card : navCards) {
            card.getStyleClass().remove("nav-card-active");
        }
        if (selectedCard != null && !selectedCard.getStyleClass().contains("nav-card-active")) {
            selectedCard.getStyleClass().add("nav-card-active");
        }
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