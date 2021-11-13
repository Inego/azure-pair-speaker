import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.csv.CSVFormat
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.awt.Toolkit
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder


const val KEY_REPEAT = "repeat"


class MainFrame(title: String) : JFrame(title) {

    companion object {
        val mainFont = Font(Font.SERIF, Font.PLAIN, 30)
        val translationFont = Font(Font.SANS_SERIF, Font.ITALIC, 22)
    }

    private val sentenceLabel = JLabel("Yükleniyor...").apply {
        font = mainFont
    }

    private val translationLabel = JLabel("Loading...").apply {
        font = translationFont
    }

    private var currentIdx = 0
    private var currentVoice = 0
    private lateinit var pairs: List<TranslationPair>

    init {
        defaultCloseOperation = EXIT_ON_CLOSE

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(Insets(10, 10, 10, 10))
        }

        add(panel)

        val labelWidth = Toolkit.getDefaultToolkit().screenSize.width * 95 / 100
        sentenceLabel.preferredSize = Dimension(labelWidth, 40)

        panel.add(sentenceLabel)

        panel.add(Box.createVerticalStrut(10)) // Spacer

        panel.add(translationLabel)

        panel.keyStrokeAction(' ', KEY_REPEAT) {
            sentenceLabel.text += "!"
        }

        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }

    suspend fun setLoadedPairs(loadedPairs: List<TranslationPair>) {
        pairs = loadedPairs
        refreshCurrentPair()
    }

    private suspend fun refreshCurrentPair() {
        val currentPair = pairs[currentIdx]
        val sentenceText = currentPair.sentence
        sentenceLabel.text = sentenceText
        translationLabel.text = currentPair.translation

        val clip = SoundCache.get(PairReading(currentIdx, currentVoice), sentenceText)
        clip.loop(10)
    }

    fun displayException(e: Exception) {
        sentenceLabel.text = "ERROR"
        translationLabel.text = e.message
    }
}


class TranslationPair(
    val sentence: String,
    val translation: String
)


@DelicateCoroutinesApi
fun main() = runBlocking {

    val dataDeferred = GlobalScope.async(Dispatchers.IO) {
        readPairsAsync()
    }

    val mainFrame = withContext(Dispatchers.Swing) {
        MainFrame("Azure Pair Speaker")
    }

    try {
        mainFrame.setLoadedPairs(dataDeferred.await())
    } catch (e: Exception) {
        mainFrame.displayException(e)
    }
}


private fun readPairsAsync(): List<TranslationPair> {

    // First, read properties
    val propertiesInputStream = FileInputStream("app.properties")
    val properties = Properties()
    properties.load(propertiesInputStream)

    fun getAndCheckProp(name: String): String {
        val result = properties.getProperty(name)
        check(!result.isNullOrEmpty()) {
            "Property not specified: $name"
        }
        return result
    }

    val azureRegion = getAndCheckProp("azure.region")
    val azureKey = getAndCheckProp("azure.key")

    SoundCache.setup(azureRegion, azureKey)

    val pairsTsvStream = FileInputStream("pairs.tsv")
    val reader = InputStreamReader(pairsTsvStream, Charsets.UTF_8)
    val parsed = CSVFormat.TDF.parse(reader)
    return parsed.asSequence()
        .map { TranslationPair(it[0], it[1]) }
        .toList()
}
