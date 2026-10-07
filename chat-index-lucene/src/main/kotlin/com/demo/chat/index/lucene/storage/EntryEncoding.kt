package com.demo.chat.index.lucene.storage

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * The canonical bytes of one index entry. A start compares these bytes with
 * the `_enc` field of the stored document. See CHAT-ybtirmgj.
 */
object EntryEncoding {
    /**
     * The format version.
     *
     * Increase this value when the document layout changes, or when the
     * analyzer configuration changes. The entry bytes do not show an analyzer
     * change, so only this value and the header can show it.
     */
    const val FORMAT = 1

    /** All integers are int32, big-endian. Text is UTF-8 with a byte length prefix. */
    fun encode(keyText: String, fields: List<Pair<String, String>>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT)
            writeText(out, keyText)
            out.writeInt(fields.size)
            fields.forEach { (name, value) ->
                writeText(out, name)
                writeText(out, value)
            }
        }
        return bytes.toByteArray()
    }

    private fun writeText(out: DataOutputStream, text: String) {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        out.writeInt(utf8.size)
        out.write(utf8)
    }
}
