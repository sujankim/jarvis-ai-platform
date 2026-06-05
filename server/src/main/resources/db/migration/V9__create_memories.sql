-- ═══════════════════════════════════════════════════
-- V9: Create Memories Table
-- Phase 2: Memory System
--
-- Stores long-term facts Jarvis learns about users
-- across all sessions.
--
-- Memory Types:
--   FACT       → "User's name is Dravin"
--   GOAL       → "User is building Jarvis platform"
--   PREFERENCE → "User prefers detailed explanations"
--   CONTEXT    → "User uses Windows 11, 16GB RAM"
--   EVENT      → "User published first article June 2026"
-- ═══════════════════════════════════════════════════

CREATE TABLE memories (

                          id              UUID            NOT NULL
                                                                   DEFAULT gen_random_uuid(),

                          user_id         UUID            NOT NULL,

    -- Type of memory (validated by constraint)
                          type            VARCHAR(20)     NOT NULL,

    -- The actual memory text content
                          content         TEXT            NOT NULL,

    -- Which session this memory was extracted from
    -- NULL = manually added via CLI command
                          source_session  UUID,

    -- Priority for prompt injection
    -- 0.0 = trivial, 1.0 = critical
    -- Default 0.5 = moderate importance
                          importance      DECIMAL(3,2)    NOT NULL
                                                                   DEFAULT 0.50,

    -- How many times this memory was used in prompts
    -- High count = more relevant to this user
                          access_count    INTEGER         NOT NULL DEFAULT 0,

    -- Last time this memory was retrieved
                          last_accessed   TIMESTAMPTZ,

                          created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                          updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- ── Constraints ──────────────────────────────

                          CONSTRAINT pk_memories
                              PRIMARY KEY (id),

                          CONSTRAINT fk_memories_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users (id)
                                  ON DELETE CASCADE,

                          CONSTRAINT fk_memories_session
                              FOREIGN KEY (source_session)
                                  REFERENCES chat_sessions (id)
                                  ON DELETE SET NULL,

                          CONSTRAINT chk_memories_type
                              CHECK (type IN (
                                              'FACT',
                                              'GOAL',
                                              'PREFERENCE',
                                              'CONTEXT',
                                              'EVENT'
                                  )),

                          CONSTRAINT chk_memories_importance
                              CHECK (importance >= 0.00
                                  AND importance <= 1.00),

                          CONSTRAINT chk_memories_content_not_empty
                              CHECK (LENGTH(TRIM(content)) > 0),

                          CONSTRAINT chk_memories_access_count
                              CHECK (access_count >= 0)
);

-- ── Indexes ───────────────────────────────────────

-- Most common query: all memories for a user
CREATE INDEX idx_memories_user_id
    ON memories (user_id);

-- Filter by type for a user
CREATE INDEX idx_memories_user_type
    ON memories (user_id, type);

-- Most important memories first (prompt injection)
CREATE INDEX idx_memories_user_importance
    ON memories (user_id, importance DESC);

-- Most recently accessed (relevance ranking)
CREATE INDEX idx_memories_user_accessed
    ON memories (user_id, last_accessed DESC NULLS LAST);

-- Source session lookup
CREATE INDEX idx_memories_source_session
    ON memories (source_session)
    WHERE source_session IS NOT NULL;

-- ── Auto-update trigger ───────────────────────────

-- Reuses the update_updated_at_column() function
-- created in V1__create_users.sql
CREATE TRIGGER trigger_memories_updated_at
    BEFORE UPDATE ON memories
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ── Comments ──────────────────────────────────────

COMMENT ON TABLE memories IS
    'Long-term memories Jarvis learns about users.
     Used in Phase 2 to personalize AI responses.
     Injected into prompts based on importance score.';

COMMENT ON COLUMN memories.type IS
    'FACT: true information about user.
     GOAL: what user is working toward.
     PREFERENCE: how user likes things done.
     CONTEXT: current project/situation.
     EVENT: things that happened.';

COMMENT ON COLUMN memories.importance IS
    '0.0 = trivial, 1.0 = critical.
     Higher importance = injected into prompts first.
     Managed by application logic.';

COMMENT ON COLUMN memories.access_count IS
    'Incremented each time memory is used in a prompt.
     High count signals strong relevance to this user.';

COMMENT ON COLUMN memories.source_session IS
    'NULL = manually added by user via CLI command.
     Set = automatically extracted from conversation.';