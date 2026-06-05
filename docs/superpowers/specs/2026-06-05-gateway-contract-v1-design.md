<!-- INDEX -->
- [Gateway Contract v1 — Domain Types & Service Interfaces](#gateway-contract-v1-domain-types-service-interfaces)
- [Overview](#overview)
- [Design Principles](#design-principles)
- [Domain Types](#domain-types)
- [Enums](#enums)
- [Service Interfaces](#service-interfaces)
- [Config Bean Interface](#config-bean-interface)
- [Files to Create](#files-to-create)
- [Out of Scope](#out-of-scope)
- [Request & Response Types](#request-response-types)
- [Request & Response Types](#request-response-types)
<!-- /INDEX -->

## Gateway Contract v1 — Domain Types & Service Interfaces
## Overview

Contract v1 introduces the gateway domain types and service interfaces into `chat-core`. This is a pure contract layer — no implementations, no module changes, no devops changes.

**Scope:** `chat-core/src/main/kotlin/com/demo/chat/domain/` and `chat-core/src/main/kotlin/com/demo/chat/service/` only.

**Alignment:** Derived from `Project_Gateway.md` fixture specification. Maps to the minimum viable gateway fixtures: Gateway, Ingress, Principal, RoomBinding, Agent, Session, Task, Planner, Scheduler, Model, Tool, MCPServer, Adapter, Policy, Approval, Memory, ContextBuilder, Queue, Execution, EventLog, Telemetry, Artifact, FailureHandling.

## Design Principles

1. **Generic keying preserved** — All new types use `Key<T>` like existing types (`User<T>`, `Message<T,E>`, `MessageTopic<T>`). No change to the key abstraction.
2. **Jackson polymorphic typing** — All new domain types get `@JsonTypeInfo`/`@JsonTypeName` annotations matching the existing pattern. Subtypes use `@JsonSubTypes`.
3. **No implementation** — Interfaces only. Dummy implementations follow the existing `Dummy*` pattern but are deferred to contract v2.
4. **No database schema** — Schema is deferred. The interfaces are backend-agnostic.
5. **Minimal cross-references** — Types reference each other by ID string, not by object. Keeps contracts loose and serializable.
6. **Enums as Kotlin enums** — Not strings. Type-safe at compile time, Jackson serializes to string automatically.

## Domain Types

### Agent

```kotlin
interface Agent<T> : KeyBearer<T> {
    val name: String
    val description: String
    val defaultModelId: String
    val defaultContextBuilderId: String
    val tools: List<String>
    val autonomyLevel: AutonomyLevel
    val enabled: Boolean
    val timestamp: Instant
}
```

Subtypes: `ChatAgent`, `PlanningAgent`, `CodingAgent`, `ReviewAgent`, `RetrievalAgent`, `ShellAgent`, `SupervisorAgent`, `CriticAgent`, `SummarizerAgent`

### Task

```kotlin
interface Task<T> : KeyBearer<T> {
    val title: String
    val origin: String
    val requestedBy: String
    val roomId: String
    val agentId: String
    val priority: TaskPriority
    val status: TaskStatus
    val timeoutSeconds: Long
    val maxToolCalls: Int
    val maxModelCalls: Int
    val requiresApproval: Boolean
    val cancellationAllowed: Boolean
    val timestamp: Instant
}
```

Subtypes: `ChatTask`, `PlanningTask`, `CodeTask`, `ReviewTask`, `RetrievalTask`, `ToolTask`, `ApprovalTask`, `ScheduledTask`, `RepairTask`, `SummarizationTask`

### Session

```kotlin
interface Session<T> : KeyBearer<T> {
    val principalId: String
    val roomId: String
    val agentId: String
    val projectId: String
    val memoryScope: String
    val contextWindowPolicy: String
    val expiresAt: Instant
    val preserveHistory: Boolean
    val allowResume: Boolean
    val allowFork: Boolean
    val allowHandoff: Boolean
    val timestamp: Instant
}
```

Subtypes: `MatrixConversationSession`, `TaskSession`, `AgentSession`, `ProjectSession`, `ReviewSession`

### Model

```kotlin
interface Model<T> : KeyBearer<T> {
    val provider: String
    val endpoint: String
    val modelName: String
    val contextLimitTokens: Int
    val supportsTools: Boolean
    val supportsVision: Boolean
    val maxConcurrentRequests: Int
    val maxPromptTokens: Int
    val maxCompletionTokens: Int
    val temperatureDefault: Double
    val fallbackModelId: String
    val enabled: Boolean
}
```

Subtypes: `LocalLLM`, `RemoteLLM`, `VisionModel`, `EmbeddingModel`, `RerankerModel`, `DraftModel`, `CriticModel`

### Tool

```kotlin
interface Tool<T> : KeyBearer<T> {
    val name: String
    val provider: String
    val mcpServerId: String
    val riskLevel: RiskLevel
    val inputSchemaRef: String
    val outputSchemaRef: String
    val requiresApproval: Boolean
    val timeoutSeconds: Long
    val maxCallsPerTask: Int
    val allowedAgents: List<String>
    val enabled: Boolean
}
```

Subtypes: `ReadOnlyTool`, `WriteTool`, `DestructiveTool`, `NetworkTool`, `ShellTool`, `RetrievalTool`, `ArtifactTool`, `CalendarTool`, `GitTool`, `FilesystemTool`

### MCPServer

```kotlin
interface MCPServer<T> : KeyBearer<T> {
    val transport: String
    val command: String
    val endpoint: String
    val scope: String
    val exposedTools: List<String>
    val restartOnFailure: Boolean
    val maxRequestDurationSeconds: Long
    val allowedAgents: List<String>
    val blockedPaths: List<String>
    val enabled: Boolean
}
```

Subtypes: `FilesystemMCPServer`, `GitMCPServer`, `BrowserMCPServer`, `RAGMCPServer`, `ShellMCPServer`, `CustomProjectMCPServer`, `HTTPBridgeMCPServer`

### Policy

```kotlin
interface Policy<T> : KeyBearer<T> {
    val appliesTo: List<String>
    val rules: List<PolicyRule>
    val enforcementMode: EnforcementMode
    val logDecisions: Boolean
    val failClosed: Boolean
    val enabled: Boolean
}

data class PolicyRule(
    val effect: PolicyEffect,
    val principalRole: String,
    val toolType: String,
    val toolRiskLte: RiskLevel,
    val roomType: String
)
```

Subtypes: `AccessPolicy`, `ToolPolicy`, `ModelPolicy`, `RoomPolicy`, `DataPolicy`, `RiskPolicy`, `ApprovalPolicy`, `BudgetPolicy`, `RetentionPolicy`

### Approval

```kotlin
interface Approval<T> : KeyBearer<T> {
    val requestedByTaskId: String
    val approverRoles: List<String>
    val approvalSurface: String
    val allowedResponses: List<String>
    val status: ApprovalStatus
    val expiresAt: Instant
    val defaultOnExpiry: String
    val requireReasonOnDeny: Boolean
}
```

Subtypes: `HumanApproval`, `RoomReactionApproval`, `CommandApproval`, `TimedApproval`, `MultiPartyApproval`, `PolicyApproval`

### ContextBuilder

```kotlin
interface ContextBuilder<T> : KeyBearer<T> {
    val strategy: ContextStrategy
    val include: List<String>
    val maxContextTokens: Int
    val reserveCompletionTokens: Int
    val compressionEnabled: Boolean
    val dropPolicy: String
    val requireSourceLabels: Boolean
    val enabled: Boolean
}
```

Subtypes: `SimpleChatContextBuilder`, `ProjectContextBuilder`, `RepoContextBuilder`, `RAGContextBuilder`, `WaveplanContextBuilder`, `TokenBudgetedContextBuilder`, `HierarchicalContextBuilder`

### Execution

```kotlin
interface Execution<T> : KeyBearer<T> {
    val taskId: String
    val agentId: String
    val plannerId: String
    val modelId: String
    val selectedTools: List<String>
    val status: ExecutionStatus
    val startedAt: Instant
    val cancellationToken: String
    val captureStdout: Boolean
    val captureToolIo: Boolean
}
```

Subtypes: `ModelExecution`, `ToolExecution`, `PlanExecution`, `AgentExecution`, `SubtaskExecution`, `ReviewExecution`, `RepairExecution`

### EventLog

```kotlin
interface EventLog<T> : KeyBearer<T> {
    val eventType: String
    val correlationKey: String
    val timestamp: Instant
    val payload: String
    val redacted: Boolean
}
```

Subtypes: `AuditLog`, `TimelineLog`, `WaveplanLog`, `ToolCallLog`, `ModelCallLog`, `PolicyDecisionLog`, `ErrorLog`, `ReplayLog`

### Telemetry

```kotlin
interface Telemetry<T> : KeyBearer<T> {
    val metricType: String
    val taskId: String
    val timestamp: Instant
    val value: Double
    val unit: String
}
```

Subtypes: `TokenTelemetry`, `LatencyTelemetry`, `CostTelemetry`, `QualityTelemetry`, `ErrorTelemetry`, `ResourceTelemetry`, `ContextTelemetry`

### Artifact

```kotlin
interface Artifact<T> : KeyBearer<T> {
    val taskId: String
    val storageBackend: String
    val mediaType: String
    val provenance: String
    val visibility: String
    val mutable: Boolean
    val retain: Boolean
}
```

Subtypes: `TextArtifact`, `CodeArtifact`, `PatchArtifact`, `PlanArtifact`, `ReportArtifact`, `ImageArtifact`, `AudioArtifact`, `BinaryArtifact`, `MatrixAttachmentArtifact`

### FailureHandling

```kotlin
interface FailureHandling<T> : KeyBearer<T> {
    val retryableErrors: List<String>
    val nonRetryableErrors: List<String>
    val maxAttempts: Int
    val backoffStrategy: BackoffStrategy
    val fallbackModelEnabled: Boolean
    val fallbackAgentEnabled: Boolean
    val escalateToHumanAfterFailure: Boolean
}
```

Subtypes: `RetryPolicy`, `FallbackPolicy`, `EscalationPolicy`, `CircuitBreakerPolicy`, `RepairPolicy`, `HumanInterventionPolicy`

## Enums

### AutonomyLevel
`CONSTRAINED`, `BOUNDED`, `AUTONOMOUS`

### TaskPriority
`LOW`, `NORMAL`, `HIGH`, `CRITICAL`

### TaskStatus
`QUEUED`, `RUNNING`, `WAITING_APPROVAL`, `WAITING_TOOL`, `COMPLETED`, `FAILED`, `CANCELLED`

### RiskLevel
`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`

### PolicyEffect
`ALLOW`, `DENY`, `REQUIRE_APPROVAL`

### EnforcementMode
`STRICT`, `PERMISSIVE`, `AUDIT_ONLY`

### ApprovalStatus
`PENDING`, `APPROVED`, `DENIED`, `REVISED`, `EXPIRED`

### ExecutionStatus
`QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`

### BackoffStrategy
`FIXED`, `LINEAR`, `EXPONENTIAL`

### ContextStrategy
`SIMPLE`, `HIERARCHICAL_TOKEN_BUDGET`, `RAG`, `WAVEPLAN`

## Service Interfaces

All interfaces follow the existing pattern: reactive (`Mono`/`Flux`), generic-keyed (`Key<T>`), with typed sub-interfaces where applicable.

### AgentService<T>
```kotlin
interface AgentService<T> {
    fun addAgent(agent: AgentCreateRequest): Mono<out Key<T>>
    fun findByAgentId(req: ByIdRequest<T>): Mono<out Agent<T>>
    fun listAgents(): Flux<out Agent<T>>
    fun findByCapability(capability: String): Flux<out Agent<T>>
    fun updateAgent(agent: Agent<T>): Mono<Void>
    fun removeAgent(req: ByIdRequest<T>): Mono<Void>
}
```

### TaskService<T>
```kotlin
interface TaskService<T> {
    fun createTask(task: TaskCreateRequest): Mono<out Key<T>>
    fun findByTaskId(req: ByIdRequest<T>): Mono<out Task<T>>
    fun listByStatus(status: TaskStatus): Flux<out Task<T>>
    fun listByAgent(agentId: String): Flux<out Task<T>>
    fun listByPrincipal(principalId: String): Flux<out Task<T>>
    fun updateStatus(req: TaskStatusUpdateRequest): Mono<Void>
    fun cancelTask(req: ByIdRequest<T>): Mono<Void>
}
```

### SessionService<T>
```kotlin
interface SessionService<T> {
    fun createSession(session: SessionCreateRequest): Mono<out Key<T>>
    fun findBySessionId(req: ByIdRequest<T>): Mono<out Session<T>>
    fun resumeSession(req: ByIdRequest<T>): Mono<out Session<T>>
    fun forkSession(req: SessionForkRequest): Mono<out Key<T>>
    fun handoffSession(req: SessionHandoffRequest): Mono<Void>
    fun expireSession(req: ByIdRequest<T>): Mono<Void>
}
```

### ModelService<T>
```kotlin
interface ModelService<T> {
    fun registerModel(model: ModelCreateRequest): Mono<out Key<T>>
    fun findByModelId(req: ByIdRequest<T>): Mono<out Model<T>>
    fun listModels(): Flux<out Model<T>>
    fun findByCapability(supportsTools: Boolean, supportsVision: Boolean): Flux<out Model<T>>
    fun updateModel(model: Model<T>): Mono<Void>
    fun removeModel(req: ByIdRequest<T>): Mono<Void>
}
```

### ToolService<T>
```kotlin
interface ToolService<T> {
    fun registerTool(tool: ToolCreateRequest): Mono<out Key<T>>
    fun findByToolId(req: ByIdRequest<T>): Mono<out Tool<T>>
    fun listTools(): Flux<out Tool<T>>
    fun findByRiskLevel(riskLevel: RiskLevel): Flux<out Tool<T>>
    fun listByAgent(agentId: String): Flux<out Tool<T>>
    fun updateTool(tool: Tool<T>): Mono<Void>
    fun removeTool(req: ByIdRequest<T>): Mono<Void>
}
```

### MCPServerService<T>
```kotlin
interface MCPServerService<T> {
    fun registerServer(server: MCPServerCreateRequest): Mono<out Key<T>>
    fun findByServerId(req: ByIdRequest<T>): Mono<out MCPServer<T>>
    fun listServers(): Flux<out MCPServer<T>>
    fun startServer(req: ByIdRequest<T>): Mono<Void>
    fun stopServer(req: ByIdRequest<T>): Mono<Void>
    fun updateServer(server: MCPServer<T>): Mono<Void>
    fun removeServer(req: ByIdRequest<T>): Mono<Void>
}
```

### PolicyService<T>
```kotlin
interface PolicyService<T> {
    fun addPolicy(policy: PolicyCreateRequest): Mono<out Key<T>>
    fun findByPolicyId(req: ByIdRequest<T>): Mono<out Policy<T>>
    fun listPolicies(): Flux<out Policy<T>>
    fun evaluate(principalId: String, toolId: String, roomId: String): Mono<PolicyEffect>
    fun updatePolicy(policy: Policy<T>): Mono<Void>
    fun removePolicy(req: ByIdRequest<T>): Mono<Void>
}
```

### ApprovalService<T>
```kotlin
interface ApprovalService<T> {
    fun createApproval(approval: ApprovalCreateRequest): Mono<out Key<T>>
    fun findByApprovalId(req: ByIdRequest<T>): Mono<out Approval<T>>
    fun listPending(): Flux<out Approval<T>>
    fun resolveApproval(req: ApprovalResolveRequest): Mono<Void>
    fun listByTask(taskId: String): Flux<out Approval<T>>
}
```

### ContextBuilderService<T>
```kotlin
interface ContextBuilderService<T> {
    fun registerBuilder(builder: ContextBuilderCreateRequest): Mono<out Key<T>>
    fun findByBuilderId(req: ByIdRequest<T>): Mono<out ContextBuilder<T>>
    fun listBuilders(): Flux<out ContextBuilder<T>>
    fun buildContext(builderId: String, sessionId: String): Mono<ContextResult>
    fun updateBuilder(builder: ContextBuilder<T>): Mono<Void>
    fun removeBuilder(req: ByIdRequest<T>): Mono<Void>
}
```

### ExecutionService<T>
```kotlin
interface ExecutionService<T> {
    fun startExecution(execution: ExecutionCreateRequest): Mono<out Key<T>>
    fun findByExecutionId(req: ByIdRequest<T>): Mono<out Execution<T>>
    fun listByTask(taskId: String): Flux<out Execution<T>>
    fun updateStatus(req: ExecutionStatusUpdateRequest): Mono<Void>
    fun cancelExecution(req: ByIdRequest<T>): Mono<Void>
}
```

### EventLogService<T>
```kotlin
interface EventLogService<T> {
    fun append(event: EventLogEntry): Mono<out Key<T>>
    fun findByEventId(req: ByIdRequest<T>): Mono<out EventLog<T>>
    fun findByCorrelation(correlationKey: String): Flux<out EventLog<T>>
    fun findByType(eventType: String): Flux<out EventLog<T>>
    fun replay(from: Instant, to: Instant): Flux<out EventLog<T>>
}
```

### TelemetryService<T>
```kotlin
interface TelemetryService<T> {
    fun record(telemetry: TelemetryEntry): Mono<out Key<T>>
    fun findByMetricType(metricType: String): Flux<out Telemetry<T>>
    fun findByTask(taskId: String): Flux<out Telemetry<T>>
    fun aggregate(metricType: String, from: Instant, to: Instant): Mono<TelemetryAggregate>
}
```

### ArtifactService<T>
```kotlin
interface ArtifactService<T> {
    fun store(artifact: ArtifactCreateRequest): Mono<out Key<T>>
    fun findByArtifactId(req: ByIdRequest<T>): Mono<out Artifact<T>>
    fun listByTask(taskId: String): Flux<out Artifact<T>>
    fun retrieve(req: ByIdRequest<T>): Mono<ByteArray>
    fun remove(req: ByIdRequest<T>): Mono<Void>
}
```

### FailurePolicyService<T>
```kotlin
interface FailurePolicyService<T> {
    fun registerPolicy(policy: FailureHandlingCreateRequest): Mono<out Key<T>>
    fun findByPolicyId(req: ByIdRequest<T>): Mono<out FailureHandling<T>>
    fun evaluateFailure(error: String, attempt: Int): Mono<FailureDecision>
    fun updatePolicy(policy: FailureHandling<T>): Mono<Void>
    fun removePolicy(req: ByIdRequest<T>): Mono<Void>
}
```

## Config Bean Interface

```kotlin
interface GatewayServices<T, V, Q> :
    AgentServiceBeans<T>,
    TaskServiceBeans<T>,
    SessionServiceBeans<T>,
    ModelServiceBeans<T>,
    ToolServiceBeans<T>,
    MCPServerServiceBeans<T>,
    PolicyServiceBeans<T>,
    ApprovalServiceBeans<T>,
    ContextBuilderServiceBeans<T>,
    ExecutionServiceBeans<T>,
    EventLogServiceBeans<T>,
    TelemetryServiceBeans<T>,
    ArtifactServiceBeans<T>,
    FailurePolicyServiceBeans<T>
```

## Files to Create

### Domain types (14 files)
- `domain/Agent.kt`
- `domain/Task.kt`
- `domain/Session.kt`
- `domain/Model.kt`
- `domain/Tool.kt`
- `domain/MCPServer.kt`
- `domain/Policy.kt`
- `domain/Approval.kt`
- `domain/ContextBuilder.kt`
- `domain/Execution.kt`
- `domain/EventLog.kt`
- `domain/Telemetry.kt`
- `domain/Artifact.kt`
- `domain/FailureHandling.kt`

### Enums (10 files)
- `domain/AutonomyLevel.kt`
- `domain/TaskPriority.kt`
- `domain/TaskStatus.kt`
- `domain/RiskLevel.kt`
- `domain/PolicyEffect.kt`
- `domain/EnforcementMode.kt`
- `domain/ApprovalStatus.kt`
- `domain/ExecutionStatus.kt`
- `domain/BackoffStrategy.kt`
- `domain/ContextStrategy.kt`

### Service interfaces (14 files)
- `service/gateway/AgentService.kt`
- `service/gateway/TaskService.kt`
- `service/gateway/SessionService.kt`
- `service/gateway/ModelService.kt`
- `service/gateway/ToolService.kt`
- `service/gateway/MCPServerService.kt`
- `service/gateway/PolicyService.kt`
- `service/gateway/ApprovalService.kt`
- `service/gateway/ContextBuilderService.kt`
- `service/gateway/ExecutionService.kt`
- `service/gateway/EventLogService.kt`
- `service/gateway/TelemetryService.kt`
- `service/gateway/ArtifactService.kt`
- `service/gateway/FailurePolicyService.kt`

### Config beans (1 file)
- `config/GatewayServices.kt`

**Total: 39 new files in chat-core.**

## Out of Scope

- Implementations (deferred to contract v2)
- Dummy implementations (deferred to contract v2)
- Database schema (deferred)
- DevOps changes (deferred)
- Module additions or removals (deferred)
- Gateway module expansion (deferred)
- Matrix adapter (deferred)
- MCP server lifecycle management (deferred)
- Planner and Scheduler fixtures (deferred — require Task/Agent/Execution to exist first)


## Request & Response Types
## Request & Response Types

All request/response types follow the existing pattern in `domain/RequestResponse.kt` — sealed class hierarchy with `@JsonTypeInfo`/`@JsonTypeName` annotations, extending `RequestResponse<T>`.

### Gateway Request Types

```kotlin
// Agent requests
@JsonTypeName("AgentCreateRequest")
data class AgentCreateRequest(
    val name: String,
    val description: String,
    val defaultModelId: String,
    val defaultContextBuilderId: String,
    val tools: List<String>,
    val autonomyLevel: AutonomyLevel
) : RequestResponse<Any>()

// Task requests
@JsonTypeName("TaskCreateRequest")
data class TaskCreateRequest(
    val title: String,
    val origin: String,
    val requestedBy: String,
    val roomId: String,
    val agentId: String,
    val priority: TaskPriority,
    val timeoutSeconds: Long,
    val maxToolCalls: Int,
    val maxModelCalls: Int,
    val requiresApproval: Boolean
) : RequestResponse<Any>()

@JsonTypeName("TaskStatusUpdateRequest")
data class TaskStatusUpdateRequest<T>(
    val taskId: T,
    val status: TaskStatus
) : RequestResponse<T>()

// Session requests
@JsonTypeName("SessionCreateRequest")
data class SessionCreateRequest(
    val principalId: String,
    val roomId: String,
    val agentId: String,
    val projectId: String,
    val memoryScope: String,
    val contextWindowPolicy: String,
    val expiresAfterSeconds: Long,
    val allowResume: Boolean,
    val allowFork: Boolean,
    val allowHandoff: Boolean
) : RequestResponse<Any>()

@JsonTypeName("SessionForkRequest")
data class SessionForkRequest<T>(
    val sessionId: T,
    val newPrincipalId: String,
    val newAgentId: String
) : RequestResponse<T>()

@JsonTypeName("SessionHandoffRequest")
data class SessionHandoffRequest<T>(
    val sessionId: T,
    val targetAgentId: String
) : RequestResponse<T>()

// Model requests
@JsonTypeName("ModelCreateRequest")
data class ModelCreateRequest(
    val provider: String,
    val endpoint: String,
    val modelName: String,
    val contextLimitTokens: Int,
    val supportsTools: Boolean,
    val supportsVision: Boolean,
    val maxConcurrentRequests: Int,
    val maxPromptTokens: Int,
    val maxCompletionTokens: Int,
    val temperatureDefault: Double,
    val fallbackModelId: String
) : RequestResponse<Any>()

// Tool requests
@JsonTypeName("ToolCreateRequest")
data class ToolCreateRequest(
    val name: String,
    val provider: String,
    val mcpServerId: String,
    val riskLevel: RiskLevel,
    val inputSchemaRef: String,
    val outputSchemaRef: String,
    val requiresApproval: Boolean,
    val timeoutSeconds: Long,
    val maxCallsPerTask: Int,
    val allowedAgents: List<String>
) : RequestResponse<Any>()

// MCP Server requests
@JsonTypeName("MCPServerCreateRequest")
data class MCPServerCreateRequest(
    val transport: String,
    val command: String,
    val endpoint: String,
    val scope: String,
    val exposedTools: List<String>,
    val restartOnFailure: Boolean,
    val maxRequestDurationSeconds: Long,
    val allowedAgents: List<String>,
    val blockedPaths: List<String>
) : RequestResponse<Any>()

// Policy requests
@JsonTypeName("PolicyCreateRequest")
data class PolicyCreateRequest(
    val appliesTo: List<String>,
    val rules: List<PolicyRule>,
    val enforcementMode: EnforcementMode,
    val logDecisions: Boolean,
    val failClosed: Boolean
) : RequestResponse<Any>()

// Approval requests
@JsonTypeName("ApprovalCreateRequest")
data class ApprovalCreateRequest(
    val requestedByTaskId: String,
    val approverRoles: List<String>,
    val approvalSurface: String,
    val allowedResponses: List<String>,
    val expiresAfterSeconds: Long,
    val defaultOnExpiry: String,
    val requireReasonOnDeny: Boolean
) : RequestResponse<Any>()

@JsonTypeName("ApprovalResolveRequest")
data class ApprovalResolveRequest<T>(
    val approvalId: T,
    val response: String,
    val reason: String
) : RequestResponse<T>()

// ContextBuilder requests
@JsonTypeName("ContextBuilderCreateRequest")
data class ContextBuilderCreateRequest(
    val strategy: ContextStrategy,
    val include: List<String>,
    val maxContextTokens: Int,
    val reserveCompletionTokens: Int,
    val compressionEnabled: Boolean,
    val dropPolicy: String,
    val requireSourceLabels: Boolean
) : RequestResponse<Any>()

// Execution requests
@JsonTypeName("ExecutionCreateRequest")
data class ExecutionCreateRequest(
    val taskId: String,
    val agentId: String,
    val plannerId: String,
    val modelId: String,
    val selectedTools: List<String>,
    val timeoutSeconds: Long,
    val captureStdout: Boolean,
    val captureToolIo: Boolean
) : RequestResponse<Any>()

@JsonTypeName("ExecutionStatusUpdateRequest")
data class ExecutionStatusUpdateRequest<T>(
    val executionId: T,
    val status: ExecutionStatus
) : RequestResponse<T>()

// EventLog requests
@JsonTypeName("EventLogEntry")
data class EventLogEntry(
    val eventType: String,
    val correlationKey: String,
    val payload: String,
    val redacted: Boolean
) : RequestResponse<Any>()

// Telemetry requests
@JsonTypeName("TelemetryEntry")
data class TelemetryEntry(
    val metricType: String,
    val taskId: String,
    val value: Double,
    val unit: String
) : RequestResponse<Any>()

// Artifact requests
@JsonTypeName("ArtifactCreateRequest")
data class ArtifactCreateRequest(
    val taskId: String,
    val storageBackend: String,
    val mediaType: String,
    val provenance: String,
    val visibility: String,
    val mutable: Boolean,
    val retain: Boolean,
    val data: ByteArray
) : RequestResponse<Any>()

// FailureHandling requests
@JsonTypeName("FailureHandlingCreateRequest")
data class FailureHandlingCreateRequest(
    val retryableErrors: List<String>,
    val nonRetryableErrors: List<String>,
    val maxAttempts: Int,
    val backoffStrategy: BackoffStrategy,
    val fallbackModelEnabled: Boolean,
    val fallbackAgentEnabled: Boolean,
    val escalateToHumanAfterFailure: Boolean
) : RequestResponse<Any>()
```

### Gateway Response Types

```kotlin
@JsonTypeName("ContextResult")
data class ContextResult(
    val context: String,
    val tokenCount: Int,
    val sources: List<String>,
    val dropped: List<String>
)

@JsonTypeName("TelemetryAggregate")
data class TelemetryAggregate(
    val metricType: String,
    val count: Long,
    val sum: Double,
    val average: Double,
    val min: Double,
    val max: Double,
    val from: Instant,
    val to: Instant
)

@JsonTypeName("FailureDecision")
data class FailureDecision(
    val retry: Boolean,
    val fallbackModel: Boolean,
    val fallbackAgent: Boolean,
    val escalateToHuman: Boolean,
    val backoffSeconds: Long,
    val reason: String
)
```

**Note:** These request/response types are added to `domain/RequestResponse.kt` alongside existing types (`UserCreateRequest`, `ByIdRequest`, `ByStringRequest`, `MembershipRequest`, `MessageSendRequest`, `MemberTopicRequest`).
