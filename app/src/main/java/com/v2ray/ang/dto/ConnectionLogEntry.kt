package com.v2ray.ang.dto

data class ConnectionLogEntry(
    val timestampMillis: Long,
    val serverName: String,
    val serverAddress: String
)
