package chat_ui;
import javax.swing.*;
import java.awt.*;

public class PasswordUpdateDialog extends JDialog {
    private String userEmail; // Passed from the previous window
    private JPasswordField newPassField = new JPasswordField(20);
    private JPasswordField confirmPassField = new JPasswordField(20);
    private JButton updateBtn = new JButton("Update Password");
    private JButton cancelBtn = new JButton("Cancel");

    public PasswordUpdateDialog(Frame owner, String email) {
        super(owner, "Secure Password Update", true);
        this.userEmail = email;
        
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 15, 10, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Header
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel title = new JLabel("Updating account: " + this.userEmail);
        title.setFont(new Font("Arial", Font.ITALIC, 11));
        add(title, gbc);

        // New Password
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        add(new JLabel("New Password:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0; // Allow the password field to expand
        add(newPassField, gbc);

        // Confirm Password
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; // Reset weight for label
        add(new JLabel("Confirm Password:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; // Allow the confirm password field to expand
        add(confirmPassField, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(cancelBtn);
        btnPanel.add(updateBtn);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 0; // Reset weight for button panel
        add(btnPanel, gbc);

        // Event Listeners
        updateBtn.addActionListener(e -> this.handleUpdateLogic()); // This will contain the API call and validation logic
        cancelBtn.addActionListener(e -> this.dispose()); // Close the dialog without making changes

        pack();
        setLocationRelativeTo(owner);
    }

    private void handleUpdateLogic() {
        String newPass = new String(newPassField.getPassword());
        String confirmPass = new String(confirmPassField.getPassword());

        // Basic validation
        if (newPass.isEmpty() || confirmPass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All fields are required.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!newPass.equals(confirmPass)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Here we would add the logic to verify the code and update the password in the database.
        // This would typically involve an API call to the backend which checks the code against what's stored for that email, and if valid, updates the password.
        String hashedPass = Database.hashPw(newPass);

        boolean success = Database.updateData("chat_users", "password_hash = ?", "email = ?", hashedPass, this.userEmail);

        if (success) {
            JOptionPane.showMessageDialog(this, "Your password has been successfully updated!");
            this.dispose(); // Close the dialog after successful update
        } else {
            JOptionPane.showMessageDialog(this, "Invalid code or error updating password. Please try again.", "Update Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
