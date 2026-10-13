package com.grga.wifi_admin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
// Prima REST zahtjeve:
// GET /wifi-parameter/{cpeId} za dohvat konfiguracije,
// PUT /wifi-parameter za promjenu konfiguracije.
// Prosljeđuje obradu WifiServiceu i vraća rezultat kao JSON.
@RestController

@RequestMapping("/wifi-parameter")
class WifiController(
    private val wifiService: WifiService
) {
    @GetMapping("/{cpeId}")
    fun getWifiParameter(
        @PathVariable("cpeId") cpeId: String
    ): WifiConfiguration {
        return wifiService.getConfiguration(cpeId)
    }
    @PutMapping
    fun putWifiParameter(
        @RequestBody configuration: WifiConfiguration
    ): WifiConfiguration {
        return wifiService.updateConfiguration(configuration)
    }
}
