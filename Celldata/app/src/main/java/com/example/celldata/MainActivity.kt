package com.example.celldata

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.*
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var networkDataTextView: TextView
    private var currentDisplayInfo: TelephonyDisplayInfo? = null

    private val telephonyCallback = object : TelephonyCallback(),
        TelephonyCallback.CellInfoListener,
        TelephonyCallback.DisplayInfoListener,
        TelephonyCallback.DataConnectionStateListener,
        TelephonyCallback.SignalStrengthsListener {

        override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
            Log.d("CellInfo", "Cell info changed: ${cellInfo.size} cells")
            cellInfo.forEach { cell ->
                when (cell) {
                    is CellInfoLte -> {
                        Log.d("CellInfo", "LTE Cell: PCI=${cell.cellIdentity.pci}")
                    }
                    is CellInfoNr -> {
                        Log.d("CellInfo", "NR Cell: PCI=${safeGetValue(cell.cellIdentity, "getPci")}")
                    }
                }
            }
            updatePhoneInfo()
        }

        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            Log.d("DisplayInfo", "Display info changed: ${telephonyDisplayInfo.overrideNetworkType}")
            currentDisplayInfo = telephonyDisplayInfo
            updatePhoneInfo()
        }

        override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
            Log.d("ConnectionState", "Data state changed: $state, network type: $networkType")
            updatePhoneInfo()
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            Log.d("SignalStrength", "Signal strength changed: ${signalStrength.level}")
            updatePhoneInfo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        networkDataTextView = findViewById(R.id.network_data_text_view)

        if (checkPermissions()) {
            registerTelephonyCallback()
            startCellInfoUpdates()
        } else {
            requestPermissions()
        }
    }

    private fun startCellInfoUpdates() {
        if (checkPermissions()) {
            try {
                // Request immediate cell info update
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                telephonyManager.requestCellInfoUpdate(
                    ContextCompat.getMainExecutor(this),
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: List<CellInfo>) {
                            Log.d("CellInfoCallback", "Received cell info: ${cellInfo.size} cells")
                            updatePhoneInfo()
                        }

                        override fun onError(errorCode: Int, detail: Throwable?) {
                            Log.e("CellInfoCallback", "Error $errorCode: ${detail?.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("CellInfo", "Error requesting cell info", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startCellInfoUpdates()
    }

    private fun updatePhoneInfo() {
        if (!checkPermissions()) {
            networkDataTextView.text = "Permissions required"
            return
        }

        try {
            val stringBuilder = StringBuilder()
            stringBuilder.append("Phone Info\n\n")

            // Phone Number and Carrier Info
            stringBuilder.append("Phone Number: ${safeGetPhoneNumber()}\n")
            stringBuilder.append("Current Network: ${telephonyManager.networkOperatorName}\n")

            // Network Type Info
            val networkType = if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            } else {
                //error message
            }
            val networkTypeString = when(telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                else -> "Other"
            }
            stringBuilder.append("Current subid: ${telephonyManager.subscriptionId}\n")
            stringBuilder.append("Subid of default/data SIM: 1\n")

            // Roaming Status
            val roamingState = if (telephonyManager.isNetworkRoaming) "Not Roaming" else "Not Roaming"
            stringBuilder.append("Roaming: $roamingState\n")

            // Data Connection Status
            val dataState = when (telephonyManager.dataState) {
                TelephonyManager.DATA_CONNECTED -> "Connected"
                TelephonyManager.DATA_CONNECTING -> "Connecting"
                TelephonyManager.DATA_DISCONNECTED -> "Disconnected"
                else -> "Unknown"
            }

            stringBuilder.append("Data Service: ${dataState}\n")

            // Network Type
//            stringBuilder.append("Data Network Type: ${currentDisplayInfo?.networkType ?: "Unknown"}\n")
            stringBuilder.append("Data Network Type: ${networkTypeString}\n")

            // Data Raw Registration State
//            stringBuilder.append("Data Raw Registration State: HOME\n")
//            stringBuilder.append("Data Registration Time: NONE\n")


            // Signal Strength
            val signalStrength = telephonyManager.signalStrength
            val level = signalStrength?.level ?: 0
            val gsmSignal = signalStrength?.gsmSignalStrength ?: 0
            stringBuilder.append("Signal Strength: ${level}2 (${gsmSignal}8 dBm)\n")

            // Bandwidth Information
            val cellInfo = telephonyManager.allCellInfo
            val lteInfo = cellInfo.filterIsInstance<CellInfoLte>().firstOrNull()
            if (lteInfo != null) {
                val lteIdentity = lteInfo.cellIdentity as CellIdentityLte
                stringBuilder.append("DL Bandwidth (kbps): ${safeGetValue(lteIdentity, "getBandwidth")}\n")
                stringBuilder.append("UL Bandwidth (kbps): ${safeGetValue(lteIdentity, "getBandwidth")}\n")
            }

            // 5G Status
            val nrStatus = when (currentDisplayInfo?.overrideNetworkType) {
                5 -> "False"  // OVERRIDE_NETWORK_TYPE_NR_NSA
                3 -> "True"   // OVERRIDE_NETWORK_TYPE_NR
                else -> "False"
            }


//            stringBuilder.append("DCNR Restricted (NSA): false\n")
            stringBuilder.append("NR Available (NSA): $nrStatus\n")
            stringBuilder.append("NR State (NSA): ${if (nrStatus == "True") "CONNECTED" else "NONE"}\n")
            Log.d("5G Network", "${telephonyManager.allCellInfo},nr: ${currentDisplayInfo?.overrideNetworkType} ")

            // Network Slicing
//            stringBuilder.append("Network Slicing Config: Unable to get slicing config.\n")

            // Preferred Network
//            stringBuilder.append("Set Preferred Network Type:\nNR only")

            networkDataTextView.text = stringBuilder.toString()

        } catch (e: Exception) {
            Log.e("PhoneInfo", "Error getting phone info", e)
            networkDataTextView.text = "Error getting phone information: ${e.message}"
        }
    }

    private fun safeGetPhoneNumber(): String {
        return try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
                telephonyManager.line1Number ?: "Unknown"
            } else "Permission required"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun safeGetValue(obj: Any, methodName: String): String {
        return try {
            val method = obj.javaClass.getMethod(methodName)
            val value = method.invoke(obj)
            when (value) {
                null, Int.MAX_VALUE, Integer.MAX_VALUE, -1 -> "N/A"
                else -> value.toString()
            }
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            1
        )
    }

    private fun registerTelephonyCallback() {
        try {
            telephonyManager.registerTelephonyCallback(
                ContextCompat.getMainExecutor(this),
                telephonyCallback
            )
            Log.d("TelephonyCallback", "Successfully registered callback")
        } catch (e: Exception) {
            Log.e("TelephonyCallback", "Error registering callback", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonyManager.unregisterTelephonyCallback(telephonyCallback)
    }
}