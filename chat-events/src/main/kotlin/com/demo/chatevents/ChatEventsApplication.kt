package com.demo.chatevents

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

@SpringBootApplication
class ChatEventsApplication

fun main(args: Array<String>) {
    runApplication<ChatEventsApplication>(*args)
}

@Configuration
class RedisConfig {

    @Bean
    fun redisConnectionFactory(): ReactiveRedisConnectionFactory = LettuceConnectionFactory()

    @Bean
    fun stringRedisTemplate(): ReactiveStringRedisTemplate = ReactiveStringRedisTemplate(redisConnectionFactory())
}