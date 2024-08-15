import org.apache.commons.csv.CSVFormat
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Converts the original file from Tatoeba into the format used by the program
 */

fun main() {
    // Open a file input stream for the "pairs.tsv" file
    val pairsTsvStream = FileInputStream("original.tsv")
    // Wrap the file input stream with an input stream reader, specifying UTF-8 encoding
    val reader = InputStreamReader(pairsTsvStream, Charsets.UTF_8)

    // Configure the CSV parser for TSV format
    val csvFormat = CSVFormat.TDF.builder()
        .setQuote(null)
        .build()

    // Parse the TSV file and extract data from the specified columns
    val pairs = csvFormat.parse(reader).use { parsed ->
        parsed.asSequence()
            .map { TranslationPair(it[3], it[1]) }
            .toList()
    }

    // Shuffle the list of translation pairs
    val shuffledPairs = pairs.shuffled()

    savePairsToFile("pairs.tsv", shuffledPairs)
}

