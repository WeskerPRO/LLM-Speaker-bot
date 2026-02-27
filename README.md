# LLM Speaker Bot 🤖🔊
![Version](https://img.shields.io/badge/version-0.1.0-blue)

An AI Doctor Chat Bot powered by **SmolLM2-1.7B-Instruct**, featuring a Java desktop UI with a full registration/login system and a Python AI backend.

## What is this?
A desktop application where users can **chat and talk with an AI Doctor bot**. The app includes a secure registration and login system before accessing the chat. The AI model was custom trained to act as a medical assistant.

## Project Structure
```
project/
├── brain_api/              # Python backend
│   ├── chat_ai_model/      # AI model files (not included, see Setup)
│   ├── main.py             # Runs the API server
│   ├── train.py            # Training script for the AI model
│   └── db_manager.py       # Database helper (connects to Java's database)
│
└── chat_app/               # Java desktop frontend
    ├── src/java/chat_ui/
    │   ├── ChatWindow.java          # Main app window (chat with AI Doctor)
    │   ├── Database.java            # Creates and manages the database
    │   ├── Login.java               # Login window (called by ChatWindow)
    │   ├── Registration.java        # Registration system (email, password, birthdate, email verification)
    │   ├── PasswordUpdateDialog.java # Password reset request (email input)
    │   └── RequestResetDialog.java  # Password reset process window
    ├── src/resources/
    └── pom.xml
```

## Features
- 🔐 **Secure Auth System** — Register, Login, Email Verification, Password Reset
- 🤖 **AI Doctor Bot** — Powered by SmolLM2-1.7B-Instruct (custom trained)
- 💬 **Chat & Talk** — Users can write or speak to the AI Doctor
- 🗄️ **Integrated Database** — Java manages the DB, Python connects to it

## How It Works
1. User launches `ChatWindow.java`
2. Login screen appears (always, for security)
3. New users can **Register** with email, password, and birthdate → email verification required
4. Forgot password? → Request reset via email
5. Once logged in → **Chat with the AI Doctor Bot** 🩺

## Requirements

### Brain API (Python)
- Python 3.x
- Dependencies:
```bash
pip install -r requirements.txt
```
- Download the AI model and place it in `brain_api/chat_ai_model/`

### Chat App (Java)
- Java 17+
- Maven

## Setup & Run

### 1. Start the Python API
```bash
cd brain_api
python main.py
```

### 2. Run the Java App
```bash
cd chat_app
mvn install
mvn exec:java
```
Or simply run `ChatWindow.java` from your IDE.

## Notes
> ⚠️ AI model files are **not included** in this repository due to size limitations.
> Download or train the model separately and place it in `brain_api/chat_ai_model/`

## Roadmap
- [x] Python LLM backend (SmolLM2-1.7B-Instruct)
- [x] Custom AI model training
- [x] Java Chat UI
- [x] Registration & Login system
- [x] Email verification & Password reset
- [ ] Flutter mobile app integration
- [ ] Voice output (Speaker feature)
- [ ] Packaging & deployment

## License
MIT