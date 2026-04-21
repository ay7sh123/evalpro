package com.evalpro.controller.student;

import com.evalpro.database.DatabaseHelper;
import com.evalpro.database.ExamHelper;
import com.evalpro.database.ExamHelper.ExamInfo;
import com.evalpro.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.Optional;

public class StudentExamListController {

    @FXML private Label studentNameLabel;
    @FXML private Label examCountLabel;
    @FXML private Label statusLabel;

    @FXML private ComboBox<String> filterComboBox;
    @FXML private TableView<ExamDisplay> examsTable;

    @FXML private TableColumn<ExamDisplay, String> examIdColumn;
    @FXML private TableColumn<ExamDisplay, String> examTitleColumn;
    @FXML private TableColumn<ExamDisplay, String> subjectColumn;
    @FXML private TableColumn<ExamDisplay, String> durationColumn;
    @FXML private TableColumn<ExamDisplay, String> totalMarksColumn;
    @FXML private TableColumn<ExamDisplay, String> statusColumn;
    @FXML private TableColumn<ExamDisplay, String> scoreColumn;
    @FXML private TableColumn<ExamDisplay, Void> actionColumn;

    private ObservableList<ExamDisplay> examList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        loadStudentInfo();
        loadExams();
        setupFilter();
    }

    private void setupTable() {
        examIdColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().examId)));
        examTitleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().title));
        subjectColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().subject));
        durationColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().duration)));
        totalMarksColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().totalMarks)));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status));
        scoreColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().score));

        actionColumn.setCellFactory(param -> new TableCell<>() {
            private final Button actionButton = new Button();

            {
                actionButton.setOnAction(event -> {
                    ExamDisplay exam = getTableView().getItems().get(getIndex());
                    if (exam.status.equals("Not Attempted")) {
                        startExam(exam.examId);
                    } else {
                        viewResult(exam.examId);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ExamDisplay exam = getTableView().getItems().get(getIndex());
                    if (exam.status.equals("Not Attempted")) {
                        actionButton.setText("▶️ Start");
                        actionButton.getStyleClass().setAll("primary-button");
                    } else {
                        actionButton.setText("👁️ View");
                        actionButton.getStyleClass().setAll("secondary-button");
                    }
                    actionButton.setPrefWidth(100);
                    setGraphic(actionButton);
                }
            }
        });
    }

    private void setupFilter() {
        filterComboBox.setItems(FXCollections.observableArrayList(
                "All Exams", "Not Attempted", "Completed"
        ));
        filterComboBox.setValue("All Exams");
        filterComboBox.setOnAction(e -> applyFilter());
    }

    private void loadStudentInfo() {
        String studentName = SessionManager.getCurrentUser().getFullName();
        studentNameLabel.setText("Student: " + studentName);
    }

    private void loadExams() {
        examList.clear();
        List<ExamInfo> exams = ExamHelper.getAllExams();
        int studentId = SessionManager.getCurrentUser().getId();

        for (ExamInfo exam : exams) {
            // ✅ FIX: Only count 'completed' submissions, not 'in_progress'
            SubmissionInfo sub = getCompletedSubmission(exam.id, studentId);

            String status = (sub == null) ? "Not Attempted" : "Completed";
            String score = (sub == null) ? "-" : String.format("%.1f/%d", sub.score, exam.totalMarks);

            examList.add(new ExamDisplay(
                    exam.id,
                    exam.title,
                    exam.subject != null ? exam.subject : "General",
                    exam.duration,
                    exam.totalMarks,
                    status,
                    score
            ));
        }

        examsTable.setItems(examList);
        examCountLabel.setText("Total: " + examList.size() + " exams");
        statusLabel.setText("Loaded " + examList.size() + " exams");
    }

    /**
     * ✅ FIX: Get only COMPLETED submissions (status = 'completed')
     * so 'in_progress' entries don't show as Attempted
     */
    private SubmissionInfo getCompletedSubmission(int examId, int studentId) {
        String sql = "SELECT * FROM submissions WHERE exam_id = ? AND student_id = ? AND status = 'completed'";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, examId);
            pstmt.setInt(2, studentId);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                SubmissionInfo sub = new SubmissionInfo();
                sub.score = rs.getDouble("score");
                return sub;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error getting submission: " + e.getMessage());
        }
        return null;
    }

    private void applyFilter() {
        String filter = filterComboBox.getValue();
        if (filter == null || filter.equals("All Exams")) {
            examsTable.setItems(examList);
        } else {
            ObservableList<ExamDisplay> filtered = examList.filtered(e -> {
                if (filter.equals("Not Attempted")) return e.status.equals("Not Attempted");
                if (filter.equals("Completed")) return e.status.equals("Completed");
                return true;
            });
            examsTable.setItems(filtered);
        }
        examCountLabel.setText("Showing: " + examsTable.getItems().size() + " exams");
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
            stage.setMaximized(true);

            // Block close — force submit
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

            // ✅ Attach AFTER show() so scene is ready
            controller.attachTabSwitchListener(stage);

            // Close exam list window
            ((Stage) examsTable.getScene().getWindow()).close();

        } catch (IOException e) {
            showError("Failed to open exam: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void viewResult(int examId) {
        showInfo("Result viewing feature will be implemented in M8 (Results module)");
    }

    @FXML
    private void handleRefresh() {
        loadExams();
        statusLabel.setText("Refreshed");
    }

    @FXML
    private void handleBackToDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/evalpro/views/student_dashboard.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) examsTable.getScene().getWindow();
            stage.setScene(new Scene(root, 1100, 700));
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Error loading dashboard: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Logout");
        confirm.setHeaderText("Are you sure you want to logout?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            SessionManager.setCurrentUser(null);
            loadLoginScreen();
        }
    }

    private void loadLoginScreen() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/evalpro/views/login.fxml"));
            Stage stage = (Stage) examsTable.getScene().getWindow();
            stage.setScene(new Scene(root, 900, 650));
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class SubmissionInfo {
        double score;
    }

    public static class ExamDisplay {
        private final int examId;
        private final String title;
        private final String subject;
        private final int duration;
        private final int totalMarks;
        private final String status;
        private final String score;

        public ExamDisplay(int examId, String title, String subject, int duration,
                           int totalMarks, String status, String score) {
            this.examId = examId;
            this.title = title;
            this.subject = subject;
            this.duration = duration;
            this.totalMarks = totalMarks;
            this.status = status;
            this.score = score;
        }
    }
}