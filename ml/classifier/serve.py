"""Optional local HTTP sidecar for a trained SetFit model."""
from __future__ import annotations

import json
from pathlib import Path

from flask import Flask, jsonify, request
from setfit import SetFitModel

MODEL_DIR = Path.home() / ".kesh" / "models" / "setfit-intents"
app = Flask(__name__)
model = SetFitModel.from_pretrained(str(MODEL_DIR))


@app.post("/classify")
def classify():
    payload = request.get_json(force=True) or {}
    text = payload.get("text") or ""
    pred = model.predict([text])[0]
    probs = None
    try:
        probs = model.predict_proba([text])[0]
    except Exception:
        probs = None
    confidence = 0.8
    if probs is not None:
        confidence = float(max(probs))
    return jsonify({"intent": str(pred), "confidence": confidence, "intents": [
        {"intent": str(pred), "confidence": confidence}
    ]})


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=8091)
