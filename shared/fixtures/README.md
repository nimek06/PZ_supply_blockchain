# Przykłady i wektory testowe

**Język:** JSON; kanoniczny raport jako bajty UTF-8 zapisane w pliku tekstowym.

Plan: `report-valid.json`, `report-qa-fail.json`, `signature-vector.json`
(`StageCompletion` bez nonce, `StageApproval`; wektor wspólny dla Python, Java, TS/viem i Solidity),
`station-certificate.json` (certyfikat stacji: payload, `payloadHash`, podpis wystawcy i `registryCall`)
oraz przykładowe odpowiedzi partii/historii/polecenia zgodne z OpenAPI.
Wektor zawiera kanoniczny raport, oczekiwany reportHash, typed data, podpis
i publiczny adres testowej stacji. Wartości kryptograficzne trzeba wygenerować
i zweryfikować, nie wpisać jako losowe przykłady.

Klucz testowy odtwarza kod testów lokalnie z jasno oznaczonego, nieprodukcyjnego
mechanizmu; nie dodawaj rzeczywistych kluczy do JSON. Fixture'y nie zawierają
tokenów, rzeczywistych danych produkcyjnych ani kont z finansowaniem.

Każdy moduł czyta te same dane. Testy negatywne mogą mutować poprawny fixture,
żeby udowodnić odrzucenie. Po zmianie schematu aktualizuj wszystkie przykłady.
Zapisz, które dane są raportem, a które obwiednią podpisu - nie hashuj obwiedni.

**Stan 2026-10-31:** `signature-vector.json` zawiera wektor współzatwierdzenia kontroli - podpis stacji
i podpis `StageApproval` niezależnego operatora zmiany dla tej samej partii/etapu/hash; numer partii
`B-RRRR-NNNN` i `batchId = keccak256(utf8(batchNumber))`. `station-certificate.json` zawiera przykładowy
certyfikat stacji z testowym wystawcą (Anvil #0 to publiczny klucz deweloperski, nie zasilać go na Amoy):
obwiednia zgodna ze schematem `../schemas/station-certificate.schema.json`, blok EIP-712
`StationCertificate(bytes32 payloadHash)` w domenie `SupplyChainStationRegistry/1` oraz `registryCall`
z argumentami `registerStation` (w tym `stageMask`). Testuj też ten sam adres po obu stronach
i brakujący/odwołany certyfikat oraz niezgodność z rejestrem stacji. Nie wymyślaj poprawnych hashy
i podpisów ręcznie - generuje je `python -m supply_chain_simulator --export-certificate-fixture`
(wektor podpisu: `--export-signature-vector`).
