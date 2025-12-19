package com.kozak.xtrustvpn.ui.profile

import com.kozak.xtrustvpn.fmt.http.HttpBean

class HttpSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createEntity() = HttpBean()

}

