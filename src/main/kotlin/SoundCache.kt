import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.microsoft.cognitiveservices.speech.*
import kotlinx.coroutines.future.await
import java.io.ByteArrayInputStream
import java.lang.IllegalStateException
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

data class PairReading(
    val idx: Int,
    val voice: String
)

object SoundCache {

    private lateinit var config: SpeechConfig
    private val synthesizers = mutableMapOf<String, SpeechSynthesizer>()

    private val cache: AsyncCache<PairReading, Clip> = Caffeine.newBuilder().buildAsync()

    fun setup(serviceRegion: String, speechSubscriptionKey: String, voices: List<String>) {
        check(synthesizers.isEmpty()) { "Cannot setup speech more than once" }

        config = SpeechConfig.fromSubscription(speechSubscriptionKey, serviceRegion).apply {
            speechSynthesisLanguage = "tr-TR"
            setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Riff48Khz16BitMonoPcm)
        }

        voices.forEach { voice ->
            config.speechSynthesisVoiceName = voice
            synthesizers[voice] = SpeechSynthesizer(config, null)
        }
    }

    suspend fun get(pairReading: PairReading, text: String): Clip =
        cache.get(pairReading) { _ ->
            val synth = synthesizers[pairReading.voice] ?: throw IllegalArgumentException("Unknown voice: ${pairReading.voice}")

            synth.SpeakText(text).use { result ->
                if (result.reason != ResultReason.SynthesizingAudioCompleted) {
                    val reason = if (result.reason == ResultReason.Canceled) {
                        SpeechSynthesisCancellationDetails.fromResult(result).toString()
                    } else {
                        result.reason.toString()
                    }
                    throw IllegalStateException("Audio problem: $reason")
                }

                val audioData = result.audioData
                val byteArrayInputStream = ByteArrayInputStream(audioData)
                val audioInputStream = AudioSystem.getAudioInputStream(byteArrayInputStream)

                val clip = AudioSystem.getClip()
                clip.open(audioInputStream)

                clip
            }
        }.await()

    fun cleanup() {
        synthesizers.values.forEach { it.close() }
        config.close()
    }
}