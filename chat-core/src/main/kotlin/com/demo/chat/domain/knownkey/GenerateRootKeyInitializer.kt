package com.demo.chat.domain.knownkey

import com.demo.chat.service.core.IKeyGenerator

/**
 * This class fills a `RootKeys` with a fresh root for every domain and a fresh
 * key for each identity. Tests use it. T2 of `CHAT-avduuqwp` replaces it with
 * `RootKeysFixture`.
 */
class GenerateRootKeyInitializer<T>(private val keyGen: IKeyGenerator<T>) {

    fun initRootKeys(rootKeys: RootKeys<T>) {
        rootKeys.loadDomains(ChatDomain.entries.associateWith { keyGen.nextKey() })
        rootKeys.loadIdentities(keyGen.nextKey(), keyGen.nextKey())
    }
}
