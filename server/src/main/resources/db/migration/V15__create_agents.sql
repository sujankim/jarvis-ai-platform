-- ═══════════════════════════════════════════════════
-- V15: Create Agents Table
-- Phase 6: Agent System
--
-- An agent = one agentic task execution.
-- User gives a goal → agent plans + executes steps
-- to achieve it using available tools.
--
-- LIFECYCLE:
-- PENDING → RUNNING → COMPLETED
--                  ↘ FAILED
--                  ↘ CANCELLED
-- ═══════════════════════════════════════════════════

CREATE TABLE agents (

                        id              UUID        NOT NULL
                                                             DEFAULT gen_random_uuid(),

                        user_id         UUID        NOT NULL,

    -- Optional: links agent to a chat session
    -- NULL = standalone agent task
                        session_id      UUID,

    -- The user's original goal or task description
                        goal            TEXT        NOT NULL,

    -- Current execution status
                        status          VARCHAR(20) NOT NULL
                                                             DEFAULT 'PENDING',

    -- Final synthesized response to user
    -- NULL while agent is still running
                        final_answer    TEXT,

    -- How many ReACT steps were executed
                        step_count      INTEGER     NOT NULL DEFAULT 0,

    -- Error message if FAILED
                        error_message   TEXT,

    -- Total execution time in milliseconds
                        duration_ms     INTEGER,

                        created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Set when status becomes COMPLETED/FAILED/CANCELLED
                        completed_at    TIMESTAMPTZ,

    -- ── Constraints ──────────────────────────────

                        CONSTRAINT pk_agents
                            PRIMARY KEY (id),

                        CONSTRAINT fk_agents_user
                            FOREIGN KEY (user_id)
                                REFERENCES users (id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_agents_session
                            FOREIGN KEY (session_id)
                                REFERENCES chat_sessions (id)
                                ON DELETE SET NULL,

    -- Only valid status values
                        CONSTRAINT chk_agents_status
                            CHECK (status IN (
                                              'PENDING',
                                              'RUNNING',
                                              'COMPLETED',
                                              'FAILED',
                                              'CANCELLED'
                                )),

    -- Goal must not be empty
                        CONSTRAINT chk_agents_goal_not_empty
                            CHECK (LENGTH(TRIM(goal)) > 0),

    -- Step count can never be negative
                        CONSTRAINT chk_agents_step_count
                            CHECK (step_count >= 0),

    -- Terminal states must have completed_at set
    -- Prevents COMPLETED/FAILED/CANCELLED with NULL timestamp
                        CONSTRAINT chk_agents_terminal_completed_at
                            CHECK (
                                NOT (
                                    status IN ('COMPLETED', 'FAILED', 'CANCELLED')
                                        AND completed_at IS NULL
                                    )
                                ),

    -- final_answer only makes sense for COMPLETED agents
                        CONSTRAINT chk_agents_final_answer
                            CHECK (
                                final_answer IS NULL
                                    OR status = 'COMPLETED'
                                ),

    -- error_message only makes sense for FAILED agents
                        CONSTRAINT chk_agents_error_message
                            CHECK (
                                error_message IS NULL
                                    OR status = 'FAILED'
                                )

);

-- ── Indexes ───────────────────────────────────────

-- Most common query: all agents for a user newest first
CREATE INDEX idx_agents_user_id
    ON agents (user_id);

-- Filter by status — find RUNNING agents for a user
CREATE INDEX idx_agents_user_status
    ON agents (user_id, status);

-- Find agents linked to a specific chat session
CREATE INDEX idx_agents_session_id
    ON agents (session_id)
    WHERE session_id IS NOT NULL;

-- Composite unique index required by V16.
-- agent_steps references (agent_id, user_id) together
-- to enforce that step.user_id matches agent.user_id
CREATE UNIQUE INDEX uq_agents_id_user_id
    ON agents (id, user_id);

-- ── Auto-update trigger ───────────────────────────

CREATE TRIGGER trigger_agents_updated_at
    BEFORE UPDATE ON agents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ── Comments ──────────────────────────────────────

COMMENT ON TABLE agents IS
    'Agentic task executions.
     Each agent has a user goal and executes
     ReACT steps to achieve it using tools.
     Steps stored in agent_steps table.';

COMMENT ON COLUMN agents.goal IS
    'The user-provided task description.
     Example: Research Spring Boot 4 and summarize.';

COMMENT ON COLUMN agents.final_answer IS
    'Synthesized final response from agent.
     NULL while agent is PENDING or RUNNING.
     Populated when status = COMPLETED.';

COMMENT ON COLUMN agents.step_count IS
    'Total ReACT steps executed so far.
     Incremented as agent runs each step.
     Always >= 0.';