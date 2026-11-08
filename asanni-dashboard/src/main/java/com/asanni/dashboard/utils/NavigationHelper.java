package com.asanni.dashboard.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.stage.Stage;

import java.io.IOException;

public class NavigationHelper {

    /**
     * Navigate to a new FXML page
     * @param fxmlPath The path to the FXML file (e.g., "/fxml/login.fxml")
     * @param node Any node from the current scene to get the window
     * @param title The title for the new window
     * @param cssPath Optional CSS file path (e.g., "/css/global.css"), can be null
     */
    public static void navigateTo(String fxmlPath, Node node, String title, String cssPath) {
        try {
            if (node == null) {
                System.err.println("NavigationHelper: node is null");
                return;
            }
            
            if (node.getScene() == null) {
                System.err.println("NavigationHelper: node has no scene");
                return;
            }

            FXMLLoader loader = new FXMLLoader(
                    node.getClass().getResource(fxmlPath)
            );

            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);

            if (cssPath != null) {
                scene.getStylesheets().add(
                        node.getClass().getResource(cssPath).toExternalForm()
                );
            }

            Stage stage = (Stage) node.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle(title);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Navigate to a new FXML page without adding CSS (FXML handles its own stylesheets)
     * @param fxmlPath The path to the FXML file (e.g., "/fxml/login.fxml")
     * @param node Any node from the current scene to get the window
     * @param title The title for the new window
     */
    public static void navigateTo(String fxmlPath, Node node, String title) {
        navigateTo(fxmlPath, node, title, null);
    }
}
