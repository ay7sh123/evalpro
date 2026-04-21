package com.evalpro.controller.teacher;

import com.evalpro.database.DatabaseHelper;
import com.evalpro.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.*;
import java.util.Optional;
import java.util.ResourceBundle;

public class TeacherDashboardController implements Initializable {

    @FXML private Label teacherInitials;
    @FXML private Label teacherName;
    @FXML private Label welcomeLabel;
    @FXML private Label totalExamsLabel;
    @FXML private Label activeExamsLabel;
    @FXML private Label totalStudentsLabel;
    @FXML private Label totalQuestionsLabel;

    @FXML private TableView<ExamRow> recentExamsTable;
    @FXML private TableColumn<ExamRow, String> examTitleCol;
    @FXML private TableColumn<ExamRow, String> examDurationCol;
    @FXML private TableColumn<ExamRow, String> examMarksCol;
    @FXML private TableColumn<ExamRow, String> examStatusCol;
    @FXML private TableColumn<ExamRow, String> examDateCol;
    @FXML private TableColumn<ExamRow, Void> examActionsCol;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        loadTeacherInfo();
        loadStats();
        loadRecentExams();
    }

    private boolean checkUser() {
        if (SessionManager.getCurrentUser() == null) {
            showInfo("Error", "User not logged in.");
            return false;
        }
        return true;
    }

    private void loadTeacherInfo() {
        if (!checkUser()) return;
        String name = SessionManager.getCurrentUser().getFullName();
        teacherName.setText(name);
        welcomeLabel.setText("Welcome back, " + name + "!");
        teacherInitials.setText(
                String.valueOf(name.charAt(0)).toUpperCase()
        );
    }

    private void loadStats() {
        if (!checkUser()) return;
        int teacherId = SessionManager.getCurrentUser().getId();
        try {
            Connection conn = DatabaseHelper.getConnection();

            PreparedStatement s1 = conn.prepareStatement(
                    "SELECT COUNT(*) FROM exams WHERE teacher_id = ?");
            s1.setInt(1, teacherId);
            ResultSet r1 = s1.executeQuery();
            if (r1.next()) totalExamsLabel.setText(
                    String.valueOf(r1.getInt(1)));

            PreparedStatement s2 = conn.prepareStatement(
                    "SELECT COUNT(*) FROM exams WHERE teacher_id = ? AND status = 'active'");
            s2.setInt(1, teacherId);
            ResultSet r2 = s2.executeQuery();
            if (r2.next()) activeExamsLabel.setText(
                    String.valueOf(r2.getInt(1)));

            // Fix: Show actual student count
            PreparedStatement s3 = conn.prepareStatement(
                    "SELECT COUNT(*) FROM users WHERE role = 'student'");
            ResultSet r3 = s3.executeQuery();
            if (r3.next()) {
                totalStudentsLabel.setText(String.valueOf(r3.getInt(1)));
            } else {
                totalStudentsLabel.setText("0");
            }

            PreparedStatement s4 = conn.prepareStatement(
                    "SELECT COUNT(*) FROM question_bank WHERE teacher_id = ?");
            s4.setInt(1, teacherId);
            ResultSet r4 = s4.executeQuery();
            if (r4.next()) totalQuestionsLabel.setText(
                    String.valueOf(r4.getInt(1)));

        } catch (Exception e) {
            System.out.println("Stats error: " + e.getMessage());
        }
    }

    private void setupTable() {
        examTitleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        examDurationCol.setCellValueFactory(new PropertyValueFactory<>("duration"));
        examMarksCol.setCellValueFactory(new PropertyValueFactory<>("marks"));
        examStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        examDateCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        // Add Actions Column
        examActionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            private final Button publishBtn = new Button("Publish");
            private final Button closeBtn = new Button("Close");
            private final Button deleteBtn = new Button("Delete");
            private final HBox actionBox = new HBox(5);

            {
                viewBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-font-size: 10px; -fx-cursor: hand;");
                publishBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-size: 10px; -fx-cursor: hand;");
                closeBtn.setStyle("-fx-background-color: #F59E0B; -fx-text-fill: white; -fx-font-size: 10px; -fx-cursor: hand;");
                deleteBtn.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-font-size: 10px; -fx-cursor: hand;");

                actionBox.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ExamRow exam = getTableView().getItems().get(getIndex());

                    actionBox.getChildren().clear();
                    actionBox.getChildren().add(viewBtn);

                    // Show Publish button only for draft exams
                    if (exam.getStatusRaw().equals("draft")) {
                        actionBox.getChildren().add(publishBtn);
                        publishBtn.setOnAction(e -> handlePublishExam(exam));
                    }

                    // Show Close button only for active exams
                    if (exam.getStatusRaw().equals("active")) {
                        actionBox.getChildren().add(closeBtn);
                        closeBtn.setOnAction(e -> handleCloseExam(exam));
                    }

                    actionBox.getChildren().add(deleteBtn);

                    viewBtn.setOnAction(e -> handleViewExam(exam));
                    deleteBtn.setOnAction(e -> handleDeleteExam(exam));

                    setGraphic(actionBox);
                }
            }
        });
    }

    private void loadRecentExams() {
        if (!checkUser()) return;
        int teacherId = SessionManager.getCurrentUser().getId();
        ObservableList<ExamRow> list = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, title, duration_minutes, total_marks, status, created_at " +
                            "FROM exams WHERE teacher_id = ? " +
                            "ORDER BY created_at DESC LIMIT 10");
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new ExamRow(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getInt("duration_minutes") + " min",
                        rs.getInt("total_marks") + " marks",
                        rs.getString("status"),
                        rs.getString("created_at").substring(0, 10)
                ));
            }
        } catch (Exception e) {
            System.out.println("Table error: " + e.getMessage());
        }
        recentExamsTable.setItems(list);
    }

    // ══════════════════════════════════════════════════════════
    //  EXAM ACTIONS
    // ══════════════════════════════════════════════════════════

    private void handleViewExam(ExamRow exam) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/evalpro/views/view_exam.fxml"));
            Parent root = loader.load();

            // Pass exam ID to next controller
            // ViewExamController controller = loader.getController();
            // controller.loadExam(exam.getId());

            Stage stage = (Stage) recentExamsTable.getScene().getWindow();
            stage.setScene(new Scene(root, 1024, 700));
            stage.setTitle("EvalPro - View Exam: " + exam.getTitle());
            stage.show();
        } catch (Exception e) {
            showInfo("Coming Soon", "View Exam screen will be created in next step!\n\nExam: " + exam.getTitle());
        }
    }

    private void handlePublishExam(ExamRow exam) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Publish Exam");
        confirm.setHeaderText("Publish \"" + exam.getTitle() + "\"?");
        confirm.setContentText(
                "Once published, this exam will be visible to students.\n\n" +
                        "Are you sure you want to publish this exam?"
        );

        ButtonType yesBtn = new ButtonType("Yes, Publish");
        ButtonType noBtn = new ButtonType("No, Go Back", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(yesBtn, noBtn);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == yesBtn) {
            try {
                Connection conn = DatabaseHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE exams SET status = 'active' WHERE id = ?");
                ps.setInt(1, exam.getId());
                ps.executeUpdate();

                showInfo("Success", "Exam \"" + exam.getTitle() + "\" has been published!\n\n" +
                        "Students can now see and attempt this exam.");

                loadStats();
                loadRecentExams();
            } catch (Exception e) {
                showInfo("Error", "Failed to publish exam: " + e.getMessage());
            }
        }
    }

    private void handleCloseExam(ExamRow exam) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Close Exam");
        confirm.setHeaderText("Close \"" + exam.getTitle() + "\"?");
        confirm.setContentText(
                "This will prevent students from attempting the exam.\n\n" +
                        "Are you sure you want to close this exam?"
        );

        ButtonType yesBtn = new ButtonType("Yes, Close Exam");
        ButtonType noBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(yesBtn, noBtn);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == yesBtn) {
            try {
                Connection conn = DatabaseHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE exams SET status = 'closed' WHERE id = ?");
                ps.setInt(1, exam.getId());
                ps.executeUpdate();

                showInfo("Success", "Exam \"" + exam.getTitle() + "\" has been closed.");

                loadStats();
                loadRecentExams();
            } catch (Exception e) {
                showInfo("Error", "Failed to close exam: " + e.getMessage());
            }
        }
    }

    private void handleDeleteExam(ExamRow exam) {
        Alert confirm = new Alert(Alert.AlertType.WARNING);
        confirm.setTitle("Delete Exam");
        confirm.setHeaderText("Permanently Delete \"" + exam.getTitle() + "\"?");
        confirm.setContentText(
                "⚠️ This action cannot be undone!\n\n" +
                        "All questions and student submissions will be deleted."
        );

        ButtonType deleteBtn = new ButtonType("Delete Permanently", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(deleteBtn, cancelBtn);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == deleteBtn) {
            try {
                Connection conn = DatabaseHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM exams WHERE id = ?");
                ps.setInt(1, exam.getId());
                ps.executeUpdate();

                showInfo("Deleted", "Exam \"" + exam.getTitle() + "\" has been deleted.");

                loadStats();
                loadRecentExams();
            } catch (Exception e) {
                showInfo("Error", "Failed to delete exam: " + e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  NAVIGATION
    // ══════════════════════════════════════════════════════════

    @FXML
    private void showDashboard() {
        loadStats();
        loadRecentExams();
    }

    @FXML
    private void showCreateExamPopup(ActionEvent event) {
        navigateToCreateExam(event);
    }

    private void navigateToCreateExam(ActionEvent event) {
        try {
            System.out.println("🔄 Loading Create Exam page...");

            Parent root = FXMLLoader.load(
                    getClass().getResource("/com/evalpro/views/create_exam.fxml"));

            Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1024, 700));
            stage.setTitle("EvalPro - Create Exam");
            stage.show();

            System.out.println("✅ Create Exam page loaded");

        } catch (Exception e) {
            System.err.println("❌ Error loading Create Exam:");
            e.printStackTrace();
            showInfo("Error", "Could not load Create Exam page: " + e.getMessage());
        }
    }

    @FXML
    private void showMyExams() {
        try {
            // Navigate to My Exams full page
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/evalpro/views/my_exams.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) teacherName.getScene().getWindow();
            stage.setScene(new Scene(root, 1024, 700));
            stage.setTitle("EvalPro - My Exams");
            stage.show();
        } catch (Exception e) {
            // Fallback: refresh current view
            loadStats();
            loadRecentExams();
            showInfo("My Exams",
                    "All your exams are shown in the Recent Exams table.\n\n" +
                            "Full 'My Exams' page coming soon!");
        }
    }

    @FXML
    private void showQuestionBank() {
        showInfo("Question Bank", "Question Bank coming in Module 4!");
    }

    @FXML
    private void showResults() {
        showInfo("Results", "Results panel coming in Module 8!");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            System.out.println("🔄 Logging out...");

            SessionManager.logout();

            Parent root = FXMLLoader.load(
                    getClass().getResource("/com/evalpro/views/landing.fxml"));

            Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 650));
            stage.setTitle("EvalPro");
            stage.show();

            System.out.println("✅ Logged out");

        } catch (Exception e) {
            System.err.println("❌ Logout error:");
            e.printStackTrace();
        }
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // ══════════════════════════════════════════════════════════
    //  EXAM ROW MODEL
    // ══════════════════════════════════════════════════════════

    public static class ExamRow {
        private int id;
        private String title, duration, marks, status, createdAt;

        public ExamRow(int id, String t, String d, String m, String s, String c) {
            this.id = id;
            title = t;
            duration = d;
            marks = m;
            status = s;
            createdAt = c;
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getDuration() { return duration; }
        public String getMarks() { return marks; }
        public String getStatus() { return status.toUpperCase(); }
        public String getStatusRaw() { return status; }
        public String getCreatedAt() { return createdAt; }
    }
}