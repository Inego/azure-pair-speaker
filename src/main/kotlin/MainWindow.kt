import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.csv.CSVFormat
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import javax.sound.sampled.Clip
import javax.swing.*
import javax.swing.border.EmptyBorder


enum class Mode {
    LISTEN, SPEAK
}

const val PROP_CURRENT = "current"
const val PROP_PAIRS_FILE = "pairs_file"
const val PROP_MODE = "mode"
const val PROP_VOICES = "voices"

private fun getVoicesFromProperties(props: Properties) =
    props.getProperty(PROP_VOICES)?.split(",")?.map { it.trim() } ?: emptyList()


class MainFrame(title: String, val propertiesPath: String) : JFrame(title), KeyListener {

    private lateinit var lastSave: Instant

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

    companion object {
        val smallFont = Font(Font.SANS_SERIF, Font.ITALIC, 12)
        val mainFont = Font(Font.SERIF, Font.PLAIN, 30)
        val translationFont = Font(Font.SANS_SERIF, Font.ITALIC, 22)
    }

    private lateinit var properties: Properties

    private val numberLabel = JLabel("[...]").apply {
        font = smallFont
    }

    private val sentenceLabel = JLabel("Yükleniyor...").apply {
        font = mainFont
    }

    private val translationLabel = JLabel("Loading...").apply {
        font = translationFont
    }

    private var currentIdx = 0
    private var currentVoiceIdx = 0
    private var mode = Mode.LISTEN

    private var shown = false

    private lateinit var pairs: List<TranslationPair>
    private lateinit var voices: List<String>


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

        panel.add(numberLabel)

        panel.add(Box.createVerticalStrut(10)) // Spacer

        panel.add(sentenceLabel)

        panel.add(Box.createVerticalStrut(10))

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
        properties.setProperty(PROP_MODE, mode.name)
        FileOutputStream(propertiesPath).use { output ->
            properties.store(output, null)
        }
    }

    suspend fun setup(startupData: StartupData) {
        pairs = startupData.pairs
        currentIdx = startupData.currentIdx
        this.properties = startupData.properties
        mode = Mode.valueOf(properties.getProperty(PROP_MODE, Mode.LISTEN.name))
        voices = getVoicesFromProperties(properties)
        require(voices.isNotEmpty()) { "At least one voice must be specified in the 'voices' property" }
        lastSave = Instant.now()

        refreshCurrentPair()
    }


    private suspend fun refreshCurrentPair() {

        val currentPair = pairs[currentIdx]
        val sentenceText = currentPair.sentence
        val translationText = currentPair.translation

        numberLabel.text = "[$currentIdx] - Mode: $mode - Voice: ${voices[currentVoiceIdx]}"

        when (mode) {
            Mode.LISTEN -> {
                if (shown) {
                    sentenceLabel.text = sentenceText
                    translationLabel.text = translationText
                } else {
                    sentenceLabel.text = "..."
                    translationLabel.text = ""
                    playCurrentClip()
                }
            }
            Mode.SPEAK -> {
                if (shown) {
                    sentenceLabel.text = sentenceText
                    translationLabel.text = translationText
                } else {
                    sentenceLabel.text = ""
                    translationLabel.text = translationText
                }
            }
        }
    }

    private suspend fun playCurrentClip() {

        val currentPair = pairs[currentIdx]
        val sentenceText = currentPair.sentence

        currentClip?.stop()
        currentClip?.framePosition = 0
        currentClip = SoundCache.get(PairReading(currentIdx, voices[currentVoiceIdx]), sentenceText)
        currentClip?.start()
    }

    fun displayException(e: Exception) {
        sentenceLabel.text = "ERROR"
        translationLabel.text = e.message

        // Print the full stack trace to the console
        e.printStackTrace()
    }

    override fun keyTyped(e: KeyEvent?) {
        // Do nothing
    }

    override fun keyPressed(e: KeyEvent?) {
        // Do nothing
    }

    override fun keyReleased(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_ENTER -> next()
            KeyEvent.VK_BACK_SPACE -> {
                if (currentIdx > 0) {
                    currentIdx--
                    shown = false
                    scope.launch { refreshCurrentPair() }
                }
            }
            KeyEvent.VK_TAB -> {
                if (!e.isAltDown) {
                    currentVoiceIdx = (currentVoiceIdx + 1) % voices.size
                    scope.launch { playCurrentClip() }
                }
            }
            KeyEvent.VK_SPACE -> {
                scope.launch { playCurrentClip() }
            }
            KeyEvent.VK_C -> {
                val currentPair = pairs[currentIdx]
                val textToCopy = if (e.isShiftDown)
                    currentPair.translation
                else
                    currentPair.sentence

                val stringSelection = StringSelection(textToCopy)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(stringSelection, null)
            }
            KeyEvent.VK_N -> next(true)
            KeyEvent.VK_S -> {
                val now = LocalDateTime.now()
                saveProperties()
                numberLabel.text = "[$currentIdx] - Saved at $now"
            }
            KeyEvent.VK_F1 -> {
                mode = Mode.LISTEN
                shown = false
                scope.launch { refreshCurrentPair() }
            }
            KeyEvent.VK_F2 -> {
                mode = Mode.SPEAK
                shown = false
                scope.launch { refreshCurrentPair() }
            }
            else -> {
                println("Key released: $e")
            }
        }
    }

    private fun next(switch: Boolean = false) {
        shown = if (shown) {
            currentIdx++
            if (switch) {
                currentVoiceIdx++
                if (currentVoiceIdx == voices.size) {
                    currentVoiceIdx = 0
                }
            }
            false
        } else {
            true
        }
        scope.launch {
            refreshCurrentPair()
        }
    }
}


class TranslationPair(
    val sentence: String,
    val translation: String
)


@DelicateCoroutinesApi
fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        println("Error: Please provide a properties path as an argument.")
        return@runBlocking
    }

    val propertiesPath = args[0]

    val dataDeferred = GlobalScope.async(Dispatchers.IO) {
        readPairsAsync(propertiesPath)
    }

    val mainFrame = withContext(Dispatchers.Swing) {
        MainFrame("Azure Pair Speaker", propertiesPath)
    }

    try {
        val startupInfo = dataDeferred.await()
        launch(Dispatchers.Swing) {
            mainFrame.setup(startupInfo)
        }.join()
    } catch (e: Exception) {
        mainFrame.displayException(e)
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            SoundCache.cleanup()
        }
    })
}

class StartupData(
    val pairs: List<TranslationPair>,
    val currentIdx: Int,
    val properties: Properties
)


private fun readPairsAsync(propertiesPath: String): StartupData {

    // First, read properties
    val propertiesInputStream = FileInputStream(propertiesPath)
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
    val current = getAndCheckProp(PROP_CURRENT)
    val currentIdx = current.toInt()
    val pairsPath = getAndCheckProp(PROP_PAIRS_FILE)
    val voices = getVoicesFromProperties(properties)

    require(voices.isNotEmpty()) { "At least one voice must be specified in the 'voices' property" }

    SoundCache.setup(azureRegion, azureKey, voices)

    val pairsTsvStream = FileInputStream(pairsPath)
    val reader = InputStreamReader(pairsTsvStream, Charsets.UTF_8)
    val pairs = CSVFormat.TDF.builder()
        .setQuote(null)
        .build()
        .parse(reader)
        .use { parsed ->
            parsed.asSequence()
                .map { TranslationPair(it[0], it[1]) }
                .toList()
        }

    return StartupData(pairs, currentIdx, properties)
}
