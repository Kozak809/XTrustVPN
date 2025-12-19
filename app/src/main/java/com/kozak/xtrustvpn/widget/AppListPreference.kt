package com.kozak.xtrustvpn.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.kozak.xtrustvpn.R
import com.kozak.xtrustvpn.database.DataStore
import com.kozak.xtrustvpn.ktx.app
import com.kozak.xtrustvpn.utils.PackageCache

class AppListPreference : Preference {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context, attrs, defStyle
    )

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun getSummary(): CharSequence {
        val packages = DataStore.routePackages.split("\n").filter { it.isNotBlank() }.map {
            PackageCache.installedPackages[it]?.applicationInfo?.loadLabel(app.packageManager)
                ?: PackageCache.installedPluginPackages[it]?.applicationInfo?.loadLabel(app.packageManager)
                ?: it
        }
        if (packages.isEmpty()) {
            return context.getString(androidx.preference.R.string.not_set)
        }
        val count = packages.size
        if (count <= 5) return packages.joinToString("\n")
        return context.getString(R.string.apps_message, count)
    }

    fun postUpdate() {
        notifyChanged()
    }

}
