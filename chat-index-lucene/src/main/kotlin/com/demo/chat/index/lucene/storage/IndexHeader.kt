package com.demo.chat.index.lucene.storage

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.util.Version

/** The commit user data of one index. A start that reads a different header builds. */
data class IndexHeader(
    val format: String,
    val lucene: String,
    val analyzer: String,
    val keyType: String,
    val nodeId: String,
    val index: String,
) {
    fun userData(): Map<String, String> = mapOf(
        FORMAT to format, LUCENE to lucene, ANALYZER to analyzer,
        KEY_TYPE to keyType, NODE_ID to nodeId, INDEX to index,
    )

    companion object {
        const val FORMAT = "chat.format"
        const val LUCENE = "chat.lucene"
        const val ANALYZER = "chat.analyzer"
        const val KEY_TYPE = "chat.keyType"
        const val NODE_ID = "chat.nodeId"
        const val INDEX = "chat.index"

        fun current(name: String, keyType: String, nodeId: String, analyzer: Analyzer) = IndexHeader(
            EntryEncoding.FORMAT.toString(), Version.LATEST.toString(), analyzer.javaClass.name, keyType, nodeId, name,
        )

        /** Returns null when any key is missing. A missing key is a different header. */
        fun read(data: Map<String, String>): IndexHeader? {
            return IndexHeader(
                data[FORMAT] ?: return null,
                data[LUCENE] ?: return null,
                data[ANALYZER] ?: return null,
                data[KEY_TYPE] ?: return null,
                data[NODE_ID] ?: return null,
                data[INDEX] ?: return null,
            )
        }
    }
}
