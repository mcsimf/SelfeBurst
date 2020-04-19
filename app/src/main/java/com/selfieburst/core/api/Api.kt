package com.selfieburst.core.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val API_URL = "http://www.mocky.io"

/**
 * @author Maksym Fedyay on 4/18/20 (mcsimf@gmail.com).
 */
class Api {


    val auth: Auth


    init {
        val okHttpClient = OkHttpClient.Builder().let {
            it.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            it.build()
        }

        val gson = GsonBuilder().setLenient().create()

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(API_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()

        this.auth = retrofit.create(Auth::class.java)
    }

}