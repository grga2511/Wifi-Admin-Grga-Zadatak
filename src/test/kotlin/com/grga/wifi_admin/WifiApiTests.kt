package com.grga.wifi_admin

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicReference

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WifiApiTests {
    @LocalServerPort private var port: Int = 0
    private val http = HttpClient.newHttpClient()

    companion object {
        private const val NS = "http://wifi-admin.local/platform/v1"
        private val lastAction = AtomicReference("")
        private val lastRequest = AtomicReference("")
        private val saved = AtomicReference("""
            <tns:cpeId>CPE_001</tns:cpeId><tns:wifiBand>BAND_2_4_GHZ</tns:wifiBand>
            <tns:ssid>Office</tns:ssid><tns:encryptionType>WPA2_PSK</tns:encryptionType>
            <tns:password>seed-password</tns:password>
        """.trimIndent())
        private fun envelope(body: String) = """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="$NS"><soap:Body>$body</soap:Body></soap:Envelope>"""
        private val platform = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/platform") { exchange ->
                val request = exchange.requestBody.bufferedReader().use { it.readText() }
                val action = exchange.requestHeaders.getFirst("SOAPAction")
                lastRequest.set(request)
                lastAction.set(action)
                var status = 200
                val response = when {
                    "CPE_UNKNOWN" in request -> {
                        status = 500
                        envelope("<soap:Fault><faultcode>tns:NotFound</faultcode><faultstring>CPE not found</faultstring></soap:Fault>")
                    }
                    "CPE_FAULT" in request -> {
                        status = 500
                        envelope("<soap:Fault><faultcode>soap:Server</faultcode><faultstring>Failure</faultstring></soap:Fault>")
                    }
                    "CPE_BAD_ENUM" in request -> envelope("<tns:GetCpeIdResponse><tns:configuration>${saved.get().replace("BAND_2_4_GHZ", "UNKNOWN_BAND")}</tns:configuration></tns:GetCpeIdResponse>")
                    "CPE_MISSING" in request -> envelope("<tns:GetCpeIdResponse><tns:configuration><tns:cpeId>CPE_MISSING</tns:cpeId></tns:configuration></tns:GetCpeIdResponse>")
                    "CPE_DISCONNECT" in request -> { exchange.close(); return@createContext }
                    "CPE_XML" in request -> "<broken"
                    "CPE_HTTP" in request -> { status = 503; envelope("<tns:GetCpeIdResponse><tns:configuration>${saved.get()}</tns:configuration></tns:GetCpeIdResponse>") }
                    "CPE_XXE" in request -> """<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><x>&e;</x>"""
                    action.endsWith("#updateCpeId\"") -> {
                        saved.set(request.substringAfter("<tns:configuration>").substringBefore("</tns:configuration>"))
                        envelope("<tns:UpdateCpeIdResponse><tns:configuration>${saved.get()}</tns:configuration></tns:UpdateCpeIdResponse>")
                    }
                    else -> envelope("<tns:GetCpeIdResponse><tns:configuration>${saved.get()}</tns:configuration></tns:GetCpeIdResponse>")
                }
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/xml; charset=utf-8")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("platform.soap-url") { "http://127.0.0.1:${platform.address.port}/platform" }
        }
    }

    private fun get(id: String) = http.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/wifi-parameter/$id")).GET().build(),
        HttpResponse.BodyHandlers.ofString()
    )
    private fun put(json: String) = http.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/wifi-parameter"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
        HttpResponse.BodyHandlers.ofString()
    )

    @Test fun `GET sends WSDL action and maps SOAP into JSON`() {
        val response = get("CPE_001")
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"cpeId\":\"CPE_001\""))
        assertEquals("\"$NS#getCpeID\"", lastAction.get())
        assertTrue(lastRequest.get().contains("<tns:GetCpeIdRequest>"))
    }

    @Test fun `PUT escapes XML and next GET returns saved configuration`() {
        val response = put("""{"cpeId":"CPE_001","wifiBand":"BAND_2_4_GHZ","ssid":"Grga & <WiFi>","encryptionType":"WPA2_PSK","password":"new-password"}""")
        assertEquals(200, response.statusCode())
        assertEquals("\"$NS#updateCpeId\"", lastAction.get())
        assertTrue(lastRequest.get().contains("Grga &amp; &lt;WiFi&gt;"))
        assertTrue(get("CPE_001").body().contains("new-password"))
    }

    @Test fun `OPEN defaults when encryption is absent`() {
        assertEquals(200, put("""{"cpeId":"CPE_001","wifiBand":"BAND_2_4_GHZ","ssid":"Open network"}""").statusCode())
        assertTrue(lastRequest.get().contains("<tns:encryptionType>OPEN</tns:encryptionType>"))
    }

    @Test fun `invalid requests return 400 before calling SOAP`() {
        for (json in listOf(
            "{", "{}",
            """{"cpeId":"CPE_001","wifiBand":"WRONG","ssid":"WiFi"}""",
            """{"cpeId":"","wifiBand":"BAND_2_4_GHZ","ssid":"WiFi"}""",
            """{"cpeId":"CPE_001","wifiBand":"BAND_2_4_GHZ","ssid":" "}""",
            """{"cpeId":"CPE_001","wifiBand":"BAND_2_4_GHZ","ssid":"WiFi","encryptionType":"WPA2_PSK"}"""
        )) {
            lastRequest.set("no call")
            val response = put(json)
            assertEquals(400, response.statusCode(), json)
            assertTrue(response.body().contains("\"code\":"))
            assertEquals("no call", lastRequest.get())
        }
    }

    @Test fun `network failure returns JSON 502`() {
        val response = get("CPE_DISCONNECT")
        assertEquals(502, response.statusCode())
        assertTrue(response.body().contains("PLATFORM_UNAVAILABLE"))
    }

    @Test fun `business NotFound fault is 404 even when SOAP uses HTTP 500`() {
        val response = get("CPE_UNKNOWN")
        assertEquals(404, response.statusCode())
        assertTrue(response.body().contains("CPE_NOT_FOUND"))
    }

    @Test fun `invalid platform responses and other faults are 502`() {
        for (id in listOf("CPE_FAULT", "CPE_BAD_ENUM", "CPE_MISSING", "CPE_XML", "CPE_HTTP", "CPE_XXE")) {
            val response = get(id)
            assertEquals(502, response.statusCode(), id)
            assertTrue(response.body().contains("PLATFORM_ERROR"))
        }
    }
}
