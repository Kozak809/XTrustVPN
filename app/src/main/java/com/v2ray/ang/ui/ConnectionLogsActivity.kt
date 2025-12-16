package com.v2ray.ang.ui

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityConnectionLogsBinding
import com.v2ray.ang.handler.MmkvManager

class ConnectionLogsActivity : BaseActivity() {
    private val binding by lazy { ActivityConnectionLogsBinding.inflate(layoutInflater) }
    private val adapter by lazy { ConnectionLogsAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        supportActionBar?.hide()

        title = getString(R.string.title_connection_logs)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        adapter.submitList(MmkvManager.decodeConnectionLogs())
    }
}
