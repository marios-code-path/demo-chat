package com.demo.chat.config.deploy.web

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.web.reactive.config.WebFluxConfigurer
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper

/**
 * The JSON shape of every HTTP response of a deployment.
 *
 * **This repository never stated its server JSON codec, and a framework move
 * changed the wire because of that.** The story is worth keeping, because the
 * silence is the defect rather than either shape.
 *
 * Every deployment declares `@EnableWebFlux`, which registers a
 * `WebFluxConfigurationSupport` bean. Spring Boot `WebFluxAutoConfiguration`
 * carries `@ConditionalOnMissingBean(WebFluxConfigurationSupport)`, so it backs
 * off. Its inner `WebFluxConfig.configureHttpMessageCodecs` is the only place
 * that applies `CodecCustomizer` beans to the server codecs, so **no Spring
 * Boot mapper has ever served a response here**. `BaseDefaultCodecs` then built
 * the encoder with no arguments, and the encoder used its own default mapper.
 *
 * That default changed with the framework, measured on 2026-09-18 against the
 * same deployment and the same request:
 *
 * | Build | `startedAt` |
 * |---|---|
 * | Spring Boot 3.5.16, spring-web 6.2.19 | `1789754129.614384` |
 * | Spring Boot 4.0.8, spring-web 7.0.9 | `"2026-09-18T17:53:58.500838Z"` |
 *
 * spring-web 6.2.19 built a `Jackson2JsonEncoder`, and Jackson 2 enables
 * `WRITE_DATES_AS_TIMESTAMPS` by default. spring-web 7.0.9 picks the Jackson 3
 * branch first and builds a `JacksonJsonEncoder`. Jackson 3 removed
 * `WRITE_DATES_AS_TIMESTAMPS` from `SerializationFeature` and writes ISO-8601.
 *
 * **The owner chose ISO-8601 on 2026-09-18.** The numeric shape was never a
 * decision. It was the Jackson 2 default reached through the back off above,
 * and Spring Boot 3.5.16 would have written ISO-8601 through its own mapper,
 * because its `FEATURE_DEFAULTS` disabled that feature.
 *
 * So this class states the codec. The two features are disabled by name rather
 * than left to the default, because the default is what moved last time.
 *
 * `docs/VECTOR-RECALL-API.md` and
 * `shell-scripts/vector/gate-embedding-launch.sh` read these timestamps. Both
 * compare ISO-8601 text. See CHAT-ngevggjk.
 *
 * This class does not touch the Jackson 2 mapper. That bean is
 * `jackson2ObjectMapper`, it serves the codecs and stores of this repository,
 * and it never serves an HTTP response. See CHAT-sxydbspq.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
class ServerJsonCodecConfiguration : WebFluxConfigurer {

    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        val mapper = JsonMapper.builder()
            .disable(
                DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS,
                DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS,
            )
            .build()

        configurer.defaultCodecs().jacksonJsonEncoder(JacksonJsonEncoder(mapper))
        configurer.defaultCodecs().jacksonJsonDecoder(JacksonJsonDecoder(mapper))
    }
}
