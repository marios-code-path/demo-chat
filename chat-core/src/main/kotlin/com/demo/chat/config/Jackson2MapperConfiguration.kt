package com.demo.chat.config

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

/**
 * The bean name of the Jackson 2 mapper of this repository.
 *
 * Every injection point that takes a Jackson 2 mapper names this value. A
 * qualifier is not necessary today, because only one bean of the Jackson 2
 * type exists. It is necessary tomorrow: Spring Boot 4 builds Jackson 3
 * mappers beside it, and a second Jackson 2 mapper would otherwise bind by
 * type with no report.
 */
const val JACKSON_2_OBJECT_MAPPER = "jackson2ObjectMapper"

/**
 * The Jackson 2 mapper that Spring Boot 4 no longer builds.
 *
 * **Boot 4 builds Jackson 3 mappers only.** Its `JacksonAutoConfiguration`
 * supplies `JsonMapper`, `CborMapper` and `XmlMapper`, which are
 * `tools.jackson` types. No condition supplies
 * `com.fasterxml.jackson.databind.ObjectMapper`, and Boot 3.5.16 supplied one
 * on every deployment. So a Boot 4 launch failed at bean creation with "no
 * qualifying bean of type ObjectMapper". See CHAT-sxydbspq.
 *
 * **This class keeps the two mapper generations apart.** It does not replace
 * the Jackson 3 mappers, and it does not read them. A move of the repository
 * to `tools.jackson` is a separate decision that needs wire shape evidence.
 *
 * ### Why the construction matches Spring Boot 3.5.16
 *
 * Boot 3.5.16 built its mapper from `Jackson2ObjectMapperBuilder`, read from
 * the compiled `JacksonAutoConfiguration$JacksonObjectMapperConfiguration`.
 * Its standard customizer disabled two features and nothing more, read from
 * the compiled `FEATURE_DEFAULTS` map: `WRITE_DATES_AS_TIMESTAMPS` and
 * `WRITE_DURATIONS_AS_TIMESTAMPS`. It installed every Jackson 2 `Module` bean
 * through `modulesToInstall`, which adds to the well known modules rather than
 * replacing them.
 *
 * No `spring.jackson` property is set anywhere in this repository, measured on
 * 2026-09-18. So the customizer had nothing else to apply, and the three lines
 * below reproduce the mapper that Boot 3.5.16 built.
 *
 * ### Two facts to keep
 *
 * `Jackson2ObjectMapperBuilder` is deprecated in Spring Framework 7. It stays
 * here on purpose. It is the class that Boot 3.5.16 used, so it gives the
 * earlier behaviour by construction rather than by a hand written copy of it.
 * A hand written copy is what could drift.
 *
 * The module beans come from `DefaultChatJacksonModules` and from any module
 * that registers its own. `modulesToInstall` receives them, which is how the
 * chat domain deserializers reached the mapper under Boot 3.
 */
@Configuration(proxyBeanMethods = false)
open class Jackson2MapperConfiguration {

    @Bean(JACKSON_2_OBJECT_MAPPER)
    @Suppress("DEPRECATION")
    open fun jackson2ObjectMapper(
        context: ApplicationContext,
        modules: ObjectProvider<Module>,
    ): ObjectMapper = Jackson2ObjectMapperBuilder.json()
        .applicationContext(context)
        .modulesToInstall { install -> install.addAll(modules.orderedStream().toList()) }
        .featuresToDisable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS,
        )
        .build()
}
