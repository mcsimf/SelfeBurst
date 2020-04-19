package com.selfieburst.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.selfieburst.core.api.Api
import com.selfieburst.core.api.model.AuthData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

const val TAG: String = "UserManager"

/**
 * Stores user auth data.
 * @author Maksym Fedyay on 4/18/20 (mcsimf@gmail.com).
 */
class UserManager(context: Context, api: Api) : LiveData<UserManager.AuthState>() {

    companion object {
        private const val SP_NAME: String = "sp_user_manager"
        private const val SP_TOKEN: String = "sp_token"
    }


    override fun observe(owner: LifecycleOwner, observer: Observer<in AuthState>) {
        Log.e(TAG, "added observer " + System.identityHashCode(observer))
        super.observe(owner, observer)
    }


    private val api: Api = api

    // FOR SIMPLICITY WE WILL USE SHARED PREFS

    private val sp: SharedPreferences = context
        .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)


    init {
        value = if (sp.getString(SP_TOKEN, null) != null) {
            AuthState.AUTHORIZED
        } else {
            AuthState.NOT_AUTHORIZED
        }
    }


    enum class AuthState {
        AUTHORIZED,
        NOT_AUTHORIZED,
        AUTH_ERROR
    }


    fun logIn(name: String, pass: String) {
        api.auth.login(name, pass).enqueue(object : Callback<AuthData> {
            override fun onFailure(call: Call<AuthData>, t: Throwable) {
                value = AuthState.AUTH_ERROR
            }

            override fun onResponse(call: Call<AuthData>, response: Response<AuthData>) {
                value = if (response.isSuccessful) {
                    sp.edit().putString(SP_TOKEN, response.body()?.authToken).apply()
                    AuthState.AUTHORIZED
                } else {
                    AuthState.AUTH_ERROR
                }
            }
        })
    }


    fun logOut() {
        sp.edit().remove(SP_TOKEN).apply()
        value = AuthState.NOT_AUTHORIZED
    }

}