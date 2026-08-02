package dev.rimehrab.iptvplayer

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import dev.rimehrab.iptvplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer
    private lateinit var adapter: ChannelAdapter

    private var channels: List<Channel> = emptyList()
    private var currentIndex = 0
    private var listVisible = false

    private val hideLabelHandler = Handler(Looper.getMainLooper())
    private lateinit var gestureDetector: GestureDetector
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Generous buffer so weak/slow streams don't stutter or rebuffer constantly.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                60_000,  // min buffer before playback can start/resume
                180_000, // max buffer held in memory
                2_500,   // buffer needed to start playback
                5_000    // buffer needed to resume after a rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        binding.playerView.player = player

        // Phone only in practice — TV remotes don't send touch events, this just adds
        // swipe up/down as an extra input path alongside CHANNEL_UP/DOWN and D-pad.
        gestureDetector = GestureDetector(this, SwipeGestureListener())
        binding.playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        loadPlaylist()
    }

    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || listVisible) return false

            val deltaY = e2.y - e1.y
            val deltaX = e2.x - e1.x

            if (abs(deltaY) > abs(deltaX) &&
                abs(deltaY) > SWIPE_DISTANCE_THRESHOLD &&
                abs(velocityY) > SWIPE_VELOCITY_THRESHOLD
            ) {
                if (deltaY < 0) nextChannel() else previousChannel()
                return true
            }

            if (abs(deltaX) > abs(deltaY) &&
                abs(deltaX) > SWIPE_DISTANCE_THRESHOLD &&
                abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
            ) {
                // swipe left-to-right (finger moves right) opens the list, like sliding it into view
                toggleList(deltaX > 0)
                return true
            }
            return false
        }
    }

    private fun loadPlaylist() {
        lifecycleScope.launch {
            val loaded = try {
                withContext(Dispatchers.IO) { PlaylistLoader.fetch() }
            } catch (e: Exception) {
                emptyList()
            }
            channels = loaded
            if (channels.isNotEmpty()) {
                setupChannelList()
                val savedUrl = prefs.getString(KEY_LAST_CHANNEL_URL, null)
                val startIndex = channels.indexOfFirst { it.url == savedUrl }.let {
                    if (it >= 0) it else 0
                }
                playChannel(startIndex)
            } else {
                binding.nowPlayingLogo.visibility = View.GONE
                binding.nowPlayingCategory.text = "Connection problem"
                binding.nowPlayingName.text = "Please check your internet connection"
                binding.nowPlayingNumber.text = ""
                binding.nowPlayingCard.visibility = View.VISIBLE
            }
        }
    }

    private fun setupChannelList() {
        adapter = ChannelAdapter(channels) { position ->
            playChannel(position)
            toggleList(false)
        }
        binding.channelList.layoutManager = LinearLayoutManager(this)
        binding.channelList.adapter = adapter
    }

    private fun playChannel(index: Int) {
        if (channels.isEmpty()) return
        currentIndex = ((index % channels.size) + channels.size) % channels.size

        val channel = channels[currentIndex]

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(channel.headers["User-Agent"] ?: DEFAULT_USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(channel.headers)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6)) // retry flaky streams harder before giving up

        val mediaItem = MediaItem.Builder()
            .setUri(channel.url)
            .apply {
                if (channel.url.contains("m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        adapter.setFocused(currentIndex)
        showChannelToast(channel, currentIndex + 1)

        prefs.edit().putString(KEY_LAST_CHANNEL_URL, channel.url).apply()
    }

    private fun nextChannel() = playChannel(currentIndex + 1)
    private fun previousChannel() = playChannel(currentIndex - 1)

    private fun showChannelToast(channel: Channel, number: Int) {
        binding.nowPlayingCategory.text = "▶  ${channel.category ?: "Live"}"
        binding.nowPlayingName.text = channel.name
        binding.nowPlayingNumber.text = number.toString()

        if (channel.logo != null) {
            binding.nowPlayingLogo.visibility = View.VISIBLE
            binding.nowPlayingLogo.load(channel.logo)
        } else {
            binding.nowPlayingLogo.visibility = View.GONE
        }

        binding.nowPlayingCard.visibility = View.VISIBLE
        hideLabelHandler.removeCallbacksAndMessages(null)
        hideLabelHandler.postDelayed({
            binding.nowPlayingCard.visibility = View.GONE
        }, 3_000)
    }

    private fun toggleList(show: Boolean) {
        listVisible = show
        binding.channelListContainer.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.channelList.post {
                binding.channelList.layoutManager
                    ?.findViewByPosition(currentIndex)
                    ?.requestFocus()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            // Real remotes with dedicated channel keys
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                nextChannel()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                previousChannel()
                return true
            }

            // D-pad fallback: most cheap TV boxes only send DPAD, not CHANNEL_UP/DOWN
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!listVisible) {
                    nextChannel()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!listVisible) {
                    previousChannel()
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleList(!listVisible)
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!listVisible) {
                    toggleList(true)
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (listVisible) {
                    toggleList(false)
                    return true
                }
            }

            KeyEvent.KEYCODE_BACK -> {
                if (listVisible) {
                    toggleList(false)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    companion object {
        private const val SWIPE_DISTANCE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
        private const val PREFS_NAME = "iptv_player_prefs"
        private const val KEY_LAST_CHANNEL_URL = "last_channel_url"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    }
}
