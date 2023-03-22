package com.demo.chat.deploy.test

import com.demo.chat.config.deploy.init.InitializationProperties
import com.demo.chat.test.YamlFileContextInitializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@ExtendWith(SpringExtension::class)
@SpringJUnitConfig(initializers = [LoadInitializationPropertyTests.TestProperties::class])
@Import(LoadInitializationPropertyTestsConfig::class)
class LoadInitializationPropertyTests {
    class TestProperties : YamlFileContextInitializer("classpath:application.yml")

    @Autowired
    private lateinit var props: InitializationProperties

    @Test
    fun `should load yml into initializationProperties`() {
        println("props = " + props)
        Assertions
            .assertThat(props.initialRoles.roles)
            .isNotNull
            .hasSizeGreaterThan(1)
    }
}

@TestConfiguration
@EnableConfigurationProperties(InitializationProperties::class)
class LoadInitializationPropertyTestsConfig