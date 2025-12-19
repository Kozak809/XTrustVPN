package com.kozak.xtrustvpn.bg.proto

import com.kozak.xtrustvpn.database.DataStore
import com.kozak.xtrustvpn.database.ProxyEntity

class UrlTest {

    val link = DataStore.connectionTestURL
    private val timeout = 5000

    suspend fun doTest(profile: ProxyEntity): Int {
        return TestInstance(profile, link, timeout).doTest()
    }

}
