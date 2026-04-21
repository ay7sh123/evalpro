package com.evalpro.controller.student;

import com.evalpro.database.DatabaseHelper;
import com.evalpro.database.ExamHelper;
import com.evalpro.database.ExamHelper.ExamInfo;
import com.evalpro.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentDashboardController {

    @FXML private Label studentNameLabel;
    @FXML private Label studentEmailLabel;
    @FXML private Label studentRollLabel;

    @FXML private Label totalExamsLabel;
    @FXML private Label completedExamsLabel;
    @FXML private Label pendingExamsLabel;

    @FXML private Button notificationBtn;
    @FXML private Label notificationBadge;

    @FXML private HBox examCardsContainer;
    @FXML private VBox recentResultsContainer;

    private int studentId;
    private int totalExams = 0;
    private int completedExams = 0;
    private int pendingExams = 0;
    private int newNotifications = 0;

    @FXML
    public void initialize() {
        loadStudentInfo();
        loadStats();
        loadAvailableExams();
        loadRecentResults();
        loadNotifications();
    }

    private void loadStudentInfo() {
        studentId = SessionManager.getCurrentUser().getId();
        String name = SessionManager.getCurrentUser().getFullName();
        String email = SessionManager.getCurrentUser().getEmail();

        studentNameLabel.setText(name);
        studentEmailLabel.setText(email);
        studentRollLabel.setText("Roll No: " + String.format("2024%03d", studentId));
    }

    private void loadStats() {
        List<ExamInfo> allExams = ExamHelper.getAllExams();
        totalExams = allExams.size();

        // ✅ FIX: Count only 'completed' submissions, not 'in_progress'
        String sql = "SELECT COUNT(*) as count FROM submissions WHERE student_id = ? AND status = 'completed'";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, studentId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                completedExams = rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Error loading stats: " + e.getMessage());
        }

        pendingExams = Math.max(0, totalExams - completedExams);

        totalExamsLabel.setText(String.valueOf(totalExams));
        completedExamsLabel.setText(String.valueOf(completedExams));
        pendingExamsLabel.setText(String.valueOf(pendingExams));
    }

    private void loadAvailableExams() {
        examCardsContainer.getChildren().clear();

        List<ExamInfo> exams = ExamHelper.getAllExams();
        List<ExamInfo> pendingExamsList = new ArrayList<>();

        for (ExamInfo exam : exams) {
            if (!isExamCompleted(exam.id)) {
                pendingExamsList.add(exam);
                if (pendingExamsList.size() >= 3) break;
            }
        }

        if (pendingExamsList.isEmpty()) {
            Label noExams = new Label("🎉 No pending exams! You're all caught up!");
            noExams.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 14px;");
            examCardsContainer.getChildren().add(noExams);
        } else {
            for (ExamInfo exam : pendingExamsList) {
                examCardsContainer.getChildren().add(createExamCard(exam));
            }
        }
    }

    private VBox createExamCard(ExamInfo exam) {
        VBox card = new VBox(12);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPrefSize(260, 200);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-padding: 20; " +
                "-fx-border-color: #e5e7eb; -fx-border-radius: 15; -fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);");

        Label title = new Label(exam.title);
        title.setWrapText(true);
        title.setMaxWidth(220);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1f2937;");

        Label subject = new Label("📖 " + (exam.subject != null ? exam.subject : "General"));
        subject.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");

        Label duration = new Label("⏱️ " + exam.duration + " minutes");
        duration.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");

        Label marks = new Label("🎯 " + exam.totalMarks + " marks");
        marks.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button startBtn = new Button("Start Exam →");
        startBtn.setMaxWidth(Double.MAX_VALUE);
        startBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; " +
                "-fx-padding: 10; -fx-background-radius: 8; -fx-cursor: hand; " +
                "-fx-font-weight: bold;");
        startBtn.setOnAction(e -> startExam(exam.id));

        card.getChildren().addAll(title, subject, duration, marks, spacer, startBtn);
        return card;
    }

    private void loadRecentResults() {
        recentResultsContainer.getChildren().clear();

        // ✅ FIX: Only show completed submissions
        String sql = "SELECT e.title, s.score, e.total_marks " +
                "FROM submissions s " +
                "JOIN exams e ON s.exam_id = e.id " +
                "WHERE s.student_id = ? AND s.status = 'completed' " +
                "ORDER BY s.id DESC LIMIT 3";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, studentId);
            ResultSet rs = pstmt.executeQuery();

            boolean hasResults = false;
            while (rs.next()) {
                hasResults = true;
                String examTitle = rs.getString("title");
                double score = rs.getDouble("score");
                int totalMarks = rs.getInt("total_marks");

                recentResultsContainer.getChildren().add(createResultRow(examTitle, score, totalMarks));
            }

            if (!hasResults) {
                Label noResults = new Label("📝 No results yet. Take an exam to see your scores!");
                noResults.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 14px;");
                recentResultsContainer.getChildren().add(noResults);
            }

        } catch (SQLException e) {
            Label noResults = new Label("📝 No results yet. Take an exam to see your scores!");
            noResults.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 14px;");
            recentResultsContainer.getChildren().add(noResults);
        }
    }

    private VBox createResultRow(String examTitle, double score, int totalMarks) {
        VBox row = new VBox(8);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(examTitle);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1f2937;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        double percentage = totalMarks > 0 ? (score / totalMarks) * 100 : 0;
        Label scoreLabel = new Label(String.format("%.0f/%d (%.0f%%)", score, totalMarks, percentage));
        scoreLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + getScoreColor(percentage) + ";");

        header.getChildren().addAll(title, spacer, scoreLabel);

        ProgressBar progressBar = new ProgressBar(totalMarks > 0 ? score / totalMarks : 0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: " + getScoreColor(percentage) + ";");

        row.getChildren().addAll(header, progressBar);
        return row;
    }

    private String getScoreColor(double percentage) {
        if (percentage >= 80) return "#10b981";
        if (percentage >= 60) return "#f59e0b";
        return "#ef4444";
    }

    /**
     * ✅ FIX: isExamCompleted — only count 'completed' status, not 'in_progress'
     */
    private boolean isExamCompleted(int examId) {
        String sql = "SELECT COUNT(*) as count FROM submissions WHERE exam_id = ? AND student_id = ? AND status = 'completed'";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, examId);
            pstmt.setInt(2, studentId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error checking exam status: " + e.getMessage());
        }
        return false;
    }

    private void loadNotifications() {
        String sql = "SELECT COUNT(*) as count FROM exams e " +
                "WHERE e.created_at >= datetime('now', '-7 days') " +
                "AND NOT EXISTS (SELECT 1 FROM submissions s WHERE s.exam_id = e.id AND s.student_id = ? AND s.status = 'completed')";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, studentId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                newNotifications = rs.getInt("count");
                if (newNotifications > 0) {
                    notificationBadge.setText(String.valueOf(newNotifications));
                    notificationBadge.setVisible(true);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading notifications: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefreshDashboard() {
        System.out.println("🔄 Refreshing dashboard...");
        loadStats();
        loadAvailableExams();
        loadRecentResults();
        loadNotifications();

        notificationBtn.setText("✅");
        new Thread(() -> {
            try {
                Thread.sleep(500);
                javafx.application.Platform.runLater(() -> notificationBtn.setText("🔔"));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startExam(int examId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Start Exam");
        confirm.setHeaderText("Are you sure you want to start this exam?");
        confirm.setContentText("⏰ Timer will start immediately.\n⚠️ You cannot pause once started.\n🚫 Tab switching is monitored (max 3 warnings).");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            openExamWindow(examId);
        }
    }

    /**
     * ✅ FIX: openExamWindow — attachTabSwitchListener called AFTER stage.show()
     * so that scene graph is ready and focus listener works correctly
     */
    private void openExamWindow(int examId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/evalpro/views/student_exam.fxml"));
            Parent root = loader.load();

            StudentExamController controller = loader.getController();
            controller.initExam(examId);

            Stage stage = new Stage();
            stage.setTitle("EvalPro - Exam in Progress");
            stage.setScene(new Scene(root, 1200, 700));

            // Fullscreen with Esc disabled
            stage.setFullScreen(true);
            stage.setFullScreenExitHint("");
            stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);

            // Block window close — force submit
            stage.setOnCloseRequest(e -> {
                e.consume();
                Alert exitConfirm = new Alert(Alert.AlertType.CONFIRMATION);
                exitConfirm.setTitle("Exit Exam?");
                exitConfirm.setHeaderText("Are you sure you want to exit?");
                exitConfirm.setContentText("Your exam will be auto-submitted if you exit.");

                Optional<ButtonType> exitResult = exitConfirm.showAndWait();
                if (exitResult.isPresent() && exitResult.get() == ButtonType.OK) {
                    controller.handleSubmit();
                }
            });

            stage.show();

            // ✅ Attach AFTER show() — scene is ready now
            controller.attachTabSwitchListener(stage);

            // Close dashboard
            ((Stage) studentNameLabel.getScene().getWindow()).close();

        } catch (IOException e) {
            showError("Failed to open exam: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewAllExams() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/evalpro/views/student_exam_list.fxml"));
            Stage stage = (Stage) studentNameLabel.getScene().getWindow();
            stage.setScene(new Scene(root, 1100, 700));
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewResults() {
        showInfo("📊 Full results view will be available in M8 - Results Module!\n\nFor now, you can see recent results on the dashboard.");
    }

    @FXML
    private void handleNotifications() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Notifications");
        alert.setHeaderText("🔔 Recent Notifications");

        if (newNotifications > 0) {
            alert.setContentText("You have " + newNotifications + " new exam(s) available!\n\n" +
                    "📚 Click 'View All Exams' to see them.\n" +
                    "⏰ Don't forget to attempt them before the deadline!");
        } else {
            alert.setContentText("✅ No new notifications\n\nYou're all caught up! Check back later for new exams.");
        }

        alert.showAndWait();

        newNotifications = 0;
        notificationBadge.setVisible(false);
    }

    @FXML
    private void handleProfile() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Student Profile");
        alert.setHeaderText("👤 Your Profile Information");
        alert.setContentText(
                "Name: " + SessionManager.getCurrentUser().getFullName() + "\n" +
                        "Email: " + SessionManager.getCurrentUser().getEmail() + "\n" +
                        "Role: Student\n" +
                        "Student ID: " + studentId + "\n" +
                        "Roll No: " + String.format("2024%03d", studentId) + "\n\n" +
                        "Total Exams: " + totalExams + "\n" +
                        "Completed: " + completedExams + "\n" +
                        "Pending: " + pendingExams
        );
        alert.showAndWait();
    }

    @FXML
    private void handleSettings() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Settings");
        alert.setHeaderText("⚙️ Settings");
        alert.setContentText(
                "Settings panel features:\n\n" +
                        "✅ Change Password\n" +
                        "✅ Update Profile Picture\n" +
                        "✅ Email Notifications\n" +
                        "✅ Theme Preferences\n\n" +
                        "Coming in the next update!"
        );
        alert.showAndWait();
    }

    @FXML
    private void handleLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Logout");
        confirm.setHeaderText("Are you sure you want to logout?");
        confirm.setContentText("You'll need to login again to access your dashboard.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            SessionManager.setCurrentUser(null);
            loadLoginScreen();
        }
    }

    private void loadLoginScreen() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/evalpro/views/login.fxml"));
            Stage stage = (Stage) studentNameLabel.getScene().getWindow();
            stage.setScene(new Scene(root, 1100, 700));
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.showAndWait();
    }
}