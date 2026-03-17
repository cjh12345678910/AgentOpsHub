from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class ExecutionContext:
    """执行上下文，包含跨方法传递的信息"""
    token: Optional[str] = None
    role: str = "operator"
    task_id: Optional[str] = None


@dataclass
class ModelUsageTrace:
    model: str
    tokenIn: int
    tokenOut: int
    costEst: float
    latencyMs: int


@dataclass
class ToolCallTrace:
    callOrder: int
    toolName: str
    requestSummary: str
    responseSummary: str
    success: bool
    errorCode: Optional[str] = None
    errorMessage: Optional[str] = None
    latencyMs: int = 0
    citations: List[str] = field(default_factory=list)
    policyDecision: Optional[str] = None
    denyReason: Optional[str] = None
    requiredScope: Optional[str] = None
    safetyRule: Optional[str] = None


@dataclass
class StepTrace:
    seq: int
    stepType: str
    status: str
    inputSummary: str
    outputSummary: str
    errorCode: Optional[str] = None
    errorMessage: Optional[str] = None
    retryCount: int = 0
    citations: List[str] = field(default_factory=list)
    toolCalls: List[ToolCallTrace] = field(default_factory=list)
    round: Optional[int] = None
    modelUsage: Optional[ModelUsageTrace] = None
    verifierReport: Optional[Dict[str, Any]] = None
    diffSummary: Optional[str] = None
    parserStage: Optional[str] = None
    rawResponseSnippet: Optional[str] = None
    usageUnavailableReason: Optional[str] = None
    finalAnswerText: Optional[str] = None


@dataclass
class AgentRunResult:
    status: str
    phase: str
    phaseStatus: str
    resultMd: str
    resultJson: str
    citations: List[str] = field(default_factory=list)
    steps: List[StepTrace] = field(default_factory=list)
    errorCode: Optional[str] = None
    errorMessage: Optional[str] = None
    verifierReport: Optional[Dict[str, Any]] = None
    repairRounds: List[Dict[str, Any]] = field(default_factory=list)
    finalDecision: Optional[str] = None
    modelUsage: List[ModelUsageTrace] = field(default_factory=list)
    currentRound: Optional[int] = None
    parserStage: Optional[str] = None
    rawResponseSnippet: Optional[str] = None
    usageUnavailableReason: Optional[str] = None
    finalAnswer: Optional[Dict[str, Any]] = None
