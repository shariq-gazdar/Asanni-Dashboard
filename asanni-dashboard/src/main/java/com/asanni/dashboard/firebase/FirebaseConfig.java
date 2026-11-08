package com.asanni.dashboard.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.IOException;
import java.io.InputStream;

public class FirebaseConfig {
    private static boolean initialized = false;

    public static void initialize() throws IOException {
        if (!initialized) {
            InputStream serviceAccount = null;
            
            // Try loading from classpath resources first
            serviceAccount = FirebaseConfig.class.getClassLoader().getResourceAsStream("service-account-key.json");
            
            // If not found in resources, try file path
            if (serviceAccount == null) {
                try {
                    serviceAccount = new java.io.FileInputStream("service-account-key.json");
                } catch (java.io.FileNotFoundException e) {
                    throw new IOException("Service account key file not found. Please place 'service-account-key.json' in the project root or src/main/resources folder.", e);
                }
            }

            if (serviceAccount == null || serviceAccount.available() == 0) {
                throw new IOException("Service account key file is empty or could not be loaded. Please ensure 'service-account-key.json' contains valid Firebase credentials with client_id, client_email, private_key, and private_key_id fields.");
            }

            try {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                initialized = true;
            } catch (Exception e) {
                throw new IOException("Failed to initialize Firebase with service account key: " + e.getMessage(), e);
            }
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
