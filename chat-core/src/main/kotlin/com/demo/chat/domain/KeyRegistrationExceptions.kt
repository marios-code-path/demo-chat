package com.demo.chat.domain

/** Both refusals happen before any registry write, so both are definitive. */
class KeyRootConflictException(val id: Any?, val storedRoot: Any?, val requestedRoot: Any?) :
    ChatException("The key $id is registered under root $storedRoot. A registration under root $requestedRoot is refused."),
    NoEffectRefusal

class RootKeyRegistrationException(id: Any?) :
    ChatException("The key $id is a root key. A key service does not register a root key."),
    NoEffectRefusal
