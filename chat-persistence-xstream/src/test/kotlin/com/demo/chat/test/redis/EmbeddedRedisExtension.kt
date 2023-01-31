package com.demo.chat.test.redis


@Deprecated("Should use TestContainers")
class EmbeddedRedisExtension { //} : BeforeAllCallback, BeforeEachCallback, AfterAllCallback {
//    private lateinit var redisServer: RedisServer
//    private val logger = LoggerFactory.getLogger(this::class.qualifiedName)
//    private val redisPath = System.getenv("REDIS_TEST_PATH") ?: "/usr/local/bin/redis-server"
//
//    override fun beforeAll(context: ExtensionContext?) {
//        try {
//            logger.debug("Starting a Redis Server ${redisPath} on ${TestConfigurationPropertiesRedisCluster.port}")
//            redisServer = RedisServer(File(redisPath), TestConfigurationPropertiesRedisCluster.port)
//            redisServer.start()
//        } catch (e: Throwable) {
//            logger.error("Redis Server Startup failed with: ${e.message}")
//        }
//    }
//
//    override fun afterAll(context: ExtensionContext?) {
//        try {
//            if (redisServer.isActive)
//                redisServer.stop()
//        } catch (e: Throwable) {
//            logger.error("Redis Server Shutdown failed with: ${e.message}")
//        }
//    }
//
//    override fun beforeEach(p0: ExtensionContext?) {
//        try {
//            if (redisServer.isActive) {
//                val factory = LettuceConnectionFactory(
//                        RedisStandaloneConfiguration(
//                                TestConfigurationPropertiesRedisCluster.host,
//                                TestConfigurationPropertiesRedisCluster.port))
//                factory.afterPropertiesSet()
//
//                factory
//                        .connection
//                        .serverCommands()
//                        .flushAll()
//            }
//        } catch (e: Throwable) {
//            e.printStackTrace()
//            logger.error("Redis 'Flush' failed with: ${e.message}")
//        }
//    }
}