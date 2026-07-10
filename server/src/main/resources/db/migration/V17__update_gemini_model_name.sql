-- ═══════════════════════════════════════════════════
-- V17: Update Gemini model name in ai_providers
--
-- V8 seeded gemini-1.5-flash as the Gemini fallback
-- provider. gemini-2.0-flash is the current stable
-- free-tier model as of 2026.
--
-- gemini-3.5-flash in application.yml was also wrong
-- as this model does not exist in the Google AI API.
-- application.yml has been corrected to gemini-2.0-flash.
--
-- Reference:
-- https://ai.google.dev/gemini-api/docs/models/gemini
-- ═══════════════════════════════════════════════════

UPDATE ai_providers
SET model_name   = 'gemini-2.0-flash',
    display_name = 'Gemini 2.0 Flash (Cloud — Fallback)',
    updated_at   = NOW()
WHERE name = 'gemini-flash'
  AND provider_type = 'GEMINI';