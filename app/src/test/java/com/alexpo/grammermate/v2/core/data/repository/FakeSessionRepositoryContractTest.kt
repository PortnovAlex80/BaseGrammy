package com.alexpo.grammermate.v2.core.data.repository

import com.alexpo.grammermate.domain.repository.SessionRepository

/**
 * [SessionRepositoryContractSpec] против in-memory fake — половина gate
 * «contract suite проходит одинаково для fake и in-memory Room»
 * (Фаза 2 плана стабилизации 2026-08-26).
 */
class FakeSessionRepositoryContractTest : SessionRepositoryContractSpec() {

    override fun repository(): SessionRepository = FakeSessionRepository()
}
