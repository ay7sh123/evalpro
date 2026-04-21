package com.evalpro.controller.teacher;

import com.evalpro.database.DatabaseHelper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ViewExamController {

    @FXML private Label examTitleLabel;
    @FXML private Label examStatusLabel;
    @FXML private Label durationLabel;
    @FXML private Label totalMarksLabel;
    @FXML private Label questionCountLabel;
    @FXML private Label shuffleLabel;
    @FXML private Label questionsInfoLabel;
    @FXML private TextArea descriptionArea;
    @FXML private VBox questionsContainer;
    @FXML private Button addQuestionBtn;
    @FXML private Button publishBtn;

    private int currentExamId;
    private List<QuestionCard> questionCards = new ArrayList<>();

    // ══════════════════════════════════════════════════════════
    //  LOAD EXAM
    // ══════════════════════════════════════════════════════════

    public void loadExam(int examId) {
        this.currentExamId = examId;
        try {
            Connection conn = DatabaseHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM exams WHERE id = ?");
            ps.setInt(1, examId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                examTitleLabel.setText(rs.getString("title"));
                durationLabel.setText(rs.getInt("duration_minutes") + " min");
                totalMarksLabel.setText(String.valueOf(rs.getInt("total_marks")));
                descriptionArea.setText(rs.getString("description"));
                shuffleLabel.setText(rs.getInt("shuffle_questions") == 1 ? "Enabled" : "Disabled");

                String status = rs.getString("status");
                updateStatusLabel(status);

                if (status.equals("draft")) {
                    publishBtn.setVisible(true);
                }
            }
            loadQuestions();

        } catch (Exception e) {
            showAlert("Error", "Failed to load exam: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateStatusLabel(String status) {
        examStatusLabel.setText(status.toUpperCase());
        switch (status) {
            case "draft":
                examStatusLabel.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-padding: 6 16; -fx-background-radius: 20; -fx-font-weight: bold; -fx-font-size: 12px;");
                break;
            case "active":
                examStatusLabel.setStyle("-fx-background-color: #D1FAE5; -fx-text-fill: #065F46; -fx-padding: 6 16; -fx-background-radius: 20; -fx-font-weight: bold; -fx-font-size: 12px;");
                break;
            case "closed":
                examStatusLabel.setStyle("-fx-background-color: #E5E7EB; -fx-text-fill: #374151; -fx-padding: 6 16; -fx-background-radius: 20; -fx-font-weight: bold; -fx-font-size: 12px;");
                break;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  LOAD QUESTIONS
    // ══════════════════════════════════════════════════════════

    private void loadQuestions() {
        questionsContainer.getChildren().clear();
        questionCards.clear();

        try {
            Connection conn = DatabaseHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM exam_questions WHERE exam_id = ?");
            ps.setInt(1, currentExamId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int count = rs.getInt(1);
                questionCountLabel.setText(String.valueOf(count));

                if (count == 0) {
                    Label placeholder = new Label("No questions added yet.\n\nClick '+ Add Question' to start building your exam.");
                    placeholder.setStyle("-fx-text-fill: #64748B; -fx-font-size: 14px; -fx-padding: 40; -fx-alignment: center;");
                    placeholder.setWrapText(true);
                    placeholder.setMaxWidth(Double.MAX_VALUE);
                    questionsContainer.getChildren().add(placeholder);
                    questionsInfoLabel.setText("No questions added yet");
                } else {
                    questionsInfoLabel.setText(count + " question" + (count == 1 ? "" : "s"));
                    loadActualQuestions();
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading questions: " + e.getMessage());
        }
    }

    private void loadActualQuestions() {
        try {
            Connection conn = DatabaseHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT q.id, q.type, q.question_text, eq.marks " +
                            "FROM question_bank q " +
                            "JOIN exam_questions eq ON q.id = eq.question_id " +
                            "WHERE eq.exam_id = ? " +
                            "ORDER BY eq.question_order");
            ps.setInt(1, currentExamId);
            ResultSet rs = ps.executeQuery();

            int index = 1;
            boolean found = false;

            while (rs.next()) {
                found = true;
                int qId      = rs.getInt("id");
                String type  = rs.getString("type");
                String qText = rs.getString("question_text");
                int marks    = rs.getInt("marks");

                // Fetch MCQ options from DB
                List<String[]> options = new ArrayList<>();
                if ("MCQ".equals(type)) {
                    PreparedStatement psOpt = conn.prepareStatement(
                            "SELECT option_text, is_correct FROM mcq_options WHERE question_id = ? ORDER BY rowid");
                    psOpt.setInt(1, qId);
                    ResultSet rsOpt = psOpt.executeQuery();
                    while (rsOpt.next()) {
                        options.add(new String[]{
                                rsOpt.getString("option_text"),
                                String.valueOf(rsOpt.getInt("is_correct"))
                        });
                    }
                }

                QuestionCard card = createQuestionCard(index++, qId, type, qText, marks, options);
                questionCards.add(card);
                questionsContainer.getChildren().add(card.getCard());
            }

            if (!found) {
                Label placeholder = new Label("No questions found in database.");
                placeholder.setStyle("-fx-text-fill: #64748B; -fx-font-size: 14px; -fx-padding: 20;");
                questionsContainer.getChildren().add(placeholder);
            }

        } catch (Exception e) {
            System.err.println("Error loading actual questions: " + e.getMessage());
            e.printStackTrace();
            Label errLabel = new Label("Error loading questions: " + e.getMessage());
            errLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 13px; -fx-padding: 20;");
            questionsContainer.getChildren().add(errLabel);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CREATE QUESTION CARD  (FULLY FIXED)
    // ══════════════════════════════════════════════════════════

    private QuestionCard createQuestionCard(int index, int questionId, String type,
                                            String questionText, int marks, List<String[]> options) {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 10; -fx-border-color: #E2E8F0; -fx-border-radius: 10; -fx-border-width: 1;");

        // ── Header ──────────────────────────────────────────
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label indexLabel = new Label("Q" + index);
        indexLabel.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-padding: 5 12; -fx-background-radius: 15; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label typeLabel = new Label(getTypeIcon(type) + " " + type);
        typeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label marksLabel = new Label("Marks:");
        marksLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        Spinner<Integer> marksSpinner = new Spinner<>(1, 50, marks);
        marksSpinner.setPrefWidth(80);
        marksSpinner.setEditable(true);

        Button deleteBtn = new Button("Delete");
        deleteBtn.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #EF4444; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 5 12; -fx-background-radius: 6;");
        deleteBtn.setOnAction(e -> handleDeleteQuestion(questionId, card));

        header.getChildren().addAll(indexLabel, typeLabel, spacer, marksLabel, marksSpinner, deleteBtn);

        // ── Question Text ────────────────────────────────────
        Label questionLabel = new Label(questionText);
        questionLabel.setWrapText(true);
        questionLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #1A2744;");

        // ── Type-specific content ────────────────────────────
        VBox typeContent = new VBox(8);

        if ("MCQ".equals(type)) {
            String[] labels = {"A", "B", "C", "D"};
            String correctLabel = "A";

            if (!options.isEmpty()) {
                for (int i = 0; i < options.size(); i++) {
                    String optText  = options.get(i)[0];
                    boolean correct = options.get(i)[1].equals("1");
                    String lbl      = i < labels.length ? labels[i] : String.valueOf(i + 1);
                    typeContent.getChildren().add(createOptionLabel(lbl + ") " + optText));
                    if (correct) correctLabel = lbl;
                }
            } else {
                typeContent.getChildren().add(createOptionLabel("A) Option A"));
                typeContent.getChildren().add(createOptionLabel("B) Option B"));
                typeContent.getChildren().add(createOptionLabel("C) Option C"));
                typeContent.getChildren().add(createOptionLabel("D) Option D"));
            }

            Label corrLbl = new Label("Correct Answer: " + correctLabel);
            corrLbl.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold; -fx-font-size: 12px;");
            typeContent.getChildren().add(corrLbl);

        } else if ("Theory".equals(type)) {
            Label modelLabel = new Label("Model Answer:");
            modelLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #64748B;");

            // Fetch model answer from DB
            String modelAnswerText = "";
            try {
                Connection conn2 = DatabaseHelper.getConnection();
                PreparedStatement psMA = conn2.prepareStatement(
                        "SELECT model_answer FROM question_bank WHERE id = ?");
                psMA.setInt(1, questionId);
                ResultSet rsMA = psMA.executeQuery();
                if (rsMA.next() && rsMA.getString("model_answer") != null) {
                    modelAnswerText = rsMA.getString("model_answer");
                }
            } catch (Exception ignored) {}

            TextArea modelArea = new TextArea(modelAnswerText.isEmpty() ? "No model answer saved." : modelAnswerText);
            modelArea.setPrefRowCount(2);
            modelArea.setWrapText(true);
            modelArea.setEditable(false);
            modelArea.setStyle("-fx-background-color: #F9FAFB;");

            typeContent.getChildren().addAll(modelLabel, modelArea);

        } else if ("Coding".equals(type)) {
            // Fetch coding details from DB
            String inputFmt = "—", outputFmt = "—";
            int testCaseCount = 0;
            try {
                Connection conn2 = DatabaseHelper.getConnection();
                PreparedStatement psCQ = conn2.prepareStatement(
                        "SELECT input_format, output_format FROM coding_questions WHERE question_id = ?");
                psCQ.setInt(1, questionId);
                ResultSet rsCQ = psCQ.executeQuery();
                if (rsCQ.next()) {
                    inputFmt  = rsCQ.getString("input_format") != null ? rsCQ.getString("input_format") : "—";
                    outputFmt = rsCQ.getString("output_format") != null ? rsCQ.getString("output_format") : "—";
                }

                PreparedStatement psTCCount = conn2.prepareStatement(
                        "SELECT COUNT(*) FROM test_cases WHERE question_id = ?");
                psTCCount.setInt(1, questionId);
                ResultSet rsTCCount = psTCCount.executeQuery();
                if (rsTCCount.next()) testCaseCount = rsTCCount.getInt(1);

            } catch (Exception ignored) {}

            Label inputLabel  = new Label("Input: " + inputFmt);
            inputLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
            Label outputLabel = new Label("Output: " + outputFmt);
            outputLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
            Label testLabel   = new Label("Test Cases: " + testCaseCount + " defined");
            testLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #10B981; -fx-font-weight: bold;");

            typeContent.getChildren().addAll(inputLabel, outputLabel, testLabel);
        }

        card.getChildren().addAll(header, questionLabel, typeContent);
        return new QuestionCard(questionId, card, marksSpinner);
    }

    // ── Helpers ──────────────────────────────────────────────

    private Label createOptionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151; -fx-padding: 4 0;");
        return label;
    }

    private String getTypeIcon(String type) {
        switch (type) {
            case "MCQ":    return "MCQ";
            case "Theory": return "Theory";
            case "Coding": return "Coding";
            default:       return "?";
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ACTIONS
    // ══════════════════════════════════════════════════════════

    private void handleDeleteQuestion(int questionId, VBox card) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Question");
        confirm.setHeaderText("Delete this question?");
        confirm.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Connection conn = DatabaseHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM exam_questions WHERE question_id = ? AND exam_id = ?");
                ps.setInt(1, questionId);
                ps.setInt(2, currentExamId);
                ps.executeUpdate();

                questionsContainer.getChildren().remove(card);
                loadQuestions();
                showAlert("Success", "Question deleted successfully!");
            } catch (Exception e) {
                showAlert("Error", "Failed to delete question: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleAddQuestion() {
        showAlert("Add Question", "This will open question creation dialog.\n\nComing in next update!");
    }

    @FXML
    private void handlePublishExam() {
        if (questionCards.isEmpty()) {
            showAlert("Cannot Publish", "Please add at least one question before publishing.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Publish Exam");
        confirm.setHeaderText("Publish this exam?");
        confirm.setContentText("Students will be able to see and attempt this exam.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Connection conn = DatabaseHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE exams SET status = 'active' WHERE id = ?");
                ps.setInt(1, currentExamId);
                ps.executeUpdate();

                updateStatusLabel("active");
                publishBtn.setVisible(false);
                showAlert("Success", "Exam published successfully!");
            } catch (Exception e) {
                showAlert("Error", "Failed to publish: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleSaveChanges() {
        try {
            Connection conn = DatabaseHelper.getConnection();
            for (QuestionCard qc : questionCards) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE exam_questions SET marks = ? WHERE question_id = ? AND exam_id = ?");
                ps.setInt(1, qc.getMarks());
                ps.setInt(2, qc.getQuestionId());
                ps.setInt(3, currentExamId);
                ps.executeUpdate();
            }
            showAlert("Success", "Changes saved successfully!");
        } catch (Exception e) {
            showAlert("Error", "Failed to save changes: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteExam() {
        Alert confirm = new Alert(Alert.AlertType.WARNING);
        confirm.setTitle("Delete Exam");
        confirm.setHeaderText("Permanently delete this exam?");
        confirm.setContentText("This action cannot be undone!\n\nAll questions and submissions will be deleted.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Connection conn = DatabaseHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM exams WHERE id = ?");
                ps.setInt(1, currentExamId);
                ps.executeUpdate();

                showAlert("Deleted", "Exam deleted successfully!");
                handleBack(null);
            } catch (Exception e) {
                showAlert("Error", "Failed to delete exam: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(
                    getClass().getResource("/com/evalpro/views/my_exams.fxml"));
            Stage stage;
            if (event != null) {
                stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            } else {
                stage = (Stage) examTitleLabel.getScene().getWindow();
            }
            stage.setScene(new Scene(root, 1024, 700));
            stage.setTitle("EvalPro - My Exams");
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

    // ══════════════════════════════════════════════════════════
    //  INNER CLASS
    // ══════════════════════════════════════════════════════════

    private static class QuestionCard {
        private final int questionId;
        private final VBox card;
        private final Spinner<Integer> marksSpinner;

        public QuestionCard(int questionId, VBox card, Spinner<Integer> marksSpinner) {
            this.questionId   = questionId;
            this.card         = card;
            this.marksSpinner = marksSpinner;
        }

        public int  getQuestionId() { return questionId; }
        public VBox getCard()       { return card; }
        public int  getMarks()      { return marksSpinner.getValue(); }
    }
}