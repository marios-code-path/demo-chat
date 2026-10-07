package com.demo.chat.index.lucene.impl

/** The lifecycle of one Lucene index. No operation opens an index implicitly. */
enum class IndexState { NEW, OPEN, FAILED, CLOSED }
