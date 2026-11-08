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

import io.github.cdimascio.dotenv.Dotenv;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Controller for MapView.fxml.
 * Displays the logged-in restaurant from the "restaurantOwner" collection on a CARTO/Leaflet map.
 */
public class MapViewController {

    private static final String OWNER_COLLECTION = "restaurantOwner";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AsanniDesktop/1.0";
    private static final double DEFAULT_LAT = 24.8607; // Karachi default centre
    private static final double DEFAULT_LNG = 67.0011;

    // ====== FXML ======
    @FXML private Label cityTitleLabel, discoverLabel, dataStatusLabel, avatarLabel;
    @FXML private Label countLabel, emptyLabel;
    @FXML private Label detailCityLabel, detailAddressLabel, detailPhoneLabel;
    @FXML private Label bannerIcon, bannerTitle, bannerText;
    @FXML private VBox placesBox;
    @FXML private HBox locationBanner;
    @FXML private Button showOnMapButton, clearSearchButton, backButton, helpButton;
    @FXML private TextField searchField;
    @FXML private MenuButton filtersButton;
    @FXML private CheckMenuItem openNowItem, freeTablesItem;
    @FXML private ToggleGroup mapModeGroup;
    @FXML private ToggleButton mapModeButton, satelliteModeButton;
    @FXML private StackPane mapPanel;
    @FXML private WebView mapView;

    // ====== State ======
    private final Map<String, Restaurant> ownerDocs = new LinkedHashMap<>();
    private final List<Restaurant> all = new ArrayList<>();
    private final List<Restaurant> visible = new ArrayList<>();
    private Restaurant selected;
    private String cityId = "karachi";
    private String cityName = "Karachi";
    private double[] userLocation; // {lat, lng}, optional
    private Runnable onBack;
    private String restaurantFilter;

    private WebEngine engine;
    private boolean mapReady;
    private final MapBridge bridge = new MapBridge();
    private ListenerRegistration ownerListener;
    private Timeline ticker;

    // ================= Public API =================

    /** Call if you ever want to browse an entire city instead of a single restaurant. */
    public void setCity(String cityId, String cityName) {
        this.restaurantFilter = null;
        this.cityId = cityId == null ? "karachi" : cityId.trim().toLowerCase(Locale.ROOT);
        this.cityName = (cityName == null || cityName.isBlank()) ? "Karachi" : cityName.trim();
        if (cityTitleLabel != null) {
            cityTitleLabel.setText("Explore " + this.cityName);
            discoverLabel.setText("DISCOVER · " + this.cityName.toUpperCase(Locale.ROOT));
            reload();
        }
    }

    /** Focuses the map exclusively on the specified restaurant name. */
    public void setRestaurantFilter(String name) {
        this.restaurantFilter = (name == null || name.isBlank()) ? null : name.trim();

        if (restaurantFilter != null) {
            if (cityTitleLabel != null) cityTitleLabel.setText("Directions · " + restaurantFilter);
            if (discoverLabel != null) discoverLabel.setText("NAVIGATING · " + restaurantFilter.toUpperCase(Locale.ROOT));
            setInitialsFromName(restaurantFilter);
        }

        if (ownerListener != null) {
            reload();
        }
    }

    public void setUserInitials(String initials) {
        if (avatarLabel != null && initials != null) {
            avatarLabel.setText(initials);
        }
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    public void setUserLocation(double lat, double lng) {
        userLocation = new double[]{lat, lng};
        if (mapReady) js("setUser(" + lat + "," + lng + ")");
        applyFilters(false);
    }

    /** Stop the realtime listener when the view is closed. */
    public void dispose() {
        if (ownerListener != null) {
            ownerListener.remove();
            ownerListener = null;
        }
        if (ticker != null) ticker.stop();
    }

    // ================= Lifecycle =================

    @FXML
    private void initialize() {
        // 1. Pull logged-in restaurant info from OnboardingData session state
        OnboardingData session = OnboardingData.getInstance();
        if (session.getCity() != null && !session.getCity().isBlank()) {
            this.cityName = session.getCity().trim();
        }
        if (session.getCityId() != null && !session.getCityId().isBlank()) {
            this.cityId = session.getCityId().trim().toLowerCase(Locale.ROOT);
        }
        if (this.restaurantFilter == null && session.getRestaurantName() != null && !session.getRestaurantName().isBlank()) {
            this.restaurantFilter = session.getRestaurantName().trim();
        }

        if (this.restaurantFilter != null) {
            cityTitleLabel.setText("Directions · " + this.restaurantFilter);
            discoverLabel.setText("NAVIGATING · " + this.restaurantFilter.toUpperCase(Locale.ROOT));
            setInitialsFromName(this.restaurantFilter);
        } else {
            cityTitleLabel.setText("Explore " + cityName);
            discoverLabel.setText("DISCOVER · " + cityName.toUpperCase(Locale.ROOT));
        }

        // 2. Rounded corners for the WebView container
        Rectangle clip = new Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(mapPanel.widthProperty());
        clip.heightProperty().bind(mapPanel.heightProperty());
        mapPanel.setClip(clip);

        // 3. Map / Satellite toggle
        mapModeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) {
                mapModeGroup.selectToggle(oldT);
                return;
            }
            if (mapReady) js("setSatellite(" + (newT == satelliteModeButton) + ")");
        });

        // 4. Search input listener
        searchField.textProperty().addListener((obs, o, n) -> {
            boolean has = n != null && !n.isBlank();
            clearSearchButton.setVisible(has);
            clearSearchButton.setManaged(has);
            applyFilters(true);
        });

        showOnMapButton.setDisable(true);

        // 5. Periodic card refresh ticker ("Updated X min ago")
        ticker = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshCards()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();

        // 6. Initialize Leaflet with CARTO API key and load Firestore data
        setupMap();
        reload();
    }

    // ================= CARTO API Key (.env Loader) =================

    /**
     * Reads CARTO_API_KEY from system environment variables, JVM properties, or the local .env file.
     */
 private static String loadCartoApiKey() {
    // 1. Check System env first
    String key = System.getenv("CARTO_API_KEY");
    if (key != null && !key.isBlank()) return key.trim();

    // 2. Check JVM system property
    key = System.getProperty("CARTO_API_KEY");
    if (key != null && !key.isBlank()) return key.trim();

    // 3. Use the same Dotenv library your FirestoreService already uses
    try {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        key = dotenv.get("CARTO_API_KEY");
        if (key != null && !key.isBlank()) return key.trim();
    } catch (Exception e) {
        System.err.println("Dotenv failed to load CARTO_API_KEY: " + e.getMessage());
    }

    return "";
}
    private static String buildCartoTileUrl() {
        String baseUrl = "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png";
        String apiKey = loadCartoApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            return baseUrl + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        }
        return baseUrl;
    }

    // ================= Map (Leaflet in WebView) =================

    private void setupMap() {
        String cartoKey = loadCartoApiKey();
        System.out.println("CARTO API Key loaded: " + 
            (cartoKey.isBlank() ? "❌ EMPTY" : "✅ " + cartoKey.substring(0, Math.min(8, cartoKey.length())) + "..."));

        engine = mapView.getEngine();
        engine.setUserAgent(USER_AGENT);
        engine.setJavaScriptEnabled(true);

        engine.getLoadWorker().stateProperty().addListener((obs, o, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                try {
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("bridge", bridge);
                } catch (Exception ex) {
                    System.err.println("Could not bind JS bridge: " + ex.getMessage());
                }

                Object leafletOk = engine.executeScript("typeof L !== 'undefined'");
                mapReady = Boolean.TRUE.equals(leafletOk);
                if (!mapReady) {
                    setBanner(false, "Map could not load",
                            "Check your internet connection — map tiles are fetched online.");
                    return;
                }
                if (userLocation != null) {
                    js("setUser(" + userLocation[0] + "," + userLocation[1] + ")");
                }
                pushToMap();
                pushSelection(true);
            }
        });

        String html = MAP_HTML
                .replace("%LAT%", String.valueOf(DEFAULT_LAT))
                .replace("%LNG%", String.valueOf(DEFAULT_LNG))
                .replace("%CARTO_TILE_URL%", buildCartoTileUrl());

        engine.loadContent(html);
    }

    /** Called from JavaScript when a pin is clicked. Must be public. */
    public class MapBridge {
        public void onMarkerClicked(String id) {
            Platform.runLater(() -> {
                for (Restaurant r : visible) {
                    if (r.id.equals(id)) {
                        selectRestaurant(r, false);
                        return;
                    }
                }
            });
        }
    }

    private void pushToMap() {
        if (!mapReady) return;
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Restaurant r : visible) {
            if (!r.hasLocation()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":").append(q(r.id))
              .append(",\"name\":").append(q(r.name))
              .append(",\"sub\":").append(q(r.subtitle()))
              .append(",\"lat\":").append(r.lat)
              .append(",\"lng\":").append(r.lng)
              .append(",\"free\":").append(r.availableTables)
              .append(",\"total\":").append(r.totalTables)
              .append(",\"status\":").append(q(r.status().name().toLowerCase(Locale.ROOT)))
              .append('}');
        }
        sb.append(']');
        js("setRestaurants(" + sb + ")");
    }

    private void pushSelection(boolean fly) {
        if (!mapReady) return;
        if (selected != null && selected.hasLocation()) {
            js("selectRestaurant(" + q(selected.id) + "," + fly + ")");
        } else {
            js("clearSelection()");
        }
    }

    private void js(String script) {
        try {
            if (engine != null) {
                engine.executeScript(script);
            }
        } catch (Exception ex) {
            System.err.println("Map script failed: " + ex.getMessage());
        }
    }

    // ================= Data (Firestore realtime from "restaurantOwner") =================

    private void reload() {
        dispose();
        if (ticker != null) ticker.play();
        setStatus("Connecting…", null);

        final String filterAtLoad = this.restaurantFilter;

        try {
            FirestoreService.initialize();
            Firestore db = FirestoreService.getDb();
            if (db == null) {
                setStatus("Offline", "error");
                setBanner(false, "Firebase isn't initialised",
                        "Check your .env or serviceAccountKey.json credentials.");
                return;
            }

            ownerDocs.clear();

            ownerListener = db.collection(OWNER_COLLECTION)
                    .addSnapshotListener((QuerySnapshot snap, FirestoreException err) -> {
                        if (err != null) {
                            Platform.runLater(() -> {
                                setStatus("Offline", "error");
                                setBanner(false, "Couldn't load restaurant", err.getMessage());
                            });
                            return;
                        }
                        if (snap == null) return;

                        Platform.runLater(() -> {
                            if (!Objects.equals(filterAtLoad, this.restaurantFilter)) return;

                            ownerDocs.clear();

                            // 1. Try matching the logged-in restaurant filter
                            for (QueryDocumentSnapshot d : snap) {
                                if (matchesFilters(d)) {
                                    Restaurant r = Restaurant.from(d);
                                    if (!r.name.isBlank()) {
                                        ownerDocs.put(r.name.toLowerCase(Locale.ROOT), r);
                                    }
                                }
                            }

                            // 2. Fallback: If restaurantFilter wasn't set or didn't match, take the first owner document
                            // so we only show the logged-in owner's restaurant and not an empty map
                            if (ownerDocs.isEmpty() && !snap.isEmpty()) {
                                QueryDocumentSnapshot firstDoc = snap.iterator().next();
                                Restaurant r = Restaurant.from(firstDoc);
                                if (!r.name.isBlank()) {
                                    this.restaurantFilter = r.name;
                                    OnboardingData.getInstance().setRestaurantName(r.name);
                                    cityTitleLabel.setText("Directions · " + r.name);
                                    discoverLabel.setText("NAVIGATING · " + r.name.toUpperCase(Locale.ROOT));
                                    setInitialsFromName(r.name);
                                    ownerDocs.put(r.name.toLowerCase(Locale.ROOT), r);
                                }
                            }

                            publishOwnerRestaurants();
                        });
                    });

        } catch (Exception ex) {
            setStatus("Offline", "error");
            setBanner(false, "Firebase isn't initialised", ex.getMessage());
        }
    }

    private boolean matchesFilters(DocumentSnapshot d) {
        if (Boolean.FALSE.equals(d.getBoolean("isEnabled"))) return false;

        // Focused mode: match the logged-in restaurant name
        if (restaurantFilter != null && !restaurantFilter.isBlank()) {
            String name = d.getString("name");
            if (name == null) name = d.getString("restaurantName");
            return name != null && name.trim().equalsIgnoreCase(restaurantFilter);
        }

        // City filter fallback
        String docCityId = d.getString("cityId");
        String docCity = d.getString("city");
        if (docCityId != null && !docCityId.isBlank()) {
            return docCityId.trim().equalsIgnoreCase(cityId);
        }
        if (docCity != null && !docCity.isBlank()) {
            return docCity.trim().equalsIgnoreCase(cityName)
                    || docCity.trim().equalsIgnoreCase(cityId);
        }
        return true;
    }

    private void publishOwnerRestaurants() {
        all.clear();
        all.addAll(ownerDocs.values());

        // Update city label from the loaded restaurant document if present
        if (!all.isEmpty()) {
            Restaurant first = all.get(0);
            if (!first.cityId.isBlank()) {
                this.cityId = first.cityId;
                this.cityName = capitalize(first.cityId);
            }
        }

        setStatus("Live data · " + all.size()
                + (all.size() == 1 ? " restaurant" : " restaurants"), "live");
        applyFilters(true);
    }

    // ================= Filtering / sorting =================

    @FXML
    private void onFilterChanged() {
        applyFilters(true);
    }

    @FXML
    private void onClearSearch() {
        searchField.clear();
    }

    private void applyFilters(boolean fitSelection) {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        boolean openOnly = openNowItem.isSelected();
        boolean freeOnly = freeTablesItem.isSelected();

        visible.clear();
        for (Restaurant r : all) {
            if (restaurantFilter != null && !r.name.equalsIgnoreCase(restaurantFilter)) continue;
            if (openOnly && !r.isOpenForDiners()) continue;
            if (freeOnly && r.availableTables <= 0) continue;
            if (!q.isEmpty() && !r.searchText().contains(q)) continue;
            visible.add(r);
        }

        Comparator<Restaurant> cmp = userLocation != null
                ? Comparator.comparingDouble((Restaurant r) -> r.distanceKm(userLocation))
                : Comparator.comparingInt((Restaurant r) -> -r.availableTables).thenComparing(r -> r.name);
        visible.sort(cmp);

        // Keep the same restaurant selected across live updates
        Restaurant keep = null;
        if (selected != null) {
            for (Restaurant r : visible) {
                if (r.id.equals(selected.id)) {
                    keep = r;
                    break;
                }
            }
        }
        boolean selectionChanged = keep == null;
        selected = keep != null ? keep : (visible.isEmpty() ? null : visible.get(0));

        countLabel.setText(visible.size() + (visible.size() == 1 ? " place" : " places"));
        emptyLabel.setVisible(visible.isEmpty());
        emptyLabel.setManaged(visible.isEmpty());
        emptyLabel.setText(all.isEmpty()
                ? "No restaurant found in restaurantOwner."
                : "No restaurants match your filter.");

        refreshCards();
        updateDetail();
        pushToMap();
        pushSelection(selectionChanged || fitSelection);
    }

    // ================= Selection =================

    private void selectRestaurant(Restaurant r, boolean fly) {
        selected = r;
        refreshCards();
        updateDetail();
        pushSelection(fly);
    }

    @FXML
    private void onShowOnMap() {
        if (selected != null && selected.hasLocation()) {
            pushSelection(true);
        }
    }

    // ================= UI building =================

    private void refreshCards() {
        placesBox.getChildren().clear();
        for (Restaurant r : visible) {
            placesBox.getChildren().add(buildCard(r, r == selected));
        }
    }

    private HBox buildCard(Restaurant r, boolean isSelected) {
        Label logo = new Label(r.name.isEmpty() ? "?" : r.name.substring(0, 1).toUpperCase(Locale.ROOT));
        logo.getStyleClass().add("place-logo");

        Label name = new Label(r.name);
        name.getStyleClass().add("place-name");
        name.setWrapText(true);

        Label sub = new Label(r.subtitle());
        sub.getStyleClass().add("place-sub");

        Label meta = new Label(r.metaLine(userLocation));
        meta.getStyleClass().add("place-meta");
        if (r.isStale()) meta.getStyleClass().add("stale");

        VBox text = new VBox(2, name, sub, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label chip = new Label(r.chipText());
        chip.getStyleClass().addAll("chip", r.chipClass());

        HBox card = new HBox(12, logo, text, chip);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("place-card");
        if (isSelected) card.getStyleClass().add("place-card-selected");
        card.setOnMouseClicked(e -> selectRestaurant(r, true));
        return card;
    }

    private void updateDetail() {
        detailCityLabel.setText(cityName + ", Pakistan");
        if (selected == null) {
            detailAddressLabel.setText("Select a restaurant to see its address.");
            detailPhoneLabel.setVisible(false);
            detailPhoneLabel.setManaged(false);
            showOnMapButton.setDisable(true);
            if (all.isEmpty()) {
                setBanner(false, "Waiting for restaurant data",
                        "The map will update as soon as locations load.");
            }
            return;
        }

        detailAddressLabel.setText(selected.address.isBlank()
                ? "Street address not provided."
                : selected.address);

        boolean hasPhone = !selected.phone.isBlank();
        detailPhoneLabel.setText(hasPhone ? "Phone: " + selected.prettyPhone() : "");
        detailPhoneLabel.setVisible(hasPhone);
        detailPhoneLabel.setManaged(hasPhone);

        showOnMapButton.setDisable(!selected.hasLocation());
        if (selected.hasLocation()) {
            setBanner(true, "Location verified · " + selected.name,
                    String.format(Locale.ROOT,
                            "%s (%.4f° N, %.4f° E)",
                            selected.address.isBlank() ? "Coordinates verified" : selected.address,
                            selected.lat, selected.lng));
        } else {
            setBanner(false, "Location not available",
                    "This restaurant has no coordinates saved, so it can't be pinned on the map.");
        }
    }

    private void setBanner(boolean ok, String title, String text) {
        locationBanner.getStyleClass().removeAll("banner-ok", "banner-warn");
        locationBanner.getStyleClass().add(ok ? "banner-ok" : "banner-warn");
        bannerIcon.setText(ok ? "✓" : "!");
        bannerTitle.setText(title);
        bannerText.setText(text == null ? "" : text);
    }

    private void setStatus(String text, String styleClass) {
        dataStatusLabel.setText(text);
        dataStatusLabel.getStyleClass().removeAll("live", "error");
        if (styleClass != null) dataStatusLabel.getStyleClass().add(styleClass);
    }

    private void setInitialsFromName(String name) {
        if (name == null || name.isBlank() || avatarLabel == null) return;
        String[] words = name.trim().split("\\s+");
        String initials = "AS";
        if (words.length >= 2) {
            initials = ("" + words[0].charAt(0) + words[words.length - 1].charAt(0)).toUpperCase(Locale.ROOT);
        } else if (words[0].length() >= 2) {
            initials = words[0].substring(0, 2).toUpperCase(Locale.ROOT);
        }
        avatarLabel.setText(initials);
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "Karachi";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    // ================= Toolbar handlers =================

    @FXML private void onZoomIn()  { if (mapReady) js("map.zoomIn()"); }
    @FXML private void onZoomOut() { if (mapReady) js("map.zoomOut()"); }
    @FXML private void onLocate()  { if (mapReady) js("locate()"); }

    @FXML
    private void onBackClicked() {
        dispose();
        if (onBack != null) {
            onBack.run();
            return;
        }

        try {
            URL resource = getClass().getResource("/fxml/customerView.fxml");
            if (resource == null) {
                resource = getClass().getResource("/fxml/CustomerView.fxml");
            }
            if (resource == null) {
                resource = getClass().getResource("/fxml/MainDashboard.fxml");
            }
            if (resource == null) {
                System.err.println("No back-target FXML could be found.");
                return;
            }

            Parent root = FXMLLoader.load(resource);
            if (backButton.getScene() != null) {
                backButton.getScene().setRoot(root);
            } else if (backButton.getWindow() instanceof Stage stage) {
                stage.setScene(new Scene(root, 1220, 840));
            }
        } catch (IOException e) {
            System.err.println("Failed to navigate back: " + e.getMessage());
        }
    }

    @FXML
    private void onHelpClicked() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Help");
        a.setHeaderText("Using the map");
        a.setContentText("The pin shows your restaurant's saved location and live free tables from restaurantOwner.");
        a.showAndWait();
    }

    // ================= Helpers =================

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            switch (c) {
                case '"'      -> b.append("\\\"");
                case '\\'     -> b.append("\\\\");
                case '\n'     -> b.append("\\n");
                case '\r'     -> b.append("\\r");
                case '\t'     -> b.append("\\t");
                case '<'      -> b.append("\\u003c");
                case '\u2028' -> b.append("\\u2028");
                case '\u2029' -> b.append("\\u2029");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    // ================= Model =================

    public enum Status { FREE, FILLING, FULL, CLOSED }

    /** Maps directly to "restaurantOwner" Firestore schema. */
    public static final class Restaurant {
        final String id, name, address, areaId, cityId, phone;
        final List<String> cuisines;
        final Double lat, lng;
        final boolean acceptingDiners, isOpen, perHead;
        final int availableTables, totalTables, waitMinutes;
        final long price;
        final Instant updatedAt;

        private Restaurant(String id, String name, String address, String areaId, String cityId, String phone,
                           List<String> cuisines, Double lat, Double lng,
                           boolean acceptingDiners, boolean isOpen, boolean perHead,
                           int availableTables, int totalTables, int waitMinutes,
                           long price, Instant updatedAt) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.areaId = areaId;
            this.cityId = cityId;
            this.phone = phone;
            this.cuisines = cuisines;
            this.lat = lat;
            this.lng = lng;
            this.acceptingDiners = acceptingDiners;
            this.isOpen = isOpen;
            this.perHead = perHead;
            this.availableTables = availableTables;
            this.totalTables = totalTables;
            this.waitMinutes = waitMinutes;
            this.price = price;
            this.updatedAt = updatedAt;
        }

        static Restaurant from(DocumentSnapshot d) {
            // 1. Parse location GeoPoint
            Double lat = null;
            Double lng = null;
            Object rawLoc = d.get("location");
            if (rawLoc instanceof GeoPoint gp) {
                lat = gp.getLatitude();
                lng = gp.getLongitude();
            } else if (rawLoc instanceof Map<?, ?> map) {
                lat = toDouble(map.get("latitude") != null ? map.get("latitude") : map.get("lat"));
                lng = toDouble(map.get("longitude") != null ? map.get("longitude") : map.get("lng"));
            }
            if (lat == null || lng == null) {
                lat = toDouble(d.get("lat") != null ? d.get("lat") : d.get("latitude"));
                lng = toDouble(d.get("lng") != null ? d.get("lng") : d.get("longitude"));
            }

            // 2. Parse availabilityUpdatedAt Timestamp
            Instant updatedAt = null;
            Object rawTs = d.get("availabilityUpdatedAt");
            if (rawTs instanceof Timestamp ts) {
                updatedAt = ts.toDate().toInstant();
            } else if (rawTs instanceof Number num) {
                long val = num.longValue();
                if (val > 0) {
                    updatedAt = val < 10_000_000_000L
                            ? Instant.ofEpochSecond(val)
                            : Instant.ofEpochMilli(val);
                }
            }

            // 3. Parse cuisines array
            List<String> cuisines = new ArrayList<>();
            Object rawCuisines = d.get("cuisines");
            if (rawCuisines instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !o.toString().isBlank()) cuisines.add(o.toString().trim());
                }
            }

            Boolean openVal = d.getBoolean("isOpen");
            boolean isOpen = openVal != null ? openVal : true;

            Boolean acceptingVal = d.getBoolean("acceptingDiners");
            boolean acceptingDiners = acceptingVal != null ? acceptingVal : true;

            Boolean perHeadVal = d.getBoolean("perHead");
            boolean perHead = perHeadVal != null ? perHeadVal : false;

            long price = 0L;
            if (d.get("price") instanceof Number n) price = n.longValue();

            int totalTables = num(d.get("totalTables"));
            int availableTables = d.get("availableTables") != null
                    ? num(d.get("availableTables"))
                    : totalTables;

            return new Restaurant(
                    d.getId(),
                    str(d.getString("name")),
                    str(d.getString("address")),
                    str(d.getString("areaId")),
                    str(d.getString("cityId")),
                    str(d.getString("phone")),
                    cuisines,
                    lat, lng,
                    acceptingDiners, isOpen, perHead,
                    availableTables, totalTables,
                    num(d.get("waitMinutes")),
                    price,
                    updatedAt
            );
        }

        private static String str(String s) { return s == null ? "" : s.trim(); }
        private static int num(Object o)   { return (o instanceof Number n) ? n.intValue() : 0; }
        private static Double toDouble(Object o) { return (o instanceof Number n) ? n.doubleValue() : null; }

        boolean hasLocation() {
            return lat != null && lng != null
                    && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                    && !(lat == 0 && lng == 0);
        }

        boolean isOpenForDiners() { return isOpen && acceptingDiners; }

        Status status() {
            if (!isOpenForDiners()) return Status.CLOSED;
            if (availableTables <= 0) return Status.FULL;
            int threshold = Math.max(2, (int) Math.ceil(totalTables * 0.25));
            return availableTables <= threshold ? Status.FILLING : Status.FREE;
        }

        String chipText() {
            return switch (status()) {
                case CLOSED -> isOpen ? "Not accepting" : "Closed";
                case FULL   -> "Full";
                default     -> availableTables + " free";
            };
        }

        String chipClass() {
            return switch (status()) {
                case FREE    -> "chip-free";
                case FILLING -> "chip-filling";
                case FULL    -> "chip-full";
                case CLOSED  -> "chip-closed";
            };
        }

        String subtitle() {
            String c = cuisines.isEmpty() ? "Restaurant" : String.join(" · ", cuisines);
            if (price > 0) {
                c += " · PKR " + NumberFormat.getIntegerInstance(Locale.US).format(price)
                        + (perHead ? " pp" : "");
            }
            return c;
        }

        String searchText() {
            return (name + " " + String.join(" ", cuisines) + " " + address + " " + areaId)
                    .toLowerCase(Locale.ROOT);
        }

        boolean isStale() {
            return updatedAt != null
                    && java.time.Duration.between(updatedAt, Instant.now()).toMinutes() > 60;
        }

        double distanceKm(double[] from) {
            if (from == null || !hasLocation()) return Double.MAX_VALUE;
            double r = 6371.0088;
            double dLat = Math.toRadians(lat - from[0]);
            double dLng = Math.toRadians(lng - from[1]);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(from[0])) * Math.cos(Math.toRadians(lat))
                    * Math.sin(dLng / 2) * Math.sin(dLng / 2);
            return 2 * r * Math.asin(Math.sqrt(a));
        }

        String metaLine(double[] from) {
            List<String> parts = new ArrayList<>();
            if (from != null && hasLocation()) {
                parts.add(String.format(Locale.ROOT, "%.1f km", distanceKm(from)));
            }
            if (totalTables > 0) {
                parts.add(availableTables + "/" + totalTables + " tables");
            }
            if (waitMinutes > 0 && isOpenForDiners()) {
                parts.add("~" + waitMinutes + " min wait");
            } else if (isOpenForDiners() && availableTables > 0) {
                parts.add("No wait");
            }
            return String.join(" · ", parts);
        }

        String prettyPhone() {
            String p = phone.replaceAll("[^0-9+]", "");
            return p.startsWith("+") ? p : "+" + p;
        }
    }

    // ================= Leaflet HTML =================

    private static final String MAP_HTML = """
<!DOCTYPE html>
<html><head><meta charset="utf-8">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html,body,#map{height:100%;margin:0;background:#E7E3DB;font-family:'Inter','Segoe UI',sans-serif}
  .pin-wrap{width:36px;height:42px}
  .pin{position:absolute;left:4px;top:2px;width:28px;height:28px;background:#B3262E;border:2px solid #fff;
       border-radius:50% 50% 50% 0;transform:rotate(-45deg);box-shadow:0 2px 6px rgba(0,0,0,.35);
       display:flex;align-items:center;justify-content:center}
  .pin span{transform:rotate(45deg);color:#fff;font-weight:700;font-size:12px}
  .pin.filling{background:#D08A00}.pin.full{background:#7A1A20}.pin.closed{background:#777}
  .pin.sel{left:2px;top:-2px;width:32px;height:32px;box-shadow:0 0 0 6px rgba(179,38,46,.25),0 3px 8px rgba(0,0,0,.4)}
  .user-dot{width:16px;height:16px;border-radius:50%;background:#2B7BE4;border:3px solid #fff;
            box-shadow:0 0 0 6px rgba(43,123,228,.22)}
  .leaflet-popup-content-wrapper{border-radius:12px;box-shadow:0 4px 14px rgba(0,0,0,.2)}
  .leaflet-popup-content{margin:12px 14px}
  .pop{display:flex;gap:14px;align-items:center}
  .pop b{font-size:13px;color:#111}.pop small{color:#666;font-size:11px;display:block;margin-top:2px}
  .pop .n{font-size:26px;font-weight:800;color:#1E8E4E;line-height:1;text-align:center}
  .pop .n.filling{color:#B7791F}.pop .n.full,.pop .n.closed{color:#B3262E}
  .pop .l{font-size:10px;color:#666;text-align:center}
</style></head>
<body><div id="map"></div>
<script>
var map, base, sat, markers = {}, data = {}, selectedId = null, userMarker = null, userLL = null;
function esc(s){return String(s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
if (typeof L !== 'undefined') {
  map = L.map('map',{zoomControl:false}).setView([%LAT%,%LNG%],14);
  base = L.tileLayer('%CARTO_TILE_URL%',
          {maxZoom:19,subdomains:'abcd',attribution:'&copy; OpenStreetMap &copy; CARTO'}).addTo(map);
  sat = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
          {maxZoom:19,attribution:'Tiles &copy; Esri'});
}
function icon(r, sel){
  var label = (r.status==='closed') ? '–' : r.free;
  return L.divIcon({className:'pin-wrap',iconSize:[36,42],iconAnchor:[18,40],
    html:'<div class="pin '+r.status+(sel?' sel':'')+'"><span>'+label+'</span></div>'});
}
function popupHtml(r){
  var lbl = r.status==='closed' ? 'closed' : (r.status==='full' ? 'full' : 'free');
  var num = r.status==='closed' ? '–' : r.free;
  return '<div class="pop"><div><b>'+esc(r.name)+'</b><small>'+esc(r.sub)+'</small></div>'+
         '<div><div class="n '+r.status+'">'+num+'</div><div class="l">'+lbl+'</div></div></div>';
}
function setRestaurants(list){
  Object.keys(markers).forEach(function(k){ map.removeLayer(markers[k]); });
  markers = {}; data = {};
  list.forEach(function(r){
    data[r.id] = r;
    var m = L.marker([r.lat,r.lng],{icon:icon(r,r.id===selectedId),keyboard:false}).addTo(map);
    m.bindPopup(popupHtml(r),{closeButton:false,offset:[0,-34],autoPanPadding:[60,60]});
    m.on('click',function(){ if(window.bridge) window.bridge.onMarkerClicked(r.id); });
    markers[r.id] = m;
  });
  if (selectedId && !markers[selectedId]) selectedId = null;
  if (!selectedId) fitAll();
}
function selectRestaurant(id, fly){
  var prev = selectedId; selectedId = id;
  if (prev && markers[prev] && data[prev]) markers[prev].setIcon(icon(data[prev],false));
  var m = markers[id]; if(!m) return;
  m.setIcon(icon(data[id],true));
  if (fly) { map.flyTo(m.getLatLng(), Math.max(map.getZoom(),16), {duration:0.8}); setTimeout(function(){m.openPopup()},850); }
  else m.openPopup();
}
function clearSelection(){
  if (selectedId && markers[selectedId] && data[selectedId]) markers[selectedId].setIcon(icon(data[selectedId],false));
  selectedId = null; map.closePopup(); fitAll();
}
function fitAll(){
  var pts = Object.keys(markers).map(function(k){return markers[k].getLatLng()});
  if (userLL) pts.push(userLL);
  if (pts.length === 0) return;
  if (pts.length === 1) map.setView(pts[0], 16);
  else map.fitBounds(L.latLngBounds(pts), {padding:[80,80], maxZoom:16});
}
function setUser(lat,lng){
  userLL = L.latLng(lat,lng);
  if (userMarker) map.removeLayer(userMarker);
  userMarker = L.marker(userLL,{interactive:false,
    icon:L.divIcon({className:'',html:'<div class="user-dot"></div>',iconSize:[16,16],iconAnchor:[8,8]})}).addTo(map);
}
function locate(){ if (userLL) map.flyTo(userLL, Math.max(map.getZoom(),15), {duration:0.8}); else fitAll(); }
function setSatellite(on){
  if (on) { map.removeLayer(base); sat.addTo(map); } else { map.removeLayer(sat); base.addTo(map); }
}
</script></body></html>
""";
}