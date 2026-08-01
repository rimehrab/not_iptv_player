package dev.rimehrab.iptvplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        loadPlaylist()
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
                playChannel(0)
            } else {
                showLabel("Failed to load playlist: ${PlaylistLoader.PLAYLIST_URL}")
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
        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        player.playWhenReady = true

        adapter.setFocused(currentIndex)
        showLabel(channel.name)
    }

    private fun nextChannel() = playChannel(currentIndex + 1)
    private fun previousChannel() = playChannel(currentIndex - 1)

    private fun showLabel(text: String) {
        binding.nowPlayingLabel.text = text
        binding.nowPlayingLabel.visibility = View.VISIBLE
        hideLabelHandler.removeCallbacksAndMessages(null)
        hideLabelHandler.postDelayed({
            binding.nowPlayingLabel.visibility = View.GONE
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
}
