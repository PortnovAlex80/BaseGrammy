package com.alexpo.grammermate.v2.core.data.packimport

import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.repository.ContentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seed bundled-пака при первом запуске — Фаза 1 плана стабилизации
 * 2026-08-26 (P0 «fresh install не имеет рабочего пути получения контента»).
 *
 * Идемпотентен: если пак уже установлен — no-op (политика версий/обновления
 * `updateDefaultPacksIfNeeded` — Фаза 5 плана). Ошибка seed'а не крашит
 * приложение: Home остаётся с empty-state, ошибка логируется; повторная
 * установка/перезапуск повторит попытку.
 *
 * Вызывается из `GrammarMateApplicationV2.onCreate` в application-scoped
 * корутине на [Dispatchers.IO] — не блокирует main thread.
 */
@Singleton
class BundledPackSeeder @Inject constructor(
    private val packImporter: PackImporter,
    private val contentRepository: ContentRepository,
) {

    /** Bundled-пак golden journey: итальянский экспресс-курс (63 урока, manifest v2). */
    companion object {
        const val BUNDLED_PACK_ASSET = "grammarmate/packs/ITALIAN_EXPRESS_SHORT.zip"
        const val BUNDLED_PACK_ID = "ITALIAN_SHORT"
    }

    /** Импортировать bundled-пак, если он ещё не установлен. */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        runCatching {
            val installed = contentRepository.getPack(PackId(BUNDLED_PACK_ID))
            if (installed == null) {
                packImporter.importPackFromAssets(BUNDLED_PACK_ASSET)
            }
        }.onFailure { e ->
            // Не крашим первый запуск: Home покажет empty-state, следующий
            // launch повторит попытку (идемпотентность по наличию пака).
            android.util.Log.w(TAG, "Bundled pack seed failed: ${e.message}")
        }
    }

    private val TAG: String get() = "BundledPackSeeder"
}
