package com.evalpro.util;

import com.evalpro.model.User;

public class SessionManager {

    private static User loggedInUser = null;

    // Save logged in user
    public static void setCurrentUser(User user) {
        loggedInUser = user;
    }

    // Get current logged in user
    public static User getCurrentUser() {
        return loggedInUser;
    }

    // Check if someone is logged in
    public static boolean isLoggedIn() {
        return loggedInUser != null;
    }

    // Get role of logged in user
    public static String getCurrentRole() {
        if (loggedInUser != null) {
            return loggedInUser.getRole();
        }
        return null;
    }

    // Logout - clear session
    public static void logout() {
        loggedInUser = null;
        System.out.println("User logged out successfully.");
    }
}
