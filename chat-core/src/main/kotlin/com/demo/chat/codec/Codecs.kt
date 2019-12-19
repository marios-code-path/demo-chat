package com.demo.chat.codec

interface Codec<F, E> {
    fun decode(record: F): E
}
