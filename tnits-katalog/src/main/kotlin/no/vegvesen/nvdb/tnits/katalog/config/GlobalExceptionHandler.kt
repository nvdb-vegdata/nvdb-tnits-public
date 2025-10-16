package no.vegvesen.nvdb.tnits.katalog.config

import jakarta.servlet.http.HttpServletRequest
import no.vegvesen.vt.nvdb.apiles.common.WithLogger
import no.vegvesen.vt.nvdb.apiles.common.model.ClientException
import no.vegvesen.vt.nvdb.apiles.external.core.exceptions.DomainException
import no.vegvesen.vt.nvdb.apiles.external.core.exceptions.KreverInnloggingException
import no.vegvesen.vt.nvdb.apiles.external.core.exceptions.ManglerTilgangException
import no.vegvesen.vt.nvdb.apiles.external.core.exceptions.NodeNotFoundException
import no.vegvesen.vt.nvdb.apiles.external.core.exceptions.VeglenkesekvensNotFoundException
import no.vegvesen.vt.nvdb.apiles.external.core.exceptions.VegobjektNotFoundException
import no.vegvesen.vt.nvdb.apiles.external.core.exceptions.VegobjekttypeNotFoundException
import org.springframework.http.*
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler(), WithLogger {

    @ExceptionHandler(VegobjektNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleVegobjektNotFoundException(e: VegobjektNotFoundException) = ProblemDetail.forStatus(HttpStatus.NOT_FOUND).apply {
        title = "Vegobjekt ikke funnet"
        detail = e.localizedMessage
    }

    @ExceptionHandler(VeglenkesekvensNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleVeglenkesekvensNotFoundException(e: VeglenkesekvensNotFoundException) = ProblemDetail.forStatus(HttpStatus.NOT_FOUND).apply {
        title = "Veglenkesekvens ikke funnet"
        detail = e.localizedMessage
    }

    @ExceptionHandler(NodeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNodeNotFoundException(e: NodeNotFoundException) = ProblemDetail.forStatus(HttpStatus.NOT_FOUND).apply {
        title = "Node ikke funnet"
        detail = e.localizedMessage
    }

    @ExceptionHandler(VegobjekttypeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleVegobjekttypeNotFoundException(e: VegobjekttypeNotFoundException) = ProblemDetail.forStatus(HttpStatus.NOT_FOUND).apply {
        title = "Vegobjekttype ikke funnet"
        detail = e.localizedMessage
    }

    @ExceptionHandler(KreverInnloggingException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleKreverInnloggingException(e: KreverInnloggingException) = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED).apply {
        title = "Krever innlogging"
        detail = e.localizedMessage
    }

    @ExceptionHandler(ManglerTilgangException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleManglerTilgangException(e: ManglerTilgangException) = ProblemDetail.forStatus(HttpStatus.FORBIDDEN).apply {
        title = "Mangler tilgang"
        detail = e.localizedMessage
    }

    @ExceptionHandler(ClientException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleClientException(e: ClientException) = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
        title = "Ugyldig forespørsel"
        detail = e.localizedMessage
    }

    @ExceptionHandler(DomainException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleDomainException(e: DomainException): ProblemDetail {
        log.error("Unexpected domain exception: ${e.localizedMessage}", e)
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            title = "Intern systemfeil"
            detail = e.localizedMessage
        }
    }

    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Throwable, request: HttpServletRequest): ProblemDetail {
        log.error("Unexpected exception: ${e.localizedMessage}", e)
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            title = "Uventet systemfeil"
            detail = "En uventet feil oppstod for forespørsel med ID ${request.requestId}"
        }
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            title = "Valideringsfeil"
            detail = ex.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }
        }
        return ResponseEntity(problemDetail, HttpStatus.BAD_REQUEST)
    }
}
