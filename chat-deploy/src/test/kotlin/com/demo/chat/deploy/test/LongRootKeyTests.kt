package com.demo.chat.deploy.test

import com.demo.chat.domain.LongUtil
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.TestLongKeyService

class LongRootKeyTests : RootKeyTests<Long>(LongUtil(), TestLongKeyGenerator(), TestLongKeyService())