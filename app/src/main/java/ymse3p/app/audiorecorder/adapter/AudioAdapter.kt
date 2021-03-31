package ymse3p.app.audiorecorder.adapter

import android.app.Application
import android.media.session.PlaybackState
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ymse3p.app.audiorecorder.R
import ymse3p.app.audiorecorder.data.database.entities.AudioEntity
import ymse3p.app.audiorecorder.databinding.AudioRowLayoutBinding
import ymse3p.app.audiorecorder.util.AudioDiffUtil
import ymse3p.app.audiorecorder.util.Constants.Companion.MEDIA_METADATA_QUEUE
import ymse3p.app.audiorecorder.viewmodels.MainViewModel
import ymse3p.app.audiorecorder.viewmodels.playbackViewModel.PlayBackViewModel
import java.io.File
import kotlin.coroutines.CoroutineContext

class AudioAdapter(
    private val mainViewModel: MainViewModel,
    private val playBackViewModel: PlayBackViewModel,
    private val requireActivity: FragmentActivity
) : RecyclerView.Adapter<AudioAdapter.MyViewHolder>(),
    ActionMode.Callback, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = playBackViewModel.viewModelScope.coroutineContext


    private var audio = emptyList<AudioEntity>()

    /** 生成されたViewHolderを保持 */
    private val viewHolders = mutableListOf<MyViewHolder>()

    /** Contextual Action Mode */
    private lateinit var mActionMode: ActionMode
    private var multiSelection = false
    private var selectedAudioList = arrayListOf<AudioEntity>()

    class MyViewHolder(
        val binding: AudioRowLayoutBinding,
        private val playBackViewModel: PlayBackViewModel,
        var currentPosition: Int? = null,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(audioEntity: AudioEntity, position: Int) {
            currentPosition = position

            /** 再生ボタンのクリックリスナー設定　*/
            binding.playFloatButton.setOnClickListener {
                val state = playBackViewModel.playbackState.replayCache.firstOrNull()?.state
                val playingId = playBackViewModel.metadata.replayCache.firstOrNull()
                    ?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)?.toInt()

                if (state == PlaybackState.STATE_PLAYING && playingId == audioEntity.id) {
                    playBackViewModel.pause()
                } else {
                    playBackViewModel.viewModelScope.launch {
                        currentPosition?.let { playBackViewModel.skipToQueueItem(it.toLong()) }
                        cancel()
                    }
                }
            }

            /** バインド時に、「現在再生中の曲」と「バインドされた音声ID」が一致しているかどうか確認
             　　一致している場合は、一時停止アイコンに切り替える */
            val state = playBackViewModel.playbackState.replayCache.firstOrNull()?.state
            if (state != PlaybackStateCompat.STATE_PLAYING) {
                binding.playFloatButton.setImageResource(R.drawable.ic_baseline_play_arrow_24)
            } else {
                val playingId = playBackViewModel.metadata.replayCache.firstOrNull()
                    ?.getLong(MEDIA_METADATA_QUEUE)
                if (currentPosition == playingId?.toInt())
                    binding.playFloatButton.setImageResource(R.drawable.ic_baseline_pause_24)
                else
                    binding.playFloatButton.setImageResource(R.drawable.ic_baseline_play_arrow_24)
            }

            binding.audioEntity = audioEntity
            binding.executePendingBindings()
        }

        companion object {
            fun factory(
                parent: ViewGroup,
                playBackViewModel: PlayBackViewModel,
            ): MyViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = AudioRowLayoutBinding.inflate(layoutInflater, parent, false)
                return MyViewHolder(binding, playBackViewModel)
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val viewHolder = MyViewHolder.factory(parent, playBackViewModel)
        viewHolders.add(viewHolder)
        return viewHolder
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentAudio = audio[position]
        holder.bind(currentAudio, position)

        saveItemStateOnScroll(currentAudio, holder)

        holder.binding.audioRowLayout.setOnClickListener {
            if (multiSelection) {
                applySelection(holder, currentAudio)
            }
        }
        holder.binding.audioRowLayout.setOnLongClickListener {
            if (!multiSelection) {
                multiSelection = true
                requireActivity.startActionMode(this)
                applySelection(holder, currentAudio)
                true

            } else {
                applySelection(holder, currentAudio)
                true
            }
        }
    }

    override fun getItemCount(): Int {
        return audio.size
    }

    fun setData(newData: List<AudioEntity>) {
        val audioDiffUtil = AudioDiffUtil(audio, newData)
        val diffUtilResult = DiffUtil.calculateDiff(audioDiffUtil)
        audio = newData
        diffUtilResult.dispatchUpdatesTo(this)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        /** 再生音源が変更されたら、すべてのViewHolderに対してアイコン変更有無確認を実行する */
        launch {
            playBackViewModel.metadata.collect { metadata ->
                viewHolders.forEach { viewHolder -> changePlaybackIcon(metadata, viewHolder) }
            }
        }
        launch {
            playBackViewModel.playbackState.collect { playbackState ->
                viewHolders.forEach { viewHolder -> changePlaybackIcon(playbackState, viewHolder) }
            }
        }
    }

    private fun changePlaybackIcon(metadata: MediaMetadataCompat?, viewHolder: MyViewHolder) {
        val playingId = metadata?.getLong(MEDIA_METADATA_QUEUE)?.toInt()
        viewHolder.binding.run {
            if (viewHolder.currentPosition == playingId)
                playFloatButton.setImageResource(R.drawable.ic_baseline_pause_24)
            else
                playFloatButton.setImageResource(R.drawable.ic_baseline_play_arrow_24)
        }
    }

    private fun changePlaybackIcon(playbackState: PlaybackStateCompat?, viewHolder: MyViewHolder) {
        viewHolder.binding.run {
            if (playbackState?.state != PlaybackStateCompat.STATE_PLAYING) {
                playFloatButton.setImageResource(R.drawable.ic_baseline_play_arrow_24)
            } else {
                val playingId =
                    playBackViewModel.metadata.replayCache.firstOrNull()
                        ?.getLong(MEDIA_METADATA_QUEUE)
                if (viewHolder.currentPosition == playingId?.toInt())
                    playFloatButton.setImageResource(R.drawable.ic_baseline_pause_24)
                else
                    playFloatButton.setImageResource(R.drawable.ic_baseline_play_arrow_24)
            }
        }
    }


    /** Contextual Action Mode */
    private fun saveItemStateOnScroll(currentAudio: AudioEntity, holder: MyViewHolder) {
        if (selectedAudioList.contains(currentAudio)) {
            changeAudioRowStyle(holder, R.color.transparent, R.color.colorPrimary)
        } else {
            changeAudioRowStyle(holder, R.color.transparent, R.color.strokeColor)
        }
    }

    override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
        actionMode?.let {
            actionMode.menuInflater.inflate(R.menu.audio_list_contextual_menu, menu)
            mActionMode = actionMode
            applyStatusBarColor(R.color.contextualStatusBarColor)
            return true
        }
        return false
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return true
    }

    override fun onActionItemClicked(actionMode: ActionMode?, menu: MenuItem?): Boolean {
        if (menu?.itemId == R.id.delete_audio_menu) {
            val currentPlayId =
                playBackViewModel.metadata.replayCache.firstOrNull()?.description?.mediaId?.toInt()
            val selectedAudioIdList =
                List(selectedAudioList.size) { i -> selectedAudioList[i].id }
            if (selectedAudioIdList.contains(currentPlayId))
                playBackViewModel.stop()

            selectedAudioList.forEach { audioEntity ->
                val deleteFilePath = audioEntity.audioUri.lastPathSegment
                if (deleteFilePath !== null)
                    File(
                        mainViewModel.getApplication<Application>().filesDir,
                        deleteFilePath
                    ).delete()
                mainViewModel.deleteAudio(audioEntity)
            }
        }
        showSnackBar("${selectedAudioList.size}個削除されました")
        multiSelection = false
        selectedAudioList.clear()
        actionMode?.finish()
        return true
    }

    override fun onDestroyActionMode(actionMode: ActionMode?) {
        viewHolders.forEach { holder ->
            changeAudioRowStyle(holder, R.color.transparent, R.color.strokeColor)
        }
        multiSelection = false
        selectedAudioList.clear()
        applyStatusBarColor(R.color.statusBarColor)
    }

    private fun applyStatusBarColor(color: Int) {
        requireActivity.window.statusBarColor =
            ContextCompat.getColor(requireActivity, color)
    }

    private fun applySelection(holder: MyViewHolder, currentAudio: AudioEntity) {
        if (selectedAudioList.contains(currentAudio)) {
            selectedAudioList.remove(currentAudio)
            changeAudioRowStyle(holder, R.color.transparent, R.color.strokeColor)
            applyActionModeTitle()
        } else {
            selectedAudioList.add(currentAudio)
            changeAudioRowStyle(holder, R.color.transparent, R.color.colorPrimary)
            applyActionModeTitle()
        }
    }

    private fun changeAudioRowStyle(
        holder: MyViewHolder,
        backgroundColor: Int, strokeColor: Int
    ) {
        holder.binding.audioRowLayout.setBackgroundColor(
            ContextCompat.getColor(requireActivity, backgroundColor)
        )
        holder.binding.rowCardView.strokeColor =
            ContextCompat.getColor(requireActivity, strokeColor)

    }

    private fun showSnackBar(message: String) {
        Snackbar.make(
            requireActivity.window.decorView.findViewById(R.id.main_activity_snack_bar),
            message,
            Snackbar.LENGTH_SHORT
        ).setAction("OK") {}.show()
    }


    private fun applyActionModeTitle() {
        when (selectedAudioList.size) {
            0 -> {
                mActionMode.finish()
                multiSelection = false
            }
            else -> {
                mActionMode.title = "${selectedAudioList.size}個選択中"
            }
        }
    }

    fun clearContextualActionMode() {
        if (this::mActionMode.isInitialized) {
            mActionMode.finish()
        }
    }
}
