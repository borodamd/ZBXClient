package com.itsoul.zbxclient

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DataStore(private val context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("zabbix_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveServers(servers: List<ZabbixServer>) {
        val json = gson.toJson(servers)
        sharedPreferences.edit().putString("servers", json).apply()
    }

    fun getServers(): List<ZabbixServer> {
        val json = sharedPreferences.getString("servers", null)
        return if (json != null) {
            val type = object : TypeToken<List<ZabbixServer>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun saveSettings(settings: AppSettings) {
        val json = gson.toJson(settings)
        sharedPreferences.edit().putString("app_settings", json).apply()
    }

    fun getSettings(): AppSettings {
        val json = sharedPreferences.getString("app_settings", null)
        return if (json != null) {
            gson.fromJson(json, AppSettings::class.java)
        } else {
            AppSettings() // default settings
        }
    }
}