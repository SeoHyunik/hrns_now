package io.hrns_now.infra.process

import java.util.concurrent.TimeUnit

/**
 * timeout/cancel 시 Windows child process tree까지 종료를 시도하고, 종료 뒤 잔존 여부를
 * 검사한다(`doc/claude_prompts/phase3-process-adapter-lock.md` §2). `Process.destroy()`
 * 호출 성공만으로 취소가 끝났다고 가정하지 않는다 — 항상 [terminate]의 반환값(잔존 여부)을
 * 확인해서 typed result에 반영해야 한다.
 *
 * `ProcessHandle`(JDK 9+)의 `descendants()`를 사용한다 — `taskkill` 외부 프로세스를 새로
 * 기동하지 않고 JVM 안에서 tree를 순회·종료·재확인할 수 있다.
 */
class WindowsProcessTreeTerminator(
    private val graceMillis: Long = 1500L,
) {

    /** tree 종료를 시도한 뒤 여전히 살아있는 process(자신 포함)가 있으면 true를 반환한다. */
    fun terminate(process: Process): Boolean {
        val handle = process.toHandle()
        // 부모가 먼저 종료되면 descendants() 관계가 사라질 수 있으므로 종료 전 snapshot을 유지한다.
        val targets = (handle.descendants().toList() + handle).distinctBy { it.pid() }

        destroyAll(targets, forcibly = false)
        val exitedGracefully = try {
            process.waitFor(graceMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        if (!exitedGracefully || targets.any { it.isAlive }) {
            destroyAll(targets, forcibly = true)
            try {
                process.waitFor(graceMillis, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        return targets.any { it.isAlive }
    }

    fun hasResidualProcess(handle: ProcessHandle): Boolean =
        handle.isAlive || handle.descendants().anyMatch { it.isAlive }

    private fun destroyAll(targets: List<ProcessHandle>, forcibly: Boolean) {
        targets.forEach { target ->
            if (target.isAlive) {
                if (forcibly) target.destroyForcibly() else target.destroy()
            }
        }
    }
}
