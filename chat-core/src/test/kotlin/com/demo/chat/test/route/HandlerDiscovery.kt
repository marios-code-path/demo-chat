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
import java.time.temporal.Temporal
import java.util.UUID

/**
 * One handler that a guard found, with every value of its input that needs a
 * classification. See `CHAT-avduuqwp`, T4 step 7 and review correction 2.
 *
 * [signature] keeps the declared generic structure of each parameter, so a
 * changed parameter type no longer matches its catalog entry.
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
 * - The walk follows getters into nested values, collections, arrays, and
 *   map keys and values. It has no depth limit. A cycle stops the walk at the
 *   repeated type, and the path is reported.
 *
 * **The walk reports every value that it cannot prove free of identity.** A
 * guard then requires a classification for each one, so no input passes as
 * free of identity only because the walk did not look.
 *
 * It reports these values:
 *
 * 1. A `Key`, a `MessageKey`, and a `VerifiedKey`.
 * 2. A type variable named `T` that no class resolves. That is the key type
 *    of this repository.
 * 3. A parameter bound from a path variable, whatever its type.
 * 4. A concrete `Long` or `UUID`, the two key types. The type cannot say what
 *    the value is for, so the catalog classifies it.
 * 5. An opaque value that the walk cannot examine: `Any`, an unresolved type
 *    variable other than `T`, a class outside this project, or a cycle.
 */
object HandlerDiscovery {

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
                    signature = parameters.joinToString(",") { render(ResolvableType.forMethodParameter(it)) },
                    identityFields = parameters.flatMap { parameter ->
                        val name = parameter.parameterName ?: "arg${parameter.parameterIndex}"
                        if (isPathVariable(parameter)) listOf(name)
                        else walk(name, ResolvableType.forMethodParameter(parameter), emptySet())
                    }.toSortedSet(),
                )
            }

    /** The paths of every value inside a value of [type] that needs a classification. */
    fun walk(path: String, type: ResolvableType, seen: Set<Class<*>>): List<String> {
        val declared = type.type
        if (declared is TypeVariable<*> && type.resolve() in setOf(null, Any::class.java)) {
            // Rule 2 for T, rule 5 for any other unresolved variable.
            return listOf(path)
        }
        val raw = type.resolve() ?: return listOf(path)

        if (VerifiedKey::class.java.isAssignableFrom(raw)) return listOf(path)
        if (MessageKey::class.java.isAssignableFrom(raw)) return listOf(path, "$path.from", "$path.dest")
        if (Key::class.java.isAssignableFrom(raw)) return listOf(path)

        if (raw.isArray) return walk("$path[]", type.componentType, seen)
        if (Collection::class.java.isAssignableFrom(raw)) return walk("$path[]", type.asCollection().getGeneric(0), seen)
        if (Map::class.java.isAssignableFrom(raw)) {
            val map = type.asMap()
            return walk("$path{key}", map.getGeneric(0), seen) + walk("$path{}", map.getGeneric(1), seen)
        }

        if (isIdType(raw)) return listOf(path)
        if (isLeaf(raw)) return emptyList()
        if (raw == Any::class.java || !raw.name.startsWith("com.demo.chat")) return listOf(path)
        if (raw in seen) return listOf(path)

        return getters(raw).flatMap { getter ->
            walk("$path.${propertyName(getter)}", returnType(getter, type), seen + raw)
        }
    }

    /**
     * The getter type, with a type variable resolved through the value type.
     * So `User<T>.key` stays `Key<T>`, and `Message<T, String>.data` becomes
     * `String`.
     */
    private fun returnType(getter: Method, owner: ResolvableType): ResolvableType {
        val declared = ResolvableType.forMethodReturnType(getter)
        val variable = declared.type as? TypeVariable<*>
            ?: return owner.resolve()?.let { ResolvableType.forMethodReturnType(getter, it) } ?: declared
        val index = getter.declaringClass.typeParameters.indexOfFirst { it.name == variable.name }
        if (index < 0) return declared
        val resolved = owner.`as`(getter.declaringClass).getGeneric(index)
        return if (resolved == ResolvableType.NONE) declared else resolved
    }

    // The web annotation is named, so this test jar needs no spring-web dependency.
    private fun isPathVariable(parameter: MethodParameter): Boolean =
        parameter.parameterAnnotations.any { it.annotationClass.java.name == "org.springframework.web.bind.annotation.PathVariable" }

    private fun isIdType(raw: Class<*>): Boolean =
        raw == java.lang.Long::class.java || raw == java.lang.Long.TYPE || raw == UUID::class.java

    private fun isLeaf(raw: Class<*>): Boolean =
        raw.isPrimitive || raw.isEnum || CharSequence::class.java.isAssignableFrom(raw) ||
            Number::class.java.isAssignableFrom(raw) || raw == java.lang.Boolean::class.java ||
            raw == java.lang.Character::class.java || Temporal::class.java.isAssignableFrom(raw) ||
            raw == java.time.Duration::class.java || raw == Class::class.java

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

    /** A type with its generic structure, such as `List<Key<T>>`. */
    fun render(type: ResolvableType): String {
        val declared = type.type
        if (declared is TypeVariable<*>) {
            return type.resolve()?.takeIf { it != Any::class.java }?.let { render(ResolvableType.forClass(it)) } ?: declared.name
        }
        val raw = type.resolve() ?: return declared.typeName
        if (raw.isArray) return "${render(type.componentType)}[]"
        val generics = type.generics
        return if (generics.isEmpty()) raw.simpleName else "${raw.simpleName}<${generics.joinToString(",") { render(it) }}>"
    }
}
