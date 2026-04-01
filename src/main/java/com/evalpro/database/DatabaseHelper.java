package com.evalpro.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:evalpro.db";
    private static Connection connection = null;

    // Get database connection
    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
                System.out.println("Database connected successfully!");
            }
        } catch (Exception e) {
            System.out.println("Database connection failed: " + e.getMessage());
        }
        return connection;
    }

    // Create all 10 tables
    public static void initializeDatabase() {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();

            // 1. USERS TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    role TEXT NOT NULL CHECK(role IN ('teacher','student','admin')),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // 2. EXAMS TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exams (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    description TEXT,
                    teacher_id INTEGER NOT NULL,
                    duration_minutes INTEGER NOT NULL,
                    total_marks INTEGER NOT NULL,
                    shuffle_questions INTEGER DEFAULT 1,
                    status TEXT DEFAULT 'draft' CHECK(status IN ('draft','active','closed')),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(teacher_id) REFERENCES users(id)
                )
            """);

            // 3. QUESTION BANK TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS question_bank (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    teacher_id INTEGER NOT NULL,
                    subject TEXT NOT NULL,
                    topic TEXT NOT NULL,
                    question_type TEXT NOT NULL CHECK(question_type IN ('mcq','theory','coding')),
                    question_text TEXT NOT NULL,
                    marks INTEGER NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(teacher_id) REFERENCES users(id)
                )
            """);

            // 4. EXAM QUESTIONS TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exam_questions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    exam_id INTEGER NOT NULL,
                    question_id INTEGER NOT NULL,
                    question_order INTEGER NOT NULL,
                    FOREIGN KEY(exam_id) REFERENCES exams(id),
                    FOREIGN KEY(question_id) REFERENCES question_bank(id)
                )
            """);

            // 5. MCQ OPTIONS TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mcq_options (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    question_id INTEGER NOT NULL,
                    option_text TEXT NOT NULL,
                    is_correct INTEGER DEFAULT 0,
                    FOREIGN KEY(question_id) REFERENCES question_bank(id)
                )
            """);

            // 6. CODING QUESTIONS TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS coding_questions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    question_id INTEGER NOT NULL,
                    language TEXT DEFAULT 'any',
                    sample_input TEXT,
                    sample_output TEXT,
                    test_cases TEXT,
                    FOREIGN KEY(question_id) REFERENCES question_bank(id)
                )
            """);

            // 7. SUBMISSIONS TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS submissions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    exam_id INTEGER NOT NULL,
                    student_id INTEGER NOT NULL,
                    start_time DATETIME,
                    submit_time DATETIME,
                    total_marks_obtained INTEGER DEFAULT 0,
                    tab_switch_count INTEGER DEFAULT 0,
                    status TEXT DEFAULT 'in_progress' CHECK(status IN ('in_progress','submitted','evaluated')),
                    FOREIGN KEY(exam_id) REFERENCES exams(id),
                    FOREIGN KEY(student_id) REFERENCES users(id)
                )
            """);

            // 8. ANSWERS TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS answers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    submission_id INTEGER NOT NULL,
                    question_id INTEGER NOT NULL,
                    answer_text TEXT,
                    marks_obtained INTEGER DEFAULT 0,
                    ai_predicted_marks INTEGER DEFAULT 0,
                    ai_feedback TEXT,
                    FOREIGN KEY(submission_id) REFERENCES submissions(id),
                    FOREIGN KEY(question_id) REFERENCES question_bank(id)
                )
            """);

            // 9. STUDENT SHUFFLE TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS student_shuffle (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    submission_id INTEGER NOT NULL,
                    question_id INTEGER NOT NULL,
                    shuffle_order INTEGER NOT NULL,
                    FOREIGN KEY(submission_id) REFERENCES submissions(id),
                    FOREIGN KEY(question_id) REFERENCES question_bank(id)
                )
            """);

            // 10. NOTIFICATIONS TABLE
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    message TEXT NOT NULL,
                    is_read INTEGER DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(user_id) REFERENCES users(id)
                )
            """);

            stmt.close();
            System.out.println("All 10 tables created successfully!");

        } catch (Exception e) {
            System.out.println("Error creating tables: " + e.getMessage());
        }
    }
}
