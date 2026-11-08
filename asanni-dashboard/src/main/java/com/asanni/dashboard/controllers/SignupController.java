package com.asanni.dashboard.controllers;

import com.asanni.dashboard.firebase.FirestoreService;
import com.asanni.dashboard.models.OnboardingData;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class SignupController {

    // Top Navbar & Logo Preview
    @FXML private Label navBrandLabel;

    // Form Inputs
    @FXML private TextField nameField;
    @FXML private TextField descriptionField; // Maps to Primary Cuisine
    @FXML private ComboBox<String> cityComboBox;
    @FXML private ComboBox<String> areaComboBox;
    @FXML private TextField addressField;
    @FXML private Label locationStatusLabel;
    @FXML private TextField phoneField;
    @FXML private TextField logoUrlField;
    @FXML private CheckBox perheadCheckBox;
    @FXML private TextField perheadPriceField;
    @FXML private TextField numberOfTablesField;

    // Right-Side Live Preview Labels
    @FXML private Label previewNameLabel;
    @FXML private Label previewSubtitleLabel;

    // Feedback & Action
    @FXML private Label messageLabel;
    @FXML private Button saveButton;
    @FXML private javafx.scene.layout.HBox step1Button;

    // Maps City Display Name ("Karachi") -> City Slug ID ("karachi")
    private final Map<String, String> cityNameToSlugMap = new HashMap<>();
    // Maps City Display Name ("Karachi") -> Firestore Document ID
    private final Map<String, String> cityNameToDocIdMap = new HashMap<>();

    @FXML
    public void initialize() {
        // Show/hide perhead price field based on checkbox
        if (perheadCheckBox != null && perheadPriceField != null) {
            perheadCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                perheadPriceField.setVisible(newVal);
            });
        }

        // Load cities from Firebase
        loadCitiesFromFirebase();

        // Listen to city value changes (works for both user clicks and detectLocation())
        cityComboBox.valueProperty().addListener((obs, oldCity, newCity) -> {
            loadAreasForCity(newCity);
            updateLivePreview();
        });

        // Live-update the Featured Preview whenever inputs change
        nameField.textProperty().addListener((obs, oldVal, newVal) -> updateLivePreview());
        descriptionField.textProperty().addListener((obs, oldVal, newVal) -> updateLivePreview());
        areaComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateLivePreview());
    }

    private void loadCitiesFromFirebase() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                List<String> cities = new ArrayList<>();
                try {
                    FirestoreService.initialize();
                    Firestore db = FirestoreService.getDb();

                    if (db != null) {
                        CollectionReference citiesRef = db.collection("cities");
                        List<QueryDocumentSnapshot> documents = citiesRef.get().get().getDocuments();

                        for (QueryDocumentSnapshot doc : documents) {
                            // Skip inactive cities
                            if (Boolean.FALSE.equals(doc.getBoolean("isActive"))) {
                                continue;
                            }

                            // Case A: Single document containing the "cities" array from the seed JSON
                            if (doc.contains("cities") && doc.get("cities") instanceof List<?> rawCities) {
                                for (Object item : rawCities) {
                                    if (item instanceof Map<?, ?> cityMap) {
                                        String name = (String) cityMap.get("name");
                                        String slug = (String) cityMap.get("id");
                                        if (name != null) {
                                            cities.add(name);
                                            cityNameToSlugMap.put(name, slug != null ? slug : toSlug(name));
                                        }
                                    }
                                }
                                continue;
                            }

                            // Case B: Standard 1-document-per-city in "cities" collection
                            String cityName = doc.getString("name");
                            if (cityName != null && !cityName.isEmpty()) {
                                cities.add(cityName);
                                String slugId = doc.getString("id");
                                cityNameToSlugMap.put(cityName, (slugId != null && !slugId.isEmpty()) ? slugId : toSlug(cityName));
                                cityNameToDocIdMap.put(cityName, doc.getId());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error loading cities from Firestore: " + e.getMessage());
                }

                // Fallback to full seed list if Firestore returned empty
                if (cities.isEmpty()) {
                    cities.addAll(getFallbackCities());
                }
                return cities;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            cityComboBox.getItems().setAll(task.getValue());
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            cityComboBox.getItems().setAll(getFallbackCities());
        }));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @SuppressWarnings("unchecked")
    private void loadAreasForCity(String cityName) {
        if (cityName == null || cityName.isEmpty()) {
            areaComboBox.getItems().clear();
            return;
        }

        String citySlug = cityNameToSlugMap.getOrDefault(cityName, toSlug(cityName));
        String cityDocId = cityNameToDocIdMap.getOrDefault(cityName, citySlug);

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                List<String> areas = new ArrayList<>();
                try {
                    FirestoreService.initialize();
                    Firestore db = FirestoreService.getDb();

                    if (db != null) {
                        // 1. Check "areas" collection (handles all 4 common Firestore structures)
                        List<QueryDocumentSnapshot> areaDocs = db.collection("areas").get().get().getDocuments();

                        for (QueryDocumentSnapshot doc : areaDocs) {
                            String docId = doc.getId();
                            Map<String, Object> data = doc.getData();

                            // Structure 1: Document ID matches citySlug or cityDocId (e.g. areas/karachi)
                            if (docId.equalsIgnoreCase(citySlug) || docId.equalsIgnoreCase(cityDocId)) {
                                extractAreasFromObject(data.get("areas"), areas);
                                extractAreasFromObject(data.get("items"), areas);
                                extractAreasFromObject(data.get(citySlug), areas);

                                // Or if the document fields themselves are area objects
                                if (areas.isEmpty()) {
                                    for (Object val : data.values()) {
                                        if (val instanceof Map<?, ?> mapVal && mapVal.containsKey("name")) {
                                            addAreaFromMap((Map<String, Object>) mapVal, areas);
                                        }
                                    }
                                }
                            }

                            // Structure 2: A single document in "areas" that holds the city map (e.g. data.get("karachi"))
                            if (data.containsKey(citySlug)) {
                                extractAreasFromObject(data.get(citySlug), areas);
                            } else if (data.containsKey("areas") && data.get("areas") instanceof Map<?, ?> nestedAreasMap) {
                                extractAreasFromObject(nestedAreasMap.get(citySlug), areas);
                            }

                            // Structure 3: Each document in "areas" is an individual area with a "cityId" or "city" field
                            String docCityId = doc.getString("cityId");
                            if (docCityId == null) docCityId = doc.getString("city");
                            if (docCityId == null) docCityId = doc.getString("city_id");

                            if ((docCityId != null && (docCityId.equalsIgnoreCase(citySlug) || docCityId.equalsIgnoreCase(cityDocId) || docCityId.equalsIgnoreCase(cityName)))
                                    || docId.toLowerCase().startsWith(citySlug + "-")
                                    || docId.toLowerCase().startsWith(citySlug + "_")) {
                                if (!Boolean.FALSE.equals(doc.getBoolean("isActive"))) {
                                    String areaName = doc.getString("name");
                                    if (areaName != null && !areas.contains(areaName)) {
                                        areas.add(areaName);
                                    }
                                }
                            }
                        }

                        // 2. Check subcollection: cities/{cityDocId}/areas
                        if (areas.isEmpty()) {
                            List<QueryDocumentSnapshot> subDocs = db.collection("cities")
                                    .document(cityDocId)
                                    .collection("areas")
                                    .get().get().getDocuments();
                            for (QueryDocumentSnapshot subDoc : subDocs) {
                                if (!Boolean.FALSE.equals(subDoc.getBoolean("isActive"))) {
                                    String areaName = subDoc.getString("name");
                                    if (areaName != null && !areas.contains(areaName)) {
                                        areas.add(areaName);
                                    }
                                }
                            }
                        }

                        // 3. Check if the city document itself has an "areas" array field
                        if (areas.isEmpty()) {
                            DocumentSnapshot cityDoc = db.collection("cities").document(cityDocId).get().get();
                            if (cityDoc.exists()) {
                                extractAreasFromObject(cityDoc.get("areas"), areas);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error loading areas from Firestore for " + cityName + ": " + e.getMessage());
                }

                // Fallback to the JSON seed catalogue if Firestore had no areas for this city
                if (areas.isEmpty()) {
                    areas.addAll(getFallbackAreasForCity(citySlug));
                }

                // Apply the JSON's "cityWideArea": "All of {city}" rule if not already present
                String cityWideOption = "All of " + cityName;
                if (!areas.contains(cityWideOption)) {
                    areas.add(cityWideOption);
                }

                return areas;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            List<String> loadedAreas = task.getValue();
            areaComboBox.getItems().setAll(loadedAreas);
            if (!loadedAreas.isEmpty()) {
                areaComboBox.getSelectionModel().selectFirst();
            }
            System.out.println("Loaded " + loadedAreas.size() + " areas for " + cityName);
        }));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @SuppressWarnings("unchecked")
    private void extractAreasFromObject(Object rawObj, List<String> targetList) {
        if (rawObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String str && !targetList.contains(str)) {
                    targetList.add(str);
                } else if (item instanceof Map<?, ?> mapItem) {
                    addAreaFromMap((Map<String, Object>) mapItem, targetList);
                }
            }
        } else if (rawObj instanceof Map<?, ?> map) {
            for (Object val : map.values()) {
                if (val instanceof Map<?, ?> mapItem) {
                    addAreaFromMap((Map<String, Object>) mapItem, targetList);
                }
            }
        }
    }

    private void addAreaFromMap(Map<String, Object> areaMap, List<String> targetList) {
        if (Boolean.FALSE.equals(areaMap.get("isActive"))) {
            return;
        }
        Object nameObj = areaMap.get("name");
        if (nameObj instanceof String areaName && !areaName.isEmpty() && !targetList.contains(areaName)) {
            targetList.add(areaName);
        }
    }

    private String toSlug(String input) {
        return input == null ? "" : input.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private List<String> getFallbackCities() {
        return List.of(
                "Karachi", "Lahore", "Islamabad", "Rawalpindi", "Faisalabad",
                "Peshawar", "Quetta", "Multan", "Hyderabad", "Gujranwala",
                "Sialkot", "Sukkur", "Abbottabad", "Bahawalpur", "Sargodha",
                "Larkana", "Mardan", "Sheikhupura", "Rahim Yar Khan", "Gujrat",
                "Sahiwal", "Jhelum", "Kasur", "Okara", "Dera Ghazi Khan",
                "Mirpur Khas", "Nawabshah", "Muzaffarabad", "Gilgit", "Gwadar",
                "Murree", "Swat"
        );
    }

    private List<String> getFallbackAreasForCity(String citySlug) {
        return switch (citySlug) {
            case "karachi" -> List.of(
                    "Clifton", "DHA", "Gulshan-e-Iqbal", "Gulistan-e-Johar", "North Nazimabad",
                    "Nazimabad", "Federal B Area", "Saddar", "Korangi", "Malir", "PECHS",
                    "Bahadurabad", "Shahrah-e-Faisal", "Karsaz", "North Karachi", "New Karachi",
                    "Gulshan-e-Maymar", "Scheme 33", "Bahria Town Karachi", "Kemari", "Lyari",
                    "Orangi Town", "Landhi", "Shah Faisal Colony", "Model Colony", "Garden",
                    "Kharadar", "Civil Lines", "Surjani Town", "Buffer Zone", "Gulberg (Karachi)",
                    "University Road"
            );
            case "lahore" -> List.of(
                    "Gulberg", "DHA Lahore", "Model Town", "Johar Town", "Bahria Town Lahore",
                    "Garden Town", "Faisal Town", "Lahore Cantt", "Mall Road", "Anarkali",
                    "Allama Iqbal Town", "Wapda Town", "Township", "Shadman", "Walled City",
                    "Cavalry Ground"
            );
            case "islamabad" -> List.of(
                    "F-6", "F-7", "F-8", "F-10", "F-11", "E-7", "E-11",
                    "G-9", "G-10", "G-11", "I-8", "Blue Area", "DHA Islamabad", "Bani Gala"
            );
            case "rawalpindi" -> List.of(
                    "Saddar", "Bahria Town", "Satellite Town", "Commercial Market",
                    "Westridge", "Chaklala", "Raja Bazaar"
            );
            case "faisalabad" -> List.of(
                    "D Ground", "Madina Town", "Susan Road", "Kohinoor City", "Clock Tower"
            );
            case "peshawar" -> List.of(
                    "University Town", "Hayatabad", "Saddar", "Gulbahar", "Peshawar Cantt"
            );
            case "quetta" -> List.of(
                    "Jinnah Road", "Satellite Town", "Quetta Cantt", "Samungli Road"
            );
            case "multan" -> List.of(
                    "Gulgasht Colony", "Multan Cantt", "Bosan Road", "Shah Rukn-e-Alam Colony"
            );
            case "hyderabad" -> List.of(
                    "Latifabad", "Qasimabad", "Saddar", "Auto Bhan Road"
            );
            default -> Collections.emptyList();
        };
    }

    private void updateLivePreview() {
        String name = nameField.getText().trim();
        String cuisine = descriptionField.getText().trim();
        String area = areaComboBox.getValue() != null ? areaComboBox.getValue().trim() : "";

        String displayName = name.isEmpty() ? "Restaurant" : name;
        previewNameLabel.setText(displayName);
        navBrandLabel.setText(displayName);

        String displayCuisine = cuisine.isEmpty() ? "Cuisine" : cuisine;
        String displayArea = area.isEmpty() ? "Karachi" : area;
        previewSubtitleLabel.setText(displayCuisine + " · " + displayArea);
    }

    @FXML
    private void handleSignup() {
        // Validate required fields
        String name = nameField.getText().trim();
        String cuisine = descriptionField.getText().trim();
        String city = cityComboBox.getValue();
        String area = areaComboBox.getValue();
        String address = addressField.getText().trim();
        String phone = phoneField.getText().trim();
        String logoUrl = logoUrlField.getText().trim();
        String tablesText = numberOfTablesField.getText().trim();

        if (name.isEmpty() || cuisine.isEmpty() || city == null || area == null || 
            address.isEmpty() || phone.isEmpty() || tablesText.isEmpty()) {
            messageLabel.setText("Please fill in all required fields.");
            return;
        }

        try {
            int numberOfTables = Integer.parseInt(tablesText);
            boolean perHead = perheadCheckBox.isSelected();
            long perHeadPrice = 0;
            if (perHead) {
                String priceText = perheadPriceField.getText().trim();
                if (!priceText.isEmpty()) {
                    perHeadPrice = Long.parseLong(priceText);
                }
            }

            // Save data to OnboardingData
            OnboardingData data = OnboardingData.getInstance();
            data.setRestaurantName(name);
            data.setPrimaryCuisine(cuisine);
            data.setCity(city);
            data.setCityId(cityNameToSlugMap.getOrDefault(city, toSlug(city)));
            data.setArea(area);
            data.setAreaId(toSlug(area));
            data.setAddress(address);
            data.setPhone(phone);
            data.setLogoUrl(logoUrl);
            data.setNumberOfTables(numberOfTables);
            data.setPerHead(perHead);
            data.setPerHeadPrice(perHeadPrice);

            // Navigate to featured listing
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/featuredlisting.fxml")
            );

            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);

            Stage stage = (Stage) saveButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Create Account - Asanni");
            stage.show();
        } catch (NumberFormatException e) {
            messageLabel.setText("Please enter a valid number for tables and price.");
        } catch (IOException e) {
            e.printStackTrace();
            messageLabel.setText("Error loading next screen. Please try again.");
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

            Stage stage = (Stage) step1Button.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Select Plan - Asanni");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void detectLocation() {
        addressField.setText("Detecting...");
        addressField.setDisable(true);
        locationStatusLabel.setText("Fetching your location...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://ipapi.co/json/"))
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                return response.body();
            }
        };

        task.setOnSucceeded(e -> {
            String jsonResponse = task.getValue();
            Platform.runLater(() -> {
                try {
                    String city = extractField(jsonResponse, "city");
                    String region = extractField(jsonResponse, "region");
                    String postal = extractField(jsonResponse, "postal");

                    if (city != null && !city.isEmpty()) {
                        cityComboBox.setValue(city);
                    }

                    String address = "";
                    if (postal != null && !postal.isEmpty()) {
                        address = postal;
                    }
                    if (region != null && !region.isEmpty()) {
                        address = address.isEmpty() ? region : address + ", " + region;
                    }

                    addressField.setText(address);
                    locationStatusLabel.setText("Location detected: " + (city != null ? city : "your area"));
                } catch (Exception ex) {
                    addressField.setText("");
                    messageLabel.setText("Failed to detect location. Please enter manually.");
                }
                addressField.setDisable(false);
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                addressField.setText("");
                messageLabel.setText("Failed to detect location. Please check your internet connection.");
                addressField.setDisable(false);
                locationStatusLabel.setText("");
            });
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private String extractField(String json, String fieldName) {
        String searchPattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(searchPattern);
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @FXML
    private void openLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);
            Stage stage = (Stage) nameField.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Sign In - Asanni");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}