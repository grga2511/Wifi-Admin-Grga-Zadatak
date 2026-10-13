package com.grga.wifi_admin
// Označava da je SOAP platforma vratila NotFound fault.
// ApiExceptionHandler pretvara je u HTTP 404.
class CpeNotFoundException :
    RuntimeException("CPE ne postoji na platformi.")
// Označava grešku SOAP platforme, npr. neispravan XML,
// nepoznatu enum vrijednost ili drugi SOAP fault.
// ApiExceptionHandler pretvara je u HTTP 502.
class PlatformException(message: String) :
    RuntimeException(message)