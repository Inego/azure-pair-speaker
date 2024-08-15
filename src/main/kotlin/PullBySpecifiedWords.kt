import edu.stanford.nlp.pipeline.CoreDocument
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import java.util.*


import java.io.File


class WordChecker(wordListFile: String) {
    private val wordSet: Set<String>
    private val pipeline: StanfordCoreNLP

    init {
        // Read words from file and convert to lowercase
        wordSet = File(wordListFile).readLines().map { it.trim().lowercase() }.toSet()

        // Set up Stanford CoreNLP pipeline
        val props = Properties().apply {
            setProperty("annotators", "tokenize,ssplit,pos,lemma")
        }
        pipeline = StanfordCoreNLP(props)
    }

    // Predicate function to check if any word from the sentence is in the word set
    val containsAnyWord: (String) -> Boolean = { sentence ->
        val document = CoreDocument(sentence)
        pipeline.annotate(document)

        document.tokens()
            .map { it.lemma().lowercase() }
            .any { it in wordSet }
    }
}



fun main() {
    val checker = WordChecker("word_list.txt")

    val tsvFileName = "pairs_EN_bkp.tsv"
    val startingPosition = 251
    val n = 300 // The number of sentences we need in list1

    // 1. Read the pairs from the file
    val pairs = readPairs(tsvFileName)

    // Lists to store the results
    val list1 = mutableListOf<TranslationPair>()
    val list2 = mutableListOf<TranslationPair>()

    // Variables for progress tracking
    var completed = 0
    var sentencesInList1 = 0

    // 2. Process pairs until we reach `n` sentences in list1
    for (i in startingPosition until pairs.size) {
        val pair = pairs[i]

        if (checker.containsAnyWord(pair.sentence)) {
            list1.add(pair)
            sentencesInList1++
        } else {
            list2.add(pair)
        }

        // Update progress
        completed++
        printProgress(sentencesInList1, n)

        // Stop processing once we've added `n` sentences to list1
        if (sentencesInList1 >= n) {
            break
        }
    }

    // Combine the lists: unchanged pairs before startingPosition + processed pairs + remaining unprocessed pairs
    val finalList = pairs.subList(0, startingPosition) + list1 + list2 + pairs.subList(startingPosition + completed, pairs.size)

    savePairsToFile("pairs_EN.tsv", finalList)
}


// Function to print a simple progress bar
fun printProgress(completed: Int, total: Int) {
    val progress = (completed.toDouble() / total) * 100
    val barLength = 50
    val filledLength = (progress / 100 * barLength).toInt()
    val bar = "=".repeat(filledLength) + " ".repeat(barLength - filledLength)
    print("\r[$bar] ${"%.2f".format(progress)}%")
    if (completed == total) {
        println()  // Move to the next line when done
    }
}
