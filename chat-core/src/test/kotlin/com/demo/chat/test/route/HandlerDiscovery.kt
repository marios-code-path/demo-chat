package com.demo.chat.test.route

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.core.VerifiedKey
import org.springframework.core.MethodParameter
import org.springframework.core.ResolvableType
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.TypeVariable

/**
 * One handler that a guard found, with every key and id of its input. See
 * `CHAT-avduuqwp`, T4 step 7.
 *
 * [signature] names the parameter types, so a changed handler no longer
 * matches its catalog entry.
 */
data class DiscoveredHandler(
    val owner: String,
    val method: String,
    val route: String,
    val signature: String,
    val identityFields: Set<String>,
)

/**
 * Finds the handlers of a set of classes by reflection.
 *
 * - A handler is a method that carries the mapping annotation, found through
 *   merged annotations. That includes a handler that a class inherits from a
 *   generic interface.
 * - Each parameter type resolves against the concrete class, so an entity
 *   type parameter becomes its entity.
 * - The walk follows getters into nested values and into collections.
 *
 * **Three rules decide what an id is.**
 *
 * 1. A type variable named `T` that no class resolves is the key type of this
 *    repository. A value of that type is an id.
 * 2. A parameter bound from a path variable is an id, whatever its type.
 * 3. An untyped property named `key` is an id. `KVRequest` carries one.
 *
 * A `Key`, a `MessageKey`, and a `VerifiedKey` are keys.
 */
object HandlerDiscovery {
    private const val MAX_DEPTH = 4

    fun all(
        types: Collection<Class<*>>,
        mapping: Class<out Annotation>,
        routeOf: (Class<*>, Method) -> String,
    ): List<DiscoveredHandler> = types.flatMap { type -> handlersOf(type, mapping, routeOf) }
        .sortedWith(compareBy({ it.owner }, { it.method }))

    private fun handlersOf(type: Class<*>, mapping: Class<out Annotation>, routeOf: (Class<*>, Method) -> String) =
        ReflectionUtils.getUniqueDeclaredMethods(type) { method ->
            !method.isBridge && !method.isSynthetic && Modifier.isPublic(method.modifiers) &&
                AnnotatedElementUtils.hasAnnotation(method, mapping)
        }
            .map { method ->
                val parameters = (0 until method.parameterCount).map { index ->
                    MethodParameter(method, index).withContainingClass(type)
                }
                DiscoveredHandler(
                    owner = type.simpleName,
                    method = method.name,
                    route = routeOf(type, method),
                    signature = parameters.joinToString(",") { typeName(ResolvableType.forMethodParameter(it)) },
                    identityFields = parameters.flatMap { parameter ->
                        val name = parameter.parameterName ?: "arg${parameter.parameterIndex}"
                        if (isPathVariable(parameter)) listOf(name)
                        else walk(name, ResolvableType.forMethodParameter(parameter), 0, emptySet())
                    }.toSortedSet(),
                )
            }

    /** The names of every key and id inside a value of [type], rooted at [path]. */
    fun walk(path: String, type: ResolvableType, depth: Int, seen: Set<Class<*>>): List<String> {
        if (isId(type)) return listOf(path)
        val raw = type.resolve() ?: return emptyList()

        if (VerifiedKey::class.java.isAssignableFrom(raw)) return listOf(path)
        if (MessageKey::class.java.isAssignableFrom(raw)) return listOf(path, "$path.from", "$path.dest")
        if (Key::class.java.isAssignableFrom(raw)) return listOf(path)

        if (Collection::class.java.isAssignableFrom(raw) || raw.isArray) {
            val element = if (raw.isArray) type.componentType else type.asCollection().getGeneric(0)
            return walk("$path[]", element, depth + 1, seen)
        }
        if (Map::class.java.isAssignableFrom(raw)) return emptyList()

        if (depth >= MAX_DEPTH || raw in seen || !raw.name.startsWith("com.demo.chat")) return emptyList()

        return getters(raw).flatMap { getter ->
            if (getter.returnType == Any::class.java && propertyName(getter) == "key") return@flatMap listOf("$path.key")
            walk("$path.${propertyName(getter)}", ResolvableType.forMethodReturnType(getter, raw).let { declared ->
                // A getter type variable resolves through the value type, so User<T>.key stays Key<T>.
                if (declared.type is TypeVariable<*>) type.`as`(getter.declaringClass).getGeneric(
                    getter.declaringClass.typeParameters.indexOfFirst { it.name == (declared.type as TypeVariable<*>).name }
                        .coerceAtLeast(0)
                ).takeIf { it != ResolvableType.NONE } ?: declared
                else declared
            }, depth + 1, seen + raw)
        }
    }

    // The web annotation is named, so this test jar needs no spring-web dependency.
    private fun isPathVariable(parameter: MethodParameter): Boolean =
        parameter.parameterAnnotations.any { it.annotationClass.java.name == "org.springframework.web.bind.annotation.PathVariable" }

    private fun isId(type: ResolvableType): Boolean {
        val declared = type.type
        return declared is TypeVariable<*> && declared.name == "T" && type.resolve() in setOf(null, Any::class.java)
    }

    private fun getters(type: Class<*>): List<Method> = type.methods
        .filter { it.parameterCount == 0 && it.declaringClass != Any::class.java && !Modifier.isStatic(it.modifiers) }
        .filter { (it.name.startsWith("get") && it.name.length > 3) || (it.name.startsWith("is") && it.name.length > 2) }
        // A key returns before this point, so its id and root never reach it.
        .filter { it.name !in setOf("getClass") }
        .sortedBy { it.name }

    private fun propertyName(getter: Method): String {
        val bare = if (getter.name.startsWith("get")) getter.name.substring(3) else getter.name.substring(2)
        return bare.replaceFirstChar { it.lowercase() }
    }

    private fun typeName(type: ResolvableType): String = when (val declared = type.type) {
        is TypeVariable<*> -> type.resolve()?.simpleName ?: declared.name
        else -> type.resolve()?.simpleName ?: declared.typeName
    }
}
