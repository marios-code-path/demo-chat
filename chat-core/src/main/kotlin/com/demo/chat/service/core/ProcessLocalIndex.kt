package com.demo.chat.service.core

/**
 * Marks an index whose writes stay in local files of this process. The store
 * write gate does not wrap it, because its writes never reach a shared store.
 * See `CHAT-lswjobhz` and `docs/SERVICE-LEASE.md`.
 */
interface ProcessLocalIndex
