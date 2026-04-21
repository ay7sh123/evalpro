package com.evalpro.database;

import com.evalpro.controller.teacher.CreateExamController.QuestionData;
import com.evalpro.controller.teacher.CreateExamController.QuestionData.TestCase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExamHelper {

    /**
     * Get exam by ID
     */
    public static ExamInfo getExamById(int examId) {
        String sql = "SELECT * FROM exams WHERE id = ?";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, examId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                ExamInfo exam = new ExamInfo();
                exam.id = rs.getInt("id");
                exam.title = rs.getString("title");
                exam.subject = rs.getString("description"); // or subject if exists
                exam.duration = rs.getInt("duration_minutes");
                exam.totalMarks = rs.getInt("total_marks");
                return exam;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error getting exam: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get all questions for an exam with full details
     */
    public static List<QuestionData> getExamQuestions(int examId) {
        List<QuestionData> questions = new ArrayList<>();

        String sql = "SELECT eq.*, qb.type, qb.question_text " +
                "FROM exam_questions eq " +
                "JOIN question_bank qb ON eq.question_id = qb.id " +
                "WHERE eq.exam_id = ? " +
                "ORDER BY eq.question_order";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, examId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int questionId = rs.getInt("question_id");
                String type = rs.getString("type");
                String questionText = rs.getString("question_text");
                int marks = rs.getInt("marks");

                if (type.equals("MCQ")) {
                    QuestionData q = loadMCQQuestion(conn, questionId, questionText, marks);
                    if (q != null) questions.add(q);

                } else if (type.equals("Theory")) {
                    QuestionData q = loadTheoryQuestion(conn, questionId, questionText, marks);
                    if (q != null) questions.add(q);

                } else if (type.equals("Coding")) {
                    QuestionData q = loadCodingQuestion(conn, questionId, questionText, marks);
                    if (q != null) questions.add(q);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error getting exam questions: " + e.getMessage());
            e.printStackTrace();
        }

        return questions;
    }

    private static QuestionData loadMCQQuestion(Connection conn, int qId, String qText, int marks) throws SQLException {
        String sql = "SELECT * FROM mcq_options WHERE question_id = ? ORDER BY id";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, qId);
        ResultSet rs = ps.executeQuery();

        String[] options = new String[4];
        String correct = "A";
        int idx = 0;

        while (rs.next() && idx < 4) {
            options[idx] = rs.getString("option_text");
            if (rs.getInt("is_correct") == 1) {
                correct = String.valueOf((char)('A' + idx));
            }
            idx++;
        }

        return new QuestionData(qText,
                options[0] != null ? options[0] : "",
                options[1] != null ? options[1] : "",
                options[2] != null ? options[2] : "",
                options[3] != null ? options[3] : "",
                correct, marks);
    }

    private static QuestionData loadTheoryQuestion(Connection conn, int qId, String qText, int marks) throws SQLException {
        String sql = "SELECT model_answer FROM question_bank WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, qId);
        ResultSet rs = ps.executeQuery();

        String modelAnswer = "";
        if (rs.next()) {
            modelAnswer = rs.getString("model_answer");
            if (modelAnswer == null) modelAnswer = "";
        }

        return new QuestionData(qText, modelAnswer, marks);
    }

    private static QuestionData loadCodingQuestion(Connection conn, int qId, String qText, int marks) throws SQLException {
        String sql = "SELECT * FROM coding_questions WHERE question_id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, qId);
        ResultSet rs = ps.executeQuery();

        String inputFmt = "", outputFmt = "", sampleIn = "", sampleOut = "";
        if (rs.next()) {
            inputFmt = rs.getString("input_format");
            outputFmt = rs.getString("output_format");
            sampleIn = rs.getString("sample_input");
            sampleOut = rs.getString("sample_output");
        }

        // Load test cases
        List<TestCase> testCases = new ArrayList<>();
        String tcSql = "SELECT * FROM test_cases WHERE question_id = ?";
        PreparedStatement tcPs = conn.prepareStatement(tcSql);
        tcPs.setInt(1, qId);
        ResultSet tcRs = tcPs.executeQuery();

        while (tcRs.next()) {
            testCases.add(new TestCase(
                    tcRs.getString("input"),
                    tcRs.getString("expected_output")
            ));
        }

        return new QuestionData(qText, inputFmt, outputFmt, sampleIn, sampleOut, testCases, marks);
    }

    /**
     * Get all exams
     */
    public static List<ExamInfo> getAllExams() {
        List<ExamInfo> exams = new ArrayList<>();
        String sql = "SELECT * FROM exams ORDER BY created_at DESC";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                ExamInfo exam = new ExamInfo();
                exam.id = rs.getInt("id");
                exam.title = rs.getString("title");
                exam.subject = rs.getString("description");
                exam.duration = rs.getInt("duration_minutes");
                exam.totalMarks = rs.getInt("total_marks");
                exams.add(exam);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error getting all exams: " + e.getMessage());
        }

        return exams;
    }

    // Simple exam info class
    public static class ExamInfo {
        public int id;
        public String title;
        public String subject;
        public int duration;
        public int totalMarks;
    }
}