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
            Log.d("CellInfo", "Cell info changed: ${cellInfo} cells")
            Log.d("CellInfoSize", "Cell info changed: ${cellInfo.size} cells")
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

            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                telephonyManager.requestCellInfoUpdate(
                    ContextCompat.getMainExecutor(this@MainActivity),
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: List<CellInfo>) {
                            Log.d("CellInfo", "Cells found: ${cellInfo.size}")
                            processCellInfo(cellInfo)
                        }

                        override fun onError(errorCode: Int, detail: Throwable?) {
                            Log.e("CellInfo", "Error getting cell info: $errorCode")
                        }
                    }
                )
            } else {
                Log.e("Permission", "Missing required permission ACCESS_FINE_LOCATION")
            }
            updatePhoneInfo()
        }

        private fun processCellInfo(cellInfoList: List<CellInfo>) {
            cellInfoList.forEach { cellInfo ->
                when (cellInfo) {
                    is CellInfoLte -> {
                        Log.d("CellInfo", """
                        |LTE Cell Found:
                        |PCI: ${cellInfo.cellIdentity.pci}
                        |Signal Strength: ${cellInfo.cellSignalStrength.dbm}
                        |EARFCN: ${safeGetValue(cellInfo.cellIdentity, "getEarfcn")}
                    """.trimMargin())
                    }
                    is CellInfoNr -> {
                        Log.d("CellInfo", """
                        |5G Cell Found:
                        |PCI: ${safeGetValue(cellInfo.cellIdentity, "getPci")}
                        |NRARFCN: ${safeGetValue(cellInfo.cellIdentity, "getNrarfcn")}
                        |Signal Strength: ${safeGetValue(cellInfo.cellSignalStrength, "getCsiRsrp")}
                    """.trimMargin())
                    }
                }
            }
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

            // Get current network mode
            val networkMode = when (currentDisplayInfo?.overrideNetworkType) {
                5 -> "5G NSA (Non-Standalone)"  // OVERRIDE_NETWORK_TYPE_NR_NSA
                3 -> "5G SA (Standalone)"       // OVERRIDE_NETWORK_TYPE_NR
                else -> when (currentDisplayInfo?.networkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    else -> "Unknown"
                }
            }

            stringBuilder.append("Network Mode: $networkMode\n\n")


            // Get current cell info
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
                        cellInfo.forEach { cell ->
                            when (cell) {
                                is CellInfoLte -> {
                                    stringBuilder.append("[LTE Information]\n")
                                    appendLteInfo(stringBuilder, cell)
                                }
                                is CellInfoNr -> {
                                    stringBuilder.append("\n[5G Information]\n")
                                    append5GInfo(stringBuilder, cell)
                                }
                            }
                        }
                        networkDataTextView.text = stringBuilder.toString()
                    }

                    override fun onError(errorCode: Int, detail: Throwable?) {
                        Log.e("CellInfo", "Error getting cell info: $errorCode")
                    }
                }
            )

        } catch (e: Exception) {
            Log.e("PhoneInfo", "Error updating phone info", e)
        }
    }

    private fun appendLteInfo(sb: StringBuilder, cellInfo: CellInfoLte) {
        val identity = cellInfo.cellIdentity
        val signal = cellInfo.cellSignalStrength

        // Get LTE bandwidth
        val bandwidth = try {
            val bw = identity.bandwidth
            when {
                bw <= 0 -> "N/A"
                else -> "${bw/1000} MHz"  // Convert from kHz to MHz
            }
        } catch (e: Exception) {
            "N/A"
        }

        // Get LTE bands
        val bands = try {
            val bandsArray = identity.bands
            bandsArray.joinToString(", ") { "B${it}" }
        } catch (e: Exception) {
            "N/A"
        }

        sb.append("Band: $bands\n")
        sb.append("Bandwidth: $bandwidth\n")
        sb.append("PCI: ${safeGetValue(identity, "getPci")}\n")
        sb.append("PCI: ${safeGetValue(identity, "getTac")}\n")
        sb.append("EARFCN: ${safeGetValue(identity, "getEarfcn")}\n")
//        sb.append("Bandwidth: ${safeGetValue(identity, "getBandwidth")} MHz\n")
        sb.append("Signal Strength: ${signal.dbm} dBm\n")
        sb.append("RSRP: ${signal.rsrp} dBm\n")
        sb.append("RSRQ: ${signal.rsrq} dB\n")
        sb.append("SINR: ${signal.rssnr} dB\n")
        sb.append("RSSI: ${signal.rssi} dB \n")
        sb.append("RPLMN: ${signal}")
    }

    private fun append5GInfo(sb: StringBuilder, cellInfo: CellInfoNr) {
        val identity = cellInfo.cellIdentity
        val signal = cellInfo.cellSignalStrength

        // Get NR bandwidth
        val bandwidth = try {
            // For 5G, we need to use reflection as the method might vary by Android version
            val bw = identity.javaClass.getMethod("getBandwidth").invoke(identity) as Int
            when {
                bw <= 0 -> "N/A"
                else -> "${bw/1000} MHz"  // Convert from kHz to MHz
            }
        } catch (e: Exception) {
            "N/A"
        }

        // Get NR bands
        val nrBands = try {
            val bandsArray = identity.javaClass.getMethod("getBands").invoke(identity) as IntArray
            bandsArray.joinToString(", ") { "n${it}" }
        } catch (e: Exception) {
            "N/A"
        }

        sb.append("Band: $nrBands\n")
        sb.append("Bandwidth: $bandwidth\n")
        sb.append("PCI: ${safeGetValue(identity, "getPci")}\n")
        sb.append("NRARFCN: ${safeGetValue(identity, "getNrarfcn")}\n")
        sb.append("SS-RSRP: ${safeGetValue(signal, "getSsRsrp")} dBm\n")
        sb.append("SS-RSRQ: ${safeGetValue(signal, "getSsRsrq")} dB\n")
        sb.append("SS-SINR: ${safeGetValue(signal, "getSsbSinr")} dB\n")
        sb.append("CSI-RSRP: ${safeGetValue(signal, "getCsiRsrp")} dBm\n")
        sb.append("CSI-RSRQ: ${safeGetValue(signal, "getCsiRsrq")} dB\n")
        sb.append("CSI-RSSI: ${safeGetValue(signal, "getCsiRssi")} dB\n")
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

    private fun estimateBandwidthFromRB(numRB: Int): Int {
        return when (numRB) {
            6 -> 1400    // 1.4 MHz
            15 -> 3000   // 3 MHz
            25 -> 5000   // 5 MHz
            50 -> 10000  // 10 MHz
            75 -> 15000  // 15 MHz
            100 -> 20000 // 20 MHz
            else -> 0
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