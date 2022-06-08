package com.demo.chat.config

interface CoreClientBeans<T, V, Q> :
    KeyServiceBeans<T>,
    PersistenceServiceBeans<T, V>,
    IndexServiceBeans<T, V, Q>,
    PubSubServiceBeans<T, V>