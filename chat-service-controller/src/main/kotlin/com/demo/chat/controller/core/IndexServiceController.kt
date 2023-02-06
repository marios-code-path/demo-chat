package com.demo.chat.controller.core

import com.demo.chat.controller.core.mapping.IndexServiceMapping
import com.demo.chat.service.core.IndexService

open class IndexServiceController<T, E, Q>(private val that: IndexService<T, E, Q>) : IndexServiceMapping<T, E, Q>, IndexService<T, E, Q> by that