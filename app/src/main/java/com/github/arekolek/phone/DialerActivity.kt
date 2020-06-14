package com.github.arekolek.phone

import android.Manifest
import android.Manifest.permission.CALL_PHONE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.TelecomManager
import android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER
import android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME
import android.telephony.TelephonyManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.net.toUri
import kotlinx.android.synthetic.main.activity_dialer.*


class DialerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)
        phoneNumberInput.setText(intent?.data?.schemeSpecificPart)
    }

    fun getPhoneInfo():String{
        //Get the instance of TelephonyManager
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        //Calling the methods of TelephonyManager the returns the information
        val IMEINumber = tm.deviceId
        val subscriberID = tm.deviceId
        val SIMSerialNumber = tm.simSerialNumber
        val networkCountryISO = tm.networkCountryIso
        val SIMCountryISO = tm.simCountryIso
        val softwareVersion = tm.deviceSoftwareVersion
        val voiceMailNumber = tm.voiceMailNumber

        //Get the phone type
        var strphoneType = ""
        val phoneType = tm.phoneType

        when (phoneType) {
            TelephonyManager.PHONE_TYPE_CDMA -> strphoneType = "CDMA"
            TelephonyManager.PHONE_TYPE_GSM -> strphoneType = "GSM"
            TelephonyManager.PHONE_TYPE_NONE -> strphoneType = "NONE"
        }

        //getting information if phone is in roaming
        val isRoaming = tm.isNetworkRoaming

        var info = "Phone Details:\n"
        info += "\n IMEI Number:$IMEINumber"
        info += "\n SubscriberID:$subscriberID"
        info += "\n Sim Serial Number:$SIMSerialNumber"
        info += "\n Network Country ISO:$networkCountryISO"
        info += "\n SIM Country ISO:$SIMCountryISO"
        info += "\n Software Version:$softwareVersion"
        info += "\n Voice Mail Number:$voiceMailNumber"
        info += "\n Phone Network Type:$strphoneType"
        info += "\n In Roaming? :$isRoaming"

        return info
    }

    override fun onStart() {
        super.onStart()
        offerReplacingDefaultDialer()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), REQUEST_PERMISSION)
            return
        }

        //R.id.tvInfo
        tvInfo.text=getPhoneInfo()

        phoneNumberInput.setOnEditorActionListener { _, _, _ ->
            makeCall()
            true
        }
    }

    private fun makeCall() {
        if (checkSelfPermission(this, CALL_PHONE) == PERMISSION_GRANTED) {
            val uri = "tel:${phoneNumberInput.text}".toUri()
            startActivity(Intent(Intent.ACTION_CALL, uri))
        } else {
            requestPermissions(this, arrayOf(CALL_PHONE), REQUEST_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSION && PERMISSION_GRANTED in grantResults) {
            makeCall()
        }
    }

    private fun offerReplacingDefaultDialer() {
        if (getSystemService(TelecomManager::class.java).defaultDialerPackage != packageName) {
            Intent(ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                .let(::startActivity)
        }
    }

    companion object {
        const val REQUEST_PERMISSION = 0
    }
}
