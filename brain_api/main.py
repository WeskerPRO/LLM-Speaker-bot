from fastapi import FastAPI, HTTPException
import smtplib
from email.message import EmailMessage
from fastapi.concurrency import asynccontextmanager
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
from transformers import AutoModelForCausalLM, AutoTokenizer, pipeline
import torch
from db_manager import create_db_pool, execute_query, fetch_query, execute_transaction_query
import asyncio
from datetime import datetime, timedelta
from fastapi.middleware.cors import CORSMiddleware

SMTP_SERVER = "smtp.gmail.com" # (Gmail's) SMTP server
SMTP_PORT = 587 # Standard port for secure email submission (TLS)
SENDER_EMAIL = "" # Use your actual Gmail address here. Make sure to set up an App Password if you have 2FA enabled.
SENDER_PASSWORD = "" # For security, you should use environment variables or a secure vault to store sensitive information like email credentials in a real application. This is just for demonstration purposes.

# 1. Load your CUSTOM "Lung AI" model
MODEL_PATH = "./chat_lung_model" 

print("Loading your fine-tuned Lung AI...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH) 
model = AutoModelForCausalLM.from_pretrained(
    MODEL_PATH,
    torch_dtype=torch.float16 if torch.cuda.is_available() else torch.float32, # Use float16 for faster inference on compatible GPUs, otherwise fall back to float32 for CPU
    device_map="auto" # Automatically place model layers on available devices (GPU if available, otherwise CPU)
)

# Use the pipeline for easier chat handling
generator = pipeline("text-generation", model=model, tokenizer=tokenizer) # No need for 'device' argument when using device_map="auto"
# chat_histories = {} # In a real app, you'd use a database. For now, we use a global dictionary.

@asynccontextmanager
async def lifespan(app: FastAPI):
    # --- STARTUP LOGIC ---
    # Initialize the global pool once
    global db_pool
    db_pool = create_db_pool()
    
    # Run the cleanup task in the background
    asyncio.create_task(cleanup_expired_accounts(db_pool))
    
    print("Server starting: Database pool initialized and cleanup triggered.")
    
    yield  # The app runs while this is "yielding"
    
    # --- SHUTDOWN LOGIC ---
    if db_pool:
        db_pool._remove_connections()
    print("Server stopping: Database pool closed.")

app = FastAPI(lifespan=lifespan) # We will define the lifespan function later to handle startup and shutdown events
db_pool = None # We'll initialize this in the lifespan function

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Allows your Flutter app to connect
    allow_credentials=True,
    allow_methods=["*"],  # Allows POST, GET, etc.
    allow_headers=["*"],
)

class ChatRequest(BaseModel): # Define the expected structure of the incoming request for the /ask endpoint
    userid: str # To track conversations per user (optional, but useful for context)
    messages: list # The user's input message

@app.post("/ask")
async def ask_ai(request: ChatRequest): 
    # This structure matches the SmolLM2-Instruct format
    master_prompt = {"role": "system", "content": "You are AI Doctor bot, a professional Pulmonologist."}
    full_history = [master_prompt] # Start with the system prompt
    full_conversation = full_history + request.messages # Add the user's messages to the conversation history 
    
    # print(f"{request.messages}\n") # Debug: See the incoming messages
    print(full_conversation)

    # Generate response based ONLY on this specific request
    output = generator(full_conversation, max_new_tokens=60, do_sample=True, temperature=0.7, truncation=True)
    ai_response = output[0]['generated_text'][-1]['content']
    
    return {"reply": ai_response}
                            

@app.post("/send-activation")
async def send_activation(email: str, name: str, token: str): # The token is generated in the Java registration class and stored in the DB
    # This is the URL the user will click in their email inbox
    activation_link = f"http://127.0.0.1:8000/activate?email={email}&token={token}"
    
    msg = EmailMessage()
    msg.set_content(f"Hello {name}!\n\nPlease click the link below to activate your account:\n{activation_link}")
    msg["Subject"] = "Activate Your DoctorBot Account"
    msg["From"] = SENDER_EMAIL
    msg["To"] = email
    print(f"Sending link to {email}: {activation_link}")

    try:
        with smtplib.SMTP(SMTP_SERVER, SMTP_PORT) as server:
            server.starttls()
            server.login(SENDER_EMAIL, SENDER_PASSWORD)
            server.send_message(msg)
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    
@app.get("/activate", response_class=HTMLResponse)
async def activate_user(email: str, token: str):
    try:
        # 1. Verify the token matches the email --> Expire - Now > 0 means the token is still valid
        query = "SELECT * FROM chat_users WHERE email = %s AND verification_token = %s AND verification_expiration > NOW()" # We also check that the token hasn't expired yet
        user = await fetch_query(db_pool, query, (email, token), fetch_one=True) # We only expect one user to match this email and token
        
        if not user: # If no user is found, the link is either invalid or has already been used (since we clear the token after successful verification)
            return "<h1>Link Expired</h1><p>This link is invalid or expired. Please register again to get a new link.</p>"
        
        # 2. Token matches! Verify them and clear the token so it can't be used again
        update_query = "UPDATE chat_users SET is_verified = 1, verification_token = NULL, verification_expiration = NULL WHERE verification_token = %s and email = %s"
        result = await execute_query(db_pool, update_query, (token, email))
        
        if result is None:
            return "<h1>Database Error</h1><p>Unable to update user verification status. Please try again later.</p>"
        
        return "<h1>Success!</h1><p>Your account has been verified. You may now return to the app and login.</p>"
            
    except Exception as e:
        return f"<h1>Error</h1><p>An unexpected error occurred: {str(e)}</p>"
    

@app.post("/send-reset-password")
async def send_reset_password(email: str, name: str, token: str):
    reset_link = f"http://127.0.0.1:8000/reset-activate?email={email}&token={token}"
    msg = EmailMessage()
    msg.set_content(f"Hello {name}!\n\nPlease click the link below to reset your password:\n{reset_link}")
    msg["Subject"] = "Reset Your DoctorBot Password"
    msg["From"] = SENDER_EMAIL
    msg["To"] = email
    print(f"Sending password reset link to {email}: {reset_link}")
    
    try:
        with smtplib.SMTP(SMTP_SERVER, SMTP_PORT) as server:
            server.starttls()
            server.login(SENDER_EMAIL, SENDER_PASSWORD)
            server.send_message(msg)
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/reset-activate", response_class=HTMLResponse)
async def reset_activate(email: str, token: str):
    try:
        query = "SELECT * FROM chat_users WHERE email = %s AND reset_token = %s AND reset_expiration > NOW()" # We also check that the reset token hasn't expired yet
        user = await fetch_query(db_pool, query, (email, token), fetch_one=True) # We only expect one user to match this email and token
        
        if not user:
             return "<h1>Invalid Link</h1><p>This password reset link is expired or invalid.</p>"
        
        update_query = "UPDATE chat_users SET reset_token = NULL, reset_status = 'APPROVED', reset_expiration = NULL WHERE reset_token = %s and email = %s" # Clear the reset token and mark the reset request as approved so the frontend can show the password reset form
        result = await execute_query(db_pool, update_query, (token, email))
        
        if result is None: # If the update query failed, we should inform the user
            return "<h1>Database Error</h1><p>Unable to update reset token status. Please try again later.</p>"
        
        return "<h1>Success!</h1><p>You can now reset your password securely.</p>" # In a real app, you'd probably want to redirect them to a password reset form instead of just showing a success message here, but this is just for demonstration purposes.

    except Exception as e:
        return f"<h1>Error</h1><p>An unexpected error occurred: {str(e)}</p>"

async def cleanup_expired_accounts(db_pool):
    queries = [
        # 1. Delete unverified
        """
        DELETE FROM chat_users 
            WHERE is_verified = 0 
            AND verification_expiration < DATE_SUB(NOW(), INTERVAL 7 DAY)
        """,
        
        # 2. Clear Ghost Verification Data
        """
        UPDATE chat_users
            SET verification_token = NULL, verification_expiration = NULL
            WHERE is_verified = 1 AND (verification_expiration < NOW() OR verification_token IS NOT NULL)
        """,
        
        # 3. Expire Resets
        """
        UPDATE chat_users
            SET reset_token = NULL, reset_expiration = NULL, reset_status = 'EXPIRED'
            WHERE reset_status = 'PENDING' AND reset_expiration < DATE_SUB(NOW(), INTERVAL 1 DAY)
        """,
    ]

    try:
        # Pass the list to our new helper
        result = await execute_transaction_query(db_pool, queries)
        # print(f"Cleanup result: {result}")

        if result:
            print(f"[{datetime.now()}] Database Maintenance Complete. {len(result)} tasks synced.")
        else:
            print("Maintenance failed - Check logs.")
    except Exception as e:
        print(f"Cleanup error: {e}")

# 2. Pass the lifespan to the FastAPI constructor

if __name__ == "__main__":
    import uvicorn
    # Make sure port 8000 is open in your local environment
    uvicorn.run(app, host="127.0.0.1", port=8000)