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

public class MyExamsController implements Initializable {

    @FXML private TableView<ExamData> examsTable;
    @FXML private TableColumn<ExamData, String> titleCol;
    @FXML private TableColumn<ExamData, String> durationCol;
    @FXML private TableColumn<ExamData, String> marksCol;
    @FXML private TableColumn<ExamData, Integer> questionsCol;
    @FXML private TableColumn<ExamData, String> statusCol;
    @FXML private TableColumn<ExamData, String> createdCol;
    @FXML private TableColumn<ExamData, Void> actionsCol;

    @FXML private Button allTab, activeTab, draftTab, closedTab;

    private String currentFilter = "all";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        loadExams("all");
    }

    private void setupTable() {
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        durationCol.setCellValueFactory(new PropertyValueFactory<>("duration"));
        marksCol.setCellValueFactory(new PropertyValueFactory<>("marks"));
        questionsCol.setCellValueFactory(new PropertyValueFactory<>("questionCount"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        createdCol.setCellValueFactory(new PropertyValueFactory<>("created"));

        // Actions column with buttons
        actionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button viewBtn = new Button("View/Edit");
            private final Button publishBtn = new Button("Publish");
            private final Button closeBtn = new Button("Close");
            private final Button deleteBtn = new Button("Delete");
            private final HBox actionBox = new HBox(5);

            {
                viewBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 5 10;");
                publishBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 5 10;");
                closeBtn.setStyle("-fx-background-color: #F59E0B; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 5 10;");
                deleteBtn.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 5 10;");

                actionBox.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ExamData exam = getTableView().getItems().get(getIndex());

                    actionBox.getChildren().clear();
                    actionBox.getChildren().add(viewBtn);

                    if (exam.getStatusRaw().equals("draft")) {
                        actionBox.getChildren().add(publishBtn);
                        publishBtn.setOnAction(e -> handlePublish(exam));
                    }

                    if (exam.getStatusRaw().equals("active")) {
                        actionBox.getChildren().add(closeBtn);
                        closeBtn.setOnAction(e -> handleClose(exam));
                    }

                    actionBox.getChildren().add(deleteBtn);

                    viewBtn.setOnAction(e -> handleViewExam(exam));
                    deleteBtn.setOnAction(e -> handleDelete(exam));

                    setGraphic(actionBox);
                }
            }
        });
    }

    private void loadExams(String filter) {
        if (SessionManager.getCurrentUser() == null) return;

        int teacherId = SessionManager.getCurrentUser().getId();
        ObservableList<ExamData> list = FXCollections.observableArrayList();

        try {
            Connection conn = DatabaseHelper.getConnection();
            String query = "SELECT id, title, duration_minutes, total_marks, status, created_at " +
                    "FROM exams WHERE teacher_id = ?";

            if (!filter.equals("all")) {
                query += " AND status = ?";
            }

            query += " ORDER BY created_at DESC";

            PreparedStatement ps = conn.prepareStatement(query);
            ps.setInt(1, teacherId);
            if (!filter.equals("all")) {
                ps.setString(2, filter);
            }

            ResultSet rs = ps.executeQuery();
            // loadExams() method mein ResultSet loop ke andar ye replace karo:
            while (rs.next()) {
                int examId = rs.getInt("id");

                // ✅ FIXED: Actually question count fetch karo
                int qCount = 0;
                try {
                    PreparedStatement psCount = conn.prepareStatement(
                            "SELECT COUNT(*) FROM exam_questions WHERE exam_id = ?");
                    psCount.setInt(1, examId);
                    ResultSet rsCount = psCount.executeQuery();
                    if (rsCount.next()) qCount = rsCount.getInt(1);
                } catch (Exception ex) {
                    System.err.println("Count error: " + ex.getMessage());
                }

                list.add(new ExamData(
                        examId,
                        rs.getString("title"),
                        rs.getInt("duration_minutes") + " min",
                        rs.getInt("total_marks") + " marks",
                        qCount, // ✅ Ab sahi count aayega
                        rs.getString("status"),
                        rs.getString("created_at").substring(0, 10)
                ));
            }
        } catch (Exception e) {
            System.err.println("Error loading exams: " + e.getMessage());
        }

        examsTable.setItems(list);
    }

    @FXML
    private void showAllExams() {
        updateTabStyles(allTab);
        loadExams("all");
    }

    @FXML
    private void showActiveExams() {
        updateTabStyles(activeTab);
        loadExams("active");
    }

    @FXML
    private void showDraftExams() {
        updateTabStyles(draftTab);
        loadExams("draft");
    }

    @FXML
    private void showClosedExams() {
        updateTabStyles(closedTab);
        loadExams("closed");
    }

    private void updateTabStyles(Button activeButton) {
        allTab.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #1A2744; -fx-padding: 8 16; -fx-background-radius: 6;");
        activeTab.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #1A2744; -fx-padding: 8 16; -fx-background-radius: 6;");
        draftTab.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #1A2744; -fx-padding: 8 16; -fx-background-radius: 6;");
        closedTab.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #1A2744; -fx-padding: 8 16; -fx-background-radius: 6;");

        activeButton.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 6;");
    }

    private void handleViewExam(ExamData exam) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/evalpro/views/view_exam.fxml"));
            Parent root = loader.load();

            ViewExamController controller = loader.getController();
            controller.loadExam(exam.getId());

            Stage stage = (Stage) examsTable.getScene().getWindow();
            stage.setScene(new Scene(root, 1024, 700));
            stage.setTitle("EvalPro - " + exam.getTitle());
            stage.show();
        } catch (Exception e) {
            showAlert("Error", "Could not load exam: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handlePublish(ExamData exam) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Publish Exam");
        confirm.setHeaderText("Publish \"" + exam.getTitle() + "\"?");
        confirm.setContentText("Students will be able to see and attempt this exam.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Connection conn = DatabaseHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE exams SET status = 'active' WHERE id = ?");
                ps.setInt(1, exam.getId());
                ps.executeUpdate();

                showAlert("Success", "Exam published successfully!");
                loadExams(currentFilter);
            } catch (Exception e) {
                showAlert("Error", "Failed to publish: " + e.getMessage());
            }
        }
    }

    private void handleClose(ExamData exam) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Close Exam");
        confirm.setHeaderText("Close \"" + exam.getTitle() + "\"?");
        confirm.setContentText("Students will no longer be able to attempt this exam.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Connection conn = DatabaseHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE exams SET status = 'closed' WHERE id = ?");
                ps.setInt(1, exam.getId());
                ps.executeUpdate();

                showAlert("Success", "Exam closed successfully!");
                loadExams(currentFilter);
            } catch (Exception e) {
                showAlert("Error", "Failed to close: " + e.getMessage());
            }
        }
    }

    private void handleDelete(ExamData exam) {
        Alert confirm = new Alert(Alert.AlertType.WARNING);
        confirm.setTitle("Delete Exam");
        confirm.setHeaderText("Permanently delete \"" + exam.getTitle() + "\"?");
        confirm.setContentText("⚠️ This cannot be undone!");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Connection conn = DatabaseHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM exams WHERE id = ?");
                ps.setInt(1, exam.getId());
                ps.executeUpdate();

                showAlert("Success", "Exam deleted!");
                loadExams(currentFilter);
            } catch (Exception e) {
                showAlert("Error", "Failed to delete: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(
                    getClass().getResource("/com/evalpro/views/teacher_dashboard.fxml"));
            Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 960, 660));
            stage.setTitle("EvalPro - Teacher Dashboard");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCreateExam(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(
                    getClass().getResource("/com/evalpro/views/create_exam.fxml"));
            Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1024, 700));
            stage.setTitle("EvalPro - Create Exam");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public static class ExamData {
        private int id;
        private String title, duration, marks, status, created;
        private int questionCount;

        public ExamData(int id, String title, String duration, String marks,
                        int questionCount, String status, String created) {
            this.id = id;
            this.title = title;
            this.duration = duration;
            this.marks = marks;
            this.questionCount = questionCount;
            this.status = status;
            this.created = created;
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getDuration() { return duration; }
        public String getMarks() { return marks; }
        public int getQuestionCount() { return questionCount; }
        public String getStatus() { return status.toUpperCase(); }
        public String getStatusRaw() { return status; }
        public String getCreated() { return created; }
    }
}