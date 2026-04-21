package com.evalpro.controller.student;

import com.evalpro.controller.teacher.CreateExamController.QuestionData;
import com.evalpro.database.DatabaseHelper;
import com.evalpro.database.ExamHelper;
import com.evalpro.database.ExamHelper.ExamInfo;
import com.evalpro.util.SessionManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class StudentExamController {

    @FXML private Label examTitleLabel;
    @FXML private Label timerLabel;
    @FXML private HBox warningBar;
    @FXML private Label warningLabel;

    @FXML private FlowPane questionPalette;
    @FXML private Label questionNumberLabel;
    @FXML private Label questionMarksLabel;
    @FXML private Label questionTextLabel;
    @FXML private VBox answerArea;

    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private Label progressLabel;
    @FXML private Label tabSwitchLabel;

    private ExamInfo currentExam;
    private List<QuestionData> questions;
    private Map<Integer, String> studentAnswers = new HashMap<>();
    private int currentQuestionIndex = 0;
    private int tabSwitchCount = 0;
    private int timeRemainingSeconds;
    private Timeline timer;
    private int submissionId = -1;

    private ToggleGroup mcqToggleGroup;
    private TextArea theoryTextArea;
    private TextArea codingTextArea;
    private boolean examSubmitted = false;

    // ✅ FIX: Flag to prevent focus listener triggering on initial load
    private boolean examStarted = false;

    /**
     * Initialize exam with exam ID
     */
    public void initExam(int examId) {
        currentExam = ExamHelper.getExamById(examId);
        if (currentExam == null) {
            showError("Exam not found!");
            return;
        }

        questions = ExamHelper.getExamQuestions(examId);
        if (questions.isEmpty()) {
            showError("No questions found for this exam!");
            return;
        }

        examTitleLabel.setText(currentExam.title);
        timeRemainingSeconds = currentExam.duration * 60;

        createSubmission();
        buildQuestionPalette();
        startTimer();
        loadQuestion(0);
        updateProgress();

        // ✅ Mark exam as started after a short delay to avoid false trigger
        Platform.runLater(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    examStarted = true;
                } catch (InterruptedException ignored) {}
            }).start();
        });
    }

    /**
     * ✅ FIX: Public method to attach tab switch listener — called from Dashboard/ExamList
     * after stage.show() so scene is ready
     */
    public void attachTabSwitchListener(Stage stage) {
        stage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            // Only trigger if exam has started and focus is LOST (not gained)
            if (examStarted && !isNowFocused && !examSubmitted) {
                Platform.runLater(this::handleTabSwitch);
            }
        });
    }

    /**
     * Create submission record in database — status = in_progress
     */
    private void createSubmission() {
        String sql = "INSERT INTO submissions (exam_id, student_id, submitted_at, tab_switch_count, score, status) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, currentExam.id);
            pstmt.setInt(2, SessionManager.getCurrentUser().getId());
            pstmt.setString(3, LocalDateTime.now().toString());
            pstmt.setInt(4, 0);
            pstmt.setDouble(5, 0.0);
            pstmt.setString(6, "in_progress");

            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                submissionId = rs.getInt(1);
                System.out.println("✅ Submission created: ID = " + submissionId);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error creating submission: " + e.getMessage());
        }
    }

    /**
     * Build question palette (numbered buttons)
     */
    private void buildQuestionPalette() {
        questionPalette.getChildren().clear();

        for (int i = 0; i < questions.size(); i++) {
            final int index = i;
            Button btn = new Button(String.valueOf(i + 1));
            btn.setPrefSize(40, 40);
            btn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-background-radius: 3; -fx-font-weight: bold;");
            btn.setOnAction(e -> loadQuestion(index));
            questionPalette.getChildren().add(btn);
        }
    }

    /**
     * ✅ FIX: Update palette correctly — green=answered, yellow=current, grey=not answered
     */
    private void updatePalette() {
        for (int i = 0; i < questionPalette.getChildren().size(); i++) {
            Button btn = (Button) questionPalette.getChildren().get(i);

            if (i == currentQuestionIndex) {
                // Yellow = current question
                btn.setStyle("-fx-background-color: #ffc107; -fx-text-fill: white; -fx-background-radius: 3; -fx-font-weight: bold;");
            } else if (studentAnswers.containsKey(i)) {
                // Green = answered (use index as key, consistent with saveCurrentAnswer)
                btn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-background-radius: 3; -fx-font-weight: bold;");
            } else {
                // Grey = not answered
                btn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-background-radius: 3; -fx-font-weight: bold;");
            }
        }
    }

    /**
     * Start countdown timer
     */
    private void startTimer() {
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeRemainingSeconds--;
            updateTimerLabel();

            if (timeRemainingSeconds <= 0) {
                timer.stop();
                Platform.runLater(() -> autoSubmit("Time's up!"));
            } else if (timeRemainingSeconds <= 60) {
                timerLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold; -fx-font-size: 24;");
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    /**
     * Update timer label display
     */
    private void updateTimerLabel() {
        int minutes = timeRemainingSeconds / 60;
        int seconds = timeRemainingSeconds % 60;
        timerLabel.setText(String.format("⏰ %02d:%02d", minutes, seconds));
    }

    /**
     * ✅ FIX: setupTabSwitchDetection removed — now handled by attachTabSwitchListener
     * called externally after stage.show() to avoid premature triggers
     */

    /**
     * ✅ FIX: Handle tab switch — auto-submit on 3rd warning, guard against double calls
     */
    private void handleTabSwitch() {
        if (examSubmitted) return;

        tabSwitchCount++;
        updateTabSwitchInDB();

        Platform.runLater(() -> {
            tabSwitchLabel.setText("Tab switches: " + tabSwitchCount + "/3");

            if (tabSwitchCount >= 3) {
                examSubmitted = true;

                if (timer != null) timer.stop();

                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Auto Submit");
                alert.setHeaderText("Exam Auto-Submitted!");
                alert.setContentText("You switched tabs " + tabSwitchCount + " times.\nYour exam has been submitted automatically.");
                alert.showAndWait();

                submitExam();
                return;
            }

            // Show warning bar for 1st and 2nd switch
            warningLabel.setText("⚠️ Warning " + tabSwitchCount + "/3: Don't switch tabs!");
            warningBar.setVisible(true);
            warningBar.setManaged(true);

            Timeline hideWarning = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                warningBar.setVisible(false);
                warningBar.setManaged(false);
            }));
            hideWarning.play();
        });
    }

    private void updateTabSwitchInDB() {
        String sql = "UPDATE submissions SET tab_switch_count = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, tabSwitchCount);
            pstmt.setInt(2, submissionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error updating tab switch: " + e.getMessage());
        }
    }

    /**
     * ✅ FIX: loadQuestion — currentQuestionIndex update BEFORE fetching question
     */
    private void loadQuestion(int index) {
        if (index < 0 || index >= questions.size()) return;

        saveCurrentAnswer();

        // ✅ Update index FIRST, then fetch question
        currentQuestionIndex = index;
        QuestionData q = questions.get(currentQuestionIndex);

        questionNumberLabel.setText("Question " + (currentQuestionIndex + 1) + " of " + questions.size());
        questionMarksLabel.setText("Marks: " + q.getMarks());
        questionTextLabel.setText(q.getQuestionText());

        previousButton.setDisable(currentQuestionIndex == 0);
        nextButton.setDisable(currentQuestionIndex == questions.size() - 1);

        loadAnswerArea(q);
        updatePalette();
        updateProgress();
    }

    /**
     * Load appropriate answer input based on question type
     */
    private void loadAnswerArea(QuestionData q) {
        answerArea.getChildren().clear();
        mcqToggleGroup = null;
        theoryTextArea = null;
        codingTextArea = null;

        if (q.getType().equals("MCQ")) {
            loadMCQOptions(q);
        } else if (q.getType().equals("Theory")) {
            loadTheoryArea(q);
        } else if (q.getType().equals("Coding")) {
            loadCodingArea(q);
        }
    }

    /**
     * Load MCQ radio buttons
     */
    private void loadMCQOptions(QuestionData q) {
        mcqToggleGroup = new ToggleGroup();
        VBox optionsBox = new VBox(10);

        String[] options = {q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()};
        String[] labels = {"A", "B", "C", "D"};

        for (int i = 0; i < options.length; i++) {
            if (options[i] == null || options[i].trim().isEmpty()) continue;

            RadioButton rb = new RadioButton(labels[i] + ") " + options[i]);
            rb.setToggleGroup(mcqToggleGroup);
            rb.setUserData(labels[i]);
            rb.setStyle("-fx-font-size: 14;");
            optionsBox.getChildren().add(rb);

            // ✅ Restore saved answer using currentQuestionIndex
            String savedAnswer = studentAnswers.get(currentQuestionIndex);
            if (savedAnswer != null && savedAnswer.equals(labels[i])) {
                rb.setSelected(true);
            }
        }

        answerArea.getChildren().add(optionsBox);
    }

    /**
     * Load theory text area
     */
    private void loadTheoryArea(QuestionData q) {
        Label hint = new Label("💡 Write your answer below:");
        hint.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        theoryTextArea = new TextArea();
        theoryTextArea.setPromptText("Type your answer here...");
        theoryTextArea.setWrapText(true);
        theoryTextArea.setPrefRowCount(10);
        theoryTextArea.setStyle("-fx-font-size: 14;");

        String savedAnswer = studentAnswers.get(currentQuestionIndex);
        if (savedAnswer != null) {
            theoryTextArea.setText(savedAnswer);
        }

        answerArea.getChildren().addAll(hint, theoryTextArea);
        VBox.setVgrow(theoryTextArea, Priority.ALWAYS);
    }

    /**
     * Load coding text area
     */
    private void loadCodingArea(QuestionData q) {
        Label hint = new Label("💻 Write your code below:");
        hint.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        VBox formatBox = new VBox(5);
        formatBox.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 10; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
        formatBox.getChildren().addAll(
                new Label("Input Format: " + (q.getInputFormat() != null ? q.getInputFormat() : "N/A")),
                new Label("Output Format: " + (q.getOutputFormat() != null ? q.getOutputFormat() : "N/A")),
                new Label("Sample Input: " + (q.getSampleInput() != null ? q.getSampleInput() : "N/A")),
                new Label("Sample Output: " + (q.getSampleOutput() != null ? q.getSampleOutput() : "N/A"))
        );

        codingTextArea = new TextArea();
        codingTextArea.setPromptText("// Write your code here...\n");
        codingTextArea.setWrapText(false);
        codingTextArea.setPrefRowCount(15);
        codingTextArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 13;");

        String savedAnswer = studentAnswers.get(currentQuestionIndex);
        if (savedAnswer != null) {
            codingTextArea.setText(savedAnswer);
        }

        answerArea.getChildren().addAll(hint, formatBox, codingTextArea);
        VBox.setVgrow(codingTextArea, Priority.ALWAYS);
    }

    /**
     * ✅ FIX: Save current answer using currentQuestionIndex as key (consistent)
     */
    private void saveCurrentAnswer() {
        if (currentQuestionIndex < 0 || currentQuestionIndex >= questions.size()) return;

        QuestionData q = questions.get(currentQuestionIndex);
        String answer = null;

        if (q.getType().equals("MCQ") && mcqToggleGroup != null
                && mcqToggleGroup.getSelectedToggle() != null) {
            answer = (String) mcqToggleGroup.getSelectedToggle().getUserData();
        } else if (q.getType().equals("Theory") && theoryTextArea != null
                && !theoryTextArea.getText().trim().isEmpty()) {
            answer = theoryTextArea.getText().trim();
        } else if (q.getType().equals("Coding") && codingTextArea != null
                && !codingTextArea.getText().trim().isEmpty()) {
            answer = codingTextArea.getText().trim();
        }

        if (answer != null && !answer.isEmpty()) {
            studentAnswers.put(currentQuestionIndex, answer);
        }
    }

    /**
     * Update progress label
     */
    private void updateProgress() {
        progressLabel.setText("Progress: " + studentAnswers.size() + "/" + questions.size() + " answered");
    }

    @FXML
    private void handlePrevious() {
        if (currentQuestionIndex > 0) {
            loadQuestion(currentQuestionIndex - 1);
        }
    }

    @FXML
    private void handleNext() {
        if (currentQuestionIndex < questions.size() - 1) {
            loadQuestion(currentQuestionIndex + 1);
        }
    }

    @FXML
    private void handleSaveAndNext() {
        saveCurrentAnswer();
        updateProgress();
        updatePalette();

        if (currentQuestionIndex < questions.size() - 1) {
            loadQuestion(currentQuestionIndex + 1);
        } else {
            showInfo("This is the last question. Click 'Submit Exam' to finish.");
        }
    }

    @FXML
    public void handleSubmit() {
        if (examSubmitted) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Submit Exam");
        confirm.setHeaderText("Are you sure you want to submit?");
        confirm.setContentText("Answered: " + studentAnswers.size() + "/" + questions.size() + " questions\n" +
                "You cannot change answers after submission.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            submitExam();
        }
    }

    /**
     * ✅ FIX: Submit exam — status = 'completed' only on submit (not on start)
     */
    private void submitExam() {
        if (examSubmitted) return;
        examSubmitted = true;

        saveCurrentAnswer();

        if (timer != null) timer.stop();

        double totalScore = 0.0;

        try (Connection conn = DatabaseHelper.getConnection()) {
            for (int i = 0; i < questions.size(); i++) {
                QuestionData q = questions.get(i);
                String studentAnswer = studentAnswers.get(i);
                double marks = 0.0;

                if (studentAnswer != null) {
                    if (q.getType().equals("MCQ")) {
                        if (studentAnswer.equalsIgnoreCase(q.getCorrectOption())) {
                            marks = q.getMarks();
                        }
                    }

                    String sql = "INSERT INTO answers (submission_id, question_id, answer_text, marks_awarded) VALUES (?, ?, ?, ?)";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setInt(1, submissionId);
                    pstmt.setInt(2, i);
                    pstmt.setString(3, studentAnswer);
                    pstmt.setDouble(4, marks);
                    pstmt.executeUpdate();

                    totalScore += marks;
                }
            }

            // ✅ Update submission to 'completed' with final score and submit time
            String updateSql = "UPDATE submissions SET score = ?, status = 'completed', submit_time = ?, tab_switch_count = ? WHERE id = ?";
            PreparedStatement updatePs = conn.prepareStatement(updateSql);
            updatePs.setDouble(1, totalScore);
            updatePs.setString(2, LocalDateTime.now().toString());
            updatePs.setInt(3, tabSwitchCount);
            updatePs.setInt(4, submissionId);
            updatePs.executeUpdate();

            System.out.println("✅ Exam submitted! Score: " + totalScore);

            final double finalScore = totalScore;
            Platform.runLater(() -> {
                showSuccess("Exam submitted successfully!\n\nYour score: " + finalScore + "/" + currentExam.totalMarks);
                returnToExamList();
            });

        } catch (SQLException e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> showError("Error submitting exam: " + e.getMessage()));
        }
    }

    /**
     * Auto-submit exam
     */
    private void autoSubmit(String reason) {
        if (examSubmitted) return;

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Auto Submit");
        alert.setHeaderText(reason);
        alert.setContentText("Your exam will be submitted automatically.");
        alert.showAndWait();

        submitExam();
    }

    /**
     * Return to exam list screen
     */
    private void returnToExamList() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/evalpro/views/student_exam_list.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) examTitleLabel.getScene().getWindow();
            stage.setFullScreen(false);
            stage.setScene(new Scene(root, 1000, 700));
            stage.centerOnScreen();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText("✅ Success!");
        alert.setContentText(message);
        alert.showAndWait();
    }
}