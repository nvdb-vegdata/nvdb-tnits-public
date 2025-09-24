package no.vegvesen.nvdb.tnits.extensions

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.vegvesen.nvdb.tnits.Services.Companion.objectMapper

data class ProblemDetail(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
)

class HttpErrorException(
    val statusCode: HttpStatusCode,
    val problemDetail: ProblemDetail? = null,
    message: String = "HTTP Error ${statusCode.value}: ${statusCode.description}",
) : Exception(message)

// Generic ndjson flow extension for ByteReadChannel
inline fun <reified T> ByteReadChannel.ndjsonFlow(): Flow<T> = flow {
    while (!isClosedForRead) {
        val line = readUTF8Line() ?: break
        if (line.isBlank()) continue
        val item = objectMapper.readValue(line, T::class.java)
        emit(item)
    }
}

// Generic ndjson flow extension for HttpStatement
inline fun <reified T> HttpStatement.executeAsNdjsonFlow(): Flow<T> = flow {
    execute { response ->
        if (!response.status.isSuccess()) {
            val problemDetail =
                try {
                    val contentType = response.headers[HttpHeaders.ContentType]
                    if (contentType?.contains("application/problem+json") == true ||
                        contentType?.contains("application/json") == true
                    ) {
                        val errorBody = response.bodyAsText()
                        objectMapper.readValue(errorBody, ProblemDetail::class.java)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }

            val errorMessage =
                problemDetail?.let { pd ->
                    buildString {
                        append("HTTP Error ${response.status.value}: ")
                        append(pd.title ?: response.status.description)
                        pd.detail?.let { append(" - $it") }
                        pd.instance?.let { append(" (Instance: $it)") }
                        append(" [URL: ${response.request.url}]")
                    }
                } ?: "HTTP Error ${response.status.value}: ${response.status.description} [URL: ${response.request.url}]"

            throw HttpErrorException(response.status, problemDetail, errorMessage)
        }

        val channel = response.bodyAsChannel()
        channel.ndjsonFlow<T>().collect { emit(it) }
    }
}
