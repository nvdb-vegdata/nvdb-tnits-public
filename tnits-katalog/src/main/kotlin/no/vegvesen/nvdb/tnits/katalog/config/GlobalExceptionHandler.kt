package no.vegvesen.nvdb.tnits.katalog.config

import jakarta.servlet.http.HttpServletRequest
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.katalog.core.exceptions.ClientException
import no.vegvesen.nvdb.tnits.katalog.core.exceptions.DomainException
import org.springframework.http.*
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler(), WithLogger {

    @ExceptionHandler(ClientException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleClientException(e: ClientException) = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
        title = "Bad request"
        detail = e.localizedMessage
    }

    @ExceptionHandler(DomainException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleDomainException(e: DomainException): ProblemDetail {
        log.error("Unexpected domain exception: ${e.localizedMessage}", e)
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            title = "Internal error"
            detail = e.localizedMessage
        }
    }

    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Throwable, request: HttpServletRequest): ProblemDetail {
        log.error("Unexpected exception: ${e.localizedMessage}", e)
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            title = "Unexpected system error"
            detail = "An unexpected error occurred for request with ID ${request.requestId}"
        }
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            title = "Validation error"
            detail = ex.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }
        }
        return ResponseEntity(problemDetail, HttpStatus.BAD_REQUEST)
    }
}
