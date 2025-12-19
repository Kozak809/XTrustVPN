package com.kozak.xtrustvpn.bg.proto

import com.kozak.xtrustvpn.BuildConfig
import com.kozak.xtrustvpn.bg.GuardedProcessPool
import com.kozak.xtrustvpn.database.ProxyEntity
import com.kozak.xtrustvpn.fmt.buildConfig
import com.kozak.xtrustvpn.ktx.Logs
import com.kozak.xtrustvpn.ktx.runOnDefaultDispatcher
import com.kozak.xtrustvpn.ktx.tryResume
import com.kozak.xtrustvpn.ktx.tryResumeWithException
import kotlinx.coroutines.delay
import libcore.Libcore
import moe.matsuri.nb4a.net.LocalResolverImpl
import kotlin.coroutines.suspendCoroutine

class TestInstance(profile: ProxyEntity, val link: String, private val timeout: Int) :
    BoxInstance(profile) {

    suspend fun doTest(): Int {
        return suspendCoroutine { c ->
            processes = GuardedProcessPool {
                Logs.w(it)
                c.tryResumeWithException(it)
            }
            runOnDefaultDispatcher {
                use {
                    try {
                        init()
                        launch()
                        if (processes.processCount > 0) {
                            // wait for plugin start
                            delay(500)
                        }
                        c.tryResume(Libcore.urlTest(box, link, timeout))
                    } catch (e: Exception) {
                        c.tryResumeWithException(e)
                    }
                }
            }
        }
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        // don't call destroyAllJsi here
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = Libcore.newSingBoxInstance(config.config, LocalResolverImpl)
    }

}

