package com.demo.chat.domain

/**
 * The operator's name for the model that writes a corpus.
 *
 * The value is never derived. A base URL and a model name do not identify the
 * output of a remote service. A compatible service can change its model behind
 * both values and return different vectors for the same text. Only an operator
 * knows that a change happened.
 *
 * The value is immutable for a corpus. A new identity means a new corpus, and
 * it does not mean a migration.
 *
 * The type exists to stop a raw string from reaching the wrong parameter.
 * Three call sites already take a keyType string, and a second string beside
 * it would be easy to swap.
 *
 * The type is a data class, and it is not a JvmInline value class. Kotlin
 * unboxes a value class at a return type, so a bean method would supply a
 * String bean. Spring must see this type.
 */
data class EmbeddingIdentity(val value: String) {

    companion object {
        /** A redis key prefix and a directory name both carry this value. */
        val PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}")

        const val MOCK_EMBEDDING = "mock"

        val MOCK = EmbeddingIdentity(MOCK_EMBEDDING)

        const val PROPERTY = "app.service.core.embedding.identity"

        /**
         * Applies the three rules and returns the resolved value.
         *
         * A mock embedding resolves the fixed identity and refuses an operator
         * value. A production embedding requires an operator value. Any value
         * must match [PATTERN].
         */
        fun of(embedding: String, identity: String?): EmbeddingIdentity {
            val given = identity?.takeIf { it.isNotBlank() }

            if (embedding == MOCK_EMBEDDING) {
                if (given != null) {
                    throw IllegalStateException(
                        "$PROPERTY=$given is set, and app.service.core.embedding=mock. " +
                            "The mock embedding resolves the fixed identity '$MOCK_EMBEDDING'. " +
                            "Remove $PROPERTY, or name a production embedding."
                    )
                }
                return MOCK
            }

            if (given == null) {
                throw IllegalStateException(
                    "$PROPERTY is not set, and app.service.core.embedding=$embedding. " +
                        "A production embedding needs an identity that this operator names. " +
                        "The value must match ${PATTERN.pattern}."
                )
            }

            if (!PATTERN.matches(given)) {
                throw IllegalStateException(
                    "$PROPERTY=$given does not match ${PATTERN.pattern}. " +
                        "A redis key prefix and a directory name both carry this value, " +
                        "so the character set is narrow."
                )
            }

            return EmbeddingIdentity(given)
        }
    }
}
