package com.kozak.xtrustvpn.ui.profile

import com.kozak.xtrustvpn.fmt.trojan.TrojanBean

class TrojanSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createEntity() = TrojanBean()

}

