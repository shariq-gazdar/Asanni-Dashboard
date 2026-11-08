package com.asanni.dashboard.controllers;

import com.asanni.dashboard.firebase.FirestoreService;
import com.asanni.dashboard.models.OnboardingData;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditDetailsController {

    private static final String COLLECTION_NAME = "restaurantOwner";
    private static final int MAX_DESC_LENGTH = 300;
    private static final String DEFAULT_COVER_URL =
            "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=900&q=80";

    // Full-screen GIF Loading Overlay
    @FXML private StackPane loadingOverlay;

    // Top Navbar & Sidebar
    @FXML private Label logoBadge;
    @FXML private Label navBrandLabel;
    @FXML private Label avatarLabel;
    @FXML private Label sidebarRestaurantLabel;

    // Form Fields
    @FXML private TextField nameField;
    @FXML private TextField addressField;
    @FXML private TextField cuisineField;
    @FXML private TextArea descriptionArea;
    @FXML private Label charCountLabel;

    // Cover Photo & Logo Media (Direct URL + Live Preview)
    @FXML private StackPane coverPreviewContainer;
    @FXML private Label coverPlaceholderLabel;
    @FXML private ImageView coverImageView;
    @FXML private HBox currentCoverBadge;
    @FXML private Label coverBadgeText;
    @FXML private TextField coverUrlField;

    @FXML private StackPane logoBoxContainer;
    @FXML private Label logoPreviewInitials;
    @FXML private ImageView logoImageView;
    @FXML private Label logoFileNameLabel;
    @FXML private TextField logoUrlField;

    // Bottom Save Bar
    @FXML private Label saveStatusSubLabel;
    @FXML private Button saveBtn;

    private final OnboardingData onboardingData = OnboardingData.getInstance();

    // Cached Firestore document reference so renaming still updates the exact same document
    private DocumentReference loadedDocRef = null;

    // Snapshots of loaded state so "Cancel" can revert unsaved edits
    private String loadedLookupName = "";
    private String loadedName = "";
    private String loadedAddress = "";
    private String loadedAreaDisplay = "";
    private String loadedCuisineText = "";
    private String loadedDescription = "";
    private String loadedCoverUrl = "";
    private String loadedLogoUrl = "";

    private String currentCoverUrl = "";
    private String currentLogoUrl = "";

    @FXML
    public void initialize() {
        // 1. Show full-screen GIF loading overlay
        showLoading();

        // 2. Setup rounded corners on Cover Photo & Logo
        setupImageClips();

        // 3. Setup live character counter, name sync, and direct URL preview listeners
        setupFormListeners();

        // 4. Load initial values from OnboardingData singleton (if already populated)
        populateFromOnboardingData();

        // 5. Fetch actual restaurant profile from Firestore (hides loader when done)
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

    private void setupImageClips() {
        if (coverPreviewContainer != null && coverImageView != null) {
            coverImageView.fitWidthProperty().bind(coverPreviewContainer.widthProperty());
            Rectangle coverClip = new Rectangle();
            coverClip.setArcWidth(28.0);
            coverClip.setArcHeight(28.0);
            coverClip.widthProperty().bind(coverPreviewContainer.widthProperty());
            coverClip.heightProperty().bind(coverPreviewContainer.heightProperty());
            coverPreviewContainer.setClip(coverClip);
        }

        if (logoBoxContainer != null) {
            Rectangle logoClip = new Rectangle(64.0, 64.0);
            logoClip.setArcWidth(28.0);
            logoClip.setArcHeight(28.0);
            logoBoxContainer.setClip(logoClip);
        }
    }

    private void setupFormListeners() {
        if (descriptionArea != null) {
            descriptionArea.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.length() > MAX_DESC_LENGTH) {
                    descriptionArea.setText(oldVal);
                    return;
                }
                int len = newVal == null ? 0 : newVal.length();
                if (charCountLabel != null) {
                    charCountLabel.setText(len + " / " + MAX_DESC_LENGTH);
                }
            });
        }

        if (nameField != null) {
            nameField.textProperty().addListener((obs, oldVal, newVal) -> {
                String display = (newVal == null || newVal.isBlank()) ? "Restaurant" : newVal.trim();
                if (navBrandLabel != null) navBrandLabel.setText(display);
                updateInitialsAndSidebar(display);
                if (!isValidImageUrl(currentLogoUrl)) {
                    updateLogoPreview(currentLogoUrl, display);
                }
            });
        }

        if (addressField != null) {
            addressField.textProperty().addListener((obs, oldVal, newVal) -> {
                String currentName = (nameField != null && nameField.getText() != null && !nameField.getText().isBlank())
                        ? nameField.getText().trim()
                        : "Restaurant";
                updateInitialsAndSidebar(currentName);
            });
        }

        // Live Cover URL text listener
        if (coverUrlField != null) {
            coverUrlField.textProperty().addListener((obs, oldVal, newVal) -> {
                currentCoverUrl = (newVal == null) ? "" : newVal.trim();
                updateCoverPreview(currentCoverUrl);
            });
        }

        // Live Logo URL text listener
        if (logoUrlField != null) {
            logoUrlField.textProperty().addListener((obs, oldVal, newVal) -> {
                currentLogoUrl = (newVal == null) ? "" : newVal.trim();
                updateLogoPreview(currentLogoUrl, nameField != null ? nameField.getText() : "");
            });
        }
    }

    private void populateFromOnboardingData() {
        String name = onboardingData.getRestaurantName();
        if (name != null && !name.isBlank() && !"Asanni".equalsIgnoreCase(name.trim())) {
            loadedLookupName = name.trim();
            loadedName = name.trim();
        }

        if (onboardingData.getAddress() != null && !onboardingData.getAddress().isBlank()) {
            loadedAddress = onboardingData.getAddress().trim();
        }

        if (onboardingData.getArea() != null && !onboardingData.getArea().isBlank()) {
            loadedAreaDisplay = onboardingData.getArea().trim();
        }

        List<String> obCuisines = onboardingData.getCuisines();
        if (obCuisines != null && !obCuisines.isEmpty()) {
            loadedCuisineText = String.join(", ", obCuisines);
        } else if (onboardingData.getPrimaryCuisine() != null && !onboardingData.getPrimaryCuisine().isBlank()) {
            loadedCuisineText = onboardingData.getPrimaryCuisine().trim();
        }

        if (onboardingData.getShortDescription() != null && !onboardingData.getShortDescription().isBlank()) {
            loadedDescription = onboardingData.getShortDescription().trim();
        }

        if (onboardingData.getCoverUrl() != null && !onboardingData.getCoverUrl().isBlank()) {
            loadedCoverUrl = onboardingData.getCoverUrl().trim();
            currentCoverUrl = loadedCoverUrl;
        }

        if (onboardingData.getLogoUrl() != null && !onboardingData.getLogoUrl().isBlank()) {
            loadedLogoUrl = onboardingData.getLogoUrl().trim();
            currentLogoUrl = loadedLogoUrl;
        }

        applySnapshotToFields();
    }

    private void applySnapshotToFields() {
        if (nameField != null) {
            nameField.setText(loadedName != null ? loadedName : "");
        }
        if (addressField != null) {
            addressField.setText(loadedAddress != null ? loadedAddress : "");
        }
        if (cuisineField != null) {
            cuisineField.setText(loadedCuisineText != null ? loadedCuisineText : "");
        }
        if (descriptionArea != null) {
            String desc = loadedDescription != null ? loadedDescription : "";
            descriptionArea.setText(desc);
            if (charCountLabel != null) {
                charCountLabel.setText(desc.length() + " / " + MAX_DESC_LENGTH);
            }
        }

        currentCoverUrl = loadedCoverUrl != null ? loadedCoverUrl : "";
        currentLogoUrl = loadedLogoUrl != null ? loadedLogoUrl : "";

        if (coverUrlField != null) {
            coverUrlField.setText(currentCoverUrl);
        }
        if (logoUrlField != null) {
            logoUrlField.setText(currentLogoUrl);
        }

        String displayName = (loadedName != null && !loadedName.isBlank()) ? loadedName : "Restaurant";
        if (navBrandLabel != null) {
            navBrandLabel.setText(displayName);
        }

        updateCoverPreview(currentCoverUrl);
        updateLogoPreview(currentLogoUrl, displayName);
        updateInitialsAndSidebar(displayName);
    }

    private void loadFromFirestore() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                FirestoreService.initialize();
                var db = FirestoreService.getDb();
                if (db == null) return null;

                List<QueryDocumentSnapshot> docs = db.collection(COLLECTION_NAME)
                        .get().get().getDocuments();

                if (docs.isEmpty()) return null;

                QueryDocumentSnapshot doc = findCurrentRestaurantDoc(docs);
                if (doc == null) return null;

                loadedDocRef = doc.getReference();

                String docName = safeString(doc, "name");
                if (docName == null || docName.isBlank()) {
                    docName = safeString(doc, "restaurantName");
                }
                String docAddress = safeString(doc, "address");
                String docAreaId = safeString(doc, "areaId");
                String docCityId = safeString(doc, "cityId");
                String docPhone = safeString(doc, "phone");
                String docDesc = safeString(doc, "shortDescription");
                if (docDesc == null || docDesc.isBlank()) {
                    docDesc = safeString(doc, "description");
                }
                String docCover = safeString(doc, "coverUrl");
                String docLogo = safeString(doc, "logoUrl");
                Long docPrice = safeLong(doc, "price");
                Long docTotalTables = safeLong(doc, "totalTables");
                List<String> docCuisines = extractCuisines(doc);

                final String finalName = docName != null ? docName.trim() : "";
                final String finalAddress = docAddress != null ? docAddress.trim() : "";
                final String finalAreaDisplay = formatAreaAndCity(docAreaId, docCityId, finalAddress);
                final String finalCuisineText = !docCuisines.isEmpty() ? String.join(", ", docCuisines) : "";
                final String finalDesc = docDesc != null ? docDesc.trim() : "";
                final String finalCover = docCover != null ? docCover.trim() : "";
                final String finalLogo = docLogo != null ? docLogo.trim() : "";

                // Sync OnboardingData singleton so all other screens have the actual restaurant data
                if (!finalName.isEmpty()) onboardingData.setRestaurantName(finalName);
                if (!finalAddress.isEmpty()) onboardingData.setAddress(finalAddress);
                if (!finalAreaDisplay.isEmpty()) onboardingData.setArea(finalAreaDisplay);
                if (docCityId != null && !docCityId.isBlank()) onboardingData.setCity(capitalizeWords(docCityId));
                if (!docCuisines.isEmpty()) {
                    onboardingData.setCuisines(docCuisines);
                    onboardingData.setPrimaryCuisine(docCuisines.get(0));
                }
                if (!finalDesc.isEmpty()) onboardingData.setShortDescription(finalDesc);
                if (!finalCover.isEmpty()) onboardingData.setCoverUrl(finalCover);
                if (!finalLogo.isEmpty()) onboardingData.setLogoUrl(finalLogo);
                if (docPhone != null && !docPhone.isBlank()) onboardingData.setPhone(docPhone.trim());
                if (docPrice != null && docPrice > 0) onboardingData.setAvgSpendPerHead(docPrice.intValue());
                if (docTotalTables != null && docTotalTables > 0) onboardingData.setTotalTables(docTotalTables.intValue());

                Platform.runLater(() -> {
                    loadedLookupName = finalName;
                    loadedName = finalName;
                    loadedAddress = finalAddress;
                    loadedAreaDisplay = finalAreaDisplay;
                    // Only update cuisine and description if Firestore has actual data
                    if (!finalCuisineText.isEmpty()) {
                        loadedCuisineText = finalCuisineText;
                    }
                    if (!finalDesc.isEmpty()) {
                        loadedDescription = finalDesc;
                    }
                    loadedCoverUrl = finalCover;
                    loadedLogoUrl = finalLogo;

                    applySnapshotToFields();
                });

                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(this::hideLoading));

        task.setOnFailed(e -> {
            System.err.println("Failed to load restaurant profile from Firestore: " + task.getException().getMessage());
            Platform.runLater(this::hideLoading);
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Finds the current logged-in restaurant document from restaurantOwner:
     * 1. Matches OnboardingData restaurantName (if set and not placeholder "Asanni")
     * 2. Matches OnboardingData phone
     * 3. Matches OnboardingData address
     * 4. Falls back to the document with the latest availabilityUpdatedAt timestamp
     */
    private QueryDocumentSnapshot findCurrentRestaurantDoc(List<QueryDocumentSnapshot> docs) {
        if (docs == null || docs.isEmpty()) return null;

        String targetName = onboardingData.getRestaurantName();
        if (targetName != null && !targetName.isBlank() && !"Asanni".equalsIgnoreCase(targetName.trim())) {
            for (QueryDocumentSnapshot d : docs) {
                String n = safeString(d, "name");
                if (n == null || n.isBlank()) n = safeString(d, "restaurantName");
                if (n != null && n.trim().equalsIgnoreCase(targetName.trim())) {
                    return d;
                }
            }
        }

        String targetPhone = onboardingData.getPhone();
        if (targetPhone != null && !targetPhone.isBlank()) {
            String cleanTarget = targetPhone.replaceAll("[^0-9]", "");
            if (!cleanTarget.isEmpty()) {
                for (QueryDocumentSnapshot d : docs) {
                    String p = safeString(d, "phone");
                    if (p != null && p.replaceAll("[^0-9]", "").equals(cleanTarget)) {
                        return d;
                    }
                }
            }
        }

        String targetAddress = onboardingData.getAddress();
        if (targetAddress != null && !targetAddress.isBlank()) {
            for (QueryDocumentSnapshot d : docs) {
                String a = safeString(d, "address");
                if (a != null && a.trim().equalsIgnoreCase(targetAddress.trim())) {
                    return d;
                }
            }
        }

        QueryDocumentSnapshot latestDoc = docs.get(0);
        Instant latestInstant = Instant.EPOCH;
        for (QueryDocumentSnapshot d : docs) {
            Instant ts = safeInstant(d, "availabilityUpdatedAt");
            if (ts != null && ts.isAfter(latestInstant)) {
                latestInstant = ts;
                latestDoc = d;
            }
        }
        return latestDoc;
    }

    /* ==================== DIRECT TEXT MEDIA HANDLERS ==================== */

    @FXML
    private void handleChangeCover() {
        if (coverUrlField != null) {
            String url = coverUrlField.getText() != null ? coverUrlField.getText().trim() : "";
            if (url.isEmpty()) {
                coverUrlField.requestFocus();
            } else {
                currentCoverUrl = url;
                updateCoverPreview(currentCoverUrl);
            }
        }
    }

    @FXML
    private void handleRemoveCover() {
        currentCoverUrl = "";
        if (coverUrlField != null) {
            coverUrlField.clear();
        }
        updateCoverPreview("");
    }

    @FXML
    private void handleChangeLogo() {
        if (logoUrlField != null) {
            String url = logoUrlField.getText() != null ? logoUrlField.getText().trim() : "";
            if (url.isEmpty()) {
                logoUrlField.requestFocus();
            } else {
                currentLogoUrl = url;
                updateLogoPreview(currentLogoUrl, nameField != null ? nameField.getText() : "");
            }
        }
    }

    @FXML
    private void handleRemoveLogo() {
        currentLogoUrl = "";
        if (logoUrlField != null) {
            logoUrlField.clear();
        }
        updateLogoPreview("", nameField != null ? nameField.getText() : "");
    }

    private boolean isValidImageUrl(String url) {
        if (url == null) return false;
        String trimmed = url.trim();
        if (trimmed.equalsIgnoreCase("https://") || trimmed.equalsIgnoreCase("http://")) {
            return false;
        }
        return (trimmed.startsWith("https://") || trimmed.startsWith("http://") || trimmed.startsWith("file:"))
                && trimmed.length() > 10;
    }

    private void updateCoverPreview(String url) {
        if (isValidImageUrl(url)) {
            try {
                Image img = new Image(url.trim(), true);
                coverImageView.setImage(img);
                coverImageView.setVisible(true);
                if (coverPlaceholderLabel != null) coverPlaceholderLabel.setVisible(false);
                if (coverBadgeText != null) {
                    coverBadgeText.setText(url.trim().equals(loadedCoverUrl) ? "Current cover" : "Live cover preview");
                }

                img.errorProperty().addListener((obs, wasErr, isErr) -> {
                    if (isErr) {
                        Platform.runLater(() -> {
                            coverImageView.setVisible(false);
                            if (coverPlaceholderLabel != null) {
                                coverPlaceholderLabel.setText("Invalid image URL");
                                coverPlaceholderLabel.setVisible(true);
                            }
                            if (coverBadgeText != null) coverBadgeText.setText("URL error");
                        });
                    }
                });
                return;
            } catch (Exception ignored) {
            }
        }

        // If no valid cover URL is set (e.g. "" or "https://"), show default cover or placeholder
        if (url == null || url.isBlank() || url.trim().equalsIgnoreCase("https://")) {
            try {
                Image fallback = new Image(DEFAULT_COVER_URL, true);
                coverImageView.setImage(fallback);
                coverImageView.setVisible(true);
                if (coverPlaceholderLabel != null) coverPlaceholderLabel.setVisible(false);
                if (coverBadgeText != null) coverBadgeText.setText("Default cover");
                return;
            } catch (Exception ignored) {
            }
        }

        coverImageView.setImage(null);
        coverImageView.setVisible(false);
        if (coverPlaceholderLabel != null) {
            coverPlaceholderLabel.setText("No cover photo");
            coverPlaceholderLabel.setVisible(true);
        }
        if (coverBadgeText != null) coverBadgeText.setText("No cover photo");
    }

    private void updateLogoPreview(String url, String restaurantName) {
        if (isValidImageUrl(url)) {
            try {
                Image img = new Image(url.trim(), true);
                logoImageView.setImage(img);
                logoImageView.setVisible(true);
                if (logoPreviewInitials != null) logoPreviewInitials.setVisible(false);
                if (logoFileNameLabel != null) logoFileNameLabel.setText("Current logo preview");

                img.errorProperty().addListener((obs, wasErr, isErr) -> {
                    if (isErr) {
                        Platform.runLater(() -> {
                            logoImageView.setVisible(false);
                            if (logoPreviewInitials != null) logoPreviewInitials.setVisible(true);
                            if (logoFileNameLabel != null) logoFileNameLabel.setText("Invalid logo URL");
                        });
                    }
                });
                return;
            } catch (Exception ignored) {
            }
        }

        logoImageView.setImage(null);
        logoImageView.setVisible(false);
        if (logoPreviewInitials != null) logoPreviewInitials.setVisible(true);
        String slug = (restaurantName == null || restaurantName.isBlank())
                ? "restaurant"
                : restaurantName.trim().toLowerCase().replaceAll("\\s+", "-");
        if (logoFileNameLabel != null) {
            logoFileNameLabel.setText(slug + "-logo.png");
        }
    }

    private void updateInitialsAndSidebar(String name) {
        String displayName = (name == null || name.isBlank()) ? "Restaurant" : name.trim();
        String initials = getInitials(displayName);
        if (logoBadge != null) logoBadge.setText(initials);
        if (avatarLabel != null) avatarLabel.setText(initials);
        if (logoPreviewInitials != null) logoPreviewInitials.setText(initials);

        String areaText = loadedAreaDisplay;
        if (areaText == null || areaText.isBlank()) {
            String addr = addressField != null && addressField.getText() != null ? addressField.getText().trim() : "";
            areaText = extractShortAreaFromAddress(addr);
        }
        if (sidebarRestaurantLabel != null) {
            sidebarRestaurantLabel.setText(areaText != null && !areaText.isBlank()
                    ? displayName + " · " + areaText
                    : displayName);
        }
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "RS";
        String[] words = name.trim().split("\\s+");
        if (words.length >= 2) {
            return (words[0].charAt(0) + "" + words[words.length - 1].charAt(0)).toUpperCase();
        } else if (words[0].length() >= 2) {
            return (words[0].charAt(0) + "" + words[0].charAt(1)).toUpperCase();
        }
        return words[0].toUpperCase();
    }

    /* ==================== SAVE & CANCEL HANDLERS ==================== */

    @FXML
    private void handleCancel() {
        applySnapshotToFields();
        if (saveStatusSubLabel != null) {
            saveStatusSubLabel.setText("Changes discarded.");
        }
    }

    @FXML
    private void handleSaveChanges() {
        String updatedName = nameField != null && nameField.getText() != null ? nameField.getText().trim() : "";
        if (updatedName.isEmpty()) {
            if (saveStatusSubLabel != null) {
                saveStatusSubLabel.setText("Restaurant name cannot be empty.");
            }
            return;
        }

        String updatedAddress = addressField != null && addressField.getText() != null
                ? addressField.getText().trim()
                : "";
        String updatedCuisineRaw = cuisineField != null && cuisineField.getText() != null
                ? cuisineField.getText().trim()
                : "";
        List<String> updatedCuisines = parseCuisineList(updatedCuisineRaw);
        String updatedCuisineText = String.join(", ", updatedCuisines);

        String updatedDesc = descriptionArea != null && descriptionArea.getText() != null
                ? descriptionArea.getText().trim()
                : "";
        String updatedCoverUrl = coverUrlField != null && coverUrlField.getText() != null
                ? coverUrlField.getText().trim()
                : currentCoverUrl;
        String updatedLogoUrl = logoUrlField != null && logoUrlField.getText() != null
                ? logoUrlField.getText().trim()
                : currentLogoUrl;

        currentCoverUrl = updatedCoverUrl;
        currentLogoUrl = updatedLogoUrl;

        // 1. Update local OnboardingData singleton immediately
        onboardingData.setRestaurantName(updatedName);
        onboardingData.setAddress(updatedAddress);
        if (!updatedCuisines.isEmpty()) {
            onboardingData.setPrimaryCuisine(updatedCuisines.get(0));
            onboardingData.setCuisines(updatedCuisines);
        } else {
            onboardingData.setPrimaryCuisine("");
            onboardingData.setCuisines(Collections.emptyList());
        }
        onboardingData.setShortDescription(updatedDesc);
        onboardingData.setCoverUrl(updatedCoverUrl);
        onboardingData.setLogoUrl(updatedLogoUrl);

        saveBtn.setDisable(true);
        saveBtn.setText("Saving...");
        if (saveStatusSubLabel != null) {
            saveStatusSubLabel.setText("Saving your profile changes to Firestore...");
        }

        final String previousLookupName = (loadedLookupName == null || loadedLookupName.isBlank())
                ? updatedName
                : loadedLookupName;

        // 2. Persist changes to Firestore
        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                FirestoreService.initialize();
                var db = FirestoreService.getDb();
                if (db == null) return null;

                Map<String, Object> updates = new HashMap<>();
                updates.put("name", updatedName);
                updates.put("address", updatedAddress);
                updates.put("cuisines", updatedCuisines);
                updates.put("shortDescription", updatedDesc);
                updates.put("coverUrl", updatedCoverUrl);
                updates.put("logoUrl", updatedLogoUrl);
                updates.put("availabilityUpdatedAt", Timestamp.now());

                if (loadedDocRef != null) {
                    loadedDocRef.update(updates).get();
                } else {
                    List<QueryDocumentSnapshot> docs = db.collection(COLLECTION_NAME)
                            .whereEqualTo("name", previousLookupName)
                            .get().get().getDocuments();

                    if (!docs.isEmpty()) {
                        loadedDocRef = docs.get(0).getReference();
                        loadedDocRef.update(updates).get();
                    } else {
                        List<QueryDocumentSnapshot> allDocs = db.collection(COLLECTION_NAME)
                                .get().get().getDocuments();
                        QueryDocumentSnapshot matched = findCurrentRestaurantDoc(allDocs);
                        if (matched != null) {
                            loadedDocRef = matched.getReference();
                            loadedDocRef.update(updates).get();
                        } else {
                            loadedDocRef = db.collection(COLLECTION_NAME).add(updates).get();
                        }
                    }
                }
                return null;
            }
        };

        saveTask.setOnSucceeded(e -> Platform.runLater(() -> {
            loadedLookupName = updatedName;
            loadedName = updatedName;
            loadedAddress = updatedAddress;
            loadedCuisineText = updatedCuisineText;
            loadedDescription = updatedDesc;
            loadedCoverUrl = updatedCoverUrl;
            loadedLogoUrl = updatedLogoUrl;

            if (cuisineField != null) {
                cuisineField.setText(updatedCuisineText);
            }
            if (coverBadgeText != null) {
                coverBadgeText.setText(isValidImageUrl(updatedCoverUrl) ? "Current cover" : "Default cover");
            }
            updateInitialsAndSidebar(updatedName);

            saveBtn.setText("✓  Saved");
            if (saveStatusSubLabel != null) {
                saveStatusSubLabel.setText("Your profile has been updated live.");
            }

            PauseTransition pause = new PauseTransition(Duration.seconds(1.8));
            pause.setOnFinished(ev -> {
                saveBtn.setDisable(false);
                saveBtn.setText("✓  Save changes");
            });
            pause.play();
        }));

        saveTask.setOnFailed(e -> Platform.runLater(() -> {
            saveBtn.setDisable(false);
            saveBtn.setText("✓  Save changes");
            if (saveStatusSubLabel != null) {
                saveStatusSubLabel.setText("Could not save to Firestore — please retry.");
            }
            System.err.println("Failed to update restaurant profile: " + saveTask.getException().getMessage());
        }));

        Thread thread = new Thread(saveTask);
        thread.setDaemon(true);
        thread.start();
    }

    /* ==================== SAFE FIRESTORE HELPERS ==================== */

    private String safeString(QueryDocumentSnapshot doc, String field) {
        try {
            Object val = doc.get(field);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long safeLong(QueryDocumentSnapshot doc, String field) {
        try {
            Object val = doc.get(field);
            if (val instanceof Number num) return num.longValue();
            if (val instanceof String s && !s.isBlank()) return Long.parseLong(s.trim());
        } catch (Exception ignored) {
        }
        return null;
    }

    private Instant safeInstant(QueryDocumentSnapshot doc, String field) {
        try {
            Object val = doc.get(field);
            if (val instanceof Timestamp ts) {
                return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
            }
            if (val instanceof Number num) {
                return Instant.ofEpochMilli(num.longValue());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<String> extractCuisines(QueryDocumentSnapshot doc) {
        List<String> result = new ArrayList<>();
        try {
            Object raw = doc.get("cuisines");
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && !String.valueOf(item).isBlank()) {
                        result.add(String.valueOf(item).trim());
                    }
                }
            } else if (raw instanceof String s && !s.isBlank()) {
                result.addAll(parseCuisineList(s));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private List<String> parseCuisineList(String rawText) {
        List<String> list = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) return list;
        for (String part : rawText.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private String formatAreaAndCity(String areaId, String cityId, String address) {
        String city = (cityId != null && !cityId.isBlank()) ? capitalizeWords(cityId.replace('-', ' ')) : "";
        String area = "";
        if (areaId != null && !areaId.isBlank()) {
            String cleaned = areaId.toLowerCase();
            if (cityId != null && !cityId.isBlank() && cleaned.startsWith(cityId.toLowerCase() + "-")) {
                cleaned = cleaned.substring(cityId.length() + 1);
            }
            area = capitalizeWords(cleaned.replace('-', ' '));
        }
        if (!area.isEmpty() && !city.isEmpty()) return area + ", " + city;
        if (!area.isEmpty()) return area;
        if (!city.isEmpty()) return city;
        return extractShortAreaFromAddress(address);
    }

    private String extractShortAreaFromAddress(String address) {
        if (address == null || address.isBlank()) return "";
        String[] parts = address.split(",");
        if (parts.length >= 2) {
            return parts[parts.length - 2].trim();
        }
        return parts[0].trim();
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isBlank()) return "";
        String[] words = input.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    /* ==================== SIDEBAR NAVIGATION HANDLERS ==================== */

    @FXML
    private void handleNavDashboard() {
        navigateTo("/fxml/MainDashboard.fxml", "Owner Dashboard - Asanni");
    }

    @FXML
    private void handleNavTables() {
        navigateTo("/fxml/TableControls.fxml", "Table Controls - Asanni");
    }

    @FXML
    private void handleNavPreview() {
        var resource = getClass().getResource("/fxml/CustomerView.fxml");
        if (resource != null) {
            navigateTo("/fxml/CustomerView.fxml", "Customer Preview - Asanni");
        } else {
            navigateTo("/fxml/customerView.fxml", "Customer Preview - Asanni");
        }
    }

    @FXML
    private void handleNavSettings() {
        navigateTo("/fxml/WaitingControl.fxml", "Waiting Control - Asanni");
    }

    @FXML
    private void handleHelp() {
        navigateTo("/fxml/MainDashboard.fxml", "Owner Dashboard - Asanni");
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