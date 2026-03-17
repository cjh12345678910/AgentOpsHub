from dataclasses import dataclass, field
from typing import Any, Dict, List


@dataclass
class RuleResult:
    rule: str
    passed: bool
    reason: str
    detail: str
    score: float = 1.0
    appliedThreshold: float = 0.0


@dataclass
class VerifierReport:
    overallPass: bool
    failedReasons: List[str] = field(default_factory=list)
    ruleResults: List[RuleResult] = field(default_factory=list)
    summary: str = ""


def run_verifier(result_obj: Dict[str, Any], citations: List[str], coverage_threshold: float, expected_doc_count: int) -> VerifierReport:
    rule_results: List[RuleResult] = []

    summary = str(result_obj.get("summary") or "").strip()
    schema_pass = bool(summary)
    rule_results.append(
        RuleResult(
            rule="schema",
            passed=schema_pass,
            reason="" if schema_pass else "missing_fields",
            detail="summary field must be non-empty",
        )
    )

    citations_pass = len(citations) > 0
    rule_results.append(
        RuleResult(
            rule="citations",
            passed=citations_pass,
            reason="" if citations_pass else "no_citations",
            detail="result must include citations",
        )
    )

    if expected_doc_count <= 0:
        coverage_score = 1.0
    else:
        coverage_score = min(1.0, len(citations) / float(expected_doc_count))
    coverage_pass = coverage_score >= coverage_threshold
    rule_results.append(
        RuleResult(
            rule="coverage",
            passed=coverage_pass,
            reason="" if coverage_pass else "format_error",
            detail=f"coverage score={coverage_score:.2f}",
            score=coverage_score,
            appliedThreshold=coverage_threshold,
        )
    )

    lowered_summary = summary.lower()
    risk_pass = "contradiction" not in lowered_summary
    rule_results.append(
        RuleResult(
            rule="risk",
            passed=risk_pass,
            reason="" if risk_pass else "contradiction",
            detail="summary must not contain contradiction marker",
        )
    )

    failed_reasons = [r.reason for r in rule_results if not r.passed and r.reason]
    unique_reasons = list(dict.fromkeys(failed_reasons))
    overall = len(unique_reasons) == 0
    report_summary = "all verifier rules passed" if overall else f"verifier failed: {', '.join(unique_reasons)}"

    return VerifierReport(
        overallPass=overall,
        failedReasons=unique_reasons,
        ruleResults=rule_results,
        summary=report_summary,
    )
