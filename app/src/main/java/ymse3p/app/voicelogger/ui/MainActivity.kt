package ymse3p.app.voicelogger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import ymse3p.app.voicelogger.MyApplication
import ymse3p.app.voicelogger.R
import ymse3p.app.voicelogger.databinding.ActivityMainBinding
import ymse3p.app.voicelogger.util.CannotCollectGpsLocationException
import ymse3p.app.voicelogger.util.CannotSaveAudioException
import ymse3p.app.voicelogger.util.CannotStartRecordingException
import ymse3p.app.voicelogger.util.Constants.Companion.REQUEST_RECORD_AUDIO_PERMISSION
import ymse3p.app.voicelogger.viewmodels.MainViewModel
import ymse3p.app.voicelogger.viewmodels.playbackViewModel.PlayBackViewModel
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** Data Bindings */
    private lateinit var _binding: ActivityMainBinding
    private val binding get() = _binding

    /** ViewModels */
    private val mainViewModel by viewModels<MainViewModel>()
    private val playbackViewModel by viewModels<PlayBackViewModel>()

    private val playSpeedList: List<Float> = listOf(1F, 1.25F, 1.5F, 1.75F, 2.0F)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /** UIの初期化 */
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "VoiceLogger"

        /** コルーチン起動 */
        // metadataの変更を受け取る
        lifecycleScope.launchWhenCreated {
            playbackViewModel.metadata.collect { metadata ->
                changeMetadata(metadata)
            }
        }
        // playbackStateの変更を受け取る
        lifecycleScope.launchWhenCreated {
            playbackViewModel.playbackState.collect { playbackState ->
                if (binding.linearLayoutBottom.height == 0) {
                    binding.linearLayoutBottom.apply {
                        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        BottomSheetBehavior.from(this).state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                    binding.includeBottomGap.visibility = View.VISIBLE
                }
                changePlaybackState(playbackState)
            }
        }

        // 録音状態の変更を受け取る
        lifecycleScope.launchWhenCreated {
            mainViewModel.isRecording.collect { isRecording ->
                if (isRecording)
                    binding.mic.setImageResource(R.drawable.ic_baseline_stop_24)
                else
                    binding.mic.setImageResource(R.drawable.ic_baseline_mic_24)
            }
        }

        lifecycleScope.launchWhenCreated {
            mainViewModel.showRecordButton.collect { shouldShow ->
                if (shouldShow) binding.mic.visibility = View.VISIBLE
                else binding.mic.visibility = View.INVISIBLE
            }
        }

        /** 録音用UIの設定　*/
        binding.mic.setOnClickListener {
            if (mainViewModel.isRecording.value) {
                try {
                    mainViewModel.stopRecording()
                    Snackbar.make(
                        binding.mainActivitySnackBar, "録音を終了しました", Snackbar.LENGTH_SHORT
                    ).show()
                    findNavController(R.id.nav_host_fragment).navigate(R.id.action_global_audioSaveBottomSheet)
                } catch (e: CannotSaveAudioException) {
                    Toast.makeText(this, "エラー発生のため、録音データは保存されませんでした。", Toast.LENGTH_SHORT).show()
                }
            } else {
                requestPermission()
            }
        }

        /** 再生用UIの設定 */
        binding.buttonPrev.setOnClickListener { playbackViewModel.skipToPrev() }
        binding.buttonNext.setOnClickListener { playbackViewModel.skipToNext() }

        binding.replay10Button.setOnClickListener {
            val currentPos = playbackViewModel.playbackState.replayCache.firstOrNull()?.position
                ?: return@setOnClickListener
            playbackViewModel.seekTo(currentPos - 10000)
        }
        binding.forward10Button.setOnClickListener {
            val currentPos = playbackViewModel.playbackState.replayCache.firstOrNull()?.position
                ?: return@setOnClickListener
            playbackViewModel.seekTo(currentPos + 10000)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let { playbackViewModel.seekTo(it.progress.toLong()) }
            }
        })

        /** 再生速度変更ボタン */
        val speedButtonList = mutableListOf<Button?>()
        val speedButtonSetView: ViewGroup = binding.playbackSpeedButtonSet

        for (i in 0 until speedButtonSetView.childCount) {
            val button = speedButtonSetView.getChildAt(i) as Button?
            button?.text = playSpeedList.getOrNull(i)?.toString()
            speedButtonList.add(button)
        }

        speedButtonList.forEachIndexed { index, button ->
            button?.setOnClickListener {
                (application as MyApplication).playbackSpeedFlow.value = playSpeedList[index]
            }
        }

        /** 現在の再生速度を表示するボタン */
        binding.changeSpeedButton.setOnClickListener {
            BottomSheetBehavior.from(binding.linearLayoutBottom).state =
                BottomSheetBehavior.STATE_EXPANDED
        }

        lifecycleScope.launchWhenCreated {
            (application as MyApplication).playbackSpeedFlow.collect { playbackSpeed ->
                val speedText = playbackSpeed.toString() + "x"
                binding.changeSpeedButton.text = speedText
            }
        }

        binding.switchCutSilence.setOnCheckedChangeListener { buttonView, isChecked ->
            (application as MyApplication).skipSilenceFlow.value = isChecked
        }
    }

    override fun onStart() {
        super.onStart()
        // onStart()時の音声再生状態を受け取る
        lifecycleScope.launchWhenStarted {
            changeMetadata(playbackViewModel.getCurrentMetadata())
            changePlaybackState(playbackViewModel.getCurrentPlaybackState())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.contains(PackageManager.PERMISSION_DENIED))
                showSnackBarGrantNeeded()
            else
                try {
                    lifecycleScope.launchWhenCreated {
                        mainViewModel.startRecording()
                        mainViewModel.startLocationUpdates()
                    }
                    Snackbar.make(
                        binding.mainActivitySnackBar, "録音を開始しました", Snackbar.LENGTH_SHORT
                    ).show()
                    return
                } catch (e: CannotStartRecordingException) {
                    Snackbar.make(
                        binding.mainActivitySnackBar, "エラーが発生しました", Snackbar.LENGTH_SHORT
                    ).show()
                    return
                } catch (e: CannotCollectGpsLocationException) {
                    showSnackBarGrantNeeded()
                    return
                }
        }
    }


    private fun changeMetadata(metadata: MediaMetadataCompat?) {
        metadata?.let {
            binding.textViewTitle.text = metadata.description.title
            binding.textViewDuration.text = milliSecToTimeString(
                it.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
            )
            binding.seekBar.max =
                it.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
        }
    }

    private fun changePlaybackState(state: PlaybackStateCompat?) {
        if (state !== null) {
            if (state.state == PlaybackStateCompat.STATE_PLAYING) {
                binding.buttonPlay.apply {
                    setOnClickListener { playbackViewModel.pause() }
                    setImageResource(R.drawable.ic_baseline_pause_24)
                }
            } else {
                binding.buttonPlay.apply {
                    setOnClickListener { playbackViewModel.play() }
                    setImageResource(R.drawable.ic_baseline_play_arrow_24)
                }
            }

            binding.textViewPosition.text = milliSecToTimeString(state.position)
            binding.seekBar.progress = state.position.toInt()
        }

    }

    private fun milliSecToTimeString(duration: Long): String {
        val minutes =
            TimeUnit.MILLISECONDS.toMinutes(duration).toString()
        var seconds = (
                TimeUnit.MILLISECONDS.toSeconds(duration) % 60).toString()
        if (seconds.length == 1) seconds = "0$seconds"
        return "${minutes}:${seconds}"
    }

    private fun requestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        else
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
    }


    private fun showSnackBarGrantNeeded() {
        Snackbar.make(
            binding.mainActivitySnackBar,
            "位置情報を記録するには録音機能及び、位置情報の取得を許可して下さい",
            Snackbar.LENGTH_SHORT
        ).show()
    }

}