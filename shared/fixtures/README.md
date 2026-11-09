# Przykłady i wektory testowe

**Język:** JSON; kanoniczny raport jako bajty UTF-8 zapisane w pliku tekstowym.

Plan: `report-valid.json`, `report-qa-fail.json`, `report-profile-violation-stage0..3.json`, `signature-vector.json`
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

**Stan 2026-11-05:** negatywne wektory dla walidatora profilu (tydzień 2), dwie rodziny:

- `report-profile-violation-stage0.json` … `-stage3.json` — raport etapu z `qaPassed = true`,
  który łamie regułę swojego etapu (po jednym na regułę `qaPassed` z `docs/product-scenario.md`).
  Format JSON Schema jest poprawny, a podpis `StageCompletion` stacji jest ważny, więc backend
  ma odrzucić raport kodem `PROFILE_VIOLATION` i statusem `422` (patrz `docs/corrections.md`),
  a nie `403`. Blok `expectedViolation` opisuje oczekiwany kod, status i profil.
- `report-qa-fail.json` — etap 1 z `qaPassed = false` **zgodnym** z regułą: poprawny raport
  z wynikiem negatywnym, nie naruszenie profilu. Oczekiwane `202` i `outcome=REJECTED`
  (blok `expectedOutcome`). To kontrola, że walidator nie odrzuca zgodnego negatywu.

Każdy plik ma blok `submission` = gotowe ciało `POST /api/v1/batches/{batchId}/stages`
(`stationCertificateId`, `reportHash`, `deadline`, `signature`) i własną partię (`B-2026-0002`…`0006`),
żeby testy się nie kolidowały. Generuje je `python -m supply_chain_simulator --export-negative-fixtures`
(katalog docelowy opcjonalnie); pliki muszą zostać odtwarzalne bajt w bajt
(`tests/test_negative_fixtures.py`).

**Stan 2026-11-09:** poprawne raporty etapów 0-3, `report-valid-stage0.json` … `-stage3.json`:

- raport zgodny ze schematem i z regułą `qaPassed`, oczekiwane `202` i `outcome=ACCEPTED` (blok `expectedOutcome`)
- wszystkie etapy w jednej partii `B-2026-0007`, więc można je wysłać po kolei 0 -> 3
- etap 2 ma dodatkowo `stageApproval` operatora zmiany (Anvil #2)
- blok `submission` jak w negatywnych, gotowe ciało `POST /api/v1/batches/{batchId}/stages`
- generuje je `python -m supply_chain_simulator --export-valid-fixtures`, testy w `tests/test_valid_fixtures.py`
