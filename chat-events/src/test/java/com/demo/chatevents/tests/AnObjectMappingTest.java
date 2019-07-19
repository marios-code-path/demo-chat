package com.demo.chatevents.tests;

import com.demo.chatevents.Zoom;
import com.demo.chatevents.config.ConfigurationTopicRedis;
import com.demo.chatevents.topic.TopicData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.hash.HashMapper;
import org.springframework.data.redis.hash.Jackson2HashMapper;
import org.springframework.data.redis.hash.ObjectHashMapper;
import org.springframework.data.redis.serializer.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Hooks;

@ExtendWith(SpringExtension.class)
@Import(TopicRedisTemplateConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureJsonTesters
public class AnObjectMappingTest {

    private int port = 6379;
    private ReactiveRedisTemplate<String, Zoom> template;

    //@Autowired
    private ReactiveRedisTemplate<String, TopicData> msgTemplate;

    private ReactiveStringRedisTemplate stringTemplate;

    private Jackson2HashMapper hashMapper;

    private HashMapper<Object, byte[], byte[]> objectHashMapper;

    private Logger logger = LoggerFactory.getLogger(AnObjectMappingTest.class);

    @BeforeAll
    void setupRedis() {

        LettuceConnectionFactory lettuce = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port));

        lettuce.afterPropertiesSet();

        RedisSerializer<String> serializer = new StringRedisSerializer();
        RedisSerializer<Zoom> topicSer = new Jackson2JsonRedisSerializer(Zoom.class);

        hashMapper = new Jackson2HashMapper(true);

        objectHashMapper = new ObjectHashMapper();

        template = new ReactiveRedisTemplate(lettuce,
                RedisSerializationContext
                        .newSerializationContext(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                        .hashKey(serializer)
                        .hashValue(new GenericJackson2JsonRedisSerializer())
                        .build()
        );

        msgTemplate = new ReactiveRedisTemplate(lettuce,
                RedisSerializationContext
                        .newSerializationContext(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                        .hashKey(serializer)
                        .hashValue(topicSer)
                        .build()
        );

        stringTemplate = new ReactiveStringRedisTemplate(lettuce);
        Hooks.onOperatorDebug();
    }

    //@Test
//    void testShouldHashMap() {
//        Zoom zoom = new Zoom("foo");
//
//        Map<byte[], byte[]> hash = objectHashMapper.toHash(zoom).entrySet().stream()
//                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
//
//        Zoom mappedBack = (Zoom)objectHashMapper.fromHash(hash);
//
//        logger.info("Mapped from: " + hash);
//
//        logger.info("Mapped back to: " + mappedBack);
//
//        Publisher writer = stringTemplate
//                .opsForHash()
//                .putAll("test", hash)
//                .then();
//
//        Publisher<Map<Object, Object>> reader = stringTemplate
//                .opsForHash()
//                .entries("test")
//                .collect(Collectors.toMap(e -> e.getKey(), e-> e.getValue()));
//
//        Publisher stream = Mono.from(writer)
//                .thenMany(reader)
//                ;
//
//        StepVerifier
//                .create(stream)
//                .assertNext( hashmap -> {
//                    logger.info("hashmap: " + hashmap);
//                    logger.info("hashmapclass: " + hashmap);
//
//                    Map<byte[], byte[]> demap = (Map<byte[], byte[]>)hashmap;
//
//                    logger.info("demap: " + demap);
//
//                    Object deser = objectHashMapper.fromHash(demap);
//                })
//                .verifyComplete();
//    }
//
//    @Test
//    void testShouldDeserializeObjectRecord() {
//        RecordId recordId = RecordId.autoGenerate();
//        String testStreamKey = "test";
//
//        ObjectRecord<String, Zoom> objectRecord = StreamRecords.newRecord()
//                .in(testStreamKey)
//                .ofObject(new Zoom("foo"))
//                .withId(recordId);
//
//        Mono<RecordId> sendStream = msgTemplate
//                .opsForStream(new Jackson2HashMapper(true))
//                .add(objectRecord)
//                .checkpoint("Send");
//
//        Flux<ObjectRecord<String, Zoom>> receiveStream = msgTemplate
//                .opsForStream(new Jackson2HashMapper(true))
//                .read(Zoom.class, StreamOffset.fromStart("test"))
//                .checkpoint("receive");
//
//        Flux<ObjectRecord<String, Zoom>> testStream = Flux
//                .from(sendStream)
//                .thenMany(receiveStream);
//
//        StepVerifier
//                .create(testStream)
//                .expectSubscription()
//                .assertNext((x) -> {
//                    Assertions
//                            .assertThat(x.getValue())
//                            .isNotNull()
//                            .hasNoNullFieldsOrProperties()
//                            .hasFieldOrProperty("data");
//                })
//                .verifyComplete();
//
//    }

}