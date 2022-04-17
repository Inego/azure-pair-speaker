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
            config.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Riff48Khz16BitMonoPcm)

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

            synth.SpeakText(text).use { result ->
                if (result.reason != ResultReason.SynthesizingAudioCompleted) {

                    var reason = result.reason.toString()

                    if (result.reason == ResultReason.Canceled) {
                        reason = SpeechSynthesisCancellationDetails.fromResult(result).toString()
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
        }
            .await()
}
