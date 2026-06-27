-- ═══════════════════════════════════════════════════
-- V16: Create Agent Steps Table
-- Phase 6: Agent System
--
-- Each row = one step in the ReACT agent loop.
--
-- STEP TYPES (in order of execution):
-- THINK:   AI reasoning about what to do next
-- ACT:     Calling a tool with specific input
-- OBSERVE: Recording the result from the tool
-- FINAL:   The synthesized final answer
--
-- Example for "What is the weather in London?":
-- Step 0: THINK  → "I need to call WeatherTool"
-- Step 1: ACT    → WeatherTool("London")
-- Step 2: OBSERVE → "22°C, Sunny, Humidity: 45%"
-- Step 3: FINAL  → "The weather in London is 22°C"
-- ═══════════════════════════════════════════════════

CREATE TABLE agent_steps (

                             id              UUID            NOT NULL
                                                                      DEFAULT gen_random_uuid(),

                             agent_id        UUID            NOT NULL,

                             user_id         UUID            NOT NULL,

    -- Position in the execution (0-based)
                             step_index      INTEGER         NOT NULL DEFAULT 0,

    -- Which ReACT step type this is
                             step_type       VARCHAR(20)     NOT NULL,

    -- For ACT steps: which tool was called
    -- NULL for THINK, OBSERVE, FINAL
                             tool_name       VARCHAR(100),

    -- Input to this step:
    -- THINK: the reasoning prompt given to AI
    -- ACT:   tool input parameters
    -- FINAL: null (output only)
                             input           TEXT,

    -- Output from this step:
    -- THINK: AI reasoning result
    -- ACT:   null (tool not yet called)
    -- OBSERVE: tool execution result
    -- FINAL: the final answer text
                             output          TEXT,

    -- Current execution status of this step
                             status          VARCHAR(20)     NOT NULL
                                                                      DEFAULT 'PENDING',

    -- How long this step took in milliseconds
                             duration_ms     INTEGER,

                             created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Set when status becomes DONE or FAILED
                             completed_at    TIMESTAMPTZ,

    -- ── Constraints ──────────────────────────────

                             CONSTRAINT pk_agent_steps
                                 PRIMARY KEY (id),

                             CONSTRAINT fk_steps_agent
                                 FOREIGN KEY (agent_id)
                                     REFERENCES agents (id)
                                     ON DELETE CASCADE,

                             CONSTRAINT fk_steps_user
                                 FOREIGN KEY (user_id)
                                     REFERENCES users (id)
                                     ON DELETE CASCADE,

                             CONSTRAINT chk_steps_type
                                 CHECK (step_type IN (
                                                      'THINK',
                                                      'ACT',
                                                      'OBSERVE',
                                                      'FINAL'
                                     )),

                             CONSTRAINT chk_steps_status
                                 CHECK (status IN (
                                                   'PENDING',
                                                   'RUNNING',
                                                   'DONE',
                                                   'FAILED'
                                     )),

                             CONSTRAINT chk_steps_index
                                 CHECK (step_index >= 0)
);

-- ── Indexes ───────────────────────────────────────

-- All steps for an agent in execution order
CREATE INDEX idx_steps_agent_id
    ON agent_steps (agent_id, step_index ASC);

-- All steps for a user (analytics)
CREATE INDEX idx_steps_user_id
    ON agent_steps (user_id);

-- Filter steps by type
CREATE INDEX idx_steps_agent_type
    ON agent_steps (agent_id, step_type);

-- ── Comments ──────────────────────────────────────

COMMENT ON TABLE agent_steps IS
    'Individual ReACT steps in an agent execution.
     THINK: AI reasons about next action.
     ACT: Tool called with input.
     OBSERVE: Tool result recorded.
     FINAL: Synthesized answer produced.';

COMMENT ON COLUMN agent_steps.tool_name IS
    'Name of the tool called in ACT steps.
     NULL for THINK, OBSERVE, and FINAL steps.
     Example: getWeather, calculate, search';

COMMENT ON COLUMN agent_steps.step_index IS
    '0-based position in the execution.
     Determines display order for users.';