package io.hrns_now.infra.process

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 실제 `cmd.exe`가 `powershell.exe` 자식을 낳는 2단 process tree를 기동해 검증한다(mock이 아닌
 * real child fixture). 이 host/CI 환경에 따라 `ProcessHandle.descendants()`가 실제 OS
 * process tree를 완전히 못 볼 수도 있으므로, 그 경우는 별도로 보고한다(§ 필수 테스트 2).
 */
class WindowsProcessTreeTerminatorTest {

    private val terminator = WindowsProcessTreeTerminator(graceMillis = 800L)

    @Test
    fun `실제 자식 트리를 기동한 뒤 종료하면 잔존 프로세스가 없다고 확인된다`() {
        val process = ProcessBuilder(
            "cmd.exe", "/c", "powershell.exe -NoProfile -Command \"Start-Sleep -Seconds 30\"",
        ).start()

        try {
            // 트리가 실제로 자리잡을 시간을 잠깐 준다(cmd -> powershell 자식 기동).
            Thread.sleep(500)
            assertTrue(process.isAlive, "부모 process(cmd.exe)가 아직 살아있어야 한다")

            val hadDescendantBeforeTerminate = process.toHandle().descendants().count() > 0
            if (!hadDescendantBeforeTerminate) {
                println(
                    "[환경 한계] 이 host에서 cmd.exe -> powershell.exe 자식이 " +
                        "ProcessHandle.descendants()로 보이지 않았다. 여전히 terminate() 자체는 검증한다.",
                )
            }

            val residual = terminator.terminate(process)

            assertFalse(residual, "종료 시도 후에는 잔존 프로세스가 없어야 한다")
            assertFalse(terminator.hasResidualProcess(process.toHandle()))
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
        }
    }

    @Test
    fun `이미 정상 종료된 프로세스는 잔존이 없다고 즉시 확인된다`() {
        val process = ProcessBuilder("cmd.exe", "/c", "exit 0").start()
        process.waitFor(5, TimeUnit.SECONDS)

        val residual = terminator.terminate(process)

        assertFalse(residual)
    }
}
