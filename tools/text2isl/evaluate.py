import json
import pandas as pd
import torch
from transformers import DistilBertTokenizer
from model import TextToSignModel


def generate_all_animations(model_path='isl_transformer_v1.pth', output_file='prediction.js', labels_csv='labels.csv'):
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"Running inference on: {device}")

    # Load unique sentences from labels.csv
    try:
        df = pd.read_csv(labels_csv)
        sentences = df['sentence'].dropna().unique().tolist()
    except Exception as e:
        print(f"Error loading {labels_csv}: {e}. Falling back to default list.")
        sentences = ["can i help you", "are you free today", "comb your hair", "congratulations"]

    tokenizer = DistilBertTokenizer.from_pretrained('distilbert-base-uncased')
    model = TextToSignModel(feature_dim=196, max_frames=100)

    try:
        model.load_state_dict(torch.load(model_path, map_location=device))
        print("Model weights loaded successfully.")
    except Exception as e:
        print(f"Error loading model weights: {e}")
        return

    model.to(device)
    model.eval()

    master_predictions = {}

    for sentence in sentences:
        print(f"Translating: '{sentence}'")
        text_tokens = tokenizer(
            sentence,
            padding='max_length',
            max_length=20,
            truncation=True,
            return_tensors='pt'
        )

        input_ids = text_tokens['input_ids'].to(device)
        attention_mask = text_tokens['attention_mask'].to(device)

        with torch.no_grad():
            predicted_frames_tensor = model(input_ids, attention_mask=attention_mask)

        predicted_frames = predicted_frames_tensor.squeeze(0).cpu().numpy()

        frames_list = []
        for frame_idx, frame_data in enumerate(predicted_frames):
            frame_list = [float(v) for v in frame_data]
            bs = frame_list[:52]
            pts = frame_list[52:]
            frames_list.append({
                "frame": int(frame_idx),
                "blendshapes": bs,
                "coordinates": pts,
                "quaternions": pts
            })

        master_predictions[sentence] = {
            "sentence": sentence,
            "total_frames": int(predicted_frames.shape[0]),
            "frames": frames_list
        }

    # Write dictionary mapping to window.predictionDatabase
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("window.predictionDatabase = " + json.dumps(master_predictions, indent=2) + ";\n")
        # Default fallback pointer
        first_key = sentences[0] if sentences else "can i help you"
        f.write(f"window.predictionData = window.predictionDatabase['{first_key}'];")

    print(f"Successfully saved multi-sentence database for {len(sentences)} sentences to {output_file}")


if __name__ == '__main__':
    generate_all_animations()