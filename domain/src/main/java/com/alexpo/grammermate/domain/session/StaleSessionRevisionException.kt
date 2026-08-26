package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.SessionId

/**
 * Попытка сохранить снимок сессии, построенный из устаревшей ревизии
 * (Фаза 2 плана стабилизации 2026-08-26: «session revision/command token
 * для stale result и double-tap защиты»).
 *
 * Каждый durable-мутатор [SessionEngine] строит копию снимка с
 * `revision = загруженная + 1`; персистенция отвергает сохранение, если
 * в хранилище уже ДРУГАЯ ревизия — значит, между load и save работал второй
 * писатель (stale async-результат, повтор после process death, будущее
 * multi-owner окружение). Состояние хранилища при этом не изменяется.
 *
 * Presentation отображает это как recoverable error (перечитать сессию и
 * повторить команду), а не как засчитанный результат.
 */
class StaleSessionRevisionException(
    val sessionId: SessionId,
    val snapshotRevision: Long,
    val storedRevision: Long,
) : IllegalStateException(
    "Session ${sessionId.value} stale revision: snapshot=$snapshotRevision, stored=$storedRevision. " +
        "Re-load the session and retry the command.",
)
