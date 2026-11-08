package com.asanni.dashboard.controllers;

import com.asanni.dashboard.firebase.FirestoreService;
import com.asanni.dashboard.models.OnboardingData;
import com.asanni.dashboard.models.RestaurantOwner;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.GeoPoint;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FeaturedListingController {

    @FXML private StackPane imageContainer;
    @FXML private ImageView coverImageView;
    @FXML private Label photoCountLabel;
    @FXML private TextField descriptionField;
    @FXML private TextField coverPhotoUrlField;
    @FXML private HBox step1Card;
    @FXML private HBox step2Card;

    @FXML private Label statusBadge;
    @FXML private Button publishButton;

    private int currentPhotoCount = 4;
    private final int maxPhotos = 8;

    @FXML
    public void initialize() {
        // 1. Apply Rounded Corners Clip to the Cover Photo ImageView
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(coverImageView.fitWidthProperty());
        clip.heightProperty().bind(coverImageView.fitHeightProperty());
        clip.setArcWidth(28.0);
        clip.setArcHeight(28.0);
        coverImageView.setClip(clip);

        // 2. Load a warm restaurant cover photo (with fallback)
        loadDefaultCoverPhoto();
    }

    private void loadDefaultCoverPhoto() {
        try {
            // High-res warm restaurant interior matching your mockup
            String imageUrl = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80";
            Image image = new Image(imageUrl, true);
            coverImageView.setImage(image);
        } catch (Exception e) {
            System.err.println("Could not load default cover image: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddPhotos() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Restaurant Cover Photo");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );

        File selectedFile = fileChooser.showOpenDialog(imageContainer.getScene().getWindow());
        if (selectedFile != null) {
            Image newImage = new Image(selectedFile.toURI().toString());
            coverImageView.setImage(newImage);

            if (currentPhotoCount < maxPhotos) {
                currentPhotoCount++;
                photoCountLabel.setText(currentPhotoCount + " of " + maxPhotos);
            }
        }
    }

    @FXML
    private void handleEditCover() {
        // Opens file chooser to replace current cover photo
        handleAddPhotos();
    }

    @FXML
    private void handleReorder() {
        System.out.println("Reorder photos clicked.");
    }

    @FXML
    private void handleOpenPreview() {
        System.out.println("Opening external preview for Asanni...");
    }

    @FXML
    private void handleHelp() {
        System.out.println("Help button clicked.");
    }

    @FXML
    private void handlePublishProfile() {
        String coverUrl = coverPhotoUrlField.getText().trim();
        String shortDescription = descriptionField.getText().trim();

        // Save to OnboardingData
        OnboardingData data = OnboardingData.getInstance();
        data.setCoverUrl(coverUrl.isEmpty() ? "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80" : coverUrl);
        data.setShortDescription(shortDescription);

        // Upload to Firebase in background
        uploadToFirebase();
    }

    private void uploadToFirebase() {
        publishButton.setDisable(true);
        publishButton.setText("Publishing...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    FirestoreService.initialize();
                    Firestore db = FirestoreService.getDb();

                    if (db != null) {
                        OnboardingData data = OnboardingData.getInstance();

                        // Geocode the address to get coordinates
                        GeoPoint location = geocodeAddress(data.getAddress());
                        data.setLocation(location);

                        // Create RestaurantOwner object
                        RestaurantOwner restaurant = data.toRestaurantOwner();

                        // Upload to Firestore
                        db.collection("restaurantOwner")
                                .add(restaurant.toMap())
                                .get();

                        System.out.println("Restaurant data uploaded to Firebase successfully");
                    }
                } catch (Exception e) {
                    System.err.println("Error uploading to Firebase: " + e.getMessage());
                    throw e;
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                // Update UI status badge to Live
                statusBadge.setText("●  Live");
                statusBadge.getStyleClass().remove("status-badge-not-live");
                if (!statusBadge.getStyleClass().contains("status-badge-live")) {
                    statusBadge.getStyleClass().add("status-badge-live");
                }
                publishButton.setText("Profile published");

                // Navigate to Main Dashboard after a short delay
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1));
                pause.setOnFinished(event -> navigateToMainDashboard());
                pause.play();
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                publishButton.setDisable(false);
                publishButton.setText("Publish profile");
                System.err.println("Failed to publish: " + task.getException().getMessage());
            });
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private GeoPoint geocodeAddress(String address) {
        try {
            // Use a geocoding API to get coordinates
            // Using OpenStreetMap's Nominatim API (free, no API key required)
            String encodedAddress = java.net.URLEncoder.encode(address, "UTF-8");
            String url = "https://nominatim.openstreetmap.org/search?format=json&q=" + encodedAddress;

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "AsanniDashboard/1.0")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String jsonResponse = response.body();

            // Parse the JSON response to get lat and lon
            Pattern latPattern = Pattern.compile("\"lat\"\\s*:\\s*\"([^\"]+)\"");
            Pattern lonPattern = Pattern.compile("\"lon\"\\s*:\\s*\"([^\"]+)\"");

            Matcher latMatcher = latPattern.matcher(jsonResponse);
            Matcher lonMatcher = lonPattern.matcher(jsonResponse);

            if (latMatcher.find() && lonMatcher.find()) {
                double lat = Double.parseDouble(latMatcher.group(1));
                double lon = Double.parseDouble(lonMatcher.group(1));
                return new GeoPoint(lat, lon);
            }
        } catch (Exception e) {
            System.err.println("Error geocoding address: " + e.getMessage());
        }

        // Fallback to Karachi coordinates if geocoding fails
        return new GeoPoint(24.8607, 67.0011);
    }

    private void navigateToMainDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/maindashboard.fxml")
            );

            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);

            Stage stage = (Stage) publishButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Main Dashboard - Asanni");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleStep1Click() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/planselect.fxml")
            );

            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);

            Stage stage = (Stage) step1Card.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Select Plan - Asanni");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleStep2Click() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/signup.fxml")
            );

            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);

            Stage stage = (Stage) step2Card.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Create Account - Asanni");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}