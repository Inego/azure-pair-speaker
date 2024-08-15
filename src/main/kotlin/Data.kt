import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStreamReader


class TranslationPair(
    val sentence: String,
    val translation: String
)


fun readPairs(pairsPath: String): List<TranslationPair> {
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
    return pairs
}


fun savePairsToFile(fileName: String, pairs: List<TranslationPair>) {
    val outputFile = File(fileName)
    val writer = FileWriter(outputFile)

    val tsvPrinter = CSVPrinter(writer, CSVFormat.TDF.builder().setQuote(null).build())

    pairs.forEach { pair ->
        tsvPrinter.printRecord(pair.sentence, pair.translation)
    }

    tsvPrinter.flush()
    tsvPrinter.close()

    println("Saved to $fileName")
}
