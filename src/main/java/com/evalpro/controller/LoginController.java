package com.evalpro.controller;

import com.evalpro.database.UserDAO;
import com.evalpro.model.User;
import com.evalpro.util.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    private void handleLogin(ActionEvent event) {
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();

        if (email.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please enter email and password.");
            return;
        }

        User user = UserDAO.loginUser(email, password);

        if (user != null) {
            SessionManager.setCurrentUser(user);
            errorLabel.setStyle("-fx-text-fill: green;");
            errorLabel.setText("Login successful! Welcome " + user.getFullName());
            redirectToDashboard(user.getRole(), event);
        } else {
            errorLabel.setStyle("-fx-text-fill: #e53935;");
            errorLabel.setText("Invalid email or password. Please try again.");
        }
    }

    private void redirectToDashboard(String role, ActionEvent event) {
        try {
            String fxmlFile = null;
            int width = 900;
            int height = 650;

            switch (role) {
                case "teacher":
                    fxmlFile = "/com/evalpro/views/teacher_dashboard.fxml";
                    width = 960;
                    height = 660;
                    break;

                case "student":
                    // ✅ CHANGED: Student ko direct exam list pe le jao
                    fxmlFile = "/com/evalpro/views/student_dashboard.fxml";
                    width = 1100;
                    height = 700;
                    break;

                case "admin":
                    fxmlFile = "/com/evalpro/views/admin_dashboard.fxml";
                    break;

                default:
                    errorLabel.setText("Unknown role: " + role);
                    return;
            }

            if (fxmlFile != null) {
                Parent root = FXMLLoader.load(getClass().getResource(fxmlFile));
                Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
                stage.setScene(new Scene(root, width, height));
                stage.setTitle("EvalPro - " + capitalize(role));
                stage.centerOnScreen();
                stage.show();
            }

        } catch (Exception e) {
            errorLabel.setStyle("-fx-text-fill: #e53935;");
            errorLabel.setText("Error loading dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void goBack(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(
                    getClass().getResource("/com/evalpro/views/landing.fxml")
            );
            Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 650));
            stage.setTitle("EvalPro");
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            System.out.println("Error going back: " + e.getMessage());
        }
    }

    /**
     * Helper method to capitalize first letter
     */
    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}