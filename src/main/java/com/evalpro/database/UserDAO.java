package com.evalpro.database;

import com.evalpro.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {

    // Register a new user
    public static boolean registerUser(String fullName, String email,
                                       String password, String role) {
        try {
            Connection conn = DatabaseHelper.getConnection();

            // Check if email already exists
            PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM users WHERE email = ?"
            );
            check.setString(1, email);
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                System.out.println("Email already exists!");
                return false;
            }

            // Hash password with BCrypt
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            // Insert new user
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users (full_name, email, password, role) VALUES (?, ?, ?, ?)"
            );
            stmt.setString(1, fullName);
            stmt.setString(2, email);
            stmt.setString(3, hashedPassword);
            stmt.setString(4, role);
            stmt.executeUpdate();

            System.out.println("User registered: " + fullName);
            return true;

        } catch (Exception e) {
            System.out.println("Registration error: " + e.getMessage());
            return false;
        }
    }

    // Login user - verify email and password
    public static User loginUser(String email, String password) {
        try {
            Connection conn = DatabaseHelper.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM users WHERE email = ?"
            );
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String hashedPassword = rs.getString("password");

                // Verify password with BCrypt
                if (BCrypt.checkpw(password, hashedPassword)) {
                    User user = new User(
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            hashedPassword,
                            rs.getString("role")
                    );
                    System.out.println("Login successful: " + user.getFullName());
                    return user;
                } else {
                    System.out.println("Wrong password!");
                    return null;
                }
            } else {
                System.out.println("Email not found!");
                return null;
            }

        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
            return null;
        }
    }
}
