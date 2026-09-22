# Model Configuration Identity and Default Selection Design

## Goal

Allow multiple model configurations with the same provider and upstream model key, while making the database model ID the platform identity, preserving model-key snapshots for observability, and applying one default-first fallback rule for every model type.

## Decisions

- `v5ai_model.id` is the only platform identity used for references, runtime resolution, and usage aggregation.
- The `provider_id + model_key` unique constraint is removed. A non-unique lookup index remains.
- Model options display `modelId/modelName (modelType)` so duplicate upstream keys are distinguishable.
- `v5ai_model_usage.model_id` is added and is authoritative for new usage records. `model_key` remains as a historical upstream-name snapshot.
- Existing usage rows cannot be safely backfilled and retain a nullable `model_id`.
- For a requested model type, selection is: enabled default model, then earliest enabled model by ID, otherwise a configuration error.
- Missing EMBEDDING models are errors; implicit local HashEmbedding fallback is removed from the normal path.
- Explicit model IDs remain authoritative; the fallback selector is used only when a caller has not supplied an ID.

## Affected flows

- Model schema and migration history.
- Model option labels and default ordering.
- Runtime model-call metadata and platform usage recording.
- Embedding model fallback and knowledge-base chat model initialization.
- Unit and integration-facing tests, schema/API documentation.

## Compatibility

- Agent, knowledge-base, and summary/title references remain ID-based.
- The external `MODEL_CALL` event keeps the model name payload for existing clients; model ID is carried internally alongside it for persistence.
- Historical usage remains readable with nullable `model_id`.
