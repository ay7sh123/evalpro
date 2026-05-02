package com.evalpro.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LandingController {

    @FXML private VBox featuresSection
    @FXML
    private void goToLogin(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(
                    getClass().getResource("/com/evalpro/views/login.fxml")
            );
            Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 650));
            stage.setTitle("EvalPro - Login");
            stage.show();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    @FXML
    private void scrollToFeatures(ActionEvent event) {
        if (featuresSection != null) {
            featuresSection.setStyle(
                    "-fx-padding: 30 40 30 40;" +
                            "-fx-background-color: #EEEDFE;"
            );
            // Reset color after 1 second to highlight the section
            new Thread(() -> {
                try {
                    Thread.sleep(800);
                    javafx.application.Platform.runLater(() ->
                            featuresSection.setStyle(
                                    "-fx-padding: 30 40 30 40;" +
                                            "-fx-background-color: #F7F7F7;"
                            )
                    );
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }).start();
        }
    }

    @FXML
    private void showAbout(ActionEvent event) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("About EvalPro");
        alert.setHeaderText("EvalPro v1.0");
        alert.setContentText(
                "EvalPro is an AI-powered desktop examination platform.\n\n" +
                        "Built with JavaFX + SQLite + Gemini AI\n\n" +
                        "Features:\n" +
                        "  • AI Question Generator\n" +
                        "  • Built-in Coding Editor\n" +
                        "  • Smart Auto Scoring\n" +
                        "  • Tab Switch Detection\n" +
                        "  • Works Fully Offline\n\n" +
                        "Version: 1.0-SNAPSHOT\n" +
                        "For all departments and institutions."
        );
        alert.showAndWait();
    }
}