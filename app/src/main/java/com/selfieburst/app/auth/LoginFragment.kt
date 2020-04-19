package com.selfieburst.app.auth

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.selfieburst.R
import com.selfieburst.app.MainViewModel
import com.selfieburst.core.UserManager
import kotlinx.android.synthetic.main.frag_login.*


/**
 * A simple [Fragment] subclass.
 * Use the [LoginFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LoginFragment : Fragment() {


    private val viewModel: MainViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.e("VM", "login " + System.identityHashCode(viewModel))
        return inflater.inflate(R.layout.frag_login, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        et_login.addTextChangedListener(textWatcher)
        et_password.addTextChangedListener(textWatcher)

        btn_continue.setOnClickListener {
            btn_continue.isEnabled = false
            val name = et_login.text.toString()
            val pass = et_password.text.toString()
            viewModel.login(name, pass)
        }

        viewModel.authState().observe(viewLifecycleOwner, Observer {
            when (it) {
                UserManager.AuthState.AUTHORIZED -> {
                    hideKeyboard()
                    findNavController().popBackStack()
                }
                UserManager.AuthState.NOT_AUTHORIZED -> checkForm()
                UserManager.AuthState.AUTH_ERROR -> {
                    checkForm()
                    showMessage()
                }
            }
        })
    }


    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = Unit
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
            checkForm()
    }


    private fun checkForm() {
        btn_continue.isEnabled = et_login.text?.isBlank()!!.not() &&
                et_password.text?.isBlank()!!.not()
    }


    companion object {
        @JvmStatic
        fun newInstance() = LoginFragment()
    }


    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(et_login.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }


    private fun showMessage() {
        Snackbar.make(
            btn_continue, getString(R.string.msg_login_error),
            Snackbar.LENGTH_SHORT
        ).show()
    }

}
