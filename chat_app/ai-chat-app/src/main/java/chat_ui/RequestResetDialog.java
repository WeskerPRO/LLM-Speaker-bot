package chat_ui;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.UUID;
import java.util.Map;

public class RequestResetDialog extends JDialog {
    private JTextField emailField = new JTextField(20);
    private JButton submitBtn = new JButton("Send Reset Link/Code");
    private JButton backBtn = new JButton("Back");

    public RequestResetDialog(Frame owner) {
        super(owner, "Forgot Password", true);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Header Label
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel header = new JLabel("Enter your email to receive a reset code");
        header.setFont(new Font("Arial", Font.BOLD, 12));
        add(header, gbc);

        // Email Label & Field
        gbc.gridwidth = 1;
        gbc.gridy = 1; gbc.gridx = 0;
        add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        add(emailField, gbc);

        // Buttons Panel
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        submitBtn.setFocusPainted(true); // Highlight the submit button for better UX
        backBtn.setFocusPainted(true); // Highlight the back button for better UX
        btnPanel.add(backBtn);
        btnPanel.add(submitBtn);

        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2;
        add(btnPanel, gbc);

        // Listeners
        backBtn.addActionListener(e -> this.dispose());
        
        submitBtn.addActionListener(e -> {
            String email = emailField.getText().trim();

            try {
                // 2. Combine your queries! 
                // Instead of 3 calls, just get everything at once. Faster and safer.
                String[] cols = {"COUNT(*)", "is_verified", "first_name"};
                Map<String, Object> userData = Database.getCustomData("chat_users", cols, "email = ?", email);

                // 3. Logic Check
                Object countObj = userData.get("COUNT(*)");
                int count = (countObj != null) ? ((Number) countObj).intValue() : 0;

                if (count == 0) {
                    JOptionPane.showMessageDialog(this, "No account found with that email.", "Email Not Found", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                // Check verification using our safe Boolean logic
                if (Boolean.FALSE.equals(userData.get("is_verified"))) {
                    JOptionPane.showMessageDialog(this, "This account is not verified. Please check your email.", "Account Not Verified", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                // 4. Check Pending Status
                if("PENDING".equals(Database.checkResetStatus(email))) {
                    JOptionPane.showMessageDialog(this, "A reset request is already pending for this email. Please check your email or wait a few minutes before trying again.", "Reset Request Pending", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                // 5. Success Flow
                String activationToken = UUID.randomUUID().toString();
                // Add the expiration here! (30 mins from now)
                Database.updateData("chat_users", 
                    "reset_token = ?, reset_status = ?, reset_expiration = DATE_ADD(NOW(), INTERVAL 30 MINUTE)", 
                    "email = ?", 
                    activationToken, "PENDING", email);
                
                this.triggerEmailNotification(email, (String) userData.get("first_name"), activationToken);
                this.openVerificationUI(email);
                this.dispose();

            } catch (Exception ex) {
                System.err.println("FATAL ERROR in Button Logic:");
                ex.printStackTrace(); // This will show you exactly which line failed in the VS Code console
            }
        });

        pack();
        setLocationRelativeTo(owner);
    }
    
    private void triggerEmailNotification(String email, String firstName, String activationToken) {
        // In a real implementation, you would make an HTTP POST request to your backend API endpoint, passing the user's email as a parameter. The backend would then generate a reset token, save it in the database, and send an email to the user with the reset instructions.
        new Thread(() -> { // We will call the FastAPI endpoint here to send the activation email
            try {
                // Encode the email and name to handle spaces or special characters
                java.net.URI uri = new java.net.URI(
                    "http", // No authentication needed for this endpoint
                    null, // No user info
                    "127.0.0.1", // Assuming FastAPI is running locally
                    8000,  // FastAPI port
                    "/send-reset-password", 
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

    private void openVerificationUI(String email) {
        JDialog verifyDialog = new JDialog((Frame)null, "Security Verification", true);
        verifyDialog.setLayout(new GridBagLayout());
        JLabel statusLabel = new JLabel("Checking email status...");
        JButton cancelBtn = new JButton("Cancel");

        // This timer checks the DB every 1.5 seconds
        Timer poller = new Timer(2000, null); // 2 seconds for better UX (not too fast, not too slow)
        poller.addActionListener(e -> {
            // We check if the reset_status has changed to 'APPROVED'
            if (Database.checkResetStatus(email).equals("APPROVED")) {
                poller.stop(); // Stop checking
                statusLabel.setText("Identity Verified! Please enter new password.");
                verifyDialog.dispose(); // Close the verification dialog
                Database.updateData("chat_users", "reset_status = ?", "email = ?", "NONE", email); // Reset the status so the user can do another reset in the future if needed without issues
                SwingUtilities.invokeLater(() -> {
                    PasswordUpdateDialog updateDialog = new PasswordUpdateDialog((Frame)verifyDialog.getOwner(), email);
                    updateDialog.setVisible(true);
                });
                // Swap the UI components
                // showPasswordEntryForm(verifyDialog, email); // Temporarily unavailable - this will open a new dialog where they can enter their new password and the reset code they received in their email, and then submit to update their password in the DB
            }
        });
        
        cancelBtn.addActionListener(e -> {
            poller.stop(); // Stop the timer when user cancels
            Database.updateData("chat_users", "reset_status = ?", "email = ?", "REJECTED", email); // Update the reset status to CANCELLED in the DB so the user can't use the same reset token if they change their mind later and click the reset link in their email
            verifyDialog.dispose(); // Close the dialog without making changes
        });

        // 3. Add components to the UI BEFORE making it visible
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; 
        gbc.insets = new Insets(10,10,10,10);
        verifyDialog.add(statusLabel, gbc);
        
        gbc.gridy = 1;
        cancelBtn.setFocusPainted(true); // Highlight the cancel button for better UX
        verifyDialog.add(cancelBtn, gbc);
        
        // 4. Start the timer and show the window
        poller.start();
        verifyDialog.setSize(400, 200);
        verifyDialog.setLocationRelativeTo(null);
        
        // WARNING: Code execution stops here until verifyDialog is disposed!
        verifyDialog.setVisible(true);
    }
}
