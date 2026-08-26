package com.alexpo.grammermate.domain.session

/**
 * Транзакционный координатор бизнес-события сессии (Фаза 2 плана стабилизации
 * 2026-08-26, ADR-001 pre-mortem №3: «атомарность не должна остановиться на
 * снимке сессии»).
 *
 * Engine оборачивает в [commit] составные события — «answer + shown + mastery»,
 * «completion + markLessonCompleted», — чтобы персистенция снимка и побочные
 * записи (mastery/SRS, завершение урока) применялись как ОДНА транзакция:
 * либо все изменения зафиксированы, либо ни одного.
 *
 * Чистый Kotlin-контракт: домен не знает о Room. Продакшн-реализация
 * (`RoomSessionCommitCoordinator`) выполняет блок внутри
 * `GrammarMateDatabase.withTransaction`; вложенные DAO-транзакции
 * (`SessionDao.saveSnapshot`, `MasteryDao.recordCardShow`) присоединяются
 * к внешней транзакции. В чистых JVM-тестах используется тривиальная
 * реализация-проход ([PassThroughCommitCoordinator]): атомарность там
 * обеспечивает fake.
 *
 * Обычный (не `fun`) interface: SAM-интерфейсам запрещены generic-методы,
 * а `commit` параметризован результатом блока.
 */
interface SessionCommitCoordinator {

    /**
     * Выполнить [block] как одну атомарную единицу работы.
     *
     * @param block составная операция (load → engine-правила → save → hooks).
     * @return результат блока; при исключении внутри блока все его persistent-
     *         эффекты откатываются.
     */
    suspend fun <R> commit(block: suspend () -> R): R
}

/** Тривиальный координатор без транзакции — unit-тесты и fake-окружение. */
val PassThroughCommitCoordinator: SessionCommitCoordinator =
    object : SessionCommitCoordinator {
        override suspend fun <R> commit(block: suspend () -> R): R = block()
    }

