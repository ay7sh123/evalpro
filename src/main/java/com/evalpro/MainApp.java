package com.evalpro;

import com.evalpro.database.DatabaseHelper;
import com.evalpro.database.UserDAO;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // Initialize database
        DatabaseHelper.initializeDatabase();

        // Create demo accounts for testing
        createDemoAccounts();

        // Load login screen
        Parent root = FXMLLoader.load(
                getClass().getResource("/com/evalpro/views/landing.fxml")
        );

        Scene scene = new Scene(root, 900, 650);
        stage.setTitle("EvalPro");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void createDemoAccounts() {
        // Create demo accounts only if they don't exist
        UserDAO.registerUser("Demo Teacher", "teacher@evalpro.com", "teacher123", "teacher");
        UserDAO.registerUser("Demo Student", "student@evalpro.com", "student123", "student");
        UserDAO.registerUser("Demo Admin",   "admin@evalpro.com",   "admin123",   "admin");
    }

    public static void main(String[] args) {
        launch(args);
    }
}