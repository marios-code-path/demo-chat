package com.demo.chat.domain.knownkey

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import java.util.concurrent.ConcurrentHashMap

class RootKeys<T> {

    private var keyMap: MutableMap<String, Key<T>> = ConcurrentHashMap(20)
        set(value) {
            field.clear()
            field.putAll(value)
        }
        get() = field

    fun merge(other: Map<String, Key<T>>) {
        keyMap.putAll(other)
    }
    fun getMapOfKeyMap(): Map<String, Key<T>> = keyMap.toMap()

    fun <S> getRootKey(domain: Class<S>): Key<T> = getRootKey(domain.simpleName)

    /**
     * Both overloads route through here so they cannot drift.
     *
     * This used to be `keyMap[domain]!!`, which turned a missing key into a
     * bare NullPointerException naming neither the domain requested nor what
     * the map held. Those two cases look identical from the stack trace and
     * are entirely different faults:
     *
     *  - the map is empty, so root-key initialization never ran; or
     *  - the map is populated and this one domain is genuinely absent.
     *
     * The first is a deployment or startup-ordering problem, the second a
     * caller asking for something that was never registered. Naming the
     * contents is what separates them.
     *
     * The return stays non-null. Every one of the callers - authentication,
     * access checks, the anonymous payload interceptor, shell identity -
     * requires the key to proceed, so a nullable return would only push an
     * unhandleable condition outward.
     */
    fun getRootKey(domain: String): Key<T> =
        keyMap[domain] ?: throw ChatException(
            if (keyMap.isEmpty()) {
                "No root key '$domain': no root keys are initialized at all. " +
                    "Root keys are populated at startup - locally when app.rootkeys.create is set, " +
                    "or fetched from a peer's /actuator/rootkeys when app.rootkeys.consume.scheme=http. " +
                    "An empty map means neither ran, or ran before the source was ready."
            } else {
                "No root key '$domain'. Known root keys: ${keyMap.keys.sorted().joinToString(", ")}"
            }
        )
    fun <S> addRootKey(domain: Class<S>, key: Key<T>) = keyMap.put(domain.simpleName, key)
    fun <S> isRootKeyWithValue(domain: Class<S>) = keyMap.containsKey(domain.simpleName)
    fun hasKey(key: String) = keyMap.containsKey(key)
    fun <S> hasKey(domain: Class<S>) = keyMap.containsKey(domain.simpleName)
    fun addRootKey(domain: String, key: Key<T>) = keyMap.put(domain, key)
    fun <S> isRootKeyWithValue(domain: Class<S>, key: Key<T>): Boolean {
        return if(isRootKeyWithValue(domain)) {
            getRootKey(domain).id == key.id
        }
        else {
            false
        }
    }

    companion object {
        fun <T> rootKeySummary(rootKeys: RootKeys<T>): String {
            val sb = StringBuilder()

            sb.append("Root Keys: \n")
            for (rootKey in rootKeys.keyMap.keys) {
                sb.append("${rootKey}=${rootKeys.getRootKey(rootKey)}\n")
            }

            return sb.toString()
        }
    }
}