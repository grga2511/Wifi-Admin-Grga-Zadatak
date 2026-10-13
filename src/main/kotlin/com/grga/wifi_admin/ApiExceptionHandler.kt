package com.grga.wifi_admin

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.io.IOException
import java.net.http.HttpTimeoutException

// Pretvara iznimke iz REST obrade u HTTP odgovore:
// 400 za neispravan zahtjev, 404 za nepostojeći CPE,
// 502 za grešku SOAP platforme ili komunikacije.
// Klijentu vraća JSON s opisom i kodom greške.

data class ErrorBody(val message: String, val code: String)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(InvalidWifiConfigurationException::class)
    fun handleInvalidConfiguration(exception: InvalidWifiConfigurationException) = error(
        HttpStatus.BAD_REQUEST, "INVALID_WIFI_CONFIGURATION",
        exception.message ?: "Neispravna konfiguracija."
    )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleInvalidJson() = error(
        HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
        "Neispravan JSON, nedostaje obavezno polje ili vrijednost nije dopuštena."
    )

    @ExceptionHandler(CpeNotFoundException::class)
    fun handleCpeNotFound() = error(
        HttpStatus.NOT_FOUND, "CPE_NOT_FOUND", "CPE ne postoji na platformi."
    )

    @ExceptionHandler(PlatformException::class)
    fun handlePlatformError(exception: PlatformException) = error(
        HttpStatus.BAD_GATEWAY, "PLATFORM_ERROR", exception.message ?: "Greška SOAP platforme."
    )

    @ExceptionHandler(HttpTimeoutException::class)
    fun handlePlatformTimeout() = error(
        HttpStatus.BAD_GATEWAY, "PLATFORM_TIMEOUT", "SOAP platforma nije odgovorila na vrijeme."
    )

    @ExceptionHandler(IOException::class)
    fun handlePlatformConnectionError() = error(
        HttpStatus.BAD_GATEWAY, "PLATFORM_UNAVAILABLE", "Greška pri komunikaciji sa SOAP platformom."
    )

    private fun error(status: HttpStatus, code: String, message: String): ResponseEntity<ErrorBody> =
        ResponseEntity.status(status).body(ErrorBody(message, code))
}
