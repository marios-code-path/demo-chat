package com.demo.chat.config

interface CoreServices<T : Any, V, Q> :
    KeyServiceBeans<T>,
    PersistenceServiceBeans<T, V>,
    IndexServiceBeans<T, V, Q>,
    PubSubServiceBeans<T, V>,
    SecretsStoreBeans<T>