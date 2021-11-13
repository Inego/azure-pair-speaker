import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.csv.CSVFormat
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.time.Instant
import java.util.*
import javax.sound.sampled.Clip
import javax.swing.*
import javax.swing.border.EmptyBorder


const val propertiesFileName = "app.properties"

class MainFrame(title: String) : JFrame(title), KeyListener {

    private lateinit var lastSave: Instant

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

    companion object {
        val mainFont = Font(Font.SERIF, Font.PLAIN, 30)
        val translationFont = Font(Font.SANS_SERIF, Font.ITALIC, 22)
        const val PROP_CURRENT = "current"
    }

    private lateinit var properties: Properties

    private val sentenceLabel = JLabel("Yükleniyor...").apply {
        font = mainFont
    }

    private val translationLabel = JLabel("Loading...").apply {
        font = translationFont
    }

    private var currentIdx = 0
    private var currentVoice = 0
    private lateinit var pairs: List<TranslationPair>

    private var currentClip: Clip? = null

    init {
        defaultCloseOperation = EXIT_ON_CLOSE

        focusTraversalKeysEnabled = false // To catch VK_TAB

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

        addKeyListener(this)

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                saveProperties()
            }
        })

        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }

    private fun saveProperties() {
        if (!this::properties.isInitialized) {
            return
        }
        properties.setProperty(PROP_CURRENT, currentIdx.toString())
        FileOutputStream(propertiesFileName).use { output ->
            properties.store(output, null)
        }
    }

    suspend fun setup(startupData: StartupData) {
        pairs = startupData.pairs
        currentIdx = startupData.currentIdx
        this.properties = startupData.properties
        lastSave = Instant.now()

        refreshCurrentPair()
    }

    private suspend fun refreshCurrentPair() {

        val currentPair = pairs[currentIdx]
        val sentenceText = currentPair.sentence
        sentenceLabel.text = sentenceText
        translationLabel.text = currentPair.translation

        currentClip?.stop()
        currentClip?.framePosition = 0
        currentClip = SoundCache.get(PairReading(currentIdx, currentVoice), sentenceText)
        currentClip?.start()
    }

    fun displayException(e: Exception) {
        sentenceLabel.text = "ERROR"
        translationLabel.text = e.message
    }

    override fun keyTyped(e: KeyEvent?) {
        // Do nothing
    }

    override fun keyPressed(e: KeyEvent?) {
        // Do nothing
    }

    override fun keyReleased(e: KeyEvent) {
        when (e.keyCode) {
            10 -> { // Enter to Go to next
                currentIdx++
                scope.launch { refreshCurrentPair() }
            }
            8 -> { // Backspace to Go back
                if (currentIdx > 0) {
                    currentIdx--
                    scope.launch { refreshCurrentPair() }
                }
            }
            9 -> { // Tab to Switch voice
                if (!e.isAltDown) {
                    currentVoice = 1 - currentVoice
                    scope.launch { refreshCurrentPair() }
                }
            }
            32 -> { // Space to Repeat
                currentClip?.stop()
                currentClip?.framePosition = 0
                currentClip?.start()
            }
            67 -> { // C to Copy
                val stringSelection = StringSelection(sentenceLabel.text)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(stringSelection, null)
            }
            else -> {
                println("Key released: $e")
            }
        }
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
        val startupInfo = dataDeferred.await()
        launch(Dispatchers.Swing) {
            mainFrame.setup(startupInfo)
        }.join()
    } catch (e: Exception) {
        mainFrame.displayException(e)
    }
}

class StartupData(
    val pairs: List<TranslationPair>,
    val currentIdx: Int,
    val properties: Properties
)


private fun readPairsAsync(): StartupData {

    // First, read properties
    val propertiesInputStream = FileInputStream(propertiesFileName)
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
    val current = getAndCheckProp(MainFrame.PROP_CURRENT)
    val currentIdx = current.toInt()

    SoundCache.setup(azureRegion, azureKey)

    val pairsTsvStream = FileInputStream("pairs.tsv")
    val reader = InputStreamReader(pairsTsvStream, Charsets.UTF_8)
    val pairs = CSVFormat.TDF.parse(reader).use { parsed ->
        parsed.asSequence()
            .map { TranslationPair(it[0], it[1]) }
            .toList()
    }

    return StartupData(pairs, currentIdx, properties)
}
