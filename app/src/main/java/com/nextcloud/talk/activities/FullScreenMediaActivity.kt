/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * @author Ezhil Shanmugham
 * @author Parneet Singh
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * Copyright (c) 2023 Parneet Singh <gurayaparneet@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityFullScreenMediaBinding
import com.nextcloud.talk.utils.Mimetype.VIDEO_PREFIX_GENERIC
import java.io.File

@AutoInjector(NextcloudTalkApplication::class)
class FullScreenMediaActivity : AppCompatActivity() {
    lateinit var binding: ActivityFullScreenMediaBinding

    private lateinit var path: String
    private var player: ExoPlayer? = null

    private var playWhenReadyState: Boolean = true
    private var playBackPosition: Long = 0L
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_preview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.share -> {
                val shareUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID,
                    File(path)
                )

                val shareIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    type = VIDEO_PREFIX_GENERIC
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))

                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val fileName = intent.getStringExtra("FILE_NAME")
        val isAudioOnly = intent.getBooleanExtra("AUDIO_ONLY", false)

        path = applicationContext.cacheDir.absolutePath + "/" + fileName

        binding = ActivityFullScreenMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.mediaviewToolbar)
        supportActionBar?.title = fileName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.playerView.showController()
        if (isAudioOnly) {
            binding.playerView.controllerShowTimeoutMs = 0
        }

        initWindowInsetsController()
        applyWindowInsets()

        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { v ->
                if (v != 0) {
                    enterImmersiveMode()
                } else {
                    exitImmersiveMode()
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
        preparePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(applicationContext).build()
        binding.playerView.player = player
    }

    private fun preparePlayer() {
        val mediaItem: MediaItem = MediaItem.fromUri(path)
        player?.let { exoPlayer ->
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = playWhenReadyState
            exoPlayer.seekTo(playBackPosition)
            exoPlayer.prepare()
        }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playBackPosition = exoPlayer.currentPosition
            playWhenReadyState = exoPlayer.playWhenReady
            exoPlayer.release()
        }
        player = null
    }

    private fun initWindowInsetsController() {
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun enterImmersiveMode() {
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        supportActionBar?.hide()
    }

    private fun exitImmersiveMode() {
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        supportActionBar?.show()
    }
    private fun applyWindowInsets() {
        val playerView = binding.playerView
        val exoControls = playerView.findViewById<FrameLayout>(R.id.exo_bottom_bar)
        val exoProgress = playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
        val progressBottomMargin = exoProgress.marginBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type
                    .displayCutout()
            )
            binding.mediaviewToolbar.updateLayoutParams<MarginLayoutParams> {
                topMargin = insets.top
            }
            exoControls.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = insets.bottom
            }
            exoProgress.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = insets.bottom + progressBottomMargin
            }
            exoControls.updatePadding(left = insets.left, right = insets.right)
            exoProgress.updatePadding(left = insets.left, right = insets.right)
            binding.mediaviewToolbar.updatePadding(left = insets.left, right = insets.right)
            WindowInsetsCompat.CONSUMED
        }
    }
}
