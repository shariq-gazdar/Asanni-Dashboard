package com.asanni.dashboard.controllers;

import com.asanni.dashboard.firebase.FirestoreService;
import com.asanni.dashboard.models.OnboardingData;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.GeoPoint;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomerViewController {

    private static final String OWNER_COLLECTION = "restaurantOwner";

    // Full-screen GIF Loading Overlay
    @FXML private StackPane loadingOverlay;

    // Top Navbar
    @FXML private Label logoBadge;
    @FXML private Label navBrandLabel;
    @FXML private Label avatarLabel;

    // Header Card
    @FXML private Label restaurantTitleLabel;
    @FXML private Label restaurantMetaLabel;

    // Cover Banner
    @FXML private StackPane coverContainer;
    @FXML private ImageView coverImageView;
    @FXML private HBox featuredOverlayBadge;

    // Wait Status Banner
    @FXML private VBox waitBannerCard;
    @FXML private Label waitPillLabel;
    @FXML private Label updatedAtLabel;
    @FXML private Label waitHeadlineLabel;
    @FXML private Label waitSubtextLabel;

    // Info & Directions
    @FXML private Label distanceTitleLabel;
    @FXML private Label distanceSubLabel;
    @FXML private Label openStatusTitleLabel;
    @FXML private Label openStatusSubLabel;
    @FXML private Label addressLabel;
    @FXML private FlowPane tagsFlowPane;

    private final OnboardingData onboardingData = OnboardingData.getInstance();
    private ListenerRegistration ownerListener;
    private String activeRestaurantName;

    // Default Karachi city-center reference coordinates for distance calculation
    private static final double REF_LAT = 24.8607;
    private static final double REF_LNG = 67.0011;

    @FXML
    public void initialize() {
        // 1. Show full-screen GIF loading screen
        showLoading();

        // 2. Setup rounded corners & responsive width on cover photo
        setupCoverImageClipping();

        // 3. Connect to Firestore "restaurantOwner" collection and populate live details
        startFirestoreListener();
    }

    /** Optional: allows another controller to explicitly specify which restaurant to preview. */
    public void setRestaurantName(String restaurantName) {
        if (restaurantName != null && !restaurantName.isBlank()) {
            this.activeRestaurantName = restaurantName.trim();
            onboardingData.setRestaurantName(this.activeRestaurantName);
            startFirestoreListener();
        }
    }

    public void dispose() {
        if (ownerListener != null) {
            ownerListener.remove();
            ownerListener = null;
        }
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

    private void setupCoverImageClipping() {
        if (coverContainer != null && coverImageView != null) {
            coverImageView.fitWidthProperty().bind(coverContainer.widthProperty());
            Rectangle clip = new Rectangle();
            clip.setArcWidth(36.0);
            clip.setArcHeight(36.0);
            clip.widthProperty().bind(coverContainer.widthProperty());
            clip.heightProperty().bind(coverContainer.heightProperty());
            coverContainer.setClip(clip);
        }
    }

    private void startFirestoreListener() {
        dispose();

        Thread initThread = new Thread(() -> {
            try {
                FirestoreService.initialize();
                Firestore db = FirestoreService.getDb();

                if (db == null) {
                    System.err.println("Firestore DB instance is null.");
                    Platform.runLater(this::hideLoading);
                    return;
                }

                ownerListener = db.collection(OWNER_COLLECTION)
                        .addSnapshotListener((QuerySnapshot snap, FirestoreException err) -> {
                            if (err != null) {
                                System.err.println("Error listening to restaurantOwner: " + err.getMessage());
                                Platform.runLater(this::hideLoading);
                                return;
                            }

                            if (snap == null || snap.isEmpty()) {
                                System.err.println("No documents found in 'restaurantOwner' collection.");
                                Platform.runLater(this::hideLoading);
                                return;
                            }

                            QueryDocumentSnapshot matchedDoc = findCurrentRestaurantDoc(snap.getDocuments());
                            if (matchedDoc != null) {
                                Platform.runLater(() -> {
                                    try {
                                        populateUI(matchedDoc);
                                    } catch (Exception ex) {
                                        System.err.println("Error populating CustomerView UI: " + ex.getMessage());
                                        ex.printStackTrace();
                                    } finally {
                                        hideLoading();
                                    }
                                });
                            } else {
                                Platform.runLater(this::hideLoading);
                            }
                        });

            } catch (Exception e) {
                System.err.println("Failed to initialize Firestore in CustomerViewController: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(this::hideLoading);
            }
        });
        initThread.setDaemon(true);
        initThread.start();
    }

    /**
     * Finds the current restaurant document from the "restaurantOwner" collection:
     * 1. Matches explicit activeRestaurantName or OnboardingData.getRestaurantName() (case-insensitive)
     * 2. Matches OnboardingData.getPhone() if available
     * 3. Matches OnboardingData.getAddress() if available
     * 4. Otherwise selects the most recently updated document in "restaurantOwner"
     */
    private QueryDocumentSnapshot findCurrentRestaurantDoc(List<QueryDocumentSnapshot> docs) {
        if (docs == null || docs.isEmpty()) return null;

        String targetName = (activeRestaurantName != null && !activeRestaurantName.isBlank())
                ? activeRestaurantName.trim()
                : onboardingData.getRestaurantName();

        // 1. Match by restaurant name (ignore placeholder "Asanni" unless a doc is actually named Asanni)
        if (targetName != null && !targetName.isBlank()) {
            for (QueryDocumentSnapshot d : docs) {
                String docName = safeString(d, "name", "restaurantName");
                if (docName.equalsIgnoreCase(targetName.trim())) {
                    return d;
                }
            }
        }

        // 2. Match by phone number from OnboardingData
        String targetPhone = onboardingData.getPhone();
        if (targetPhone != null && !targetPhone.isBlank()) {
            String cleanTargetPhone = targetPhone.replaceAll("[^0-9]", "");
            if (!cleanTargetPhone.isEmpty()) {
                for (QueryDocumentSnapshot d : docs) {
                    String docPhone = safeString(d, "phone").replaceAll("[^0-9]", "");
                    if (!docPhone.isEmpty() && docPhone.equals(cleanTargetPhone)) {
                        return d;
                    }
                }
            }
        }

        // 3. Match by address from OnboardingData
        String targetAddress = onboardingData.getAddress();
        if (targetAddress != null && !targetAddress.isBlank()) {
            for (QueryDocumentSnapshot d : docs) {
                String docAddress = safeString(d, "address");
                if (docAddress.equalsIgnoreCase(targetAddress.trim())) {
                    return d;
                }
            }
        }

        // 4. Fallback: Pick the document with the latest availabilityUpdatedAt timestamp
        QueryDocumentSnapshot bestDoc = docs.get(0);
        Instant latestInstant = Instant.MIN;
        for (QueryDocumentSnapshot d : docs) {
            Instant ts = safeInstant(d, "availabilityUpdatedAt");
            if (ts != null && ts.isAfter(latestInstant)) {
                latestInstant = ts;
                bestDoc = d;
            }
        }
        return bestDoc;
    }

    private void populateUI(DocumentSnapshot doc) {
        // --- Safely extract all schema fields from restaurantOwner ---
        String name = safeString(doc, "name", "restaurantName");
        String address = safeString(doc, "address");
        String areaId = safeString(doc, "areaId", "area");
        String cityId = safeString(doc, "cityId", "city");
        String coverUrl = safeString(doc, "coverUrl");
        String logoUrl = safeString(doc, "logoUrl");
        String phone = safeString(doc, "phone");
        String selectedPlan = safeString(doc, "selectedPlan");

        Boolean acceptingDiners = safeBoolean(doc, "acceptingDiners", true);
        Boolean isEnabled = safeBoolean(doc, "isEnabled", true);
        Boolean isOpen = safeBoolean(doc, "isOpen", true);
        Boolean perHead = safeBoolean(doc, "perHead", false);

        Long price = safeLong(doc, "price", "avgSpendPerHead", "perHeadPrice");
        Long availableTables = safeLong(doc, "availableTables");
        Long totalTables = safeLong(doc, "totalTables", "numberOfTables");

        // waitMinutes: null if the field does not exist in the document
        Long waitMinutes = safeLong(doc, "waitMinutes");

        Instant updatedAt = safeInstant(doc, "availabilityUpdatedAt");
        double[] coords = safeCoordinates(doc);
        List<String> cuisines = safeStringList(doc, "cuisines", "primaryCuisine");

        // 1. Restaurant Name & Initials (also sync to OnboardingData for MapView)
        String displayName = !name.isBlank() ? name : "Restaurant";
        this.activeRestaurantName = displayName;
        onboardingData.setRestaurantName(displayName);
        if (!cityId.isBlank()) onboardingData.setCityId(cityId);
        if (!areaId.isBlank()) onboardingData.setAreaId(areaId);
        if (!address.isBlank()) onboardingData.setAddress(address);
        if (!phone.isBlank()) onboardingData.setPhone(phone);

        navBrandLabel.setText(displayName);
        restaurantTitleLabel.setText(displayName);
        setInitials(displayName);

        // 2. Meta Subtitle: Cuisines · Area · Price
        String cuisineText = !cuisines.isEmpty()
                ? String.join(", ", cuisines)
                : "Restaurant";

        String areaText = formatAreaAndCity(areaId, cityId);

        long priceVal = (price != null && price > 0) ? price : 0L;
        String priceSuffix = Boolean.TRUE.equals(perHead) ? " pp" : "";
        String priceText = priceVal > 0
                ? "PKR " + NumberFormat.getNumberInstance(Locale.US).format(priceVal) + priceSuffix
                : "";

        StringBuilder metaBuilder = new StringBuilder(cuisineText);
        if (!areaText.isBlank()) metaBuilder.append(" · ").append(areaText);
        if (!priceText.isBlank()) metaBuilder.append(" · ").append(priceText);
        restaurantMetaLabel.setText(metaBuilder.toString());

        // 3. Cover Photo Preview (falls back to logoUrl if coverUrl is just "https://")
        String imageToLoad = isValidImageUrl(coverUrl) ? coverUrl : (isValidImageUrl(logoUrl) ? logoUrl : null);
        if (imageToLoad != null) {
            try {
                Image coverImg = new Image(imageToLoad.trim(), true);
                coverImageView.setImage(coverImg);
            } catch (Exception ignored) {
            }
        }

        // Featured Badge visibility
        if (featuredOverlayBadge != null) {
            boolean isFeatured = !selectedPlan.isBlank() || Boolean.TRUE.equals(isEnabled);
            featuredOverlayBadge.setVisible(isFeatured);
            featuredOverlayBadge.setManaged(isFeatured);
        }

        // 4. Wait Time & Live Status Banner
        updateWaitBanner(isOpen, isEnabled, acceptingDiners, availableTables, totalTables, waitMinutes, updatedAt);

        // 5. Distance / Location Card
        updateLocationCard(coords, areaText, phone);

        // 6. Open Status & Tables Card
        updateOpenStatusCard(isOpen, isEnabled, acceptingDiners, availableTables, totalTables);

        // 7. Address Row
        addressLabel.setText(!address.isBlank() ? address : areaText);

        // 8. Dynamic Tags / Chips Row
        updateTagsFlowPane(cuisines, availableTables, totalTables, waitMinutes, perHead, phone, selectedPlan, isEnabled);
    }

    private void updateWaitBanner(Boolean isOpen,
                                  Boolean isEnabled,
                                  Boolean acceptingDiners,
                                  Long availableTables,
                                  Long totalTables,
                                  Long waitMinutes,
                                  Instant updatedAt) {

        if (updatedAtLabel != null) {
            updatedAtLabel.setText(formatInstant(updatedAt));
        }

        long avail = (availableTables != null) ? availableTables : 0L;
        long total = (totalTables != null) ? totalTables : 0L;
        boolean open = !Boolean.FALSE.equals(isOpen) && !Boolean.FALSE.equals(isEnabled);
        boolean accepting = !Boolean.FALSE.equals(acceptingDiners);

        // 1. Closed
        if (!open) {
            applyBannerTheme("red");
            waitPillLabel.setText("●  CLOSED");
            waitHeadlineLabel.setText("Currently closed");
            waitSubtextLabel.setText("This restaurant is currently closed for walk-in diners.");
            return;
        }

        // 2. Walk-ins paused
        if (!accepting) {
            applyBannerTheme("red");
            waitPillLabel.setText("●  WALK-INS PAUSED");
            waitHeadlineLabel.setText("Not accepting diners");
            waitSubtextLabel.setText("Walk-in seating is temporarily paused by the restaurant.");
            return;
        }

        // 3. No waitMinutes in document (waitMinutes == null) OR waitMinutes <= 0 -> Show "No wait time"
        if (waitMinutes == null || waitMinutes <= 0) {
            applyBannerTheme("green");
            waitPillLabel.setText("●  AVAILABLE NOW · NO WAIT");

            if (avail > 0) {
                waitHeadlineLabel.setText("No wait time · " + avail + (avail == 1 ? " table" : " tables") + " free");
                String tableRatio = total > 0 ? " (" + avail + " of " + total + " tables available)." : ".";
                waitSubtextLabel.setText("Customers can walk in immediately with no waiting time" + tableRatio);
            } else {
                waitHeadlineLabel.setText("No wait time");
                waitSubtextLabel.setText("Customers can walk in immediately with no waiting time.");
            }
            return;
        }

        // 4. Positive waitMinutes > 0 -> Show exact waitMinutes from Firestore
        applyBannerTheme(waitMinutes >= 45 ? "red" : "amber");
        waitPillLabel.setText(waitMinutes >= 45 ? "●  LONG WAIT" : "●  SHORT WAIT");
        waitHeadlineLabel.setText(waitMinutes + " min wait");
        String tableInfo = total > 0 ? " (" + avail + " of " + total + " tables free)." : ".";
        waitSubtextLabel.setText("Walk-ins are being served in order of arrival" + tableInfo);
    }

    private void applyBannerTheme(String theme) {
        if (waitBannerCard == null) return;

        waitBannerCard.getStyleClass().removeAll("wait-banner-amber", "wait-banner-green", "wait-banner-red");

        List<Label> labels = Arrays.asList(waitPillLabel, updatedAtLabel, waitHeadlineLabel, waitSubtextLabel);
        for (Label lbl : labels) {
            if (lbl != null) {
                lbl.getStyleClass().removeAll("wait-text-green", "wait-text-red");
            }
        }

        switch (theme) {
            case "green" -> {
                waitBannerCard.getStyleClass().add("wait-banner-green");
                for (Label lbl : labels) {
                    if (lbl != null) lbl.getStyleClass().add("wait-text-green");
                }
            }
            case "red" -> {
                waitBannerCard.getStyleClass().add("wait-banner-red");
                for (Label lbl : labels) {
                    if (lbl != null) lbl.getStyleClass().add("wait-text-red");
                }
            }
            default -> waitBannerCard.getStyleClass().add("wait-banner-amber");
        }
    }

    private void updateLocationCard(double[] coords, String areaText, String phone) {
        if (coords != null && coords.length == 2) {
            double lat = coords[0];
            double lng = coords[1];
            double distKm = calculateDistanceKm(REF_LAT, REF_LNG, lat, lng);
            long driveMins = Math.max(2, Math.round(distKm * 2.8));

            distanceTitleLabel.setText(String.format(Locale.US, "%.1f km", distKm));
            distanceSubLabel.setText(String.format(Locale.US, "~%d min drive (%.2f° N, %.2f° E)", driveMins, lat, lng));
        } else {
            distanceTitleLabel.setText(!areaText.isBlank() ? areaText : "Karachi");
            distanceSubLabel.setText(!phone.isBlank() ? "Tel: " + phone : "Location pinned");
        }
    }

    private void updateOpenStatusCard(Boolean isOpen, Boolean isEnabled, Boolean acceptingDiners, Long availableTables, Long totalTables) {
        boolean open = !Boolean.FALSE.equals(isOpen) && !Boolean.FALSE.equals(isEnabled);
        long avail = availableTables != null ? availableTables : 0L;
        long total = totalTables != null ? totalTables : 0L;

        if (open) {
            openStatusTitleLabel.setText("Open now");
            if (!Boolean.FALSE.equals(acceptingDiners)) {
                openStatusSubLabel.setText(total > 0 ? (avail + " of " + total + " tables available") : "Accepting walk-ins");
            } else {
                openStatusSubLabel.setText("Walk-ins paused");
            }
        } else {
            openStatusTitleLabel.setText("Closed");
            openStatusSubLabel.setText("Not taking diners right now");
        }
    }

    private void updateTagsFlowPane(List<String> cuisines,
                                    Long availableTables,
                                    Long totalTables,
                                    Long waitMinutes,
                                    Boolean perHead,
                                    String phone,
                                    String selectedPlan,
                                    Boolean isEnabled) {
        if (tagsFlowPane == null) return;
        tagsFlowPane.getChildren().clear();

        if (cuisines != null) {
            for (String cuisine : cuisines) {
                if (cuisine != null && !cuisine.isBlank()) {
                    tagsFlowPane.getChildren().add(createChip(cuisine, "amenity-chip"));
                }
            }
        }

        if (availableTables != null && totalTables != null && totalTables > 0) {
            tagsFlowPane.getChildren().add(createChip(availableTables + "/" + totalTables + " Tables Free", "amenity-chip"));
        }

        if (waitMinutes == null || waitMinutes <= 0) {
            tagsFlowPane.getChildren().add(createChip("No Wait Time", "amenity-chip"));
        } else {
            tagsFlowPane.getChildren().add(createChip("~" + waitMinutes + " Min Wait", "amenity-chip"));
        }

        if (perHead != null) {
            tagsFlowPane.getChildren().add(createChip(perHead ? "Per Head" : "À La Carte", "amenity-chip"));
        }

        if (phone != null && !phone.isBlank()) {
            tagsFlowPane.getChildren().add(createChip("📞 " + phone, "amenity-chip"));
        }

        if (selectedPlan != null && !selectedPlan.isBlank()) {
            tagsFlowPane.getChildren().add(createChip(selectedPlan.toUpperCase(), "featured-chip-dark"));
        } else if (Boolean.TRUE.equals(isEnabled)) {
            tagsFlowPane.getChildren().add(createChip("Featured", "featured-chip-dark"));
        }
    }

    private Label createChip(String text, String styleClass) {
        Label chip = new Label(text);
        chip.getStyleClass().add(styleClass);
        return chip;
    }

    /* ==================== SAFE FIRESTORE FIELD EXTRACTORS ==================== */

    private static String safeString(DocumentSnapshot d, String... keys) {
        for (String key : keys) {
            Object val = d.get(key);
            if (val != null) {
                String s = val.toString().trim();
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private static Long safeLong(DocumentSnapshot d, String... keys) {
        for (String key : keys) {
            if (!d.contains(key)) continue;
            Object val = d.get(key);
            if (val instanceof Number n) {
                return n.longValue();
            } else if (val instanceof String s && !s.isBlank()) {
                try {
                    return Long.parseLong(s.trim().replaceAll("[^0-9-]", ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private static Boolean safeBoolean(DocumentSnapshot d, String key, boolean defaultVal) {
        Object val = d.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s.trim());
        return defaultVal;
    }

    private static List<String> safeStringList(DocumentSnapshot d, String... keys) {
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            Object raw = d.get(key);
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && !item.toString().isBlank()) {
                        result.add(item.toString().trim());
                    }
                }
                if (!result.isEmpty()) return result;
            } else if (raw instanceof String s && !s.isBlank()) {
                result.add(s.trim());
                return result;
            }
        }
        return result;
    }

    private static Instant safeInstant(DocumentSnapshot d, String key) {
        Object raw = d.get(key);
        if (raw instanceof Timestamp ts) {
            return ts.toDate().toInstant();
        } else if (raw instanceof java.util.Date date) {
            return date.toInstant();
        } else if (raw instanceof Number n) {
            long val = n.longValue();
            return val < 10_000_000_000L ? Instant.ofEpochSecond(val) : Instant.ofEpochMilli(val);
        }
        return null;
    }

    private static double[] safeCoordinates(DocumentSnapshot d) {
        Object rawLoc = d.get("location");
        if (rawLoc instanceof GeoPoint gp) {
            return new double[]{gp.getLatitude(), gp.getLongitude()};
        } else if (rawLoc instanceof List<?> list && list.size() >= 2) {
            Double lat = toDouble(list.get(0));
            Double lng = toDouble(list.get(1));
            if (lat != null && lng != null) return new double[]{lat, lng};
        } else if (rawLoc instanceof Map<?, ?> map) {
            Double lat = toDouble(map.get("latitude") != null ? map.get("latitude") : map.get("lat"));
            Double lng = toDouble(map.get("longitude") != null ? map.get("longitude") : map.get("lng"));
            if (lat != null && lng != null) return new double[]{lat, lng};
        }
        Double lat = toDouble(d.get("lat") != null ? d.get("lat") : d.get("latitude"));
        Double lng = toDouble(d.get("lng") != null ? d.get("lng") : d.get("longitude"));
        if (lat != null && lng != null) return new double[]{lat, lng};
        return null;
    }

    private static Double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.replaceAll("[^0-9.-]", ""));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /* ==================== FORMATTING HELPERS ==================== */

    private String formatInstant(Instant updatedInstant) {
        if (updatedInstant == null) {
            return "Updated recently";
        }
        Instant now = Instant.now();
        long diffMinutes = Duration.between(updatedInstant, now).toMinutes();

        if (diffMinutes >= 0 && diffMinutes < 1) {
            return "Updated just now";
        } else if (diffMinutes >= 1 && diffMinutes < 60) {
            return "Updated " + diffMinutes + " min ago";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US)
                    .withZone(ZoneId.systemDefault());
            return "Updated " + formatter.format(updatedInstant);
        }
    }

    private String formatAreaAndCity(String areaId, String cityId) {
        String cleanCity = (cityId != null && !cityId.isBlank()) ? capitalizeWords(cityId) : "";
        if (areaId != null && !areaId.isBlank()) {
            String rawArea = areaId.toLowerCase(Locale.ROOT);
            if (cityId != null && !cityId.isBlank() && rawArea.startsWith(cityId.toLowerCase(Locale.ROOT) + "-")) {
                rawArea = rawArea.substring(cityId.length() + 1);
            }
            String cleanArea = capitalizeWords(rawArea.replace("-", " ").replace("_", " "));
            return cleanCity.isEmpty() ? cleanArea : cleanArea + ", " + cleanCity;
        }
        return cleanCity.isEmpty() ? "Karachi" : cleanCity;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isBlank()) return "";
        return Arrays.stream(input.trim().split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private boolean isValidImageUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim();
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) && trimmed.length() > 12;
    }

    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void setInitials(String name) {
        String[] words = name.trim().split("\\s+");
        String initials = "AS";
        if (words.length >= 2) {
            initials = (words[0].charAt(0) + "" + words[words.length - 1].charAt(0)).toUpperCase(Locale.ROOT);
        } else if (words.length == 1 && words[0].length() >= 2) {
            initials = (words[0].charAt(0) + "" + words[0].charAt(1)).toUpperCase(Locale.ROOT);
        }
        if (logoBadge != null) logoBadge.setText(initials);
        if (avatarLabel != null) avatarLabel.setText(initials);
    }

    /* ==================== ACTION HANDLERS ==================== */

    @FXML
    private void handleExitPreview() {
        dispose();
        navigateTo("/fxml/MainDashboard.fxml", "Owner Dashboard - Asanni");
    }

    @FXML
    private void handleHelp() {
        dispose();
        navigateTo("/fxml/MainDashboard.fxml", "Owner Dashboard - Asanni");
    }

    @FXML
    private void handleGetDirections() {
        dispose();
        try {
            var resource = getClass().getResource("/fxml/MapView.fxml");
            if (resource == null) {
                System.err.println("FXML file not found: /fxml/MapView.fxml");
                return;
            }
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();

            MapViewController mapController = loader.getController();
            if (mapController != null && activeRestaurantName != null && !activeRestaurantName.isBlank()) {
                mapController.setRestaurantFilter(activeRestaurantName);
            }

            Stage stage = (Stage) navBrandLabel.getScene().getWindow();
            stage.setScene(new Scene(root, 1220, 840));
            stage.setTitle("Directions · " + (activeRestaurantName != null ? activeRestaurantName : "Asanni"));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
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