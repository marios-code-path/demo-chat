package com.demo.chat.service.init

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.config.deploy.init.UserDefinition
import com.demo.chat.config.deploy.init.UserInitializationProperties
import com.demo.chat.domain.knownkey.ChatIdentity
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.SecureRandom
import java.util.Base64

/**
 * The singular ownership sentinel, as `userinit.yml` spells it.
 *
 * `AuthSummarizer.WILDCARD` holds the same text in `chat-security`, and this
 * module does not depend on that one. A `*` row answers any permission that a
 * caller asks of its target. See `CHAT-znprrzhn`.
 */
private const val ADMIN_WILDCARD = "*"

/**
 * The random bytes behind a generated password. Twenty four bytes give thirty
 * two Base64 characters. See `CHAT-werokcbb`.
 */
private const val PASSWORD_BYTES = 24

class InitialUsersService<T>(
    private val userService: ChatUserService<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val secretsStore: SecretsStore<T>,
    private val initializationProperties: UserInitializationProperties,
    private val passwordDecoder: PasswordEncoder,
    private val typeUtil: TypeUtil<T>,
) {

    private val secureRandom: SecureRandom = SecureRandom()

    fun initializeUsers(rootKeys: RootKeys<T>): Map<String, Key<T>> {
        // The grant placeholder only. A grant key is minted when the grant is
        // stored. A user never takes this key. See CHAT-avduuqwp, C16.
        val grantPlaceholder = Key.empty(typeUtil.assignFrom(Any()), rootKeys.of(ChatDomain.AUTH_METADATA).id)
        val identityKeys = mutableMapOf<String, Key<T>>()

        // add users
        initializationProperties.initialUsers.keys.forEach { identity ->
            val thisUser = initializationProperties.initialUsers[identity]!!
            val thisUserKey = Mono.from(
                userService.addUser(
                    UserCreateRequest(
                        thisUser.name,
                        thisUser.handle,
                        thisUser.imageUri
                    )
                )
            )
                .onErrorResume {
                    userService
                        .findByUsername(ByStringRequest(thisUser.handle))
                        .map { u -> u.key }
                        .switchIfEmpty(Mono.error(ChatException("Cannot initialize user ${thisUser.handle}")))
                        .single()
                }
                // No fallback key exists. A user that is neither created nor found fails the initialization.
                .switchIfEmpty(Mono.error(ChatException("Cannot initialize user ${thisUser.handle}")))
                .block()!!

            identityKeys[identity] = thisUserKey

            val secret = credentialSecret(thisUser)
            val thisCredential = KeyCredential(thisUserKey, "${passwordDecoder.encode(secret)}")

            secretsStore
                .addCredential(thisCredential)
                .block()
        }

        loadIdentities(rootKeys, identityKeys)

        val initialRoles: MutableSet<AuthMetadata<T>> = mutableSetOf()

        // get role definitions
        initializationProperties.initialRoles.roles.forEach { permission ->
            // RoleDefinition validates configuration names before binding.
            // Keep this guard because a deployment can load an incomplete root-key set.
            val user = rootKeys.byName(permission.user)
            val target = rootKeys.byName(permission.target)
            if (user != null && target != null) {
                initialRoles.add(
                    StringRoleAuthorizationMetadata(
                        grantPlaceholder,
                        user,
                        target,
                        permission.role,
                    )
                )
            } else {
                println("Missing root key for ${permission.user} or ${permission.target}")
            }
        }

        // The Admin identity holds the wildcard on every domain root. The owner
        // decided this rule on 2026-10-01. This loop reads the loaded domain
        // set, so a domain that a later release adds takes its row here and
        // needs no second edit.
        //
        // The value is the literal that `userinit.yml` names. The constant
        // `AuthSummarizer.WILDCARD` holds the same text in `chat-security`,
        // which this module does not depend on.
        //
        // **Admin is not in the actor set of any other caller.** A query
        // carries the `Anon` key, the `User` root and the caller. So these rows
        // reach the Admin identity alone, and no other caller gains a right.
        rootKeys.domains().keys.forEach { domain ->
            initialRoles.add(
                StringRoleAuthorizationMetadata(
                    grantPlaceholder,
                    rootKeys.admin(),
                    rootKeys.of(domain),
                    ADMIN_WILDCARD,
                )
            )
        }

        // set permissions, one grant at a time, so a grant that the
        // configuration names twice is seeded once.
        Flux.fromIterable(initialRoles)
            .concatMap { authMeta -> seedGrant(authMeta) }
            .blockLast()

        return identityKeys
    }

    /**
     * This method seeds one initial grant. **The configuration gives the first
     * value of a grant, and the store holds every later change.** See
     * `CHAT-ghwtzgjp`.
     *
     * - No stored row names the same principal, target and permission: the
     *   method writes the grant.
     * - One stored row names them: the method writes nothing. An expired or a
     *   muted row counts, so a restart does not undo a revoke.
     * - More than one stored row names them: the method keeps the row with the
     *   highest key id and removes the others. Before this rule, each start
     *   wrote one more copy.
     *
     * **The kept row is the row that decides access today.** The copies tie on
     * the wildcard level and on the principal rank. So `AuthSummarizer` breaks
     * the tie with the key comparator of `AuthBeansConfiguration`, which is
     * [TypeUtil.compare] on the key id, and it selects the highest. The removal
     * changes no access decision.
     *
     * **The read is not atomic with the write.** Two processes that start at
     * the same time against one store can both write a grant.
     */
    private fun seedGrant(grant: AuthMetadata<T>): Mono<Void> =
        authorizationService.getStoredGrants(grant.principal, grant.target)
            .filter { row -> row.permission == grant.permission }
            .collectList()
            .flatMap { rows ->
                if (rows.isEmpty()) {
                    println("Adding Permission ${grant.principal.id} -> ${grant.target.id} : ${grant.permission}, ${grant.mute}, ${grant.expires}")
                    authorizationService.authorize(grant, true)
                } else {
                    val kept = rows.maxWith { a, b -> typeUtil.compare(a.key.id, b.key.id) }
                    Flux.fromIterable(rows.filter { row -> row.key != kept.key })
                        .concatMap { copy ->
                            println("Removing Permission copy ${copy.key.id}: ${grant.principal.id} -> ${grant.target.id} : ${grant.permission}")
                            authorizationService.authorize(copy, false)
                        }
                        .then()
                }
            }

    /**
     * This method answers the credential secret for one initial user.
     *
     * A blank password generates one, and the method writes it to the console.
     * The account handle leads the line, so an operator reads the live
     * credential of a generated account. **The value is written at every
     * start**, and `addCredential` overwrites the stored credential. So only
     * the newest output holds the live password. See `CHAT-werokcbb`.
     */
    private fun credentialSecret(user: UserDefinition): String {
        if (user.password.isNotBlank()) return user.password
        val generated = generatePassword()
        println("Generated password for account '${user.handle}': $generated")
        return generated
    }

    /**
     * This method answers a random password. The value carries no padding, so
     * the console line holds no `=` character.
     */
    private fun generatePassword(): String {
        val bytes = ByteArray(PASSWORD_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * This method loads the two identities from the initial users.
     *
     * The `Admin` and `Anon` names must each be present. Any other initial user
     * is a plain user. It takes no identity and no grant, so it holds no
     * administrator reach. The MCP adapter account takes that route. See
     * `CHAT-werokcbb`.
     */
    private fun loadIdentities(rootKeys: RootKeys<T>, identityKeys: Map<String, Key<T>>) {
        val admin = identityKeys[ChatIdentity.ADMIN.wireName]
            ?: throw ChatException("The initial users do not name the ${ChatIdentity.ADMIN.wireName} identity.")
        val anon = identityKeys[ChatIdentity.ANON.wireName]
            ?: throw ChatException("The initial users do not name the ${ChatIdentity.ANON.wireName} identity.")
        rootKeys.loadIdentities(admin, anon)
    }
}
