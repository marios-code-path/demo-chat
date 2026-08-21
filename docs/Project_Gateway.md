Yes. You’re asking for a remasterable architecture spec, not CLI args.

Below is a coarse-grained fixture-based gateway specification.

gateway_spec:
  type: AgentGateway
  subtype: MatrixControlledAgentGateway
  purpose:
    - Accept human and system events from Matrix
    - Normalize them into agent tasks
    - Route tasks to agents, models, tools, and schedulers
    - Enforce policy, permissions, approvals, and resource limits
    - Record execution provenance for replay, audit, and debugging
  fixture_kinds:
    - Gateway
    - Ingress
    - Principal
    - RoomBinding
    - Agent
    - Session
    - Task
    - Planner
    - Scheduler
    - Model
    - Tool
    - MCPServer
    - Adapter
    - Policy
    - Approval
    - Memory
    - ContextBuilder
    - Queue
    - Execution
    - EventLog
    - Telemetry
    - Artifact
    - FailureHandling

1. Gateway

gateway:
  type: Gateway
  subtypes:
    - MatrixGateway
    - HTTPGateway
    - MCPGateway
    - HybridGateway
  config:
    id: mitraframe-agent-gateway
    display_name: Mitraframe Agent Gateway
    environment: home-lab
    mode: interactive
    default_timezone: America/Los_Angeles
  control:
    enabled: true
    maintenance_mode: false
    max_concurrent_tasks: 8
    default_task_timeout_seconds: 1800
    allow_background_execution: true
    require_audit_log: true

The gateway is the control surface. It should not be one agent. It should supervise many agents.

⸻

2. Ingress

ingress:
  type: Ingress
  subtypes:
    - MatrixRoomIngress
    - MatrixDMIngress
    - HTTPWebhookIngress
    - ScheduledIngress
    - ManualAdminIngress
  config:
    source: matrix
    homeserver_url: https://matrix.example.lan
    bot_user_id: "@agent-gateway:example.lan"
    accepted_event_types:
      - message
      - reaction
      - room_invite
      - thread_reply
      - command
  control:
    enabled: true
    deduplicate_events: true
    ignore_own_messages: true
    require_explicit_mention: true
    accepted_prefixes:
      - "!agent"
      - "!bai"
      - "@gateway"

Ingress converts messy outside events into normalized internal requests.

⸻

3. Principal

principal:
  type: Principal
  subtypes:
    - HumanUser
    - MatrixUser
    - AgentUser
    - ServiceAccount
    - SystemPrincipal
  config:
    id: mario
    matrix_user_id: "@mario:example.lan"
    display_name: Mario
    roles:
      - owner
      - operator
      - developer
  control:
    enabled: true
    may_create_agents: true
    may_approve_tool_use: true
    may_run_shell_tools: true
    may_access_private_memory: true

Principals are “who is acting.”

Do not treat Matrix users as enough. You want your own internal principal model.

⸻

4. RoomBinding

room_binding:
  type: RoomBinding
  subtypes:
    - ControlRoom
    - AgentRoom
    - ProjectRoom
    - DebugRoom
    - ApprovalRoom
    - BroadcastRoom
  config:
    matrix_room_id: "!abc123:example.lan"
    name: "waveplan-control"
    default_agent: sigma
    project_scope: stability-toys
    allowed_agents:
      - sigma
      - rhubarb
      - ginko
  control:
    enabled: true
    allow_agent_autoreply: true
    require_mention: false
    allow_tool_execution: true
    allow_artifact_uploads: true
    log_visibility: summary

Rooms become task containers.

A room can mean:

one human conversation
one project
one running task
one approval channel
one debug stream

⸻

5. Agent

agent:
  type: Agent
  subtypes:
    - ChatAgent
    - PlanningAgent
    - CodingAgent
    - ReviewAgent
    - RetrievalAgent
    - ShellAgent
    - SupervisorAgent
    - CriticAgent
    - SummarizerAgent
  config:
    id: sigma
    display_name: Sigma
    description: "Planning and implementation agent"
    default_model: qwen35
    default_context_builder: project_context
    default_memory_scope: project
    default_tools:
      - repo.search
      - repo.read
      - waveplan.emit
      - mcp.filesystem.read
  control:
    enabled: true
    max_parallel_tasks: 2
    autonomy_level: bounded
    can_spawn_subtasks: true
    can_call_tools: true
    can_write_artifacts: true
    requires_approval_for:
      - shell.write
      - git.commit
      - file.delete
      - network.call

Agent subtypes should be capability-oriented, not personality-oriented.

⸻

6. Session

session:
  type: Session
  subtypes:
    - MatrixConversationSession
    - TaskSession
    - AgentSession
    - ProjectSession
    - ReviewSession
  config:
    id: sess_20260604_001
    principal_id: mario
    room_id: "!abc123:example.lan"
    agent_id: sigma
    project_id: stability-toys
    memory_scope: project
    context_window_policy: rolling
  control:
    enabled: true
    expires_after_seconds: 86400
    preserve_history: true
    allow_resume: true
    allow_fork: true
    allow_handoff: true

Session is the continuity layer.

Matrix threads map nicely to sessions.

⸻

7. Task

task:
  type: Task
  subtypes:
    - ChatTask
    - PlanningTask
    - CodeTask
    - ReviewTask
    - RetrievalTask
    - ToolTask
    - ApprovalTask
    - ScheduledTask
    - RepairTask
    - SummarizationTask
  config:
    id: task_001
    title: "Summarize repo architecture"
    origin: matrix
    requested_by: mario
    room_id: "!abc123:example.lan"
    agent_id: sigma
    priority: normal
    input_mode: natural_language
  control:
    status: queued
    timeout_seconds: 1800
    retry_policy: standard
    requires_approval: false
    cancellation_allowed: true
    max_tool_calls: 32
    max_model_calls: 16

Everything entering the gateway becomes a task.

Even a chat reply is a task.

⸻

8. Planner

planner:
  type: Planner
  subtypes:
    - DirectPlanner
    - DAGPlanner
    - WaveplanPlanner
    - ReActPlanner
    - HumanApprovedPlanner
    - StaticTemplatePlanner
  config:
    id: waveplan_default
    planning_mode: dag
    decomposition_depth: 3
    emit_intermediate_plan: true
    use_memory: true
    use_retrieval: true
  control:
    enabled: true
    require_plan_before_execution: true
    require_human_approval_above_risk: medium
    allow_replanning: true
    max_replans: 3

Planner decides:

What are we trying to do?
What are the steps?
What tools are needed?
What should be approved?
What is success?

⸻

9. Scheduler

scheduler:
  type: Scheduler
  subtypes:
    - FIFOQueueScheduler
    - PriorityScheduler
    - ResourceAwareScheduler
    - ModelAwareScheduler
    - ContextAwareScheduler
    - CostAwareScheduler
    - HumanApprovalScheduler
  config:
    id: default_scheduler
    queue_strategy: priority
    resource_strategy: model_and_node_aware
    fairness_policy: per_principal
    default_priority: normal
  control:
    enabled: true
    max_global_concurrency: 8
    max_per_agent_concurrency: 2
    max_per_room_concurrency: 1
    preemption_enabled: false
    pause_on_high_error_rate: true

Yes: the gateway can schedule traffic.

The scheduler controls:

what runs now
what waits
what gets cancelled
what gets retried
what model/node/tool handles it

⸻

10. Model

model:
  type: Model
  subtypes:
    - LocalLLM
    - RemoteLLM
    - VisionModel
    - EmbeddingModel
    - RerankerModel
    - DraftModel
    - CriticModel
  config:
    id: qwen35
    provider: tabbyapi
    endpoint: http://enigma.lan:5000/v1
    model_name: qwen3.6-35b-a3b
    context_limit_tokens: 196608
    supports_tools: true
    supports_vision: false
  control:
    enabled: true
    max_concurrent_requests: 2
    max_prompt_tokens: 180000
    max_completion_tokens: 8192
    temperature_default: 0.3
    fallback_model: qwen27

Model config should describe capability, not just endpoint.

⸻

11. Tool

tool:
  type: Tool
  subtypes:
    - ReadOnlyTool
    - WriteTool
    - DestructiveTool
    - NetworkTool
    - ShellTool
    - RetrievalTool
    - ArtifactTool
    - CalendarTool
    - GitTool
    - FilesystemTool
  config:
    id: repo.search
    display_name: Repository Search
    provider: mcp
    mcp_server: filesystem_tools
    risk_level: low
    input_schema_ref: repo_search_input
    output_schema_ref: repo_search_output
  control:
    enabled: true
    requires_approval: false
    timeout_seconds: 60
    max_calls_per_task: 16
    allowed_agents:
      - sigma
      - rhubarb

Tool classification matters because policy depends on it.

⸻

12. MCPServer

mcp_server:
  type: MCPServer
  subtypes:
    - FilesystemMCPServer
    - GitMCPServer
    - BrowserMCPServer
    - RAGMCPServer
    - ShellMCPServer
    - CustomProjectMCPServer
    - HTTPBridgeMCPServer
  config:
    id: filesystem_tools
    transport: stdio
    command: mcp-filesystem-server
    scope: project
    exposed_tools:
      - file.read
      - file.search
      - file.stat
  control:
    enabled: true
    restart_on_failure: true
    max_request_duration_seconds: 120
    allowed_agents:
      - sigma
      - ginko
    blocked_paths:
      - /etc
      - /Users/mario/.ssh

MCP servers are tool providers.

The gateway should not blindly expose all MCP tools to all agents.

⸻

13. Adapter

adapter:
  type: Adapter
  subtypes:
    - HTTPAdapter
    - MCPAdapter
    - MatrixAdapter
    - ShellAdapter
    - FileAdapter
    - GitAdapter
    - ModelAdapter
    - WebhookAdapter
  config:
    id: waveplan_http_adapter
    target_service: waveplan
    protocol: http
    base_url: http://waveplan.lan:8080
    exposed_operations:
      - create_plan
      - append_event
      - read_timeline
  control:
    enabled: true
    timeout_seconds: 30
    retry_policy: idempotent_only
    circuit_breaker_enabled: true

Adapters normalize the outside world.

⸻

14. Policy

policy:
  type: Policy
  subtypes:
    - AccessPolicy
    - ToolPolicy
    - ModelPolicy
    - RoomPolicy
    - DataPolicy
    - RiskPolicy
    - ApprovalPolicy
    - BudgetPolicy
    - RetentionPolicy
  config:
    id: default_tool_policy
    applies_to:
      - agent
      - tool
      - principal
    rules:
      - effect: allow
        principal_role: owner
        tool_risk_lte: high
      - effect: require_approval
        tool_type: ShellTool
      - effect: deny
        tool_type: DestructiveTool
        room_type: BroadcastRoom
  control:
    enabled: true
    enforcement_mode: strict
    log_decisions: true
    fail_closed: true

Policy is one of the most important pieces.

No policy means every bot slowly becomes root.

⸻

15. Approval

approval:
  type: Approval
  subtypes:
    - HumanApproval
    - RoomReactionApproval
    - CommandApproval
    - TimedApproval
    - MultiPartyApproval
    - PolicyApproval
  config:
    id: approval_001
    requested_by_task: task_001
    approver_roles:
      - owner
      - operator
    approval_surface: matrix_reaction
    allowed_responses:
      - approve
      - deny
      - revise
  control:
    status: pending
    expires_after_seconds: 900
    default_on_expiry: deny
    require_reason_on_deny: false

Approvals should be first-class objects.

A Matrix reaction can approve, but the approval itself belongs to the gateway.

⸻

16. Memory

memory:
  type: Memory
  subtypes:
    - ConversationMemory
    - ProjectMemory
    - AgentMemory
    - UserMemory
    - EpisodicMemory
    - SemanticMemory
    - ArtifactMemory
    - ExecutionMemory
  config:
    id: project_memory_stability_toys
    backend: rhubarb
    scope: project
    embedding_model: local_embed
    retention_policy: project_lifetime
    retrieval_policy: relevance_plus_recency
  control:
    enabled: true
    allow_write: true
    allow_read: true
    require_citation: true
    max_results_per_task: 12

Memory is not just chat history.

For you, memory includes:

architecture decisions
plans
logs
project facts
model behavior
failure patterns
corpus summaries

⸻

17. ContextBuilder

context_builder:
  type: ContextBuilder
  subtypes:
    - SimpleChatContextBuilder
    - ProjectContextBuilder
    - RepoContextBuilder
    - RAGContextBuilder
    - WaveplanContextBuilder
    - TokenBudgetedContextBuilder
    - HierarchicalContextBuilder
  config:
    id: project_context
    strategy: hierarchical_token_budget
    include:
      - current_message
      - room_history
      - active_task
      - project_summary
      - relevant_files
      - memory_hits
      - tool_results
  control:
    enabled: true
    max_context_tokens: 120000
    reserve_completion_tokens: 8192
    compression_enabled: true
    drop_policy: oldest_lowest_relevance
    require_source_labels: true

This is where your work is most differentiated.

Context construction is not a prompt template. It is a scheduler for meaning.

⸻

18. Queue

queue:
  type: Queue
  subtypes:
    - ReadyQueue
    - RunningQueue
    - WaitingForApprovalQueue
    - WaitingForToolQueue
    - RetryQueue
    - DeadLetterQueue
    - CompletedQueue
  config:
    id: default_task_queue
    backend: postgres
    ordering: priority_then_created_at
    persistence: durable
  control:
    enabled: true
    max_depth: 10000
    visibility_timeout_seconds: 300
    retry_delay_seconds: 30
    dead_letter_after_attempts: 3

Queues are the gateway’s nervous system.

⸻

19. Execution

execution:
  type: Execution
  subtypes:
    - ModelExecution
    - ToolExecution
    - PlanExecution
    - AgentExecution
    - SubtaskExecution
    - ReviewExecution
    - RepairExecution
  config:
    id: exec_001
    task_id: task_001
    agent_id: sigma
    planner_id: waveplan_default
    model_id: qwen35
    selected_tools:
      - repo.search
      - repo.read
  control:
    status: running
    started_at: 2026-06-04T23:00:00-07:00
    timeout_seconds: 1800
    cancellation_token: cancel_exec_001
    capture_stdout: true
    capture_tool_io: true

Execution is an attempt to do the task.

A task can have multiple executions.

⸻

20. EventLog

event_log:
  type: EventLog
  subtypes:
    - AuditLog
    - TimelineLog
    - WaveplanLog
    - ToolCallLog
    - ModelCallLog
    - PolicyDecisionLog
    - ErrorLog
    - ReplayLog
  config:
    id: gateway_event_log
    backend: append_only_store
    format: structured_jsonl
    correlation_key: task_id
  control:
    enabled: true
    append_only: true
    redact_secrets: true
    retain_for_days: 365
    replay_enabled: true

This is the “flight recorder.”

It records:

who asked
what was understood
what context was built
which model was selected
which tools were called
what failed
what was approved
what changed

⸻

21. Telemetry

telemetry:
  type: Telemetry
  subtypes:
    - TokenTelemetry
    - LatencyTelemetry
    - CostTelemetry
    - QualityTelemetry
    - ErrorTelemetry
    - ResourceTelemetry
    - ContextTelemetry
  config:
    id: gateway_telemetry
    metrics_backend: prometheus
    trace_backend: opentelemetry
    log_backend: loki
  control:
    enabled: true
    sample_rate: 1.0
    capture_token_usage: true
    capture_model_latency: true
    capture_tool_latency: true
    capture_context_composition: true

Telemetry is for operating the system.

EventLog is for reconstructing the system.

Both matter.

⸻

22. Artifact

artifact:
  type: Artifact
  subtypes:
    - TextArtifact
    - CodeArtifact
    - PatchArtifact
    - PlanArtifact
    - ReportArtifact
    - ImageArtifact
    - AudioArtifact
    - BinaryArtifact
    - MatrixAttachmentArtifact
  config:
    id: artifact_001
    task_id: task_001
    storage_backend: object_store
    media_type: text/markdown
    provenance: generated_by_agent
    visibility: room
  control:
    enabled: true
    retain: true
    allow_matrix_upload: true
    require_scan_before_publish: false
    mutable: false

Artifacts are outputs that deserve identity.

A Matrix message is not enough.

⸻

23. FailureHandling

failure_handling:
  type: FailureHandling
  subtypes:
    - RetryPolicy
    - FallbackPolicy
    - EscalationPolicy
    - CircuitBreakerPolicy
    - RepairPolicy
    - HumanInterventionPolicy
  config:
    id: standard_failure_policy
    retryable_errors:
      - timeout
      - rate_limit
      - transient_network
      - model_overloaded
    non_retryable_errors:
      - permission_denied
      - invalid_tool_input
      - policy_denied
  control:
    max_attempts: 3
    backoff_strategy: exponential
    fallback_model_enabled: true
    fallback_agent_enabled: false
    escalate_to_human_after_failure: true

Failure policy keeps the gateway from thrashing.

⸻

Coarse full fixture

This is the kind of fixture I would want as the canonical seed.

fixture:
  type: AgentGatewayFixture
  subtype: MatrixSpringEmbabelMCPGatewayFixture
  gateway:
    type: Gateway
    subtype: MatrixGateway
    id: mitraframe-gateway
    enabled: true
  ingress:
    type: Ingress
    subtype: MatrixRoomIngress
    homeserver_url: https://matrix.example.lan
    bot_user_id: "@gateway:example.lan"
    require_explicit_mention: true
  principals:
    - type: Principal
      subtype: HumanUser
      id: mario
      matrix_user_id: "@mario:example.lan"
      roles:
        - owner
        - operator
        - developer
  rooms:
    - type: RoomBinding
      subtype: ControlRoom
      matrix_room_id: "!control:example.lan"
      default_agent: sigma
      allowed_agents:
        - sigma
        - rhubarb
        - ginko
      allow_tool_execution: true
      require_mention: false
  agents:
    - type: Agent
      subtype: PlanningAgent
      id: sigma
      default_model: qwen35
      default_context_builder: project_context
      tools:
        - repo.search
        - repo.read
        - waveplan.emit
      autonomy_level: bounded
    - type: Agent
      subtype: RetrievalAgent
      id: rhubarb
      default_model: qwen27
      default_context_builder: rag_context
      tools:
        - memory.search
        - corpus.fetch
        - vector.search
      autonomy_level: constrained
  models:
    - type: Model
      subtype: LocalLLM
      id: qwen35
      provider: tabbyapi
      endpoint: http://enigma.lan:5000/v1
      context_limit_tokens: 196608
      supports_tools: true
    - type: Model
      subtype: LocalLLM
      id: qwen27
      provider: vllm
      endpoint: http://mindgate.lan:8000/v1
      context_limit_tokens: 131072
      supports_tools: true
  tools:
    - type: Tool
      subtype: RetrievalTool
      id: memory.search
      provider: mcp
      mcp_server: rhubarb_mcp
      risk_level: low
      requires_approval: false
    - type: Tool
      subtype: FilesystemTool
      id: repo.read
      provider: mcp
      mcp_server: filesystem_mcp
      risk_level: medium
      requires_approval: false
    - type: Tool
      subtype: ShellTool
      id: shell.run
      provider: mcp
      mcp_server: shell_mcp
      risk_level: high
      requires_approval: true
  mcp_servers:
    - type: MCPServer
      subtype: RAGMCPServer
      id: rhubarb_mcp
      transport: http
      endpoint: http://rhubarb.lan:7070/mcp
      enabled: true
    - type: MCPServer
      subtype: FilesystemMCPServer
      id: filesystem_mcp
      transport: stdio
      command: mcp-filesystem-server
      enabled: true
  planner:
    type: Planner
    subtype: WaveplanPlanner
    id: waveplan_default
    require_plan_before_execution: true
    allow_replanning: true
    max_replans: 3
  scheduler:
    type: Scheduler
    subtype: ResourceAwareScheduler
    id: default_scheduler
    max_global_concurrency: 8
    max_per_agent_concurrency: 2
    max_per_room_concurrency: 1
  context_builders:
    - type: ContextBuilder
      subtype: TokenBudgetedContextBuilder
      id: project_context
      max_context_tokens: 120000
      reserve_completion_tokens: 8192
      include:
        - current_message
        - room_history
        - active_task
        - project_summary
        - memory_hits
        - relevant_files
  memory:
    type: Memory
    subtype: ProjectMemory
    id: rhubarb_project_memory
    backend: rhubarb
    require_citation: true
    max_results_per_task: 12
  policy:
    type: Policy
    subtype: ToolPolicy
    id: default_policy
    enforcement_mode: strict
    fail_closed: true
    rules:
      - effect: allow
        principal_role: owner
        tool_risk_lte: medium
      - effect: require_approval
        tool_type: ShellTool
      - effect: deny
        tool_type: DestructiveTool
        unless_approved_by_role: owner
  approval:
    type: Approval
    subtype: RoomReactionApproval
    approval_surface: matrix_reaction
    allowed_responses:
      approve: "✅"
      deny: "❌"
      revise: "📝"
    expires_after_seconds: 900
    default_on_expiry: deny
  event_log:
    type: EventLog
    subtype: WaveplanLog
    backend: append_only_store
    format: structured_jsonl
    append_only: true
    replay_enabled: true
  telemetry:
    type: Telemetry
    subtype: ContextTelemetry
    metrics_backend: prometheus
    capture_token_usage: true
    capture_context_composition: true
    capture_tool_latency: true

The essential control elements

The gateway’s real control surface is this:

Who may ask?
Where may they ask?
Which agent receives it?
Which model is allowed?
Which tools are allowed?
Which actions require approval?
How much context is allowed?
How much concurrency is allowed?
What gets recorded?
What can be replayed?
What happens on failure?

My recommendation for your first real gateway spec

Start with only these fixture kinds:

minimum_viable_gateway:
  - Gateway
  - MatrixIngress
  - Principal
  - RoomBinding
  - Agent
  - Model
  - Tool
  - MCPServer
  - ContextBuilder
  - Scheduler
  - Policy
  - Approval
  - EventLog

That is enough to remaster later without painting yourself into a corner.