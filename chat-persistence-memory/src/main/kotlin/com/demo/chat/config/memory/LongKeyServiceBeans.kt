package com.demo.chat.config.memory

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import org.springframework.context.annotation.Bean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random

open class LongKeyServiceBeans : KeyServiceBeans<Long> {
    private val atom = AtomicLong(abs(Random.nextLong()))

    @Bean
    override fun keyService() = KeyServiceInMemory { atom.incrementAndGet() }
}