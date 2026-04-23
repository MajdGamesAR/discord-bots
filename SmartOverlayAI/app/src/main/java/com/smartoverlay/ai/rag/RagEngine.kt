package com.smartoverlay.ai.rag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RagEngine(private val context: Context) {

    companion object {
        private const val TAG = "RagEngine"
        private const val SIMILARITY_THRESHOLD = 0.65f
    }

    // In-memory vector store (simplified FAISS-like implementation)
    private val documentStore = mutableListOf<DocumentVector>()
    private var isInitialized = false

    data class DocumentVector(
        val id: String,
        val text: String,
        val embedding: FloatArray,
        val metadata: Map<String, String> = emptyMap()
    )

    data class SearchResult(
        val document: DocumentVector,
        val similarity: Float
    )

    /**
     * Initialize the RAG engine with local documents (PDFs, text files)
     * This should load pre-computed embeddings from assets or internal storage
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Load pre-indexed documents from assets or internal storage
            loadDocumentsFromAssets()
            isInitialized = true
            Log.d(TAG, "RAG Engine initialized with ${documentStore.size} documents")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RAG engine", e)
            false
        }
    }

    private fun loadDocumentsFromAssets() {
        // Try to load pre-computed embeddings from assets
        // In production, this would load from PDFs that were processed during build
        try {
            val assetsDir = context.assets
            if (assetsDir.list("documents")?.isNotEmpty() == true) {
                // Load document vectors from assets
                // This is a placeholder - in real implementation, load actual embeddings
            }
        } catch (e: Exception) {
            Log.w(TAG, "No pre-loaded documents found, will use empty store")
        }
    }

    /**
     * Add a new document to the vector store
     */
    suspend fun addDocument(id: String, text: String, embedding: FloatArray): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                documentStore.add(DocumentVector(id, text, embedding))
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add document", e)
                false
            }
        }

    /**
     * Search for the most relevant document given a query embedding
     */
    suspend fun search(queryEmbedding: FloatArray, topK: Int = 3): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (!isInitialized || documentStore.isEmpty()) {
                return@withContext emptyList()
            }

            // Calculate cosine similarity with all documents
            val results = documentStore.mapNotNull { doc ->
                val similarity = cosineSimilarity(queryEmbedding, doc.embedding)
                if (similarity >= SIMILARITY_THRESHOLD) {
                    SearchResult(doc, similarity)
                } else {
                    null
                }
            }.sortedByDescending { it.similarity }.take(topK)

            results
        }

    /**
     * Find answer for a question using RAG
     */
    suspend fun findAnswer(question: String, questionEmbedding: FloatArray): RagResponse {
        if (!isInitialized) {
            return RagResponse(
                found = false,
                answer = "Not found in source",
                confidence = 0f,
                sourceDocument = null
            )
        }

        val results = search(questionEmbedding, topK = 3)
        
        if (results.isEmpty()) {
            return RagResponse(
                found = false,
                answer = "Not found in source",
                confidence = 0f,
                sourceDocument = null
            )
        }

        // Extract answer from most relevant document
        val bestMatch = results.first()
        val extractedAnswer = extractAnswerFromDocument(bestMatch.document.text, question)

        return RagResponse(
            found = true,
            answer = extractedAnswer,
            confidence = bestMatch.similarity,
            sourceDocument = bestMatch.document.text
        )
    }

    private fun extractAnswerFromDocument(document: String, question: String): String {
        // Simple extraction logic - in production, this would use a language model
        // For now, return relevant sentences from the document
        
        val sentences = document.split(".", "?", "!").map { it.trim() }.filter { it.isNotEmpty() }
        
        // Find sentences that might contain the answer
        val questionWords = question.lowercase().split("\\s+".toRegex()).toSet()
        
        val relevantSentences = sentences.filter { sentence ->
            val sentenceWords = sentence.lowercase().split("\\s+".toRegex()).toSet()
            // Check for word overlap
            (questionWords intersect sentenceWords).size >= 2
        }.take(3)

        return if (relevantSentences.isNotEmpty()) {
            relevantSentences.joinToString(". ") + "."
        } else {
            document.take(200) + if (document.length > 200) "..." else ""
        }
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) return 0f
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        if (norm1 == 0f || norm2 == 0f) return 0f
        
        return dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
    }

    fun getDocumentCount(): Int = documentStore.size

    fun clearStore() {
        documentStore.clear()
    }
}

data class RagResponse(
    val found: Boolean,
    val answer: String,
    val confidence: Float,
    val sourceDocument: String?
)
