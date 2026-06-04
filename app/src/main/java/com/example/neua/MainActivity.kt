package com.example.neua

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.PlayerView

/**
 * Demo Activity: NeUA ABR with androidx.media3
 *
 * This demo player illustrates the integration of the NeUA uncertainty-aware
 * ABR algorithm into a standard media3 (successor to ExoPlayer) playback
 * pipeline. NeUATrackSelectionFactory replaces the default
 * AdaptiveTrackSelection.Factory, injecting the uncertainty-aware bitrate
 * selection logic described in Section 4 of the paper.
 *
 * To test with a real DASH stream, replace DEMO_STREAM_URL with any valid
 * MPD manifest URL. The player will log each bitrate selection decision
 * in Logcat under the tag "NeUA".
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // BigBuckBunny DASH manifest (public test stream)
        private const val DEMO_STREAM_URL =
            "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView  = findViewById(R.id.player_view)
        statusText  = findViewById(R.id.status_text)

        initPlayer()
    }

    private fun initPlayer() {
        // 1. Bandwidth meter — shared between player and NeUA selector
        val bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()

        // 2. Track selector using NeUA factory
        val trackSelector = DefaultTrackSelector(
            this,
            NeUATrackSelectionFactory(bandwidthMeter)
        )

        // 3. Build ExoPlayer with NeUA track selector
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .build()
            .also { exoPlayer ->
                playerView.player = exoPlayer

                // 4. Load DASH stream
                val mediaItem = MediaItem.fromUri(Uri.parse(DEMO_STREAM_URL))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true

                // 5. Display current bitrate in status bar
                exoPlayer.addListener(object : Player.Listener {
                    override fun onEvents(
                        player: Player,
                        events: Player.Events
                    ) {
                        if (events.contains(Player.EVENT_TRACKS_CHANGED)) {
                            val videoFormat = player.videoFormat
                            if (videoFormat != null) {
                                val kbps = videoFormat.bitrate / 1000
                                statusText.text =
                                    "NeUA ABR — current bitrate: ${kbps} kbps"
                            }
                        }
                    }
                })
            }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
