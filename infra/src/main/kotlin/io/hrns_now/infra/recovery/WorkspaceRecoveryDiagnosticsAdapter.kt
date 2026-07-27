package io.hrns_now.infra.recovery

import io.hrns_now.core.domain.model.ContinuityDiagnosticSummary
import io.hrns_now.core.domain.model.FailureHistorySummary
import io.hrns_now.core.domain.model.RecoveryDiagnostics
import io.hrns_now.core.domain.model.UsageLedgerSummary
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.port.RecoveryDiagnosticsPort
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Harness의 optional diagnostic tree를 안전한 집계값으로만 읽는다. 각 record의 raw session
 * identifier, request thread, absolute path와 payload는 절대 반환하지 않는다. 파일이 없거나
 * 일부 손상돼도 WORKFLOW_STATE.json 기반 CTA에는 영향을 주지 않는다.
 */
class WorkspaceRecoveryDiagnosticsAdapter : RecoveryDiagnosticsPort {
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(day: WorkspaceDay): RecoveryDiagnostics = RecoveryDiagnostics(
        continuity = readContinuity(day),
        usageLedger = readUsageLedger(day),
        failureHistory = readFailureHistory(day.projectWorkspaceRoot),
    )

    private fun readContinuity(day: WorkspaceDay): ContinuityDiagnosticSummary {
        val root = day.projectWorkspaceRoot.resolve("logs").resolve("claude-session-continuity").resolve(day.date.toString())
        if (!Files.isDirectory(root)) return emptyContinuity()
        var records = 0
        var actualResumeApplied = 0
        var freshRequired = 0
        var unreadable = 0
        try {
            Files.walk(root).use { paths ->
                paths.filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".session-continuity.json") }
                    .forEach { path ->
                        val record = readObject(path)
                        if (record == null) {
                            unreadable += 1
                        } else {
                            records += 1
                            if (record.boolean("actual_resume_applied")) actualResumeApplied += 1
                            if (record.boolean("fresh_required")) freshRequired += 1
                        }
                    }
            }
        } catch (_: Exception) {
            unreadable += 1
        }
        return ContinuityDiagnosticSummary(true, records, actualResumeApplied, freshRequired, unreadable)
    }

    private fun readUsageLedger(day: WorkspaceDay): UsageLedgerSummary {
        val ledger = day.projectWorkspaceRoot.resolve("logs").resolve("usage-ledger").resolve("${day.date}.jsonl")
        if (!Files.isRegularFile(ledger)) return UsageLedgerSummary(false, 0, 0, 0)
        var records = 0
        var sessionMetadataPresent = 0
        var unreadable = 0
        try {
            Files.newBufferedReader(ledger, StandardCharsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() }.forEach { line ->
                    val record = try {
                        json.parseToJsonElement(line.removePrefix("\uFEFF")) as? JsonObject
                    } catch (_: Exception) {
                        null
                    }
                    if (record == null) {
                        unreadable += 1
                    } else {
                        records += 1
                        if (record.boolean("session_id_present")) sessionMetadataPresent += 1
                    }
                }
            }
        } catch (_: Exception) {
            unreadable += 1
        }
        return UsageLedgerSummary(true, records, sessionMetadataPresent, unreadable)
    }

    private fun readFailureHistory(workspaceRoot: Path): FailureHistorySummary {
        val history = workspaceRoot.resolve("HARNESS_FAILURES.md")
        if (!Files.isRegularFile(history)) return FailureHistorySummary(false, 0)
        return try {
            val entries = Files.newBufferedReader(history, StandardCharsets.UTF_8).useLines { lines ->
                lines.count { it.trimStart().startsWith("### [") }
            }
            FailureHistorySummary(true, entries)
        } catch (_: Exception) {
            FailureHistorySummary(false, 0)
        }
    }

    private fun readObject(path: Path): JsonObject? = try {
        json.parseToJsonElement(Files.readString(path, StandardCharsets.UTF_8).removePrefix("\uFEFF")) as? JsonObject
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.boolean(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull == true

    private fun emptyContinuity() = ContinuityDiagnosticSummary(false, 0, 0, 0, 0)
}
