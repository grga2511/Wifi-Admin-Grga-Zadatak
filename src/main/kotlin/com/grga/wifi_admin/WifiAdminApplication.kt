package com.grga.wifi_admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
// Glavna konfiguracijska klasa Spring Boot aplikacije.
// Omogućuje automatsko pronalaženje i povezivanje komponenti.
@SpringBootApplication
class WifiAdminApplication
// Ulazna točka programa: pokreće Spring Boot i web server.
fun main(args: Array<String>) {
	runApplication<WifiAdminApplication>(*args)
}
