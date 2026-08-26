package com.alexpo.grammermate.v2.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.domain.repository.SessionRepository
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SessionRepositoryContractSpec] против in-memory Room (Robolectric) —
 * реальная SQLite-транзакционность, миграции и constraint'ы.
 *
 * Фаза 2 плана стабилизации 2026-08-26: gate «contract suite проходит
 * одинаково для fake и in-memory Room».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13: max supported SDK
class RoomSessionRepositoryContractTest : SessionRepositoryContractSpec() {

    private lateinit var db: GrammarMateDatabase

    @Before
    fun setUpDb() = runBlocking {
        db = TrainingDbFixture.inMemory(ApplicationProvider.getApplicationContext())
        TrainingDbFixture.seedTrainingContent(db)
    }

    @After
    fun tearDownDb() {
        db.close()
    }

    override fun repository(): SessionRepository = SessionRepositoryImpl(db.sessionDao())
}
