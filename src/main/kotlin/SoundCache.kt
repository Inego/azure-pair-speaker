import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import kotlinx.coroutines.future.await
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

data class PairReading(
    val idx: Int,
    val voice: Int
)


object SoundCache {

    lateinit var synth0: SpeechSynthesizer
    lateinit var synth1: SpeechSynthesizer

    private val cache: AsyncCache<PairReading, Clip> = Caffeine.newBuilder().buildAsync()

    fun setup(serviceRegion: String, speechSubscriptionKey: String) {

        check(!this::synth0.isInitialized) { "Cannot setup speech more than once" }

        SpeechConfig.fromSubscription(speechSubscriptionKey, serviceRegion).use { config ->
            config.speechSynthesisLanguage = "tr-TR"

            config.speechSynthesisVoiceName = "tr-TR-AhmetNeural"
            synth0 = SpeechSynthesizer(config, null)

            config.speechSynthesisVoiceName = "tr-TR-EmelNeural"
            synth1 = SpeechSynthesizer(config, null)
        }
    }

    suspend fun get(pairReading: PairReading, text: String): Clip =
        cache.get(pairReading) { _ ->

            val synth = when (pairReading.voice) {
                0 -> synth0
                1 -> synth1
                else -> throw IllegalArgumentException("Unknown voice code: ${pairReading.voice}")
            }

            val result: SpeechSynthesisResult = synth.SpeakText(text)

            if (result.reason != ResultReason.SynthesizingAudioCompleted) {
                TODO("Handle error")
            }

            val audioData = result.audioData
            val byteArrayInputStream = ByteArrayInputStream(audioData)
            val audioInputStream = AudioSystem.getAudioInputStream(byteArrayInputStream)

            val clip = AudioSystem.getClip()
            clip.open(audioInputStream)

            clip
        }
            .await()
}