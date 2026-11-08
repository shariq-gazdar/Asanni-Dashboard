
package com.asanni.dashboard;

import com.asanni.dashboard.firebase.FirestoreService;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
public class MainApp extends Application {

    private Stage stage;
    private final String accent = "#635BFF";


    @Override
    public void start(Stage stage) throws Exception {
        // Initialize Firebase
        try {
            FirestoreService.initialize();
        } catch (Exception e) {
            System.err.println("Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
        }

        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/fxml/planselect.fxml")
        );

        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 700);

        scene.getStylesheets().add(
                MainApp.class.getResource("/css/global.css").toExternalForm()
        );

        stage.setTitle("Asanni Dashboard");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }


    private void showLogin() {
        Label brand = new Label("asanni.");
        brand.setStyle("-fx-text-fill: white; -fx-font-size: 30px; -fx-font-weight: bold;");

        Label tagline = new Label("One workspace.\nEverything under control.");
        tagline.setStyle("-fx-text-fill: white; -fx-font-size: 27px; -fx-font-weight: bold;");
        tagline.setWrapText(true);

        Label description = new Label(
                "Manage your business, team and operations from one place."
        );
        description.setStyle("-fx-text-fill: #B9C0D4; -fx-font-size: 14px;");
        description.setWrapText(true);

        Region sidebarSpacer = new Region();
        VBox.setVgrow(sidebarSpacer, Priority.ALWAYS);

        Label footer = new Label("ASANNI MANAGEMENT SYSTEM");
        footer.setStyle("-fx-text-fill: #929BB5; -fx-font-size: 10px;");

        VBox sidebar = new VBox(22, brand, sidebarSpacer, tagline, description, footer);
        sidebar.setPadding(new Insets(40));
        sidebar.setPrefWidth(360);
        sidebar.setStyle("-fx-background-color: #171B32;");

        Label heading = new Label("Welcome back");
        heading.setFont(Font.font(30));
        heading.setStyle("-fx-font-weight: bold; -fx-text-fill: #171B32;");

        Label subtitle = new Label("Sign in to continue to your workspace");
        subtitle.setStyle("-fx-text-fill: #777D8C; -fx-font-size: 14px;");

        TextField email = new TextField();
        email.setPromptText("Enter your email");
        styleInput(email);

        PasswordField password = new PasswordField();
        password.setPromptText("Enter your password");
        styleInput(password);

        Label message = new Label();
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #DC3545;");

        Button login = new Button("Sign in");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setPrefHeight(46);
        login.setStyle(buttonStyle());

        login.setOnAction(event -> {
            message.setText("Firebase connection will be added in the next step.");
        });

        Label signupText = new Label("New to Asanni?");
        signupText.setStyle("-fx-text-fill: #777D8C;");

        Hyperlink signupLink = new Hyperlink("Create an account");
        signupLink.setStyle("-fx-text-fill: " + accent + "; -fx-font-weight: bold;");
        signupLink.setOnAction(event -> showSignup());

        HBox signupRow = new HBox(4, signupText, signupLink);
        signupRow.setAlignment(Pos.CENTER);

        VBox form = new VBox(
                12,
                heading,
                subtitle,
                spacer(20),
                label("Email address"),
                email,
                label("Password"),
                password,
                message,
                spacer(4),
                login,
                spacer(8),
                signupRow
        );
        form.setMaxWidth(390);
        form.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(form);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(45));
        content.setStyle("-fx-background-color: #F7F8FC;");
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox root = new HBox(sidebar, content);
        stage.setScene(new Scene(root, 1100, 700));
    }

    private void showSignup() {
        Label heading = new Label("Create your account");
        heading.setStyle("-fx-font-size: 29px; -fx-font-weight: bold; -fx-text-fill: #171B32;");

        Label subtitle = new Label("Get started with your workspace");
        subtitle.setStyle("-fx-text-fill: #777D8C;");

        TextField name = new TextField();
        name.setPromptText("Enter your full name");
        styleInput(name);

        TextField email = new TextField();
        email.setPromptText("Enter your email");
        styleInput(email);

        PasswordField password = new PasswordField();
        password.setPromptText("At least 6 characters");
        styleInput(password);

        Label message = new Label();
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #DC3545;");

        Button signup = new Button("Create account");
        signup.setMaxWidth(Double.MAX_VALUE);
        signup.setPrefHeight(46);
        signup.setStyle(buttonStyle());

        signup.setOnAction(event -> {
            if (name.getText().isBlank()
                    || email.getText().isBlank()
                    || password.getText().isBlank()) {
                message.setText("Please fill in all fields.");
            } else if (password.getText().length() < 6) {
                message.setText("Password must contain at least 6 characters.");
            } else {
                message.setText("Firebase connection will be added in the next step.");
            }
        });

        Hyperlink back = new Hyperlink("Back to sign in");
        back.setStyle("-fx-text-fill: " + accent + ";");
        back.setOnAction(event -> showLogin());

        VBox form = new VBox(
                12,
                heading,
                subtitle,
                spacer(18),
                label("Full name"),
                name,
                label("Email address"),
                email,
                label("Password"),
                password,
                message,
                spacer(5),
                signup,
                back
        );

        form.setMaxWidth(390);

        VBox root = new VBox(form);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: #F7F8FC;");

        stage.setScene(new Scene(root, 900, 650));
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #33394D; -fx-font-weight: bold;");
        return label;
    }

    private void styleInput(TextField field) {
        field.setPrefHeight(44);
        field.setStyle(
                "-fx-background-color: white;" +
                        "-fx-border-color: #DDE1EB;" +
                        "-fx-border-radius: 7;" +
                        "-fx-background-radius: 7;" +
                        "-fx-padding: 10;" +
                        "-fx-font-size: 13px;"
        );
    }

    private String buttonStyle() {
        return "-fx-background-color: " + accent + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 7;" +
                "-fx-cursor: hand;";
    }

    private Region spacer(double height) {
        Region region = new Region();
        region.setMinHeight(height);
        region.setPrefHeight(height);
        return region;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
