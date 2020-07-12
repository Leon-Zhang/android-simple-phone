package com.github.arekolek.phone


import android.Manifest
import android.Manifest.permission.CALL_PHONE
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.telecom.TelecomManager
import android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER
import android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME
import android.telephony.*
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation
import android.text.method.ScrollingMovementMethod
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.net.toUri
import kotlinx.android.synthetic.main.activity_dialer.*
import java.text.SimpleDateFormat
import java.util.*


enum class SignalType {
    CDMA, GSM, LTE
}
class DialerActivity : AppCompatActivity() {

    val TAG = "DialerActivity"
    val ACTION_LOG_STRENGTH = "LogStrength"
    val FAIL_VALUE = "(N/A)"

    private var phoneStateListener: PhoneStateListener? = null
    private var pendingIntent: PendingIntent? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private var lastRssi = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)
        phoneNumberInput.setText(intent?.data?.schemeSpecificPart)
        btnClearListen.setOnClickListener {
            tvListen.text=""
        }

        initRsLog()
    }

    override fun onDestroy()
    {
        super.onDestroy()

        uninitRsLog()
    }

    fun uninitRsLog() {
        if (this.pendingIntent != null) {
            val alarm = getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager
            alarm.cancel(pendingIntent)
            pendingIntent = null
        }
        if (this.broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver)
            broadcastReceiver = null
        }
        if (this.phoneStateListener != null) {
            val manager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            manager.listen(
                phoneStateListener,
                PhoneStateListener.LISTEN_NONE
            )
            phoneStateListener = null
        }
    }

    fun getCurrentTimeStamp(): String? {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            dateFormat.format(Date())
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            null
        }
    }

    fun initRsLog(){
        val manager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(s: SignalStrength) {
                log(s)
            }

            override fun onCellLocationChanged(location: CellLocation?) {
                super.onCellLocationChanged(location)

                if(location!=null) {
                    Log.d(TAG, location!!.toString())
                    logToTV(TAG, "onCellLocationChanged["+getCurrentTimeStamp()+"], "+location!!.toString())
                }
            }

            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                super.onCellInfoChanged(cellInfo)

                if(cellInfo!=null) {
                    Log.d(TAG, "onCellInfoChanged, cell size = "+cellInfo!!.size.toString())
                    logToTV(TAG, "onCellInfoChanged["+getCurrentTimeStamp()+"], "+cellInfo)
                }
            }
        }
        manager.listen(phoneStateListener,
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_CELL_LOCATION
                    or PhoneStateListener.LISTEN_CELL_INFO)

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_LOG_STRENGTH == intent.action) {
                    logWifiStrength()
                }
            }
        }
        registerReceiver(broadcastReceiver,IntentFilter(ACTION_LOG_STRENGTH))

        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntent = PendingIntent.getBroadcast(this, 0,
            Intent(ACTION_LOG_STRENGTH), 0
        )
        alarm.setRepeating(AlarmManager.RTC, 1000, 3000, pendingIntent)
    }

    private fun log(signalStrength: SignalStrength) {
        Log.d(TAG, toString(signalStrength))
    }

    fun toString(signal: SignalStrength): String? {
        val s = StringBuilder()
        s.append(Date().toLocaleString()).append(" - ")
        s.append("Strength(").append(classify(signal).name).append(") {\n")

        // output LTE information.
        if (isLte(signal)) {
            s.append("  LTE strength=")
                .append(getFieldValue(signal, "mLteSignalStrength"))
                .append('\n')
            s.append("  LTE RSRP=").append(getFieldValue(signal, "mLteRsrp"))
                .append('\n')
            s.append("  LTE RSRQ=").append(getFieldValue(signal, "mLteRsrq"))
                .append('\n')
        }

        // output GSM information.
        if (signal.isGsm) {
            s.append("  GSM RSSI=").append(signal.gsmSignalStrength)
                .append('\n')
            s.append("  GSM bit error rate=")
                .append(signal.gsmBitErrorRate)
                .append('\n')
        }

        // output CDMA information.
        s.append("  CDMA RSSI=").append(signal.cdmaDbm)
            .append('\n')
        s.append("  CDMA ECIO=").append(signal.cdmaEcio / 10)
            .append('\n')

        // output EVDO information.
        s.append("  EVDO RSSI=").append(signal.evdoDbm)
            .append('\n')
        s.append("  EVDO ECIO=").append(signal.evdoEcio)
            .append('\n')
        s.append("  EVDO SNR=").append(signal.evdoSnr / 10)
            .append('\n')
        s.append("}")
        return s.toString()
    }

    fun getFieldValue(target: Any, fieldName: String?): String {
        return try {
            val field = target.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field[target]
            value?.toString() ?: FAIL_VALUE
        } catch (e: Exception) {
            FAIL_VALUE
        }
    }

    fun classify(signal: SignalStrength): SignalType {
        return if (isLte(signal)) {
            SignalType.LTE
        } else if (signal.isGsm) {
            SignalType.GSM
        } else {
            SignalType.CDMA
        }
    }

    fun isLte(signalStrength: SignalStrength): Boolean {
        val value = getFieldValue(signalStrength, "mLteSignalStrength")
        return value !== FAIL_VALUE
    }

    fun logToTV(tag:String, msg:String){
        tvListen.append(msg+"\n")
    }

    private fun logWifiStrength() {
        val manager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = manager.connectionInfo
        val curr = info.rssi
        if (curr != this.lastRssi) {
            Log.d(TAG, "Strength(WiFi)=$curr")
            this.lastRssi = curr
        }
    }

    fun getPhoneInfo():String{
        //Get the instance of TelephonyManager
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        //Calling the methods of TelephonyManager the returns the information
        val IMEINumber = tm.deviceId
        val subscriberID = tm.subscriberId
        val SIMSerialNumber = tm.simSerialNumber
        val networkCountryISO = tm.networkCountryIso
        val SIMCountryISO = tm.simCountryIso
        val softwareVersion = tm.deviceSoftwareVersion
        val voiceMailNumber = tm.voiceMailNumber
        val line1Number = tm.line1Number
        val simOpName = tm.simOperatorName
        val cellLoc=tm.cellLocation
        var cellLocStr=""
        if(cellLoc is CdmaCellLocation){
            val cdmaCellLoc=cellLoc as CdmaCellLocation
            cellLocStr=cdmaCellLoc.baseStationLatitude.toString()+","+cdmaCellLoc.baseStationLongitude.toString()
        }else if(cellLoc is GsmCellLocation){
            val gsmCellLoc=cellLoc as GsmCellLocation
            cellLocStr="LAC:"+gsmCellLoc.lac.toString()+", CID:"+gsmCellLoc.cid.toString()
        }

        var ps=""
        val locationManager =
            this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for(p in locationManager.allProviders){
            ps+=p.toString()+";"
        }
        var cell0Info = ""
        if(tm.allCellInfo!=null){
            for(cell in tm.allCellInfo){
                cell0Info+=cell.toString()
            }
        }
        /*var cell0Info = ""
        if(tm.allCellInfo.size>0)
            cell0Info=tm.allCellInfo[0].toString()*/

        //Get the phone type
        var strphoneType = ""
        val phoneType = tm.phoneType

        when (phoneType) {
            TelephonyManager.PHONE_TYPE_CDMA -> strphoneType = "CDMA"
            TelephonyManager.PHONE_TYPE_GSM -> strphoneType = "GSM"
            TelephonyManager.PHONE_TYPE_SIP -> strphoneType = "SIP"
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
        info += "\nLine1 Number:$line1Number"
        info += "\nSim operator:$simOpName"
        info += "\nproviders: $ps"
        info += "\ncell location: $cellLocStr"
        info += "\nCell infos: $cell0Info"

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

        requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE),
            REQUEST_PERMISSION+1)

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

        if(requestCode == REQUEST_PERMISSION+1) {
            tvInfo.text = getPhoneInfo()
            tvInfo.setMovementMethod(ScrollingMovementMethod())
            tvListen.setMovementMethod(ScrollingMovementMethod())
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
