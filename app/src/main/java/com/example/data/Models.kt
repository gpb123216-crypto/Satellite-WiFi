package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val role: String, // "user" or "admin"
    val status: String, // "active", "blocked", "expired"
    val plan: String // "50 Mbps Basic", "100 Mbps standard", "250 Mbps Premium", "1 Gbps Ultra"
)

@Entity(tableName = "billing")
data class Billing(
    @PrimaryKey val id: String,
    val userId: String,
    val amount: Double,
    val dueDate: String,
    val status: String // "paid" or "unpaid"
)

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey val id: String,
    val userId: String,
    val deviceName: String,
    val ipAddress: String,
    val macAddress: String,
    val isConnected: Boolean = true
)

@Entity(tableName = "wifi_settings")
data class WiFiSettings(
    @PrimaryKey val userId: String,
    val ssid: String,
    val password: String
)
