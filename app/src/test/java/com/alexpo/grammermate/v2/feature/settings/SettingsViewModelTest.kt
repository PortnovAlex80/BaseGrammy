package com.alexpo.grammermate.v2.feature.settings

import com.alexpo.grammermate.domain.model.AppConfig
import com.alexpo.grammermate.domain.model.ThemeMode
import com.alexpo.grammermate.domain.repository.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * SettingsViewModel (Фаза 7): реактивное чтение AppConfig + обновление темы
 * через updateAppConfig (применяется AppViewModel'ем без перезапуска).
 */
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val configFlow = MutableStateFlow(AppConfig())

    private val repository = mockk<SettingsRepository>(relaxed = true) {
        every { observeAppConfig() } returns configFlow
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observes theme mode reactively`() {
        val vm = SettingsViewModel(repository)
        assertThat(vm.state.value).isEqualTo(SettingsViewState.Content(ThemeMode.SYSTEM))

        configFlow.value = AppConfig(themeMode = ThemeMode.DARK)
        assertThat(vm.state.value).isEqualTo(SettingsViewState.Content(ThemeMode.DARK))
    }

    @Test
    fun `setThemeMode writes through updateAppConfig`() {
        val vm = SettingsViewModel(repository)

        vm.setThemeMode(ThemeMode.LIGHT)

        io.mockk.coVerify {
            repository.updateAppConfig(any())
        }
    }
}
