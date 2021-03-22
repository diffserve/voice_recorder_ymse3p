package ymse3p.app.audiorecorder.viewmodels

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ymse3p.app.audiorecorder.data.Repository
import ymse3p.app.audiorecorder.data.database.DataStoreRepository
import ymse3p.app.audiorecorder.data.database.entities.AudioEntity
import ymse3p.app.audiorecorder.util.CannotSaveAudioException
import ymse3p.app.audiorecorder.util.CannotStartRecordingException
import java.io.File
import java.io.IOException
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: Repository,
    application: Application,
    private val audioRecorder: MediaRecorder,
    private val dataStoreRepository: DataStoreRepository
) : AndroidViewModel(application) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val readRecordedAudioId = dataStoreRepository.readRecordedAudioId
    private var currentOutputFileName: File? = null

    val requestPlayNumber = MutableSharedFlow<Int>()

    suspend fun startRecording() {
        try {
            audioRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                this@MainViewModel.readRecordedAudioId.first { audioId ->
                    currentOutputFileName = File(
                        getApplication<Application>().filesDir,
                        audioId.toString(16) + " default_name.mp4"
                    )
                    setOutputFile(currentOutputFileName.toString())
                    Log.d("AudioNumber", audioId.toString(16))
                    true
                }
                withContext(Dispatchers.IO) {
                    prepare()
                }
                start()
            }
            _isRecording.value = true
            dataStoreRepository.incrementRecordedAudioId()
        } catch (e: IllegalStateException) {
            Log.e("MediaRecorder", e.message.orEmpty())
            throw CannotStartRecordingException("IllegalStateException occurred")

        } catch (e: IOException) {
            Log.e("MediaRecorder", e.message.orEmpty())
            throw CannotStartRecordingException("IOExceptionException occurred")
        }
    }

    fun stopRecording() {
        audioRecorder.apply {
            try {
                stop()
                _isRecording.value = false
            } catch (e: IllegalStateException) {
                Log.e("MediaRecorder", e.message.orEmpty())
                reset()
                _isRecording.value = false
                throw CannotSaveAudioException("IllegalStateException occurred")
            } catch (e: RuntimeException) {
                Log.e("MediaRecorder", e.message.orEmpty())
                File(getApplication<Application>().filesDir, "default_name.mp4").delete()
                reset()
                _isRecording.value = false
                throw CannotSaveAudioException("RuntimeException occurred")
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()

    }


    /** ROOM DATABASE */
    val readAudio: LiveData<List<AudioEntity>> =
        repository.localDataSource.readAudio().asLiveData()


    fun insertAudio(
        audioTitle: String,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val audioCreateDate = Calendar.getInstance()
        val audioDuration = try {
            MediaMetadataRetriever().run {
                setDataSource(currentOutputFileName?.path)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            }?.toInt() ?: 0
        } catch (e: NumberFormatException) {
            Log.e("MediaMetadataRetriever", e.message.orEmpty())
        }

        val audioEntity =
            AudioEntity.createAudioEntity(
                Uri.fromFile(currentOutputFileName),
                audioCreateDate,
                audioTitle,
                audioDuration
            )
        repository.localDataSource.insertAudio(audioEntity)
        currentOutputFileName = null
    }

    private fun deleteAudio(audioEntity: AudioEntity) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.localDataSource.deleteAudio(audioEntity)
        }

    private fun deleteAllAudio() =
        viewModelScope.launch(Dispatchers.IO) {
            repository.localDataSource.deleteAllAudio()
        }
}