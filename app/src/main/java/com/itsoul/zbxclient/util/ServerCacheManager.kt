package com.itsoul.zbxclient.util

import android.content.Context
import android.content.SharedPreferences
import com.itsoul.zbxclient.PreferencesManager
import com.itsoul.zbxclient.ZabbixServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ServerCacheManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("servers_cache", Context.MODE_PRIVATE)

    fun cacheServers(servers: List<ZabbixServer>) {
        val editor = prefs.edit()
        servers.forEach { server ->
            editor.putString("server_${server.id}", server.name)
        }
        editor.apply()
    }

    fun getServerName(serverId: Long): String? {
        return prefs.getString("server_$serverId", null)
    }

    companion object {
        fun updateServerCache(context: Context) {
            runBlocking {
                try {
                    val preferencesManager = PreferencesManager(context)
                    val servers = preferencesManager.getServers().first()
                    ServerCacheManager(context).cacheServers(servers)
                } catch (e: Exception) {
                    // Игнорируем ошибки кэширования
                }
            }
        }

        fun getServerName(context: Context, serverId: Long): String? {
            return ServerCacheManager(context).getServerName(serverId)
        }
    }
}