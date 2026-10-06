"""Train a local SetFit classifier for Kesh intents.

This is optional. The default Kesh runtime uses OpenNLP in Spring Boot.
Set kesh.nlp.classifier.type=setfit and start serve.py to swap the provider.
"""
from __future__ import annotations

import json
from pathlib import Path

from datasets import Dataset
from setfit import SetFitModel, Trainer, TrainingArguments
from sentence_transformers import SentenceTransformer

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data" / "kesh-intents.jsonl"
OUT = Path.home() / ".kesh" / "models" / "setfit-intents"


def load_rows(path: Path) -> Dataset:
    texts = []
    labels = []
    with path.open(encoding="utf-8") as fh:
        for line in fh:
            row = json.loads(line)
            texts.append(row["text"])
            labels.append(row["intent"])
    return Dataset.from_dict({"text": texts, "label": labels})


def main() -> None:
    model_name = "sentence-transformers/all-MiniLM-L6-v2"
    model = SetFitModel.from_pretrained(model_name)
    ds = load_rows(DATA)
    unique = sorted(set(ds["label"]))
    model.labels = unique
    args = TrainingArguments(batch_size=16, num_epochs=1, output_dir=str(OUT / "runs"))
    trainer = Trainer(model=model, args=args, train_dataset=ds)
    trainer.train()
    OUT.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(str(OUT))
    SentenceTransformer(model_name)  # keep import used for the recommended backbone
    print("saved", OUT)


if __name__ == "__main__":
    main()
