-- ═══════════════════════════════════════════════════
-- V18: Update Gemini model to gemini-3.5-flash
--
-- gemini-2.0-flash was shut down June 1, 2026.
-- Google's official recommended replacement is
-- gemini-3.5-flash (released May 19, 2026,
-- no announced shutdown date as of July 2026).
--
-- Reference:
-- https://ai.google.dev/gemini-api/docs/deprecations
-- ═══════════════════════════════════════════════════

UPDATE ai_providers
SET model_name   = 'gemini-3.5-flash',
    display_name = 'Gemini 3.5 Flash (Cloud — Fallback)',
    updated_at   = NOW()
WHERE name         = 'gemini-flash'
  AND provider_type = 'GEMINI';