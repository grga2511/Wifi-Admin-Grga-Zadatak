# WiFi Admin — Kotlin / Spring Boot

REST wrapper za SOAP 1.1 platformu. Implementiran je obavezni dio zadatka:
GET `/wifi-parameter/{cpeId}` i PUT `/wifi-parameter`. Dohvat i promjene idu
izravno na platformu; nema baze ni dodatnih zadataka.

## Pokretanje na Windowsu

Potrebni su JDK 21 i Docker Desktop. Otvori ovu mapu kao Gradle projekt u
IntelliJ IDEA ili Android Studiju. Gradle wrapper je uključen.

U PowerShellu u korijenu projekta:

```powershell
docker compose up -d
.\gradlew.bat test
.\gradlew.bat bootRun
```

Mock: `http://localhost:8082/platform`. REST: `http://localhost:8081`.
Aplikacija ostaje pokrenuta dok se ne pritisne Ctrl+C.
`docker compose down` zaustavlja mock. Restart mocka vraća seed podatke.
Ako su portovi zauzeti, prilagodi Compose i `PLATFORM_SOAP_URL`, odnosno
`SERVER_PORT`. Zadane vrijednosti su u `application.yaml`.

```powershell
$env:SERVER_PORT = "8081"
$env:PLATFORM_SOAP_URL = "http://localhost:8082/platform"
```

## Ručna provjera

Kopiraj samo naredbe, bez PowerShell oznaka `PS>` i `>>`:

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/wifi-parameter/CPE_001"

$body = @{ cpeId = "CPE_001"; wifiBand = "BAND_2_4_GHZ"; ssid = "Grga-WiFi"; encryptionType = "WPA2_PSK"; password = "nova-lozinka-123" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8081/wifi-parameter" -Method Put -ContentType "application/json; charset=utf-8" -Body $body

Invoke-RestMethod -Uri "http://localhost:8081/wifi-parameter/CPE_001"
```

Posljednji GET mora pokazati promjenu. CPE_001 do CPE_012 postoje u mocku.
Nepoznati CPE vraća 404. PUT bez lozinke uz WPA2_PSK vraća 400.
Zaustavljena platforma vraća 502.

## Struktura i poslovna pravila

- `WifiController`: dvije REST rute i JSON model.
- `WifiService`: validacija prije poziva platforme.
- `WifiSoapClient`: JDK HttpClient, SOAPAction, XML mapiranje i sigurno parsiranje.
- `ApiExceptionHandler`: JSON greške s poljima `message` i `code`.
- `openapi/` i `wsdl/`: izvorni kontrakti.

cpeId i SSID ne smiju biti prazni. WEP, WPA_PSK, WPA2_PSK i WPA3_SAE traže
ne praznu lozinku. Izostavljeni encryptionType postaje OPEN. WPA2_ENTERPRISE
ne traži PSK jer specifikacija ne definira enterprise vjerodajnice.
Ne uvode se ograničenja duljine lozinke kojih nema u priloženom kontraktu.
Prazni password element šalje se za izostavljenu lozinku radi Mock on predloška.

SOAP koristi namespace `http://wifi-admin.local/platform/v1`, HTTP/1.1 i
quoted SOAPAction iz WSDL-a. Connect timeout je 5 s, request timeout 10 s.
Automatski retry nije uključen kako se PUT ne bi ponavljao nakon nejasnog ishoda.
XML tekst se escapira; DTD i vanjski entiteti su isključeni. Lozinke i SOAP
poruke se ne ispisuju u aplikacijske logove.

| Situacija | REST status |
|---|---|
| Neispravan JSON, enum ili poslovno pravilo | 400 |
| SOAP fault NotFound | 404 |
| Drugi SOAP fault, loš XML/model, HTTP greška platforme | 502 |
| Mrežna greška ili timeout | 502 |

## Testovi

`WifiApiTests` pokreće stvarni REST server i lokalnu HTTP SOAP zamjenu bez
Dockera. Provjerava GET/PUT, SOAPAction, XML escaping, promjenu vidljivu na
GET-u, OPEN default, validaciju bez poziva platforme, faultove, neispravan
model/XML, HTTP grešku i blokiranje XXE-a.
