package com.demo.chat.domain

/** A key service refuses to remove a root key. A root must outlive its domain. See `CHAT-avduuqwp`. */
class RootKeyDeletionException(id: Any?) : ChatException("The key $id is a root key. A key service does not remove a root key.")
