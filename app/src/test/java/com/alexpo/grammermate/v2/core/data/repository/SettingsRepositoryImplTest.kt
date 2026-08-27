package com.alexpo.grammermate.v2.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.alexpo.grammermate.v2.core.data.local.dao.UserContentDao
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(temporaryFolder.root, "settings.preferences_pb")
        }
        repository = SettingsRepositoryImpl(
            dataStore = dataStore,
            userContentDao = mockk<UserContentDao>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun updateAppConfig_persistsSessionSize() = runBlocking {
        repository.updateAppConfig { it.copy(sessionSize = 24) }

        assertThat(repository.getAppConfig().sessionSize).isEqualTo(24)
    }

    @Test
    fun updateAppConfig_clampsSessionSizeToDocumentedRange() = runBlocking {
        repository.updateAppConfig { it.copy(sessionSize = -1) }
        assertThat(repository.getAppConfig().sessionSize).isEqualTo(3)

        repository.updateAppConfig { it.copy(sessionSize = 10_001) }
        assertThat(repository.getAppConfig().sessionSize).isEqualTo(1_000)
    }
}
