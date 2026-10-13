package com.grga.wifi_admin
// Dopušteni WiFi frekvencijski pojasevi.
enum class WifiBand {
    BAND_2_4_GHZ,
    BAND_5_GHZ
}
// Dopušteni načini šifriranja WiFi mreže.
enum class EncryptionType {
    OPEN,
    WEP,
    WPA_PSK,
    WPA2_PSK,
    WPA3_SAE,
    WPA2_ENTERPRISE
}
// Model WiFi konfiguracije koji koriste kontroler,
// servis i SOAP klijent. Sadrži identifikator uređaja,
// pojas, naziv mreže, šifriranje i opcionalnu lozinku.
// Spring ga pretvara iz JSON-a i u JSON.
data class WifiConfiguration(
    val cpeId: String,
    val wifiBand: WifiBand,
    val ssid: String,
    val encryptionType: EncryptionType = EncryptionType.OPEN,
    val password: String? = null
)