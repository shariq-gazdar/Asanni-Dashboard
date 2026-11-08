package com.asanni.dashboard.firebase;

import com.asanni.dashboard.models.RestaurantOwner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FirestoreService {

    private static Firestore db;

    /**
     * Safely initializes Firebase once using variables from the .env file.
     */
    public static synchronized void initialize() throws Exception {
        // Prevent "FirebaseApp name [DEFAULT] already exists!" if called multiple times
        if (!FirebaseApp.getApps().isEmpty()) {
            if (db == null) {
                db = FirestoreClient.getFirestore();
            }
            return;
        }

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        GoogleCredentials credentials;

        // Option A: If you specified a path to the JSON file in .env (FIREBASE_KEY_PATH)
        String keyPath = dotenv.get("FIREBASE_KEY_PATH");
        if (keyPath != null && !keyPath.isBlank()) {
            try (InputStream serviceAccount = new FileInputStream(keyPath)) {
                credentials = GoogleCredentials.fromStream(serviceAccount);
            }
        } else {
            // Option B: Build credentials directly from .env variables (Next.js style)
            String projectId = dotenv.get("FIREBASE_PROJECT_ID");
            String clientEmail = dotenv.get("FIREBASE_CLIENT_EMAIL");
            String privateKey = dotenv.get("FIREBASE_PRIVATE_KEY");

            if (projectId == null || clientEmail == null || privateKey == null) {
                throw new IllegalStateException(
                        "Missing Firebase credentials in .env file! Set FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL, and FIREBASE_PRIVATE_KEY."
                );
            }

            String serviceAccountJson = String.format(
                    "{\n" +
                    "  \"type\": \"service_account\",\n" +
                    "  \"project_id\": \"%s\",\n" +
                    "  \"private_key_id\": \"1\",\n" +
                    "  \"private_key\": \"%s\",\n" +
                    "  \"client_email\": \"%s\",\n" +
                    "  \"client_id\": \"1\",\n" +
                    "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
                    "  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n" +
                    "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
                    "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/%s\"\n" +
                    "}",
                    projectId,
                    privateKey.replace("\n", "\\n"),
                    clientEmail,
                    clientEmail.replace("@", "%40")
            );

            try (InputStream stream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))) {
                credentials = GoogleCredentials.fromStream(stream);
            }
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp.initializeApp(options);
        db = FirestoreClient.getFirestore();
        System.out.println("Firebase & Firestore initialized successfully!");
    }

    /**
     * Saves a RestaurantOwner document to the "restaurant_owners" collection in Firestore.
     */
    public static String addRestaurantOwner(RestaurantOwner owner) throws Exception {
        if (db == null) {
            initialize();
        }

        // Adds document to "restaurant_owners" collection and waits for the generated ID
        DocumentReference docRef = db.collection("restaurant_owners").document();
        docRef.set(owner.toMap()).get();
        return docRef.getId();
    }

    public static Firestore getDb() {
        return db;
    }
}