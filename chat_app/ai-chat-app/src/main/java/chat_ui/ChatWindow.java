package chat_ui;

import javax.swing.*;
// import javax.xml.crypto.Data;

import org.mindrot.jbcrypt.BCrypt;

import java.awt.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;
import io.javalin.Javalin;
import java.util.Map;

/* 
import java.awt.event.*;
import java.util.Enumeration;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;
*/

public class ChatWindow extends JFrame {
    private JTextArea chatArea; // Where the conversation will be displayed
    private JTextField inputField; // Where the user types their message
    private JButton sendButton, clearButton, saveButton, historyButton; // Added historyButton to view past conversations
    private HttpClient client; // For making HTTP requests to the Python server
    private String userId; // In a real app, you'd generate or manage unique user IDs properly - Temporary hardcoded user ID for demonstration
    private JProgressBar progressBar; // To show when the AI is "thinking"

    public ChatWindow(String userId) {
        // 1. Setup the Window
        this.userId = userId;
        setTitle("DoctorBot - Patient: " + this.userId); // A more descriptive title
        setSize(500, 600); // A slightly larger window for better readability
        setDefaultCloseOperation(EXIT_ON_CLOSE); // Close app when window is closed
        setLayout(new BorderLayout()); // Use BorderLayout for easy component placement

        // 2. Chat History Area
        this.chatArea = new JTextArea(); // Where the conversation will be displayed
        this.chatArea.setEditable(false); // User shouldn't edit past messages
        this.chatArea.setLineWrap(true); // Wrap lines for better readability
        this.chatArea.setWrapStyleWord(true); // Wrap at word boundaries
        this.chatArea.setBackground(new Color(245, 245, 250)); // Light gray background for a modern look
        this.chatArea.setFont(new Font("Arial", Font.PLAIN, 14)); // A clean, readable font
        this.chatArea.setForeground(new Color(44, 62, 80));
        this.chatArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding around the text
        
        JScrollPane scrollPane = new JScrollPane(this.chatArea); //  Add scroll functionality to the chat area
        add(scrollPane, BorderLayout.CENTER); // Place in the center of the window

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT)); // A simple toolbar at the top
        toolbar.setBackground(new Color(52, 73, 94)); // Darker background
        this.clearButton = new JButton("Clear Chat");
        this.saveButton = new JButton("Save Chat");
        this.historyButton = new JButton("View History"); // New button to view chat history

        this.clearButton.setBackground(new Color(231, 76, 60)); // Red color
        this.clearButton.setForeground(Color.WHITE);

        this.saveButton.setBackground(new Color(39, 174, 96)); // Green color
        this.saveButton.setForeground(Color.WHITE);

        this.historyButton.setBackground(new Color(142, 68, 173)); // Modern Purple
        this.historyButton.setForeground(Color.WHITE);

        this.clearButton.setFocusPainted(false); // Remove focus border for a cleaner look
        this.saveButton.setFocusPainted(false); // Remove focus border for a cleaner look
        this.historyButton.setFocusPainted(false);

        this.historyButton.addActionListener(e -> {
            String history = Database.loadFullHistory(userId);
            if (!history.isEmpty()) {
                this.chatArea.setText(history);
                this.chatArea.append("--- History Restored ---\n\n");
                this.chatArea.setCaretPosition(this.chatArea.getDocument().getLength());
            }
        });

        toolbar.add(this.clearButton);
        toolbar.add(this.saveButton);
        toolbar.add(this.historyButton);

        add(toolbar, BorderLayout.NORTH); // Place toolbar at the top
        // 3. Input Panel (Field + Button)
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10)); // Use BorderLayout to place field and button side by side
        JPanel southPanel = new JPanel(new BorderLayout()); // A panel to hold both the progress bar and the input area

        inputPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15)); // Padding around the input area
        inputPanel.setBackground(Color.WHITE); // Slightly different light gray for the input area

        this.inputField = new JTextField(); // Where the user types their message
        this.inputField.setFont(new Font("Segoe UI", Font.PLAIN, 15)); // Match the chat area font for consistency

        this.sendButton = new JButton("ASK DOCTOR"); // Button to send the message
        this.sendButton.setBackground(new Color(41, 128, 185)); // A nice blue color for the button
        this.sendButton.setForeground(Color.WHITE); // White text on the button
        this.sendButton.setFocusPainted(false);
        this.sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13)); // Bold font for the button
        
        inputPanel.add(this.inputField, BorderLayout.CENTER); // Input field takes up most of the space
        inputPanel.add(this.sendButton, BorderLayout.EAST); // Send button on the right

        this.progressBar = new JProgressBar();
        this.progressBar.setIndeterminate(true); // This makes the "bouncing" animation
        this.progressBar.setString("Analyzing clinical data..."); // Set specific medical context
        this.progressBar.setVisible(false);
        this.progressBar.setStringPainted(true);
        this.progressBar.setForeground(new Color(41, 128, 185)); // Match your blue theme

        southPanel.add(this.progressBar, BorderLayout.NORTH); // Progress bar on top
        southPanel.add(inputPanel, BorderLayout.CENTER);      // Input field below it
        add(southPanel, BorderLayout.SOUTH); // Place the combined panel at the bottom of the window

        // add(inputPanel, BorderLayout.SOUTH); // Place input panel at the bottom of the window

        // 4. Logic
        client = HttpClient.newHttpClient(); // Initialize HTTP client for making requests to the Python server
        this.sendButton.addActionListener(e -> sendMessage()); // When the button is clicked, call the sendMessage method
        this.inputField.addActionListener(e -> sendMessage()); // When the user presses Enter in the input field, also call sendMessage
        this.clearButton.addActionListener(e -> {
            this.chatArea.setText(""); // Clear the chat area
            this.chatArea.append("System: Screen cleared for a new topic.\n"); // Add a system message indicating the chat was cleared
        }); // Clear the chat area when "Clear Chat" is clicked
        
        this.saveButton.addActionListener(e -> {
            try (java.io.FileWriter writer = new java.io.FileWriter("Medical_Consultation.txt")) {
                writer.write(this.chatArea.getText()); // This is perfect for a text file!
                JOptionPane.showMessageDialog(this, "Consultation exported to Medical_Consultation.txt");
            } catch (java.io.IOException ex) {
                ex.printStackTrace();
            }
        }); // Save the chat history to a text file when "Save Chat" is clicked

        setLocationByPlatform(true); // Let the OS decide where to place the window
        setLocationRelativeTo(null); // Center the window on the screen
        setVisible(true); // Show the window
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) return;

        Database.saveMessage(userId, userId, message); // Save the user's message to the database

        String historyJson = Database.loadHistoryAsJson(userId);

        this.chatArea.append(userId + ": " + message + "\n\n");
        this.inputField.setText("");
        
        this.progressBar.setVisible(true); 
        this.progressBar.setString("Pulmonologist is analyzing...");
        this.sendButton.setEnabled(false);
        this.inputField.setEditable(false);

        this.progressBar.revalidate();
        this.progressBar.repaint();

        // Run AI request in background so the UI doesn't "freeze"
        CompletableFuture.runAsync(() -> {
            try {
                String jsonBody; HttpRequest request = null;
                HttpResponse<String> response = null;

                jsonBody = String.format("{\"userid\": \"%s\", \"messages\": %s}", userId, historyJson);

                request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:8000/ask"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                // Extract "reply" from JSON
                String rawResponse, aiReply;
                final String displayResponse;

                rawResponse = response.body();
                aiReply = rawResponse.split("\"reply\":\"")[1];
                aiReply = aiReply.substring(0, aiReply.lastIndexOf("\"}"))
                             .replace("\\n", "\n")
                             .replace("\\\"", "\"");
                
                displayResponse = (aiReply.isEmpty() ? "Sorry, I couldn't generate a response." : aiReply);
                SwingUtilities.invokeLater(() -> {
                    this.chatArea.append("DoctorBot: " + displayResponse + "\n\n");
                    this.chatArea.setCaretPosition(this.chatArea.getDocument().getLength()); // Scroll to the bottom
                    Database.saveMessage(userId, "DoctorBot", displayResponse); // Save the AI's response to the database
                    resetUIState(); // Re-enable the UI components after the response is processed
                });
            } 
            catch (Exception ex) 
            {
                SwingUtilities.invokeLater(() -> {
                    this.chatArea.append("SYSTEM: Connection lost.\n\n");
                    resetUIState();
                });
            }
        });
    }

    private void resetUIState() {
        this.progressBar.setVisible(false); // Hide the progress bar
        this.sendButton.setEnabled(true); // Re-enable the send button
        this.inputField.setEditable(true); // Re-enable the input field
        this.inputField.requestFocusInWindow(); // Set focus back to the input field for convenience
    }

    public static void main(String[] args) {
        Database.initialize(); // Ensure the database is set up before launching the chat window
        /* 
        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost()); 
            });
        }).start(4567);

        // 3. THE LOGIN ENDPOINT
        app.post("/login", ctx -> {
            // Get data from Flutter
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String email = body.get("email");
            String password = body.get("password");

            // REUSE YOUR EXISTING DATABASE LOGIC
            String[] cols = {"user_uuid", "first_name", "password_hash", "is_verified"};
            Map<String, Object> userData = Database.getCustomData("chat_users", cols, "email = ?", email);

            if (userData.isEmpty()) {
                ctx.status(401).json(Map.of("message", "Invalid email or password."));
                return;
            }

            // REUSE YOUR BCRYPT LOGIC
            String storedHash = (String) userData.get("password_hash");
            if (BCrypt.checkpw(password, storedHash)) {
                
                // Check verification
                if (Boolean.FALSE.equals(userData.get("is_verified"))) {
                    ctx.status(403).json(Map.of("message", "Account not verified."));
                    return;
                }

                // SUCCESS: Return the UUID and Name to Flutter
                ctx.json(Map.of(
                    "status", "success",
                    "user_uuid", userData.get("user_uuid"),
                    "user_name", userData.get("first_name")
                ));
            } else {
                ctx.status(401).json(Map.of("message", "Invalid email or password."));
            }
        });

        System.out.println("Java Database Engine is running on port 4567...");
        */
        
        Login loginWindow = new Login(null); 
        loginWindow.setVisible(true);

        String finalUserId = loginWindow.getAuthenticatedUUID(); // Get the authenticated user's UUID after login or registration
        
        System.out.println("Authenticated User UUID: " + finalUserId); // Log the authenticated user ID for debugging
        if (finalUserId != null && !finalUserId.isEmpty()) { // SUCCESS: We have a valid user ID, so we can open the chat window
            SwingUtilities.invokeLater(() -> {
                ChatWindow chat = new ChatWindow(finalUserId);
                chat.setVisible(true);
            });
        }
        
    }
}