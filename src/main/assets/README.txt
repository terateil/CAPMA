# CAPMA Embedding Models

## MediaPipe Model (Default)
- File: universal_sentence_encoder.tflite
- This is needed for the MediaPipe text embedder implementation.
- Download the Universal Sentence Encoder TFLite model from:
  https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/latest/universal_sentence_encoder.tflite
- Place the downloaded file in this assets directory.

## ONNX Model (Alternative)
- File: sentence_transformer.onnx
- This is used for the ONNX-based sentence embedding implementation.

Both embedding methods are supported in the SentenceEncoder class. MediaPipe is the default method.
You can switch between methods by calling:
`sentenceEncoder.setEmbeddingMethod(SentenceEncoder.EmbeddingMethod.MEDIAPIPE)` (default)
or
`sentenceEncoder.setEmbeddingMethod(SentenceEncoder.EmbeddingMethod.ONNX)`

The model should be a sentence transformer model that takes text input and outputs embeddings.
For example, you can convert models like 'all-MiniLM-L6-v2' to ONNX format.

Note: The current implementation uses dummy embeddings for demonstration purposes.
When you add a real model, update the SentenceEncoder class to use the actual model inputs and outputs. 