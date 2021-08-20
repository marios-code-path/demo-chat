package com.demo.chat.config

interface CoreClientBeans<T, V, Q> :
    IndexServiceBeans<T, V, Q>, KeyServiceBeans<T>, PersistenceServiceBeans<T, V>, PubSubServiceBeans<T, V>