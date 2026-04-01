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
            String fxmlFile = switch (role) {
                case "teacher" -> "/com/evalpro/views/teacher_dashboard.fxml";
                case "student" -> "/com/evalpro/views/student_dashboard.fxml";
                case "admin"   -> "/com/evalpro/views/admin_dashboard.fxml";
                default -> null;
            };

            Parent root = FXMLLoader.load(getClass().getResource(fxmlFile));
            Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 650));
            stage.show();

        } catch (Exception e) {
            errorLabel.setStyle("-fx-text-fill: green;");
            errorLabel.setText("✓ Login works! Dashboard coming in next module.");
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
            stage.show();
        } catch (Exception e) {
            System.out.println("Error going back: " + e.getMessage());
        }
    }
}