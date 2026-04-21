package com.evalpro.controller.teacher;

import com.evalpro.database.DatabaseHelper;
import com.evalpro.util.SessionManager;
import com.evalpro.service.GeminiAIService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class CreateExamController implements Initializable {

    @FXML private TextField titleField;
    @FXML private TextField durationField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea descriptionField;
    @FXML private CheckBox shuffleCheck;

    // AI Fields
    @FXML private TextField topicField;
    @FXML private TextField urlField;
    @FXML private HBox aiStatusBox;
    @FXML private ProgressIndicator aiProgress;
    @FXML private Label aiStatusLabel;

    // Questions
    @FXML private VBox questionsContainer;
    @FXML private Label totalQuestionsLabel;
    @FXML private Label totalMarksLabel;
    @FXML private Label durationDisplayLabel;
    @FXML private Label questionCountLabel;

    private List<QuestionData> questions = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        statusBox.getItems().addAll("draft", "active");
        statusBox.setValue("draft");

        durationField.textProperty().addListener((obs, old, newVal) -> {
            if (!newVal.isEmpty() && newVal.matches("\\d+")) {
                durationDisplayLabel.setText(newVal + " min");
            } else {
                durationDisplayLabel.setText("-- min");
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    //  AI QUESTION GENERATION WITH CUSTOMIZATION
    // ══════════════════════════════════════════════════════════

    @FXML
    private void handleGenerateFromTopic() {
        String topic = topicField.getText().trim();
        if (topic.isEmpty()) {
            showAlert("Error", "Please enter a topic name.");
            return;
        }

        showAIGenerationDialog(topic, false);
    }

    @FXML
    private void handleGenerateFromURL() {
        String url = urlField.getText().trim();
        if (url.isEmpty() || !url.startsWith("http")) {
            showAlert("Error", "Please enter a valid URL.");
            return;
        }

        showAIGenerationDialog(url, true);
    }

    private void showAIGenerationDialog(String input, boolean isURL) {
        Dialog<AIGenerationConfig> dialog = new Dialog<>();
        dialog.setTitle("Customize AI Generation");
        dialog.setHeaderText(isURL ?
                "AI will read webpage:\n" + input :
                "Generate questions on topic: " + input);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white;");

        Label info = new Label("How many questions do you want to generate?");
        info.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // MCQ count
        HBox mcqBox = new HBox(10);
        mcqBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label mcqLabel = new Label("MCQ Questions:");
        mcqLabel.setPrefWidth(150);
        mcqLabel.setStyle("-fx-font-size: 13px;");
        Spinner<Integer> mcqSpinner = new Spinner<>(0, 20, 5);
        mcqSpinner.setPrefWidth(80);
        Label mcqInfo = new Label("(with 4 options each)");
        mcqInfo.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");
        mcqBox.getChildren().addAll(mcqLabel, mcqSpinner, mcqInfo);

        // Theory count
        HBox theoryBox = new HBox(10);
        theoryBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label theoryLabel = new Label("Theory Questions:");
        theoryLabel.setPrefWidth(150);
        theoryLabel.setStyle("-fx-font-size: 13px;");
        Spinner<Integer> theorySpinner = new Spinner<>(0, 10, 2);
        theorySpinner.setPrefWidth(80);
        Label theoryInfo = new Label("(with model answers)");
        theoryInfo.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");
        theoryBox.getChildren().addAll(theoryLabel, theorySpinner, theoryInfo);

        // Coding count
        HBox codingBox = new HBox(10);
        codingBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label codingLabel = new Label("Coding Questions:");
        codingLabel.setPrefWidth(150);
        codingLabel.setStyle("-fx-font-size: 13px;");
        Spinner<Integer> codingSpinner = new Spinner<>(0, 5, 1);
        codingSpinner.setPrefWidth(80);
        Label codingInfo = new Label("(with test cases)");
        codingInfo.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");
        codingBox.getChildren().addAll(codingLabel, codingSpinner, codingInfo);

        content.getChildren().addAll(info, mcqBox, theoryBox, codingBox);
        dialog.getDialogPane().setContent(content);

        ButtonType generateBtn = new ButtonType("Generate Questions", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(generateBtn, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == generateBtn) {
                return new AIGenerationConfig(
                        mcqSpinner.getValue(),
                        theorySpinner.getValue(),
                        codingSpinner.getValue()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(config -> {
            if (config.getTotalCount() == 0) {
                showAlert("Error", "Please select at least one question type.");
                return;
            }

            generateQuestionsWithAI(input, isURL, config);
        });
    }

    private void generateQuestionsWithAI(String input, boolean isURL, AIGenerationConfig config) {
        showAIStatus("AI is generating " + config.getTotalCount() + " questions...");

        Task<List<QuestionData>> task = new Task<List<QuestionData>>() {
            @Override
            protected List<QuestionData> call() throws Exception {
                if (isURL) {
                    return GeminiAIService.generateQuestionsFromURL(
                            input,
                            config.getMcqCount(),
                            config.getTheoryCount(),
                            config.getCodingCount()
                    );
                } else {
                    return GeminiAIService.generateQuestionsFromTopic(
                            input,
                            config.getMcqCount(),
                            config.getTheoryCount(),
                            config.getCodingCount()
                    );
                }
            }
        };

        task.setOnSucceeded(e -> {
            List<QuestionData> generated = task.getValue();
            addQuestionsToList(generated);
            hideAIStatus();
            showAlert("Success",
                    "Generated " + generated.size() + " complete questions!\n\n" +
                            "MCQ: " + config.getMcqCount() + " (with options)\n" +
                            "Theory: " + config.getTheoryCount() + " (with model answers)\n" +
                            "Coding: " + config.getCodingCount() + " (with test cases)");

            if (isURL) urlField.clear();
            else topicField.clear();
        });

        task.setOnFailed(e -> {
            hideAIStatus();
            showAlert("Error", "AI generation failed: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void showAIStatus(String message) {
        Platform.runLater(() -> {
            aiStatusLabel.setText(message);
            aiStatusBox.setVisible(true);
            aiProgress.setVisible(true);
        });
    }

    private void hideAIStatus() {
        Platform.runLater(() -> {
            aiStatusBox.setVisible(false);
        });
    }

    // ══════════════════════════════════════════════════════════
    //  MANUAL QUESTION ADDING
    // ══════════════════════════════════════════════════════════

    @FXML
    private void handleAddMCQ() {
        showMCQDialog();
    }

    @FXML
    private void handleAddTheory() {
        showTheoryDialog();
    }

    @FXML
    private void handleAddCoding() {
        showCodingDialog();
    }

    @FXML
    private void handlePickFromBank() {
        showAlert("Module 4", "Question Bank picker coming in Module 4!");
    }

    private void showMCQDialog() {
        Dialog<QuestionData> dialog = new Dialog<>();
        dialog.setTitle("Add MCQ Question");
        dialog.setHeaderText("Create a Multiple Choice Question");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);

        Label qLabel = new Label("Question:");
        qLabel.setStyle("-fx-font-weight: bold;");
        TextArea questionField = new TextArea();
        questionField.setPromptText("Enter your question here...");
        questionField.setPrefRowCount(2);
        questionField.setWrapText(true);

        Label optLabel = new Label("Options:");
        optLabel.setStyle("-fx-font-weight: bold;");

        TextField optA = new TextField();
        optA.setPromptText("Option A");
        TextField optB = new TextField();
        optB.setPromptText("Option B");
        TextField optC = new TextField();
        optC.setPromptText("Option C");
        TextField optD = new TextField();
        optD.setPromptText("Option D");

        Label corrLabel = new Label("Correct Answer:");
        corrLabel.setStyle("-fx-font-weight: bold;");
        ComboBox<String> correctBox = new ComboBox<>();
        correctBox.getItems().addAll("A", "B", "C", "D");
        correctBox.setValue("A");

        Label marksLabel = new Label("Marks:");
        marksLabel.setStyle("-fx-font-weight: bold;");
        Spinner<Integer> marksSpinner = new Spinner<>(1, 20, 2);

        content.getChildren().addAll(
                qLabel, questionField,
                optLabel, optA, optB, optC, optD,
                corrLabel, correctBox,
                marksLabel, marksSpinner
        );

        dialog.getDialogPane().setContent(content);
        ButtonType addBtn = new ButtonType("Add Question", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == addBtn) {
                if (questionField.getText().trim().isEmpty() ||
                        optA.getText().trim().isEmpty() ||
                        optB.getText().trim().isEmpty()) {
                    showAlert("Error", "Please fill question and at least 2 options.");
                    return null;
                }

                return new QuestionData(
                        questionField.getText().trim(),
                        optA.getText().trim(),
                        optB.getText().trim(),
                        optC.getText().trim(),
                        optD.getText().trim(),
                        correctBox.getValue(),
                        marksSpinner.getValue()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(q -> {
            if (q == null) return;
            if (!questionsContainer.getChildren().isEmpty() &&
                    questionsContainer.getChildren().get(0) instanceof Label) {
                questionsContainer.getChildren().clear();
            }
            questions.add(q);
            questionsContainer.getChildren().add(createQuestionCard(q));
            updateSummary();
        });
    }

    private void showTheoryDialog() {
        Dialog<QuestionData> dialog = new Dialog<>();
        dialog.setTitle("Add Theory Question");
        dialog.setHeaderText("Create a Theory/Descriptive Question");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);

        Label qLabel = new Label("Question:");
        qLabel.setStyle("-fx-font-weight: bold;");
        TextArea questionField = new TextArea();
        questionField.setPromptText("Enter theory question...");
        questionField.setPrefRowCount(2);
        questionField.setWrapText(true);

        Label mLabel = new Label("Model Answer:");
        mLabel.setStyle("-fx-font-weight: bold;");
        TextArea modelField = new TextArea();
        modelField.setPromptText("Enter expected answer for evaluation...");
        modelField.setPrefRowCount(4);
        modelField.setWrapText(true);

        Label marksLabel = new Label("Marks:");
        marksLabel.setStyle("-fx-font-weight: bold;");
        Spinner<Integer> marksSpinner = new Spinner<>(1, 20, 5);

        content.getChildren().addAll(
                qLabel, questionField,
                mLabel, modelField,
                marksLabel, marksSpinner
        );

        dialog.getDialogPane().setContent(content);
        ButtonType addBtn = new ButtonType("Add Question", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == addBtn) {
                if (questionField.getText().trim().isEmpty()) {
                    showAlert("Error", "Please enter a question.");
                    return null;
                }

                return new QuestionData(
                        questionField.getText().trim(),
                        modelField.getText().trim(),
                        marksSpinner.getValue()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(q -> {
            if (q == null) return;
            if (!questionsContainer.getChildren().isEmpty() &&
                    questionsContainer.getChildren().get(0) instanceof Label) {
                questionsContainer.getChildren().clear();
            }
            questions.add(q);
            questionsContainer.getChildren().add(createQuestionCard(q));
            updateSummary();
        });
    }

    private void showCodingDialog() {
        Dialog<QuestionData> dialog = new Dialog<>();
        dialog.setTitle("Add Coding Question");
        dialog.setHeaderText("Create a Programming Question");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setPrefWidth(650);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(500);

        Label qLabel = new Label("Question:");
        qLabel.setStyle("-fx-font-weight: bold;");
        TextArea questionField = new TextArea();
        questionField.setPromptText("Enter coding problem statement...");
        questionField.setPrefRowCount(3);
        questionField.setWrapText(true);

        Label inLabel = new Label("Input Format:");
        inLabel.setStyle("-fx-font-weight: bold;");
        TextField inputFmt = new TextField();
        inputFmt.setPromptText("e.g., First line: N, Second line: N integers");

        Label outLabel = new Label("Output Format:");
        outLabel.setStyle("-fx-font-weight: bold;");
        TextField outputFmt = new TextField();
        outputFmt.setPromptText("e.g., Single integer - the result");

        Label sampLabel = new Label("Sample Input:");
        sampLabel.setStyle("-fx-font-weight: bold;");
        TextArea sampleIn = new TextArea();
        sampleIn.setPromptText("5\n1 2 3 4 5");
        sampleIn.setPrefRowCount(2);

        Label sampOutLabel = new Label("Sample Output:");
        sampOutLabel.setStyle("-fx-font-weight: bold;");
        TextArea sampleOut = new TextArea();
        sampleOut.setPromptText("15");
        sampleOut.setPrefRowCount(2);

        Label testLabel = new Label("Test Cases (for evaluation):");
        testLabel.setStyle("-fx-font-weight: bold;");

        VBox testCasesBox = new VBox(8);
        List<TestCaseInput> testCaseInputs = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            HBox tcBox = new HBox(10);
            Label tcLabel = new Label("Test " + i + ":");
            tcLabel.setPrefWidth(60);
            TextField tcInput = new TextField();
            tcInput.setPromptText("Input");
            tcInput.setPrefWidth(200);
            TextField tcOutput = new TextField();
            tcOutput.setPromptText("Expected Output");
            tcOutput.setPrefWidth(200);
            tcBox.getChildren().addAll(tcLabel, tcInput, tcOutput);
            testCasesBox.getChildren().add(tcBox);
            testCaseInputs.add(new TestCaseInput(tcInput, tcOutput));
        }

        Label marksLabel = new Label("Marks:");
        marksLabel.setStyle("-fx-font-weight: bold;");
        Spinner<Integer> marksSpinner = new Spinner<>(1, 50, 10);

        content.getChildren().addAll(
                qLabel, questionField,
                inLabel, inputFmt,
                outLabel, outputFmt,
                sampLabel, sampleIn,
                sampOutLabel, sampleOut,
                testLabel, testCasesBox,
                marksLabel, marksSpinner
        );

        dialog.getDialogPane().setContent(scroll);
        ButtonType addBtn = new ButtonType("Add Question", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == addBtn) {
                if (questionField.getText().trim().isEmpty()) {
                    showAlert("Error", "Please enter a question.");
                    return null;
                }

                List<QuestionData.TestCase> testCases = new ArrayList<>();
                for (TestCaseInput tci : testCaseInputs) {
                    if (!tci.input.getText().trim().isEmpty()) {
                        testCases.add(new QuestionData.TestCase(
                                tci.input.getText().trim(),
                                tci.output.getText().trim()
                        ));
                    }
                }

                if (testCases.isEmpty()) {
                    showAlert("Error", "Please add at least one test case.");
                    return null;
                }

                return new QuestionData(
                        questionField.getText().trim(),
                        inputFmt.getText().trim(),
                        outputFmt.getText().trim(),
                        sampleIn.getText().trim(),
                        sampleOut.getText().trim(),
                        testCases,
                        marksSpinner.getValue()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(q -> {
            if (q == null) return;
            if (!questionsContainer.getChildren().isEmpty() &&
                    questionsContainer.getChildren().get(0) instanceof Label) {
                questionsContainer.getChildren().clear();
            }
            questions.add(q);
            questionsContainer.getChildren().add(createQuestionCard(q));
            updateSummary();
        });
    }

    private static class TestCaseInput {
        TextField input, output;
        TestCaseInput(TextField in, TextField out) {
            this.input = in;
            this.output = out;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  QUESTION DISPLAY
    // ══════════════════════════════════════════════════════════

    private void addQuestionsToList(List<QuestionData> newQuestions) {
        Platform.runLater(() -> {
            if (!questionsContainer.getChildren().isEmpty() &&
                    questionsContainer.getChildren().get(0) instanceof Label) {
                questionsContainer.getChildren().clear();
            }
            for (QuestionData q : newQuestions) {
                questions.add(q);
                questionsContainer.getChildren().add(createQuestionCard(q));
            }
            updateSummary();
        });
    }

    private VBox createQuestionCard(QuestionData q) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: #F9FAFB; " +
                "-fx-padding: 15; " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: #E5E7EB; " +
                "-fx-border-radius: 8;");

        HBox header = new HBox(10);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label typeLabel = new Label(q.getTypeIcon() + " " + q.getType());
        typeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1E293B;");

        Label marksLabel = new Label(q.getMarks() + " marks");
        marksLabel.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button viewBtn = new Button("View Details");
        viewBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");
        viewBtn.setOnAction(e -> showQuestionDetails(q));

        Button deleteBtn = new Button("Delete");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #EF4444; -fx-cursor: hand; -fx-font-size: 11px;");
        deleteBtn.setOnAction(e -> {
            questions.remove(q);
            questionsContainer.getChildren().remove(card);
            updateSummary();
        });

        header.getChildren().addAll(typeLabel, marksLabel, spacer, viewBtn, deleteBtn);

        Label questionText = new Label(q.getQuestionText());
        questionText.setWrapText(true);
        questionText.setMaxWidth(800);
        questionText.setStyle("-fx-text-fill: #374151; -fx-font-size: 13px;");

        Label extraInfo = new Label();
        extraInfo.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");

        if (q.getType().equals("MCQ")) {
            extraInfo.setText("4 options | Correct: " + q.getCorrectOption());
        } else if (q.getType().equals("Theory")) {
            extraInfo.setText("Has model answer for AI evaluation");
        } else if (q.getType().equals("Coding")) {
            extraInfo.setText(q.getTestCases().size() + " test cases | Input/Output format defined");
        }

        card.getChildren().addAll(header, questionText, extraInfo);
        return card;
    }

    private void showQuestionDetails(QuestionData q) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Question Details");
        alert.setHeaderText(q.getType() + " Question - " + q.getMarks() + " marks");

        TextArea content = new TextArea(q.getFullDetails());
        content.setEditable(false);
        content.setWrapText(true);
        content.setPrefSize(600, 400);

        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    private void updateSummary() {
        int count = questions.size();
        int totalMarks = questions.stream().mapToInt(QuestionData::getMarks).sum();

        totalQuestionsLabel.setText(String.valueOf(count));
        totalMarksLabel.setText(String.valueOf(totalMarks));
        questionCountLabel.setText(count + " question" + (count == 1 ? "" : "s"));
    }

    // ══════════════════════════════════════════════════════════
    //  SAVE EXAM - FIXED TO SAVE QUESTIONS TO DATABASE
    // ══════════════════════════════════════════════════════════

    @FXML
    private void handleSaveDraft(ActionEvent event) {
        saveExam("draft", event);
    }

    @FXML
    private void handleSaveExam(ActionEvent event) {
        saveExam("active", event);
    }

    private void saveExam(String finalStatus, ActionEvent event) {
        String title = titleField.getText().trim();
        String durText = durationField.getText().trim();

        if (title.isEmpty() || durText.isEmpty()) {
            showAlert("Error", "Please fill exam title and duration.");
            return;
        }

        if (questions.isEmpty()) {
            showAlert("Error", "Please add at least one question.");
            return;
        }

        int duration;
        try {
            duration = Integer.parseInt(durText);
        } catch (NumberFormatException e) {
            showAlert("Error", "Duration must be a number.");
            return;
        }

        int totalMarks = questions.stream().mapToInt(QuestionData::getMarks).sum();
        int teacherId = SessionManager.getCurrentUser().getId();
        String description = descriptionField.getText().trim();
        int shuffle = shuffleCheck.isSelected() ? 1 : 0;

        try {
            Connection conn = DatabaseHelper.getConnection();

            // Step 1: Insert exam
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO exams (title, description, duration_minutes, " +
                            "total_marks, status, teacher_id, shuffle_questions) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);

            ps.setString(1, title);
            ps.setString(2, description);
            ps.setInt(3, duration);
            ps.setInt(4, totalMarks);
            ps.setString(5, finalStatus);
            ps.setInt(6, teacherId);
            ps.setInt(7, shuffle);
            ps.executeUpdate();

            // Step 2: Get exam ID
            ResultSet generatedKeys = ps.getGeneratedKeys();
            int examId = -1;
            if (generatedKeys.next()) {
                examId = generatedKeys.getInt(1);
            }

            System.out.println("✅ Exam created with ID: " + examId);

            // Step 3: Save questions to database
            if (examId != -1) {
                saveQuestionsToDatabase(conn, examId);
            }

            showAlert("Success",
                    "✅ Exam \"" + title + "\" saved successfully!\n\n" +
                            "Status: " + finalStatus.toUpperCase() + "\n" +
                            "Questions: " + questions.size() + "\n" +
                            "Total Marks: " + totalMarks);

            handleBack(event);

        } catch (Exception e) {
            showAlert("Error", "Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // NEW METHOD: Save questions to database
    private void saveQuestionsToDatabase(Connection conn, int examId) throws SQLException {
        System.out.println("📝 Saving " + questions.size() + " questions to database...");

        for (int i = 0; i < questions.size(); i++) {
            QuestionData q = questions.get(i);

            // ✅ FIXED: question_type column bhi save ho rha ab
            PreparedStatement psQuestion = conn.prepareStatement(
                    "INSERT INTO question_bank (type, question_type, question_text, subject, topic, teacher_id, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, datetime('now'))",
                    PreparedStatement.RETURN_GENERATED_KEYS);

            psQuestion.setString(1, q.getType());
            psQuestion.setString(2, q.getType()); // question_type = same as type
            psQuestion.setString(3, q.getQuestionText());
            psQuestion.setString(4, "General");
            psQuestion.setString(5, "Auto-generated");
            psQuestion.setInt(6, SessionManager.getCurrentUser().getId());
            psQuestion.executeUpdate();

            ResultSet rs = psQuestion.getGeneratedKeys();
            int questionId = -1;
            if (rs.next()) {
                questionId = rs.getInt(1);
            }

            if (questionId == -1) {
                System.out.println("❌ Failed to get questionId for question " + (i+1));
                continue;
            }

            // Link to exam with correct marks
            PreparedStatement psLink = conn.prepareStatement(
                    "INSERT INTO exam_questions (exam_id, question_id, marks, question_order) " +
                            "VALUES (?, ?, ?, ?)");
            psLink.setInt(1, examId);
            psLink.setInt(2, questionId);
            psLink.setInt(3, q.getMarks()); // ✅ marks sahi save ho rha
            psLink.setInt(4, i + 1);
            psLink.executeUpdate();

            if (q.getType().equals("MCQ")) {
                saveMCQOptions(conn, questionId, q);
            } else if (q.getType().equals("Theory")) {
                saveTheoryModelAnswer(conn, questionId, q);
            } else if (q.getType().equals("Coding")) {
                saveCodingTestCases(conn, questionId, q);
            }

            System.out.println("  ✅ Saved question " + (i+1) + ": " + q.getType() + " | marks=" + q.getMarks());
        }

        System.out.println("✅ All " + questions.size() + " questions saved!");
    }

    private void saveMCQOptions(Connection conn, int questionId, QuestionData q) throws SQLException {
        String[] options = {q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()};
        String[] labels = {"A", "B", "C", "D"};

        for (int i = 0; i < options.length; i++) {
            if (options[i] != null && !options[i].trim().isEmpty()) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO mcq_options (question_id, option_text, is_correct) VALUES (?, ?, ?)");
                ps.setInt(1, questionId);
                ps.setString(2, options[i]);
                ps.setInt(3, labels[i].equals(q.getCorrectOption()) ? 1 : 0);
                ps.executeUpdate();
            }
        }
    }

    private void saveTheoryModelAnswer(Connection conn, int questionId, QuestionData q) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE question_bank SET model_answer = ? WHERE id = ?");
        ps.setString(1, q.getModelAnswer());
        ps.setInt(2, questionId);
        ps.executeUpdate();
    }

    private void saveCodingTestCases(Connection conn, int questionId, QuestionData q) throws SQLException {
        // Insert coding question details
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO coding_questions (question_id, input_format, output_format, " +
                        "sample_input, sample_output) VALUES (?, ?, ?, ?, ?)");
        ps.setInt(1, questionId);
        ps.setString(2, q.getInputFormat());
        ps.setString(3, q.getOutputFormat());
        ps.setString(4, q.getSampleInput());
        ps.setString(5, q.getSampleOutput());
        ps.executeUpdate();

        // Save test cases
        if (q.getTestCases() != null) {
            for (QuestionData.TestCase tc : q.getTestCases()) {
                PreparedStatement psTest = conn.prepareStatement(
                        "INSERT INTO test_cases (question_id, input, expected_output) VALUES (?, ?, ?)");
                psTest.setInt(1, questionId);
                psTest.setString(2, tc.getInput());
                psTest.setString(3, tc.getExpectedOutput());
                psTest.executeUpdate();
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
            showAlert("Error", "Could not go back: " + e.getMessage());
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
    //  HELPER CLASSES
    // ══════════════════════════════════════════════════════════

    public static class AIGenerationConfig {
        private int mcqCount, theoryCount, codingCount;

        public AIGenerationConfig(int mcq, int theory, int coding) {
            this.mcqCount = mcq;
            this.theoryCount = theory;
            this.codingCount = coding;
        }

        public int getMcqCount() { return mcqCount; }
        public int getTheoryCount() { return theoryCount; }
        public int getCodingCount() { return codingCount; }
        public int getTotalCount() { return mcqCount + theoryCount + codingCount; }
    }

    public static class QuestionData {
        private String type;
        private String questionText;
        private int marks;

        // MCQ specific
        private String optionA, optionB, optionC, optionD;
        private String correctOption;

        // Theory specific
        private String modelAnswer;

        // Coding specific
        private String inputFormat;
        private String outputFormat;
        private String sampleInput;
        private String sampleOutput;
        private List<TestCase> testCases;

        // MCQ Constructor
        public QuestionData(String questionText, String optA, String optB,
                            String optC, String optD, String correct, int marks) {
            this.type = "MCQ";
            this.questionText = questionText;
            this.optionA = optA;
            this.optionB = optB;
            this.optionC = optC;
            this.optionD = optD;
            this.correctOption = correct;
            this.marks = marks;
        }

        // Theory Constructor
        public QuestionData(String questionText, String modelAnswer, int marks) {
            this.type = "Theory";
            this.questionText = questionText;
            this.modelAnswer = modelAnswer;
            this.marks = marks;
        }

        // Coding Constructor
        public QuestionData(String questionText, String inputFmt, String outputFmt,
                            String sampleIn, String sampleOut,
                            List<TestCase> testCases, int marks) {
            this.type = "Coding";
            this.questionText = questionText;
            this.inputFormat = inputFmt;
            this.outputFormat = outputFmt;
            this.sampleInput = sampleIn;
            this.sampleOutput = sampleOut;
            this.testCases = testCases;
            this.marks = marks;
        }

        public String getType() { return type; }
        public String getQuestionText() { return questionText; }
        public int getMarks() { return marks; }

        public String getOptionA() { return optionA; }
        public String getOptionB() { return optionB; }
        public String getOptionC() { return optionC; }
        public String getOptionD() { return optionD; }
        public String getCorrectOption() { return correctOption; }

        public String getModelAnswer() { return modelAnswer; }

        public String getInputFormat() { return inputFormat; }
        public String getOutputFormat() { return outputFormat; }
        public String getSampleInput() { return sampleInput; }
        public String getSampleOutput() { return sampleOutput; }
        public List<TestCase> getTestCases() { return testCases; }

        public String getTypeIcon() {
            switch (type) {
                case "MCQ": return "MCQ";
                case "Theory": return "Theory";
                case "Coding": return "Coding";
                default: return "?";
            }
        }

        public String getFullDetails() {
            StringBuilder sb = new StringBuilder();
            sb.append("Question:\n").append(questionText).append("\n\n");

            if (type.equals("MCQ")) {
                sb.append("Options:\n");
                sb.append("A) ").append(optionA).append("\n");
                sb.append("B) ").append(optionB).append("\n");
                sb.append("C) ").append(optionC).append("\n");
                sb.append("D) ").append(optionD).append("\n\n");
                sb.append("Correct Answer: ").append(correctOption).append("\n");
            } else if (type.equals("Theory")) {
                sb.append("Model Answer:\n").append(modelAnswer).append("\n");
            } else if (type.equals("Coding")) {
                sb.append("Input Format: ").append(inputFormat).append("\n");
                sb.append("Output Format: ").append(outputFormat).append("\n\n");
                sb.append("Sample Input:\n").append(sampleInput).append("\n\n");
                sb.append("Sample Output:\n").append(sampleOutput).append("\n\n");
                sb.append("Test Cases: ").append(testCases.size()).append("\n");
                for (int i = 0; i < testCases.size(); i++) {
                    sb.append("Test ").append(i + 1).append(":\n");
                    sb.append("  Input: ").append(testCases.get(i).getInput()).append("\n");
                    sb.append("  Output: ").append(testCases.get(i).getExpectedOutput()).append("\n");
                }
            }

            return sb.toString();
        }

        public static class TestCase {
            private String input;
            private String expectedOutput;

            public TestCase(String input, String expectedOutput) {
                this.input = input;
                this.expectedOutput = expectedOutput;
            }

            public String getInput() { return input; }
            public String getExpectedOutput() { return expectedOutput; }
        }
    }
}