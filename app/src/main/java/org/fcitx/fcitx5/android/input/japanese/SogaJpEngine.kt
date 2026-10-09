/*
 * SogaKey: Japanese kana-kanji conversion, powered by the converter from Sumire
 * (https://github.com/KazumaProject/JapaneseKeyboard, MIT) and the Mozc dictionary.
 */
package org.fcitx.fcitx5.android.input.japanese

import android.content.Context
import androidx.room.Room
import com.kazumaproject.markdownhelperkeyboard.SogaJpDatabase
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.graph.GraphBuilder
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcNodeAttributeTableReader
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenterDataReader
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileRole
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideValidator
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import timber.log.Timber

object SogaJpEngine {

    private class Triple(
        val tango: LOUDS,
        val yomi: LOUDSWithTermId,
        val token: TokenArray,
        val lbsYomi: SuccinctBitVector,
        val leafYomi: SuccinctBitVector,
        val tokenIdx: SuccinctBitVector,
        val lbsTango: SuccinctBitVector,
    )

    @Volatile
    private var engine: KanaKanjiEngine? = null
    private var learnRepo: LearnRepository? = null
    private var userRepo: UserDictionaryRepository? = null

    fun isReady(): Boolean = engine != null

    /** Loads the dictionaries (slow, call from a background thread). */
    @Synchronized
    fun ensure(context: Context): KanaKanjiEngine {
        engine?.let { return it }
        val app = context.applicationContext
        val store = DictionaryOverrideStore(app, DictionaryOverrideValidator())
        val reader = DictionaryBinaryReader(DictionarySourceResolver(app, store), store)

        fun triple(category: DictionaryCategory): Triple {
            val specs = DictionaryFileSpecs.forCategory(category)
            val tangoKey = specs.first { it.role == DictionaryFileRole.TANGO }.key
            val yomiKey = specs.first { it.role == DictionaryFileRole.YOMI }.key
            val tokenKey = specs.first { it.role == DictionaryFileRole.TOKEN }.key
            val tango = reader.loadLouds(tangoKey)
            val yomi = reader.loadLoudsWithTermId(yomiKey)
            val token = reader.loadTokenArray(tokenKey)
            return Triple(
                tango, yomi, token,
                reader.loadYomiLbsIndex(yomiKey, yomi),
                reader.loadYomiLeafIndex(yomiKey, yomi),
                reader.loadTokenIndex(tokenKey, token),
                reader.loadTangoLbsIndex(tangoKey, tango),
            )
        }

        val connection: ConnectionMatrix.CostTable =
            reader.loadConnectionMatrix(DictionaryFileKey.CONNECTION_ID)
        val sys = triple(DictionaryCategory.SYSTEM)
        val kanji = triple(DictionaryCategory.SINGLE_KANJI)
        val emoji = triple(DictionaryCategory.EMOJI)
        val emoticon = triple(DictionaryCategory.EMOTICON)
        val symbol = triple(DictionaryCategory.SYMBOL)
        val reading = triple(DictionaryCategory.READING_CORRECTION)
        val kotowaza = triple(DictionaryCategory.KOTOWAZA)

        val segmenter = runCatching {
            app.assets.open("mozc/segmenter.dat").use { MozcSegmenter(MozcSegmenterDataReader().read(it)) }
        }.onFailure { Timber.w(it, "mozc segmenter missing") }.getOrNull()
        val attributes = runCatching {
            app.assets.open("mozc/node_attribute_by_lid.dat").use { MozcNodeAttributeTableReader().read(it) }
        }.onFailure { Timber.w(it, "mozc node attributes missing") }.getOrNull()

        val built = KanaKanjiEngine()
        built.buildEngine(
            graphBuilder = GraphBuilder(),
            findPath = FindPath(),
            connectionMatrix = connection,
            systemTangoTrie = sys.tango, systemYomiTrie = sys.yomi, systemTokenArray = sys.token,
            systemSuccinctBitVectorLBSYomi = sys.lbsYomi, systemSuccinctBitVectorIsLeafYomi = sys.leafYomi,
            systemSuccinctBitVectorTokenArray = sys.tokenIdx, systemSuccinctBitVectorTangoLBS = sys.lbsTango,
            singleKanjiTangoTrie = kanji.tango, singleKanjiYomiTrie = kanji.yomi, singleKanjiTokenArray = kanji.token,
            singleKanjiSuccinctBitVectorLBSYomi = kanji.lbsYomi, singleKanjiSuccinctBitVectorIsLeafYomi = kanji.leafYomi,
            singleKanjiSuccinctBitVectorTokenArray = kanji.tokenIdx, singleKanjiSuccinctBitVectorTangoLBS = kanji.lbsTango,
            emojiTangoTrie = emoji.tango, emojiYomiTrie = emoji.yomi, emojiTokenArray = emoji.token,
            emojiSuccinctBitVectorLBSYomi = emoji.lbsYomi, emojiSuccinctBitVectorIsLeafYomi = emoji.leafYomi,
            emojiSuccinctBitVectorTokenArray = emoji.tokenIdx, emojiSuccinctBitVectorTangoLBS = emoji.lbsTango,
            emoticonTangoTrie = emoticon.tango, emoticonYomiTrie = emoticon.yomi, emoticonTokenArray = emoticon.token,
            emoticonSuccinctBitVectorLBSYomi = emoticon.lbsYomi, emoticonSuccinctBitVectorIsLeafYomi = emoticon.leafYomi,
            emoticonSuccinctBitVectorTokenArray = emoticon.tokenIdx, emoticonSuccinctBitVectorTangoLBS = emoticon.lbsTango,
            symbolTangoTrie = symbol.tango, symbolYomiTrie = symbol.yomi, symbolTokenArray = symbol.token,
            symbolSuccinctBitVectorLBSYomi = symbol.lbsYomi, symbolSuccinctBitVectorIsLeafYomi = symbol.leafYomi,
            symbolSuccinctBitVectorTokenArray = symbol.tokenIdx, symbolSuccinctBitVectorTangoLBS = symbol.lbsTango,
            readingCorrectionTangoTrie = reading.tango, readingCorrectionYomiTrie = reading.yomi,
            readingCorrectionTokenArray = reading.token,
            readingCorrectionSuccinctBitVectorLBSYomi = reading.lbsYomi,
            readingCorrectionSuccinctBitVectorIsLeafYomi = reading.leafYomi,
            readingCorrectionSuccinctBitVectorTokenArray = reading.tokenIdx,
            readingCorrectionSuccinctBitVectorTangoLBS = reading.lbsTango,
            kotowazaTangoTrie = kotowaza.tango, kotowazaYomiTrie = kotowaza.yomi, kotowazaTokenArray = kotowaza.token,
            kotowazaSuccinctBitVectorLBSYomi = kotowaza.lbsYomi, kotowazaSuccinctBitVectorIsLeafYomi = kotowaza.leafYomi,
            kotowazaSuccinctBitVectorTokenArray = kotowaza.tokenIdx, kotowazaSuccinctBitVectorTangoLBS = kotowaza.lbsTango,
            engineEngine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) },
            mozcSegmenter = segmenter,
            mozcNodeAttributeTable = attributes,
            mozcDictionaryActive = segmenter != null && attributes != null,
        )
        built.setDictionaryBinaryReader(reader)
        // person / places / wiki / neologd / web dictionaries
        runCatching { built.initializeOptionalDictionaryStateFromCurrentSources() }
            .onFailure { Timber.w(it, "optional dictionaries failed") }

        val db = Room.databaseBuilder(app, SogaJpDatabase::class.java, "sogakey-japanese.db")
            .fallbackToDestructiveMigration(true)
            .build()
        learnRepo = LearnRepository(db.learnDao())
        userRepo = UserDictionaryRepository(db.userWordDao())
        engine = built
        return built
    }

    suspend fun learn(entries: List<com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity>) {
        learnRepo?.upsertLearnedDataBatch(entries, allowJapaneseWithSymbolsAndNumbers = true)
    }

    /** Conversion candidates for a hiragana reading, best first. */
    suspend fun convert(context: Context, reading: String): List<Candidate> {
        val e = ensure(context)
        return e.getCandidatesOriginal(
            input = reading,
            n = 40,
            mozcUtPersonName = true,
            mozcUTPlaces = true,
            mozcUTWiki = true,
            mozcUTNeologd = true,
            mozcUTWeb = true,
            userDictionaryRepository = userRepo!!,
            learnRepository = learnRepo,
            isOmissionSearchEnable = false,
            typoCorrectionOffsetScore = 1900,
            omissionSearchOffsetScore = 1900,
        )
    }
}
