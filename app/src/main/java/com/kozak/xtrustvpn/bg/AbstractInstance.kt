package com.kozak.xtrustvpn.bg

import java.io.Closeable

interface AbstractInstance : Closeable {

    fun launch()

}
