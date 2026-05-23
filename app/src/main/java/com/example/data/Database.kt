package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "folders")
data class DocumentFolder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val localFilePath: String,           // Path to encrypted image file
    val folderId: Int? = null,           // Nullable = Uncategorized
    val docType: String,                 // e.g. "Fuel Receipt", "Bill of Lading", "Delivery Receipt", "Other"
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val syncLog: String? = null,
    val isEncrypted: Boolean = true,
    val pdfPath: String? = null          // Virtual or actual created PDF
)

@Entity(tableName = "extracted_fields")
data class ExtractedField(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val documentId: Int,                 // FK to Document
    val fieldKey: String,                // e.g. "Invoice Number", "Total Price", "Gallons", "Date", "Driver Name", "Carrier Name"
    val fieldValue: String,
    val confidence: Float                // 0.0 to 1.0 (e.g. 0.95 = 95%)
)

// --- DAO ---

@Dao
interface DocumentDao {

    // Folders
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<DocumentFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: DocumentFolder): Long

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Int)

    // Documents
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getDocumentsInFolder(folderId: Int): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE folderId IS NULL ORDER BY createdAt DESC")
    fun getUncategorizedDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Int): Document?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: Int)

    // Extracted Fields
    @Query("SELECT * FROM extracted_fields WHERE documentId = :documentId")
    fun getExtractedFieldsForDocument(documentId: Int): Flow<List<ExtractedField>>

    @Query("SELECT * FROM extracted_fields WHERE documentId = :documentId")
    suspend fun getExtractedFieldsList(documentId: Int): List<ExtractedField>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtractedFields(fields: List<ExtractedField>)

    @Query("DELETE FROM extracted_fields WHERE documentId = :documentId")
    suspend fun deleteExtractedFieldsForDocument(documentId: Int)
}

// --- AppDatabase ---

@Database(entities = [DocumentFolder::class, Document::class, ExtractedField::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "docdriver_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository ---

class DocumentRepository(private val documentDao: DocumentDao) {
    val allFolders: Flow<List<DocumentFolder>> = documentDao.getAllFolders()
    val allDocuments: Flow<List<Document>> = documentDao.getAllDocuments()
    val uncategorizedDocuments: Flow<List<Document>> = documentDao.getUncategorizedDocuments()

    fun getDocumentsInFolder(folderId: Int): Flow<List<Document>> {
        return documentDao.getDocumentsInFolder(folderId)
    }

    suspend fun getDocumentById(id: Int): Document? {
        return documentDao.getDocumentById(id)
    }

    suspend fun insertDocument(document: Document): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: Document) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(documentId: Int) {
        documentDao.deleteDocument(documentId)
        documentDao.deleteExtractedFieldsForDocument(documentId)
    }

    suspend fun insertFolder(name: String): Long {
         return documentDao.insertFolder(DocumentFolder(name = name))
    }

    suspend fun deleteFolder(folderId: Int) {
        documentDao.deleteFolder(folderId)
    }

    fun getExtractedFields(documentId: Int): Flow<List<ExtractedField>> {
        return documentDao.getExtractedFieldsForDocument(documentId)
    }

    suspend fun getExtractedFieldsList(documentId: Int): List<ExtractedField> {
        return documentDao.getExtractedFieldsList(documentId)
    }

    suspend fun saveExtractedFields(documentId: Int, fields: List<ExtractedField>) {
        documentDao.deleteExtractedFieldsForDocument(documentId)
        documentDao.insertExtractedFields(fields.map { it.copy(documentId = documentId) })
    }
}
