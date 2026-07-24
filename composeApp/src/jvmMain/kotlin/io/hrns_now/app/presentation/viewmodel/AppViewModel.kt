package io.hrns_now.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.hrns_now.app.presentation.mapper.CockpitUiStateAssembler
import io.hrns_now.app.presentation.model.HrnsUiEvent
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.WorkspaceDaySelection
import io.hrns_now.core.result.StateReadResult
import io.hrns_now.core.usecase.LoadCockpitUseCase
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 1C의 단일 UI 상태 보유자다. 조회 orchestration은 [LoadCockpitUseCase], 화면 조립은
 * [CockpitUiStateAssembler]에 위임하고 이 클래스는 refresh/polling/lifecycle만 관리한다.
 * 모든 filesystem 협력자는 [ioDispatcher] 안에서만 호출한다.
 */
class AppViewModel(
    private val loadCockpit: LoadCockpitUseCase,
    private val changeProbe: (WorkspaceDay) -> FileTime?,
    private val uiStateAssembler: CockpitUiStateAssembler = CockpitUiStateAssembler(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMillis: Long = 3000L,
    private val clock: () -> Instant = Instant::now,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(CoroutineScope(SupervisorJob() + mainDispatcher)) {

    private val _state = MutableStateFlow<HrnsUiState>(HrnsUiState.Loading)
    val state: StateFlow<HrnsUiState> = _state.asStateFlow()

    private var selectedDay: WorkspaceDaySelection? = null
    private var lastSuccessfulReadAt: Instant? = null
    private var lastAttemptAt: Instant? = null
    private var lastPolledMtime: FileTime? = null
    private var loadSequence: Long = 0L
    private var pollingJob: Job? = null

    init {
        refresh()
        startPolling()
    }

    /** 수동 새로고침이다. 날짜를 다시 선택하고 Reader를 강제로 다시 호출한다. */
    fun refresh() {
        viewModelScope.launch { loadOnce(forceRead = true) }
    }

    /** Phase 1C에서 실제로 연결된 read-only action만 처리한다. */
    fun onEvent(event: HrnsUiEvent) {
        when (event) {
            is HrnsUiEvent.ActionRequested -> if (event.action == UiAction.Refresh) refresh()
        }
    }

    private fun startPolling() {
        if (pollingJob != null) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(pollIntervalMillis)
                loadOnce(forceRead = false)
            }
        }
    }

    /**
     * polling은 선택된 State 파일의 mtime이 달라진 경우에만 전체 query를 다시 실행한다.
     * 변경 없는 polling tick은 [loadSequence]를 증가시키지 않으므로 진행 중인 수동 refresh를
     * 무효화하지 않는다. 날짜 탐색과 mtime 조회도 모두 IO dispatcher에서 수행한다.
     */
    private suspend fun loadOnce(forceRead: Boolean) {
        val daySelection = if (forceRead || selectedDay == null) {
            withContext(ioDispatcher) { loadCockpit.resolveDay() }
        } else {
            requireNotNull(selectedDay)
        }

        val currentMtime = if (loadCockpit.hasConfiguredWorkspace) {
            withContext(ioDispatcher) { changeProbe(daySelection.workspaceDay) }
        } else {
            null
        }
        val mtimeChanged = currentMtime != lastPolledMtime
        val shouldRead = forceRead || mtimeChanged || _state.value == HrnsUiState.Loading
        if (!shouldRead) return

        val sequence = ++loadSequence
        val loaded = withContext(ioDispatcher) { loadCockpit(daySelection) }
        if (sequence != loadSequence) return

        selectedDay = daySelection
        lastPolledMtime = currentMtime
        val now = clock()
        lastAttemptAt = now
        if (loaded.stateRead is StateReadResult.Success) {
            lastSuccessfulReadAt = now
        }

        _state.value = uiStateAssembler.assemble(
            loaded = loaded,
            lastSuccessfulReadAtLabel = lastSuccessfulReadAt?.let(::formatInstant),
            lastAttemptAtLabel = lastAttemptAt?.let(::formatInstant),
        )
    }

    /** 테스트나 lifecycle owner가 없는 host에서 polling과 내부 scope를 명시적으로 취소한다. */
    fun dispose() {
        pollingJob?.cancel()
        pollingJob = null
        viewModelScope.cancel()
    }

    private fun formatInstant(instant: Instant): String =
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(instant)
}
