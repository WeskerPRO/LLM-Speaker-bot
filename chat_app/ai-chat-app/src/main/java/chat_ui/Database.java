package chat_ui;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import org.mindrot.jbcrypt.BCrypt;

public class Database {
    // 1. Updated to MariaDB prefix
    public static final String URL = "jdbc:mariadb://127.0.0.1:3306/telegram_bot_db";
    public static final String USER = "root"; 
    public static final String PASS = ""; 

    public static void initialize() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) 
        {
            System.out.println("Connecting...");
            
            if (conn != null) {
                // Disable auto-commit to BEGIN TRANSACTION
                conn.setAutoCommit(false); 
                
                try (Statement stmt = conn.createStatement()) {

                    String usersSql = "CREATE TABLE IF NOT EXISTS chat_users (" +
                            "first_name VARCHAR(100)," +
                            "last_name VARCHAR(100)," +
                            "birthdate DATE," +
                            "history_id INT AUTO_INCREMENT UNIQUE," +
                            "user_uuid VARCHAR(100) PRIMARY KEY," +
                            "email VARCHAR(100) UNIQUE NOT NULL," +
                            "password_hash VARCHAR(255) NOT NULL," +
                            "is_verified BOOLEAN DEFAULT FALSE," +
                            "verification_token VARCHAR(255) DEFAULT NULL," + // Token for email verification, generated when user registers
                            "verification_expiration TIMESTAMP NULL DEFAULT NULL," + // When the verification token expires - current timestamp + 15 minutes for example
                            "reset_token VARCHAR(255) DEFAULT NULL," + // Token for password reset, generated when user requests a password reset
                            "reset_expiration TIMESTAMP NULL DEFAULT NULL," + // When the reset token expires - current timestamp + 15 minutes for example
                            "reset_status VARCHAR(20) DEFAULT 'NONE'," + // PENDING, APPROVED, EXPIRED, REJECTED
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
                    stmt.execute(usersSql);

                    // 1. History Table
                    String historySql = "CREATE TABLE IF NOT EXISTS chat_history (" +
                            "user_id VARCHAR(100), " +
                            "sender VARCHAR(50), " + 
                            "response TEXT, " + 
                            "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES chat_users(user_uuid) " + // Foreign key to link chat history to users
                            "ON DELETE CASCADE);"; // If a user is deleted, their chat history is also deleted
                    
                    stmt.execute(historySql);

                    // If both succeeded, COMMIT
                    conn.commit();
                    System.out.println("DB Initialized: Tables created and changes committed.");
                    
                } catch (SQLException e) {
                    // If any error occurs, ROLLBACK
                    conn.rollback();
                    System.err.println("Transaction failed! Rolling back changes: " + e.getMessage());
                } finally {
                    // Re-enable auto-commit for normal operations
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            System.err.println("Database Connection Error: " + e.getMessage());
        }
    }

    public static void saveMessage(String userId, String sender, String response) {
        String sql = "INSERT INTO chat_history(user_id, sender, response) VALUES(?, ?, ?)";
        // Added USER and PASS here
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, sender);
            pstmt.setString(3, response);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Save Error: " + e.getMessage());
        }
    }

    public static String loadFullHistory(String userId) {
        StringBuilder history = new StringBuilder();
        String sql = "SELECT sender, response FROM chat_history WHERE user_id = ? ORDER BY timestamp ASC";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String sender = rs.getString("sender");
                String content = rs.getString("response");

                // Make it look professional for the UI
                String label = sender.equalsIgnoreCase("DoctorBot") ? "DoctorBot" : userId;
                
                history.append(label).append(": ").append(content).append("\n\n");
            }
        } catch (SQLException e) {
            System.out.println("Load Error: " + e.getMessage());
        }
        return history.toString();
    }

    public static String loadHistoryAsJson(String userId) {
        StringBuilder json = new StringBuilder("["); // Start of JSON array
        String sql = "SELECT sender, response FROM chat_history WHERE user_id = ? ORDER BY timestamp ASC"; // Get messages in chronological order

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
            PreparedStatement pstmt = conn.prepareStatement(sql)) { // Added USER and PASS here
            
            pstmt.setString(1, userId); // Set the userId parameter for the query
            ResultSet rs = pstmt.executeQuery(); // Execute the query and get the result set

            boolean first = true; // To handle comma placement in JSON array
            while (rs.next()) {
                if (!first) json.append(",");
                
                // Map your database 'sender' to AI 'roles'
                // MariaDB "User" -> AI "user" | MariaDB "DoctorBot" -> AI "system"
                String role = rs.getString("sender").equalsIgnoreCase("DoctorBot") ? "assistant" : "user";
                String content = rs.getString("response")
                                    .replace("\\", "\\\\") // Escape backslashes first
                                    .replace("\"", "\\\""); // Escape quotes for JSON
                
                json.append(String.format("{\"role\": \"%s\", \"content\": \"%s\"}", role, content)); // Append each message as a JSON object in the array
                first = false;
            }
        } catch (SQLException e) {
            System.out.println("JSON Load Error: " + e.getMessage());
        }
        
        json.append("]");
        return json.toString();
    }

    public static String checkResetStatus(String email) {
        String sql = "SELECT reset_status FROM chat_users WHERE email = ?";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
            PreparedStatement pstmt = conn.prepareStatement(sql)) 
        {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) 
                return rs.getString("reset_status"); // Return the current reset status for the given email
            
            return "NOT_FOUND"; // Return a specific status if the email is not found in the database
        } 
        catch (SQLException e) { 
            e.printStackTrace(); 
            return "ERROR"; // Return a specific status if there's an error during the database query
        }
    }

    public static void updateResetStatus(String email, String newStatus) {
        String sql = "UPDATE chat_users SET reset_status = ? WHERE email = ?";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
            PreparedStatement pstmt = conn.prepareStatement(sql)) 
        {
            pstmt.setString(1, newStatus); // Set the new reset status (e.g., APPROVED, EXPIRED, REJECTED, PENDING)
            pstmt.setString(2, email); // Set the email to identify which user's status to update
            pstmt.executeUpdate(); // Execute the update statement
        } 
        catch (SQLException e) { 
            e.printStackTrace(); 
        }
    }

    public static Map<String, Object> getCustomData(String table, String[] columns, String conditionClause, Object... params) {
        Map<String, Object> result = new HashMap<>();
        String colString = String.join(", ", columns);
        
        // Example: conditionClause = "email = ? OR username = ?"
        String sql = "SELECT " + colString + " FROM " + table + " WHERE " + conditionClause;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // params is a "Varargs" (Variable Arguments) array
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    for (String col : columns) {
                        result.put(col, rs.getObject(col));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static boolean updateData(String table, String setClause, String condition, Object... params) {
    // SQL: UPDATE table SET column = ? WHERE condition = ?
        String sql = "UPDATE " + table + " SET " + setClause + " WHERE " + condition;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Loop through params to fill the SET values and the WHERE values
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String hashPw(String plainPassword) { // Use BCrypt to hash passwords securely
        /*
            * BCrypt automatically handles salting and is designed to be slow to prevent brute-force attacks. 
        The gensalt() method generates a random salt, and the hashpw() method combines the password and salt to produce a secure hash. 
        When verifying passwords during login, you can use the checkpw() method to compare the plain password with the stored hash.

        */
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    // You will also need this for your Login logic later
    public static boolean checkPw(String plainPassword, String hashedFromDB) {
        // This method will return true if the plain password matches the hashed password from the database, and false otherwise. You can use this in your login logic to verify the user's password.
        return BCrypt.checkpw(plainPassword, hashedFromDB);
    }
}