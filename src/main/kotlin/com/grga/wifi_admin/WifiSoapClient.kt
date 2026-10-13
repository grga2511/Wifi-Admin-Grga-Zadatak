package com.grga.wifi_admin

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
// Komunicira s vanjskom SOAP platformom.
// Izrađuje SOAP XML i SOAPAction, šalje HTTP POST zahtjeve,
// parsira odgovore u WifiConfiguration i prepoznaje faultove.
// Koristi operacije getCpeID i updateCpeId iz WSDL-a.
@Component
class WifiSoapClient(@Value("\${platform.soap-url}") private val soapUrl: String) {
    private val httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun getConfiguration(cpeId: String): WifiConfiguration = call(
        "getCpeID", "GetCpeIdRequest", "GetCpeIdResponse",
        field("cpeId", cpeId)
    )

    fun updateConfiguration(configuration: WifiConfiguration): WifiConfiguration = call(
        "updateCpeId", "UpdateCpeIdRequest", "UpdateCpeIdResponse",
        "<tns:configuration>" +
            field("cpeId", configuration.cpeId) +
            field("wifiBand", configuration.wifiBand.name) +
            field("ssid", configuration.ssid) +
            field("encryptionType", configuration.encryptionType.name) +
            // Mockoon expects the optional password element even for OPEN networks.
            field("password", configuration.password ?: "") +
            "</tns:configuration>"
    )

    private fun field(name: String, value: String): String {
        val escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<tns:$name>$escaped</tns:$name>"
    }

    private fun call(action: String, requestName: String, responseName: String, fields: String): WifiConfiguration {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="$SOAP_NS" xmlns:tns="$PLATFORM_NS">
              <soap:Body><tns:$requestName>$fields</tns:$requestName></soap:Body>
            </soap:Envelope>
        """.trimIndent()
        val request = HttpRequest.newBuilder(URI.create(soapUrl))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "text/xml; charset=utf-8")
            .header("SOAPAction", "\"$PLATFORM_NS#$action\"")
            .POST(HttpRequest.BodyPublishers.ofString(xml))
            .build()
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw PlatformException("Poziv SOAP platforme je prekinut.")
        }
        // Parse faults before checking HTTP status: SOAP 1.1 commonly uses HTTP 500 for faults.
        val configuration = parseConfiguration(response.body(), responseName)
        if (response.statusCode() !in 200..299) {
            throw PlatformException("SOAP platforma vratila je HTTP grešku.")
        }
        return configuration
    }

    private fun parseConfiguration(xml: String, responseName: String): WifiConfiguration {
        if (xml.isBlank()) throw PlatformException("SOAP platforma vratila je prazan odgovor.")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = try {
            factory.newDocumentBuilder().apply {
                // Prevent the parser from printing potentially sensitive response fragments.
                setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
                    override fun error(e: org.xml.sax.SAXParseException) { throw e }
                    override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
                })
            }.parse(InputSource(StringReader(xml.trimStart())))
        } catch (exception: SAXException) {
            throw PlatformException("SOAP platforma vratila je neispravan XML.")
        }
        val envelope = document.documentElement
        if (envelope.namespaceURI != SOAP_NS || envelope.localName != "Envelope") {
            throw PlatformException("Odgovor nije SOAP 1.1 Envelope.")
        }
        val body = child(envelope, SOAP_NS, "Body")
            ?: throw PlatformException("SOAP odgovor ne sadrži Body.")
        child(body, SOAP_NS, "Fault")?.let { fault ->

            val code = (0 until fault.childNodes.length)
                .map { fault.childNodes.item(it) }
                .firstOrNull { it.localName == "faultcode" }?.textContent?.trim()
            if (code?.substringAfterLast(':') == "NotFound") throw CpeNotFoundException()
            throw PlatformException("SOAP platforma vratila je grešku.")
        }
        val operation = child(body, PLATFORM_NS, responseName)
            ?: throw PlatformException("Neočekivana SOAP operacija.")
        val configuration = child(operation, PLATFORM_NS, "configuration")
            ?: throw PlatformException("SOAP odgovor ne sadrži konfiguraciju.")
        fun read(name: String) = child(configuration, PLATFORM_NS, name)?.textContent
        fun required(name: String) = read(name)
            ?: throw PlatformException("SOAP odgovor ne sadrži obavezno polje.")
        return try {
            WifiConfiguration(
                cpeId = required("cpeId"),
                wifiBand = WifiBand.valueOf(required("wifiBand")),
                ssid = required("ssid"),
                encryptionType = read("encryptionType")?.let { EncryptionType.valueOf(it) }
                    ?: EncryptionType.OPEN,
                password = read("password")
            )
        } catch (exception: IllegalArgumentException) {
            throw PlatformException("SOAP odgovor sadrži nepoznatu enum vrijednost.")
        }
    }

    private fun child(parent: Element, namespace: String, name: String): Element? =
        (0 until parent.childNodes.length)
            .map { parent.childNodes.item(it) }
            .filterIsInstance<Element>()
            .singleOrNull { it.namespaceURI == namespace && it.localName == name }

    companion object {
        private const val SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/"
        private const val PLATFORM_NS = "http://wifi-admin.local/platform/v1"
    }
}
