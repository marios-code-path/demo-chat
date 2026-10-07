package com.demo.chat.test.memory

import com.demo.chat.index.lucene.impl.LuceneIndex

/** Opens an index in memory mode, as the start load does in production. */
fun <I : LuceneIndex<*, *>> I.openedInMemory(): I = apply { openInMemory() }
