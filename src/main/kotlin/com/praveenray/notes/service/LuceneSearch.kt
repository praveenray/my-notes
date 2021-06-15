package com.praveenray.notes.service

import com.praveenray.notes.lib.Failure
import com.praveenray.notes.lib.Success
import com.praveenray.notes.lib.SystemUtils
import com.praveenray.notes.lib.Try
import com.praveenray.notes.models.Note
import com.praveenray.notes.models.NoteAttachment
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.MMapDirectory
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class LuceneSearch @Inject constructor(
    val systemUtils: SystemUtils,
    val noteDB: NoteDB,
    @Named("app.data.directory") private val dataDirectory: String,
    @Named("app.lucene.index.recreate") private val recreateLuceneIndex: Boolean,
    @Named("app.lucene.search.phraseSlop") private val phraseSlop: Int,
    @Named("app.max_attachments_count") private val maxAttachmentsCount: Int,
) {
    companion object {
        private val DESC_FIELD = "description"
        private val TAG_FIELD = "tag"
        private val ID_FIELD = "id"
        private val ATTACHMENT_COUNT_FIELD = "attach-count"
        private val ATTACHMENT_FILE_FIELD_PREFIX = "attach-file"
        private val ATTACHMENT_NOTE_FIELD_PREFIX = "attach-note"
        private val CREATED_DATE_FIELD = "createDate"
        private val MODIFIED_DATE_FIELD = "modifiedDate"
        private val logger = LoggerFactory.getLogger(LuceneSearch::class.java)
    }
    var noteLuceneIndex: Directory? = null
    
    init {
        val dataDirectoryPath = Paths.get(dataDirectory)
        val dataDir = if (dataDirectoryPath.isAbsolute) dataDirectoryPath else systemUtils.currentDir().resolve(dataDirectory)
        val indexDirectory = dataDir.resolve("lucene-index")
        val recreating = if (recreateLuceneIndex) {
            logger.warn("Deleting index Directory $indexDirectory. To Disable set app.lucene.index.recreate=false")
            if (Files.exists(indexDirectory)) {
                indexDirectory.toFile().deleteRecursively()
            }
            val attachmentsDir = dataDir.resolve(noteDB.attachmentsDirName)
            if (Files.exists(attachmentsDir)) {
                attachmentsDir.toFile().deleteRecursively()
            }
            true
        } else false
        if (!Files.exists(indexDirectory)) {
            Files.createDirectories(indexDirectory)
        }
        noteLuceneIndex = if (recreating) {
            val notes = noteDB.getNotesFromDB()
            val dir = createIndexOfNotes(notes, indexDirectory)
            logger.info("Indexed ${notes.size} Notes from notes.json at $noteLuceneIndex")
            dir
        } else openIndexDirectory(indexDirectory)
    }
    
    fun uniqueTags(): List<String> {
        val query = WildcardQuery(Term("tag", "*"))
        return when (val tagValues = Try {
            DirectoryReader.open(noteLuceneIndex).use { indexReader ->
                val searcher = IndexSearcher(indexReader)
                val tagSet = setOf("tag")
                searcher.search(query, 1000).scoreDocs.map(ScoreDoc::doc).map { docId ->
                    val doc = searcher.doc(docId, tagSet)
                    doc.getFields("tag").map(IndexableField::stringValue)
                }.flatten()
            }.toSet().filter { !it.isNullOrBlank() }
        }) {
            is Success -> tagValues.value.sorted()
            is Failure -> {
                logger.warn("Failed to retrieve Tags: ${tagValues.e.message}")
                emptyList()
            }
        }
    }
    
    private fun openIndexDirectory(indexDirectory: Path) = MMapDirectory(indexDirectory)
    
    private fun createIndexOfNotes(notes: List<Note>, indexDirectory: Path = systemUtils.currentDir()): Directory {
        val directory = openIndexDirectory(indexDirectory)
        createIndexWriter(directory).use { writer ->
            notes.forEach { writeNoteToWriter(writer, it, false) }
        }
        return directory
    }
    
    fun writeNoteToIndex(note: Note, isUpdate: Boolean) {
        createIndexWriter(noteLuceneIndex!!).use { writeNoteToWriter(it, note, isUpdate) }
        noteDB.writeAttachmentsForNote(note)
    }

    fun deleteNote(note: Note) {
        createIndexWriter(noteLuceneIndex!!).use { index ->
            index.deleteDocuments(Term(ID_FIELD, note.id))
            val attachDir = noteDB.defaultAttachmentsDir().resolve(note.id)
            if (Files.isDirectory(attachDir)) {
                attachDir.toFile().deleteRecursively()
            }
        }
    }
    
    private fun createIndexWriter(directory: Directory): IndexWriter {
        val indexWriterConfig = IndexWriterConfig(EnglishAnalyzer())
        return IndexWriter(directory, indexWriterConfig)
    }
    
    private fun writeNoteToWriter(writer: IndexWriter, note: Note, isUpdate: Boolean) {
        val doc = Document()
        doc.add(TextField(DESC_FIELD, note.description, Field.Store.YES))
        doc.add(StringField(ID_FIELD, note.id, Field.Store.YES))
        note.tags.forEach { tag ->
            doc.add(StringField(TAG_FIELD, tag, Field.Store.YES))
        }
        val epoch = note.createDate.atStartOfDay().toEpochSecond(
            ZoneId.systemDefault().rules.getOffset(Instant.now())
        )
        val tsfield = if (isUpdate) MODIFIED_DATE_FIELD else CREATED_DATE_FIELD
        doc.add(LongPoint(tsfield, epoch))
        doc.add(StringField(ATTACHMENT_COUNT_FIELD, note.attachments.size.toString(), Field.Store.YES))
        note.attachments.forEachIndexed { index, attach ->
            if (attach.filePath != null) {
                doc.add(TextField("${ATTACHMENT_FILE_FIELD_PREFIX}_$index", attach.filePath.fileName.toString(), Field.Store.YES))
                if (!attach.note.isNullOrBlank()) {
                    doc.add(TextField("${ATTACHMENT_NOTE_FIELD_PREFIX}_$index", attach.note, Field.Store.YES))
                }
            }
        }
        if (isUpdate) {
            writer.updateDocument(Term(ID_FIELD, note.id), doc)
        } else {
            writer.addDocument(doc)
        }
    }
    
    private fun convertDescriptionToHTML(desc: String): String {
        val re = Regex("[\n\r]+")
        return desc.split(re).joinToString("<br/>")
    }
    
    fun searchForDescription(desc: String?, isPhraseSearch: Boolean, tags: List<String> = emptyList()): Try<List<Note>> {
        if (noteLuceneIndex == null) {
            throw IllegalStateException("note index has not been initialized")
        }
        
        logger.info("Lucene searching for desc [$desc] and tags: $tags")
        return Try {
            when (val descQueryAttempt = createQueryFromDescription(desc, isPhraseSearch)) {
                is Success<BooleanQuery?> -> {
                    val tagsQuery = createTagsQuery(tags)
                    val descQuery = descQueryAttempt.value
                    val query = combineQueries(descQuery, tagsQuery)
                    runFinalQuery(query)
                }
                is Failure -> {
                    throw IllegalArgumentException("Error with Query")
                }
            }
        }
    }
    
    private fun combineQueries(descQuery: BooleanQuery?, tagsQuery: BooleanQuery?) = when {
        (descQuery != null && tagsQuery != null) -> {
            BooleanQuery.Builder().add(descQuery, BooleanClause.Occur.MUST)
                .add(tagsQuery, BooleanClause.Occur.MUST)
                .build()
        }
        (descQuery != null) -> descQuery
        (tagsQuery != null) -> tagsQuery
        else -> MatchAllDocsQuery()
    }
    
    private fun runFinalQuery(query: Query): List<Note> {
        return DirectoryReader.open(noteLuceneIndex).use { indexReader ->
            val indexSearcher = IndexSearcher(indexReader)
            val collector = TopScoreDocCollector.create(10_000, 100)
            indexSearcher.search(query, collector)
            val docs = collector.topDocs().scoreDocs.map { indexSearcher.doc(it.doc) }.map { doc ->
                val tagValues = doc.getFields(TAG_FIELD).map { it.stringValue().trim() }.filter { !it.isNullOrBlank() }
                val description = doc.getField(DESC_FIELD).stringValue()
                val id = doc.getField(ID_FIELD).stringValue()
                Note(
                    id = id,
                    description = description,
                    tags = tagValues,
                    attachments = readNoteAttachmentsFromIndex(id, doc)
                )
            }
            logger.info("Lucene search found ${docs.size} documents")
            docs
        }
    }
    
    private fun createTagsQuery(tags: List<String>): BooleanQuery? {
        return if (tags.isNotEmpty()) {
            BooleanQuery.Builder().let { bldr ->
                tags.map { TermQuery(Term(TAG_FIELD, it)) }.forEach {
                    bldr.add(it, BooleanClause.Occur.MUST)
                }
                bldr.build()
            }
        } else null
    }
    
    private fun createQueryFromDescription(desc: String?, isPhraseSearch: Boolean): Try<BooleanQuery?> {
        val english = EnglishAnalyzer()
        val attachmentRange = (0 until maxAttachmentsCount)
        val queryParser = QueryParser(DESC_FIELD, english)
        return Try {
            when {
                desc.isNullOrBlank() -> null
                isPhraseSearch -> {
                    val descQ = queryParser.createPhraseQuery(DESC_FIELD, desc, phraseSlop)
                    val noteQs = attachmentRange.map { index ->
                        queryParser.createPhraseQuery("${ATTACHMENT_NOTE_FIELD_PREFIX}_$index", desc, phraseSlop)
                    }
                    val nameQs = attachmentRange.map { index ->
                        queryParser.createPhraseQuery("${ATTACHMENT_FILE_FIELD_PREFIX}_$index", desc, phraseSlop)
                    }
                    
                    BooleanQuery.Builder().let { bldr ->
//                        bldr.add(descQ, BooleanClause.Occur.MUST)
                        noteQs.plus(nameQs).plus(descQ).forEach { bldr.add(it, BooleanClause.Occur.SHOULD) }
                        bldr.build()
                    }
                }
                else -> {
                    val descQ = queryParser.parse(desc)
                    val noteQs = attachmentRange.map { index ->
                        val noteParser = QueryParser("${ATTACHMENT_NOTE_FIELD_PREFIX}_$index", english)
                        noteParser.parse(desc)
                    }
                    
                    val nameQs = attachmentRange.map { index ->
                        val nameParser = QueryParser("${ATTACHMENT_FILE_FIELD_PREFIX}_$index", english)
                        nameParser.parse(desc)
                    }
                    
                    BooleanQuery.Builder().let { bldr ->
                        bldr.add(descQ, BooleanClause.Occur.MUST)
                        noteQs.plus(nameQs).forEach { bldr.add(it, BooleanClause.Occur.SHOULD) }
                        bldr.build()
                    }
                }
            }
        }
    }
    
    private fun readNoteAttachmentsFromIndex(noteID: String, doc: Document): List<NoteAttachment> {
        val sizeField = doc.getField(ATTACHMENT_COUNT_FIELD).stringValue()
        return if (!sizeField.isNullOrBlank()) {
            val size = sizeField.toInt()
            (0 until size).mapNotNull { index ->
                val filename = doc.getField("${ATTACHMENT_FILE_FIELD_PREFIX}_$index")?.stringValue()
                if (!filename.isNullOrBlank()) {
                    val note = doc.getField("${ATTACHMENT_NOTE_FIELD_PREFIX}_$index")?.stringValue()
                    noteDB.createNoteAttachment(noteID, note, filename)
                } else null
            }
        } else emptyList()
    }
}
