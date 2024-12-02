package pl.mrugacz95.kiosk

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import pl.mrugacz95.kiosk.databinding.ActivityMainBinding
import pl.mrugacz95.kiosk.databinding.ActivityPlayerBinding

@RequiresApi(Build.VERSION_CODES.N)
class MainActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var mAdminComponentName: ComponentName
    private lateinit var mDevicePolicyManager: DevicePolicyManager

    companion object {
        const val LOCK_ACTIVITY_KEY = "pl.mrugacz95.kiosk.MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TTT", "MainActivity.onCreate")

        mAdminComponentName = MyDeviceAdminReceiver.getComponentName(this)
        mDevicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val isDeviceOwner = isDeviceOwner()
        val allVideos = getAllVideos(this)
        val hasVideos = allVideos.isNotEmpty()
        val launchPlayer = isDeviceOwner && hasVideos

        if (!launchPlayer) {
            showErrorInfo(isDeviceOwner, allVideos)
        } else {
            setKioskPolicies(true, true)
            startPlayer(allVideos)
        }
    }

    private fun showErrorInfo(isDeviceOwner: Boolean, allVideos: List<Pair<String, Uri>>) {
        Log.d("TTT", "MainActivity.showErrorInfo")

        stopPlayer(isDeviceOwner)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val setOwnerCmd =
            if (!isDeviceOwner) "=> execute command:\nadb shell dpm set-device-owner pl.mrugacz95.kiosk/.MyDeviceAdminReceiver"
            else ""
        val videos =
            if (allVideos.isEmpty()) "None!"
            else allVideos.joinToString(separator = "\n") { "- " + it.first }
        binding.txtErrors.text = """
Is device owner: $isDeviceOwner
$setOwnerCmd

Videos found:
$videos
"""
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startPlayer(allVideos: List<Pair<String, Uri>>) {
        val binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val ctx = this

        val player = ExoPlayer.Builder(ctx).apply {

            val extractorsFactory =
                DefaultExtractorsFactory().setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            setMediaSourceFactory(DefaultMediaSourceFactory(ctx, extractorsFactory))

        }.build()
        this.exoPlayer = player

        player.apply {

            setForegroundMode(true)

            addListener(
                object : Player.Listener {
                    // ACA catch player exceptions => go to next
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("TTT", "exoPlayer.onPlayerError: $error")
                        Log.d("TTT", "exoPlayer.onPlayerError: ${error.cause}")

                        Log.d("TTT", "exoPlayer.onPlayerError: SEEK NEXT")
                        seekToNextMediaItem()
                        Log.d("TTT", "exoPlayer.onPlayerError: PREPARE")
                        prepare()
                        Log.d("TTT", "exoPlayer.onPlayerError: PLAY")
                        play()
                        Log.d("TTT", "exoPlayer.onPlayerError: EXIT")
                    }

                    // report playing/not playing status
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        super.onIsPlayingChanged(isPlaying)
                        Log.d("TTT", "exoPlayer.onIsPlayingChanged: $isPlaying")
                    }

                    // report playlist's item
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)
                        val displayName = mediaItem?.mediaMetadata?.displayTitle?.toString()
                            ?: mediaItem?.mediaMetadata?.title?.toString()
                            ?: "<none>"
                        val str = "$reason ; $displayName"
                        Log.d("TTT", "exoPlayer.onMediaItemTransition: $str")
                    }
                }
            )

            // add all videos to the playlist
            allVideos.forEach { v ->
                addMediaItem(
                    MediaItem.Builder()
                        .setUri(v.second) // Set the URI
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setDisplayTitle(v.first) // Set the display name
                                .build()
                        ) // Attach the metadata
                        .build()
                )
            }

            this.repeatMode = Player.REPEAT_MODE_ALL
        }

        binding.playerView.controllerAutoShow = false
        binding.playerView.useController = true
        binding.playerView.player = player

        if(allVideos.count()==1) {
            binding.playerView.setShowPreviousButton(false)
            binding.playerView.setShowNextButton(false)
        }

        player.prepare()
        player.play()
    }

    private fun stopPlayer(isDeviceOwner: Boolean) {
        Log.d("TTT", "MainActivity.stopPlayer")

        setKioskPolicies(enable = false, isDeviceOwner = isDeviceOwner)
        //removeActiveAdmin()

        //val intent = Intent(applicationContext, MainActivity::class.java).apply {
        //    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        //}
        //intent.putExtra(LOCK_ACTIVITY_KEY, false)
        //startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        Log.d("TTT", "MainActivity.onStart")
    }

    override fun onStop() {
        super.onStop()
        Log.d("TTT", "MainActivity.onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TTT", "MainActivity.onDestroy")

        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onPause() {
        super.onPause()
        Log.d("TTT", "MainActivity.onPause")

        this.exoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        Log.d("TTT", "MainActivity.onResume")

        val player = this.exoPlayer
        if (player != null) {
            player.play()
        }
    }

    private fun isDeviceOwner(): Boolean {
        val isDeviceOwner = mDevicePolicyManager.isDeviceOwnerApp(packageName)

        Log.d("TTT", "MainActivity.isDeviceOwner $isDeviceOwner")
        return isDeviceOwner
    }

    private fun removeActiveAdmin() {
        if (isDeviceOwner()) {
            Log.d("TTT", "MainActivity.removeActiveAdmin")
            mDevicePolicyManager.removeActiveAdmin(mAdminComponentName)
        }
    }

    private fun setKioskPolicies(enable: Boolean, isDeviceOwner: Boolean) {
        Log.d("TTT", "MainActivity.setKioskPolicies isDeviceOwner:$isDeviceOwner")

        if (isDeviceOwner) {
            setRestrictions(enable)
            enableStayOnWhilePluggedIn(enable)
            setUpdatePolicy(enable)
            setAsHomeApp(enable)
            setKeyGuardEnabled(enable)
        }
        setLockTask(enable, isDeviceOwner)
        setImmersiveMode(enable)
    }

    // region restrictions
    private fun setRestrictions(disallow: Boolean) {
        Log.d("TTT", "MainActivity.setRestrictions")

        this.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, disallow)
        //setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, disallow)
        setUserRestriction(UserManager.DISALLOW_ADD_USER, disallow)
        //setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, disallow)
        //setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, disallow)
        mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, disallow)
    }

    private fun setUserRestriction(restriction: String, disallow: Boolean) {
        Log.d("TTT", "MainActivity.setUserRestriction '$restriction'")

        if (disallow) {
            mDevicePolicyManager.addUserRestriction(mAdminComponentName, restriction)
        } else {
            mDevicePolicyManager.clearUserRestriction(mAdminComponentName, restriction)
        }
    }
    // endregion

    private fun enableStayOnWhilePluggedIn(active: Boolean) {
        Log.d("TTT", "MainActivity.enableStayOnWhilePluggedIn")

        if (active) {
            mDevicePolicyManager.setGlobalSetting(
                mAdminComponentName,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                (BatteryManager.BATTERY_PLUGGED_AC
                        or BatteryManager.BATTERY_PLUGGED_USB
                        or BatteryManager.BATTERY_PLUGGED_WIRELESS).toString()
            )
        } else {
            mDevicePolicyManager.setGlobalSetting(
                mAdminComponentName,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                "0"
            )
        }
    }

    private fun setLockTask(start: Boolean, isDeviceOwner: Boolean) {
        Log.d("TTT", "MainActivity.setLockTask")

        if (isDeviceOwner) {
            mDevicePolicyManager.setLockTaskPackages(
                mAdminComponentName, if (start) arrayOf(packageName) else arrayOf()
            )
        }
        if (start) {
            startLockTask()
        } else {
            stopLockTask()
        }
    }

    private fun setUpdatePolicy(enable: Boolean) {
        Log.d("TTT", "MainActivity.setUpdatePolicy")

        if (enable) {
            mDevicePolicyManager.setSystemUpdatePolicy(
                mAdminComponentName,
                SystemUpdatePolicy.createWindowedInstallPolicy(60, 120)
            )
        } else {
            mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName, null)
        }
    }

    private fun setAsHomeApp(enable: Boolean) {
        Log.d("TTT", "MainActivity.setAsHomeApp")

        if (enable) {
            val intentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            mDevicePolicyManager.addPersistentPreferredActivity(
                mAdminComponentName,
                intentFilter,
                ComponentName(packageName, MainActivity::class.java.name)
            )
        } else {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(
                mAdminComponentName, packageName
            )
        }
    }

    private fun setKeyGuardEnabled(enable: Boolean) {
        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, !enable)
    }

    private fun setImmersiveMode(enable: Boolean) {
        if (enable) {
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.decorView.systemUiVisibility = flags
        } else {
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun createIntentSender(
        context: Context?,
        sessionId: Int,
        packageName: String?
    ): IntentSender {
        val intent = Intent("INSTALL_COMPLETE")
        if (packageName != null) {
            intent.putExtra("PACKAGE_NAME", packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            FLAG_IMMUTABLE
        )
        return pendingIntent.intentSender
    }

    private fun getAllVideos(context: Context): List<Pair<String, Uri>> {
        Log.d("TTT", "MainActivity.getAllVideos")

        val videoDetails = mutableListOf<Pair<String, Uri>>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        val query = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) // Retrieve the display name
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                videoDetails.add(Pair(name, contentUri))
            }
        }

        return videoDetails
    }
}
