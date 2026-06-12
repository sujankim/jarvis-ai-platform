-- ═══════════════════════════════════════════════════
-- V12: Add unique constraint on memories (user_id, content)
-- Phase 2: Memory System
--
-- WHY THIS IS NEEDED:
-- MemoryService.save() does check-then-insert:
--   1. Check if duplicate exists (SELECT)
--   2. Insert if not (INSERT)
-- Under concurrent requests, two threads can both
-- pass the check and both insert → duplicates!
--
-- DB-level constraint makes duplicates impossible
-- regardless of concurrency. The application-level
-- check becomes a fast-path optimization only.
--
-- WHY LOWER(TRIM(content)):
-- "User is building Jarvis" and
-- "user is building jarvis " are the same memory.
-- Normalize before comparing.
-- ═══════════════════════════════════════════════════

-- Functional unique index on normalized content
-- Prevents duplicates even under concurrent inserts
CREATE UNIQUE INDEX idx_memories_user_content_unique
    ON memories (user_id, LOWER(TRIM(content)));

COMMENT ON INDEX idx_memories_user_content_unique IS
    'Prevents duplicate memories per user.
     Normalized (lowercased + trimmed) for comparison.
     Application-level check is optimization only.';