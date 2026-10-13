package com.grga.wifi_admin

import org.springframework.stereotype.Service
// Provodi poslovna pravila prije poziva platforme:
// provjerava cpeId, SSID i potrebnu lozinku.
// Za dohvat i ažuriranje konfiguracije koristi WifiSoapClient.
@Service
class WifiService(
    private val soapClient: WifiSoapClient
) {
    fun getConfiguration(cpeId: String): WifiConfiguration {
        if (cpeId.isBlank()) throw InvalidWifiConfigurationException("cpeId ne smije biti prazan.")
        return soapClient.getConfiguration(cpeId)
    }
    fun updateConfiguration(
        configuration: WifiConfiguration
    ): WifiConfiguration {
        validateConfiguration(configuration)
        return soapClient.updateConfiguration(configuration)
    }
    private fun validateConfiguration(
        configuration: WifiConfiguration
    ) {
        if (configuration.cpeId.isBlank()) {
            throw InvalidWifiConfigurationException(
                "cpeId ne smije biti prazan."
            )
        }

        if (configuration.ssid.isBlank()) {
            throw InvalidWifiConfigurationException(
                "SSID ne smije biti prazan."
            )
        }

        val requiresPassword = when (configuration.encryptionType) {
            EncryptionType.WEP,
            EncryptionType.WPA_PSK,
            EncryptionType.WPA2_PSK,
            EncryptionType.WPA3_SAE -> true

            EncryptionType.OPEN,
            EncryptionType.WPA2_ENTERPRISE -> false
        }

        if (
            requiresPassword &&
            configuration.password.isNullOrBlank()
        ) {
            throw InvalidWifiConfigurationException(
                "Lozinka je obavezna za odabrani tip šifriranja."
            )
        }
    }
}