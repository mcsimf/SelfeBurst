package com.selfieburst.core.api

import com.selfieburst.core.api.model.AuthData
import retrofit2.Call
import retrofit2.http.PUT
import retrofit2.http.Query


/**
 * @author Maksym Fedyay on 4/18/20 (mcsimf@gmail.com).
 */
interface Auth {

    @PUT("/v2/5e96171c2f0000ee7302584d")
    fun login(@Query("login") login: String, @Query("pass") pass: String): Call<AuthData>

}