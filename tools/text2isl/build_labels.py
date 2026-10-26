from pathlib import Path
import pandas as pd

def build_all_labels_csv(root_dir: str = "archive/ISL_CSLRT_Corpus/ISL_CSLRT_Corpus/Videos_Sentence_Level", output_csv: str = "labels.csv"):
    source_dir = Path(root_dir)
    records = []

    if not source_dir.exists():
        print(f"Error: Directory not found at {source_dir}. Please check your path.")
        return

    # Scan each sentence folder
    for sentence_folder in sorted(source_dir.iterdir()):
        if sentence_folder.is_dir():
            sentence_name = sentence_folder.name.lower().strip()
            
            # Find all mp4 files inside this sentence folder
            video_files = list(sentence_folder.rglob("*.mp4")) + list(sentence_folder.rglob("*.MP4"))
            for video_path in video_files:
                records.append({
                    "filename": video_path.stem,
                    "sentence": sentence_name
                })

    df = pd.DataFrame(records).drop_duplicates().reset_index(drop=True)
    df.to_csv(output_csv, index=False)
    print(f"Successfully generated {output_csv} with {len(df)} total samples across {df['sentence'].nunique()} sentences.")
    print("Sentences found:", df['sentence'].unique().tolist())
    return df

if __name__ == "__main__":
    build_all_labels_csv()