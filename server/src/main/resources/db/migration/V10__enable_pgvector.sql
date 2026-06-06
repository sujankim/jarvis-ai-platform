-- ═══════════════════════════════════════════════════
-- V10: Enable pgvector Extension
-- Phase 2: Semantic Memory
--
-- pgvector adds:
-- → vector(n) data type for storing embeddings
-- → Cosine similarity search (<=>)
-- → L2 distance search (<->)
-- → Inner product (<#>)
-- → HNSW index for fast approximate search
--
-- WHY pgvector over a separate vector DB:
-- → No extra infrastructure needed
-- → PostgreSQL handles both relational + vector data
-- → Single backup strategy
-- → ACID transactions include vector operations
-- ═══════════════════════════════════════════════════

-- Enable pgvector extension
-- Must be done before creating vector columns
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify installation
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'vector'
    ) THEN
        RAISE EXCEPTION 'pgvector extension failed to install';
END IF;
END $$;

COMMENT ON EXTENSION vector IS
    'pgvector: vector similarity search for PostgreSQL.
     Used by Jarvis Phase 2 for semantic memory search.
     Enables finding memories by meaning, not just keywords.';