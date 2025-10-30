package com.itsoul.zbxclient

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ZabbixApiService {
    @Headers("Content-Type: application/json-rpc")
    @POST("api_jsonrpc.php")
    suspend fun makeRequest(
        @Body request: ZabbixRequest
    ): Response<ZabbixResponse> // Убрали generic тип

    @Headers("Content-Type: application/json-rpc")
    @POST("api_jsonrpc.php")
    suspend fun makeRequestForTriggerDetails(@Body request: ZabbixRequest): Response<ZabbixResponse> // Убрали generic тип
}