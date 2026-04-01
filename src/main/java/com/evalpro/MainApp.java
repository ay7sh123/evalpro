package com.evalpro;

import com.evalpro.database.DatabaseHelper;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {

        // Initialize database on startup
        DatabaseHelper.initializeDatabase();

        // Logo — E inside purple circle
        Circle circle = new Circle(45);
        circle.setStyle("-fx-fill: #5B4FCF;");

        Label logoLetter = new Label("E");
        logoLetter.setStyle(
                "-fx-font-size: 34px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: white;"
        );

        StackPane logoBadge = new StackPane(circle, logoLetter);

        // App name
        Label title = new Label("EvalPro");
        title.setStyle(
                "-fx-font-size: 38px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: #1a1a1a;"
        );

        // Subtitle
        Label subtitle = new Label("AI-Powered Examination System");
        subtitle.setStyle(
                "-fx-font-size: 14px;" +
                        "-fx-text-fill: #888888;" +
                        "-fx-letter-spacing: 1px;"
        );

        // Divider line
        Label divider = new Label("─────────────────────");
        divider.setStyle("-fx-text-fill: #dddddd;");

        // Version tag
        Label version = new Label("v1.0  •  For CS Departments");
        version.setStyle(
                "-fx-font-size: 11px;" +
                        "-fx-text-fill: #bbbbbb;"
        );

        // Main layout
        VBox root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #F7F7F7;");
        root.getChildren().addAll(
                logoBadge,
                title,
                subtitle,
                divider,
                version
        );

        Scene scene = new Scene(root, 900, 650);
        stage.setTitle("EvalPro");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}