package com.grga.wifi_admin
// Iznimka koju WifiService baca kada konfiguracija
// ne zadovoljava poslovna pravila, npr. nedostaje lozinka.
// ApiExceptionHandler pretvara je u HTTP 400.
class InvalidWifiConfigurationException(
    message: String
) : RuntimeException(message)