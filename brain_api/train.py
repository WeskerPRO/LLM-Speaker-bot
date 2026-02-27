import torch
import math
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import nltk # Add this
from torch.utils.data import DataLoader
from torch.optim import AdamW # FIXED IMPORT
from transformers import AutoModelForCausalLM, AutoTokenizer
from datasets import load_dataset
from torch.optim.lr_scheduler import ReduceLROnPlateau
from sklearn.feature_extraction.text import TfidfVectorizer
from nltk.translate.bleu_score import sentence_bleu, SmoothingFunction
from tqdm.auto import tqdm # Progress bar

# Required for Colab to handle NLTK logic
nltk.download('punkt')

# 1. Setup Tokenization
def tokenize_fn(examples):
    formatted_texts = []
    for instr, resp in zip(examples["instruction"], examples["response"]):
        # We wrap the Q&A in the actual chat format the model was born with
        messages = [
            {"role": "user", "content": instr},
            {"role": "assistant", "content": resp}
        ]
        # tokenize=False gives us a string with <|im_start|> tags included
        text = tokenizer.apply_chat_template(messages, tokenize=False)
        formatted_texts.append(text)

    outputs = tokenizer(
        formatted_texts,
        truncation=True,
        padding="max_length",
        max_length=512
    )

    outputs["labels"] = outputs["input_ids"].copy()
    return outputs

# 2. Visualization
def plot_results(history):
    epochs = range(1, len(history['train_loss']) + 1)
    plt.figure(figsize=(14, 6))

    plt.subplot(1, 2, 1)
    plt.plot(epochs, history['train_loss'], 'b-o', label="Training Loss")
    plt.plot(epochs, history['val_loss'], 'r-o', label="Validation Loss")
    plt.xlabel("Epochs")
    plt.ylabel("Cross Entropy Loss")
    plt.title("Training and Validation Loss")
    plt.legend()
    plt.grid(True)

    plt.subplot(1, 2, 2)
    plt.plot(epochs, history['perplexity'], 'g-s', label="Perplexity")
    plt.xlabel("Epochs")
    plt.ylabel("Score")
    plt.title("Perplexity Over Epochs (Log Scale)")
    plt.yscale('log')
    plt.legend()
    plt.grid(True)

    plt.tight_layout()
    plt.show()

# 4. Training Loop
def train_model(model, train_loader, val_loader, optimizer, scheduler, epochs, patience):
    best_val_loss = np.inf
    counter = 0
    history = {'train_loss': [], 'val_loss': [], 'perplexity': []}

    for epoch in range(epochs):
        model.train()
        train_loss = 0
        train_pbar = tqdm(train_loader, desc=f"Epoch {epoch+1} [Train]")

        for batch in train_pbar:
            batch = {k: v.to(device) for k, v in batch.items()}
            optimizer.zero_grad()
            outputs = model(**batch)
            loss = outputs.loss
            loss.backward()
            optimizer.step()
            train_loss += loss.item()
            train_pbar.set_postfix({'loss': f"{loss.item():.4f}"})

        model.eval()
        val_loss = 0
        val_pbar = tqdm(val_loader, desc=f"Epoch {epoch+1} [Val]")

        with torch.no_grad():
            for batch in val_pbar:
                batch = {k: v.to(device) for k, v in batch.items()}
                outputs = model(**batch)
                val_loss += outputs.loss.item()
                val_pbar.set_postfix({'val_loss': f"{outputs.loss.item():.4f}"})

        avg_train_loss = train_loss / len(train_loader)
        avg_val_loss = val_loss / len(val_loader)
        ppl = math.exp(avg_val_loss) if avg_val_loss < 20 else 1e6

        history['train_loss'].append(avg_train_loss)
        history['val_loss'].append(avg_val_loss)
        history['perplexity'].append(ppl)

        print(f"Epoch {epoch+1}: Train Loss: {avg_train_loss:.4f} | Val Loss: {avg_val_loss:.4f} | PPL: {ppl:.2f}")

        scheduler.step(avg_val_loss)

        if avg_val_loss < best_val_loss:
            best_val_loss = avg_val_loss
            counter = 0
            model.save_pretrained("./chat_lung_model")
            tokenizer.save_pretrained("./chat_lung_model")
            print("--> Model Saved")
        else:
            counter += 1
            if counter >= patience:
                print("Early stopping triggered.")
                break

    return history

if __name__ == "__main__":
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model_name = "HuggingFaceTB/SmolLM2-1.7B-Instruct" # "HuggingFaceTB/SmolLM2-135M-Instruct"

    tokenizer = AutoTokenizer.from_pretrained(model_name)

    if tokenizer.pad_token is None:
      tokenizer.pad_token = tokenizer.eos_token

    model = AutoModelForCausalLM.from_pretrained(model_name).to(device)

    # --- Data EDA ---
    df = pd.read_json("medical_data.jsonl", lines=True)
    df['word_count'] = df['response'].apply(lambda x: len(x.split()))
    print("\n--- Dataset Description ---\n", df['word_count'].describe())

    tfidf = TfidfVectorizer(stop_words='english', max_features=10)
    weights = tfidf.fit_transform(df['response'])
    print("\n--- Top Medical Keywords ---\n", list(tfidf.get_feature_names_out()))

    # --- Data Prep ---
    dataset = load_dataset("json", data_files="medical_data.jsonl", split="train")
    split = dataset.train_test_split(test_size=0.2, seed=42)

    train_set = split["train"].map(tokenize_fn, batched=True).remove_columns(dataset.column_names)
    val_set = split["test"].map(tokenize_fn, batched=True).remove_columns(dataset.column_names)
    train_set.set_format("torch"); val_set.set_format("torch")

    train_loader = DataLoader(train_set, batch_size=2, shuffle=True)
    val_loader = DataLoader(val_set, batch_size=2)

    optimizer = AdamW(model.parameters(), lr=5e-5, weight_decay=5e-4)
    scheduler = ReduceLROnPlateau(optimizer, mode='min', factor=0.1, patience=2)

    # --- Execution ---
    history = train_model(model, train_loader, val_loader, optimizer, scheduler, 5, 3)
    plot_results(history)
