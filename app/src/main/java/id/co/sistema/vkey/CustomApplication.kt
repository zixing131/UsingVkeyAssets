package id.co.sistema.vkey

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import com.vkey.android.vguard.*
import com.vkey.android.vguard.VGuardBroadcastReceiver.*
import org.json.JSONObject

class CustomApplication : Application(), VGExceptionHandler,
    Application.ActivityLifecycleCallbacks {

    // VGuard object that is used for scanning
    private var vGuardMgr: VGuard? = null

    // LifecycleHook to notify VGuard of activity's lifecycle
    private lateinit var hook: VGuardLifecycleHook

    // For VGuard to notify host app of events
    private lateinit var broadcastRcvr: VGuardBroadcastReceiver

    companion object {
        private const val PROFILE_LOADED = "vkey.android.vguard.PROFILE_LOADED"
        private const val VOS_FIRMWARE_RETURN_CODE_KEY = "vkey.android.vguard.FIRMWARE_RETURN_CODE"
        private const val PROFILE_THREAT_RESPONSE = "vkey.android.vguard.PROFILE_THREAT_RESPONSE"
        private const val TAG_ON_RECEIVE = "OnReceive"
        private const val DEFAULT_VALUE = 0L
    }

    private fun setupVGuard() {
        // TODO: setup V-OS App Protection here,
        broadcastRcvr = object : VGuardBroadcastReceiver(null) {
            override fun onReceive(context: Context?, intent: Intent?) {
                when {
                    PROFILE_LOADED == intent?.action -> showLogDebug(
                        TAG_ON_RECEIVE,
                        "PROFILE_LOADED"
                    )

                    ACTION_SCAN_COMPLETE == intent?.action -> showLogDebug(
                        TAG_ON_RECEIVE,
                        "ACTION_SCAN_COMPLETE"
                    )

                    VGUARD_OVERLAY_DETECTED_DISABLE == intent?.action -> showLogDebug(
                        TAG_ON_RECEIVE,
                        "VGUARD_OVERLAY_DETECTED_DISABLE"
                    )

                    VGUARD_OVERLAY_DETECTED == intent?.action -> showLogDebug(
                        TAG_ON_RECEIVE,
                        "VGUARD_OVERLAY_DETECTED"
                    )

                    VGUARD_STATUS == intent?.action -> {
                        showLogDebug(
                            TAG_ON_RECEIVE,
                            "VGuardInitStatus: ${intent.hasExtra(VGUARD_INIT_STATUS)}"
                        )
                        if (intent.hasExtra(VGUARD_INIT_STATUS)) {
                            val initStatus = intent.getBooleanExtra(VGUARD_INIT_STATUS, false)
                            var message = "\n $VGUARD_STATUS: $initStatus"

                            if (!initStatus) {
                                try {
                                    val jsonObject =
                                        JSONObject(intent.getStringExtra(VGUARD_MESSAGE))
                                    showLogDebug(
                                        TAG_ON_RECEIVE,
                                        "code: ${jsonObject.getString("code")}"
                                    )
                                    showLogDebug(
                                        TAG_ON_RECEIVE,
                                        "code: ${jsonObject.getString("description")}"
                                    )
                                    message += jsonObject.toString()
                                } catch (e: Exception) {
                                    Log.e(TAG_ON_RECEIVE, e.message.toString())
                                    e.printStackTrace()
                                }
                                showLogDebug(TAG_ON_RECEIVE, message)
                            }
                        }
                    }

                    VOS_READY == intent?.action -> {
                        val firmwareReturnCode =
                            intent.getLongExtra(VOS_FIRMWARE_RETURN_CODE_KEY, DEFAULT_VALUE)
                        if (firmwareReturnCode >= DEFAULT_VALUE) {
                            // if the `VGuardManager` is not available,
                            // create a `VGuardManager` instance from `VGuardFactory`
                            if (vGuardMgr == null) {
                                vGuardMgr = VGuardFactory.getInstance()
                                hook = ActivityLifecycleHook(vGuardMgr)

                                showLogDebug(
                                    TAG_ON_RECEIVE,
                                    "isVosStarted: ${vGuardMgr?.isVosStarted.toString()}"
                                )
                                showLogDebug(
                                    TAG_ON_RECEIVE,
                                    "TID: ${vGuardMgr?.troubleshootingId.toString()}"
                                )
                            }
                        } else {
                            // Error handling
                            showLogDebug(TAG_ON_RECEIVE, "vos_ready_error_firmware")
                        }
                        showLogDebug(TAG_ON_RECEIVE, "VOS_READY")
                    }
                }
            }
        }

        // register using LocalBroadcastManager only for keeping data within your app
        val localBroadcastMgr = LocalBroadcastManager.getInstance(this)

        localBroadcastMgr.registerReceiver(broadcastRcvr, IntentFilter(ACTION_FINISH))
        localBroadcastMgr.registerReceiver(broadcastRcvr, IntentFilter(ACTION_SCAN_COMPLETE))
        localBroadcastMgr.registerReceiver(broadcastRcvr, IntentFilter(PROFILE_LOADED))
        localBroadcastMgr.registerReceiver(broadcastRcvr, IntentFilter(VOS_READY))
        localBroadcastMgr.registerReceiver(broadcastRcvr, IntentFilter(PROFILE_THREAT_RESPONSE))
        localBroadcastMgr.registerReceiver(broadcastRcvr, IntentFilter(VGUARD_OVERLAY_DETECTED))
        localBroadcastMgr.registerReceiver(
            broadcastRcvr,
            IntentFilter(VGUARD_OVERLAY_DETECTED_DISABLE)
        )
        localBroadcastMgr.registerReceiver(broadcastRcvr, IntentFilter(VGUARD_STATUS))

        /** TODO: Setting up V-OS App Protection here,
         * Using new configuration method getVGuard(context, config)
         * */
        val config = VGuardFactory.Builder()
            .setDebugable(BuildConfig.DEBUG)
            .setAllowsArbitraryNetworking(true)
            .setMemoryConfiguration(MemoryConfiguration.DEFAULT)
            .setVGExceptionHandler(this)

        VGuardFactory().getVGuard(this, config)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun handleException(e: Exception?) {}

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (vGuardMgr == null && activity is MainActivity) {
            setupVGuard()
        }
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        vGuardMgr?.onResume(hook)
    }

    override fun onActivityPaused(activity: Activity) {
        vGuardMgr?.onPause(hook)
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, savedInstanceState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is MainActivity) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastRcvr)
            vGuardMgr?.destroy()
            vGuardMgr = null
        }
    }
}