package chat_ui;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.sql.*;
import java.util.Map;
import java.util.UUID;

public class Registeration extends JDialog {
    private JTextField emailField = new JTextField(20);
    private JPasswordField passField = new JPasswordField(20);
    private JTextField firstNameField = new JTextField(20);
    private JTextField lastNameField = new JTextField(20);
    
    // JSpinner for Date of Birth
    private JSpinner dobSpinner; 
    private JButton registerBtn = new JButton("Create Account");
    private String registeredUUID = null;

    public Registeration(Frame owner) {
        super(owner, "DoctorBot - Registration", true);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1. Setup the Date Spinner (Medical Standard)
        SpinnerDateModel dateModel = new SpinnerDateModel();
        dobSpinner = new JSpinner(dateModel);
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dobSpinner, "yyyy-MM-dd");
        dobSpinner.setEditor(dateEditor);

        // 2. Add Components Row by Row
        gbc.gridx = 0; gbc.gridy = 0; add(new JLabel("First Name:"), gbc);
        gbc.gridx = 1; add(firstNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; add(new JLabel("Last Name:"), gbc);
        gbc.gridx = 1; add(lastNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; add(emailField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; add(new JLabel("Birth Date:"), gbc);
        gbc.gridx = 1; add(dobSpinner, gbc);

        // 3. Register Button at the bottom
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 10, 10, 10);
        registerBtn.setFocusPainted(true); // Highlight the register button for better UX
        add(registerBtn, gbc);

        registerBtn.addActionListener(e -> handleRegistration());

        pack();
        setLocationRelativeTo(owner);
    }

    private void handleRegistration() {
        java.util.Date selectedDate = (java.util.Date) dobSpinner.getValue();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        String dob = sdf.format(selectedDate);

        String fName = firstNameField.getText().trim();
        String lName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String pass = new String(passField.getPassword());

        if (email.isEmpty() || !email.contains("@") || pass.length() < 6 || fName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields. Password min 6 chars.");
            return;
        }

        // 2. CHECK: Does this email already exist?
        // Using your Map utility here:
        String[] cols = {"is_verified", "first_name"}; // We only need to know if the user exists and if they are verified for this flow
        Map<String, Object> userData = Database.getCustomData("chat_users", cols, "email = ?", email);

        if (!userData.isEmpty()) { // If the email is already registered, we can check if they are verified or not to decide the next steps
            boolean isVerified = Boolean.TRUE.equals(userData.get("is_verified"));
            String existingName = (String) userData.get("first_name");

            if (!isVerified) {
                int choice = JOptionPane.showConfirmDialog(this, 
                    "Account not verified. Send a new activation link?", 
                    "Unverified Account", JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    String newToken = UUID.randomUUID().toString();
                    // We use your existing updateData or raw SQL for the update
                    Database.updateData("chat_users", 
                        "verification_token = ?, verification_expiration = DATE_ADD(NOW(), INTERVAL 24 HOUR)", 
                        "email = ?", newToken, email);

                    this.triggerEmailActivation(email, existingName, newToken);
                    JOptionPane.showMessageDialog(this, "A fresh activation link has been sent!");
                    this.dispose();
                }
            } else {
                JOptionPane.showMessageDialog(this, "This email is already registered. Please login.");
            }
            return; 
        }

        // 3. NEW REGISTRATION: Proceed to INSERT (since userData was empty)
        try {
            String uuid = UUID.randomUUID().toString();
            String hashedPass = Database.hashPw(pass); 
            String activationToken = UUID.randomUUID().toString();

            // You can use a raw PreparedStatement here or a Database.insert helper if you have one
            try (Connection conn = DriverManager.getConnection(Database.URL, Database.USER, Database.PASS)) {
                String insertSql = "INSERT INTO chat_users (user_uuid, email, password_hash, first_name, last_name, birthdate, verification_token, verification_expiration) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 24 HOUR))";
                
                PreparedStatement pstmt = conn.prepareStatement(insertSql);
                pstmt.setString(1, uuid);
                pstmt.setString(2, email);
                pstmt.setString(3, hashedPass);
                pstmt.setString(4, fName);
                pstmt.setString(5, lName);
                pstmt.setString(6, dob);
                pstmt.setString(7, activationToken);
                pstmt.executeUpdate();
            }

            JOptionPane.showMessageDialog(this, "Registration successful! Check your email.");
            this.triggerEmailActivation(email, fName, activationToken);
            this.dispose();

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage());
        }

    }

    private void triggerEmailActivation(String email, String firstName, String activationToken) {
        // We run this in a background thread so the UI remains responsive
        new Thread(() -> { // We will call the FastAPI endpoint here to send the activation email
            try {
                // Encode the email and name to handle spaces or special characters
                java.net.URI uri = new java.net.URI(
                    "http", // No authentication needed for this endpoint
                    null, // No user info
                    "127.0.0.1", // Assuming FastAPI is running locally
                    8000,  // FastAPI port
                    "/send-activation", 
                    "email=" + email + "&name=" + firstName + "&token=" + activationToken, // Query parameters
                    null
                );

                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("Activation request sent to FastAPI successfully.");
                } else {
                    System.out.println("FastAPI returned error code: " + responseCode);
                }
            } catch (Exception e) {
                System.err.println("Error connecting to FastAPI: " + e.getMessage());
            }
        }).start();
    }

    public String getRegisteredUUID() {
        return registeredUUID;
    }
}
