package chat_ui;

import java.awt.*;
// import java.util.concurrent.Flow;
import java.util.Map;

import javax.swing.*;

import org.mindrot.jbcrypt.BCrypt;


public class Login extends JDialog {
    private JTextField emailField = new JTextField(20);
    private JPasswordField passField = new JPasswordField(20);
    private JButton loginBtn = new JButton("Login to Clinic");
    private String authenticatedUUID = null;
    private JButton openRegisterBtn = new JButton("No account? Sign up");
    private JButton forgotPassBtn = new JButton("Forgot Password?"); // Future feature
    private JButton exitBtn = new JButton("Exit");

    public Login(Frame owner) {
        super(owner, "Login", true);
        setLayout(new GridBagLayout()); // Stick to one layout
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // UI Placement
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; 
        add(emailField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; 
        add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; 
        add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        loginBtn.setFocusPainted(true); // Highlight the login button for better UX
        add(loginBtn, gbc);

        gbc.gridy = 3;
        openRegisterBtn.setFocusPainted(true); // Highlight the register button for better UX
        add(openRegisterBtn, gbc);
        gbc.gridy = 4;
        forgotPassBtn.setFocusPainted(true); // Highlight the forgot password button for better UX
        add(forgotPassBtn, gbc);
        gbc.gridy = 5;
        exitBtn.setFocusPainted(true); // Highlight the exit button for better UX
        add(exitBtn, gbc);

        // --- BUTTON ACTIONS ---

        loginBtn.addActionListener(e -> handleLogin());

        openRegisterBtn.addActionListener(e -> {
            //this.setVisible(false); // Hide login while registration is open
            Registeration reg = new Registeration((Frame)this.getOwner()); // Open registration dialog
            reg.setLocationRelativeTo(this);
            reg.setVisible(true); // Wait for registration to complete
            //this.setVisible(true); // Show login again if registration was cancelled or failed
        });
        
        forgotPassBtn.addActionListener(e -> {
            // Future implementation: Open a dialog to handle password reset
            RequestResetDialog resetDialog = new RequestResetDialog((Frame)this.getOwner());
            resetDialog.setLocationRelativeTo(this);
            resetDialog.setVisible(true);
        });

        exitBtn.addActionListener(e -> {
            System.out.println("Exiting application...");
            System.exit(0);
        });

        pack();
        setLocationRelativeTo(owner);
    }

    private void handleLogin() 
    {
        String email = emailField.getText();
        String password = new String(passField.getPassword());

        String[] cols = {"user_uuid", "first_name", "password_hash", "is_verified"};
        Map<String, Object> userData = Database.getCustomData("chat_users", cols, "email = ?", email);

        // 2. Step One: Does the email even exist?
        if (userData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Invalid email or password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 3. Step Two: Check the Password using BCrypt.checkpw
        String storedHash = (String) userData.get("password_hash");
        
        // IMPORTANT: We do NOT hash the input ourselves. checkpw does it internally using the salt from storedHash.
        if (BCrypt.checkpw(password, storedHash)) {
            
            // 4. Step Three: Check if the account is verified
            Object verifiedObj = userData.get("is_verified");
        
            if (Boolean.FALSE.equals(verifiedObj)) { // If the account is not verified, we can show a message prompting them to verify their account before logging in. This is important for security to prevent unverified accounts from accessing the system, and also provides a better user experience by guiding them on what they need to do next.
                JOptionPane.showMessageDialog(this, 
                    "This account is not verified. Please check your email.", 
                    "Account Not Verified", 
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // 5. SUCCESS!
            this.authenticatedUUID = (String) userData.get("user_uuid");
            String userName = (String) userData.get("first_name");
            
            JOptionPane.showMessageDialog(this, "Welcome back, " + userName + "!");
            this.dispose();

        } else {
            // Password was wrong
            JOptionPane.showMessageDialog(this, "Invalid email or password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    public String getAuthenticatedUUID() 
    { 
        return this.authenticatedUUID; 
    }
}
