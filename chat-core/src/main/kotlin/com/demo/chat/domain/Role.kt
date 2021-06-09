package com.demo.chat.domain

interface Role<T>: KeyBearer<T> {
    val name: String
}

interface RoleBinding<T> : KeyBearer<T> {
    val principal: Key<T>
    val role: Key<T>
}