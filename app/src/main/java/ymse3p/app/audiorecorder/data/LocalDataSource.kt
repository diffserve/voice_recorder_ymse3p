package ymse3p.app.audiorecorder.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ymse3p.app.audiorecorder.data.database.AudioDao
import ymse3p.app.audiorecorder.data.database.entities.AudioEntity
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class LocalDataSource @Inject constructor(
    private val audioDao: AudioDao
) {
    private lateinit var readAudioJob: Job

    private val publicAudioListFlow = MutableSharedFlow<List<AudioEntity>>(1)

    init {
        submitQuery("%%")
    }

    fun submitQuery(address: String? = null, title: String? = null) {
        GlobalScope.launch(Dispatchers.IO) {
            if (::readAudioJob.isInitialized) readAudioJob.cancel()

            readAudioJob =
                launch {
                    audioDao.searchAudio(address, title).collect { publicAudioListFlow.emit(it) }
                }
        }
    }

    fun readAudio(): Flow<List<AudioEntity>> {
        return publicAudioListFlow
    }

    private fun searchAudio(address: String): Flow<List<AudioEntity>> {
        return audioDao.searchAudio(address)
    }

    fun readAudioFromId(id: Int): Flow<AudioEntity> {
        return audioDao.readAudioFromId(id)
    }

    suspend fun insertAudio(audioEntity: AudioEntity) {
        return audioDao.insertAudio(audioEntity)
    }

    suspend fun insertAudioList(audioEntityList: List<AudioEntity>) {
        return audioDao.insertAudioList(audioEntityList)
    }

    suspend fun deleteAudio(audioEntity: AudioEntity) {
        return audioDao.deleteAudio(audioEntity)
    }

    suspend fun deleteAllSampleAudio() {
        return audioDao.deleteAllSampleAudio()
    }

    suspend fun deleteAllAudio() {
        return audioDao.deleteAllAudio()
    }

}