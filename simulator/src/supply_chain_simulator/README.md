# Pakiet symulatora

**Język:** Python 3.11+, kod z adnotacjami typów.

Moduły pakietu (stan 2026-11-05 - gotowe: `__init__`, `__main__`, `cli`, `models`,
`canonical`, `signing`, `vector`, `certificate`, `profiles`, `telemetry`, `scenario`,
`config`, `chain`, `runner`; szkic: `client`):
| Plik | Zakres |
|---|---|
| `__init__.py` | pakiet, bez efektów ubocznych |
| `__main__.py`, `cli.py` | argumenty, wybór trybu, exit codes |
| `config.py` | walidacja adresów, sieci, backend URL i sekretów |
| `identity.py` | klucz i certyfikat stacji, identyfikator poświadczenia |
| `models.py` | ścisłe modele Pydantic raportu v1, struktur EIP-712 i certyfikatu |
| `canonical.py` | kanonizacja JCS (RFC 8785) i Keccak-256; `reportHash` |
| `signing.py` | typed data EIP-712 `StageCompletion` (bez nonce), `StageApproval`, `StationCertificate` i eth-account |
| `certificate.py` | payload certyfikatu, hash JCS, domena wystawcy, weryfikacja i `stageMask` |
| `vector.py` | deterministyczna budowa `shared/fixtures/signature-vector.json` i `station-certificate.json` |
| `profiles.py` | profile pomiarów 0..3 jako dane ze schematu (klucze, zakresy, `$ref`) |
| `telemetry.py` | deterministyczne pomiary etapów 0..3; reguły `qaPassed`, tryb `force_qa_fail` |
| `scenario.py` | modele scenariusza YAML (kolejne etapy, oczekiwania) i loader |
| `chain.py` | odczyt stanu z kontraktu przez `eth_call` (`getBatch`, `getStation`, DEC-10) |
| `client.py` | HTTP, timeout, idempotentne retry, status polecenia |
| `runner.py` | kolejność kroków i tryb auto/interaktywny |

Najpierw model i test podpisu, potem HTTP, na końcu pętla/scenariusze.
Nie łącz całej aplikacji w jeden skrypt. Użyj API EIP-712 eth-account zgodnego
z przypiętą wersją; `encode_defunct` nie jest poprawnym zamiennikiem.

Wektor regeneruje `python -m supply_chain_simulator --export-signature-vector [PATH]`;
plik jest deterministyczny i nie zawiera kluczy prywatnych. Zmiana jego treści
wymaga nowej wersji i zgody osób 1, 2 i 3.

Certyfikat stacji (DEC-13): `--export-certificate-fixture [PATH]` odtwarza wspólny fixture,
`--station-key-out PATH` generuje klucz stacji (plik 0600 poza repozytorium albo
w `infrastructure/local/secrets/`), a `--issue-certificate` wystawia poświadczenie kluczem
wystawcy ze zmiennej `--issuer-key-env`; wartości klucza nigdy nie wypisujemy.

Runner po `202` czeka na status (`CONFIRMED`, `outcome`) przed nowym krokiem; `REJECTED` nie przesuwa partii.
Timeout wymaga ponowienia identycznego żądania, nie losowania nowego raportu.
Wstrzykuj zegar/seed/klienta do testów; zakończenie pętli zamyka klienta HTTP.
Logi podają stację, commandId i status, nigdy klucz prywatny.

Klient HTTP (szkic, `client.py`): `submit_stage` wysyła `report`, `stationCertificateId`,
`reportHash`, `deadline` i `signature`; `get_command` czyta status polecenia.
Zgłoszenie buduje `build_stage_submission`, który porównuje każdą podpisaną wartość
z raportem i certyfikatem (`StageCompletion` innego typu niż `StageApproval`,
adres podpisującego, etap w `allowedStageIds`) - treść, która nie jest tym, co
podpisano, nie wychodzi na sieć. Retry powtarza identyczne bajty i dotyczy tylko
`429`, `5xx` oraz timeoutów (z poszanowaniem `Retry-After`); `4xx` nie są ponawiane.
Modele żądań są ścisłe (`extra="forbid"`), a modele odpowiedzi przyjmują dodatkowe
pola, bo odpowiedź należy do backendu. Nazwy pól odpowiedzi (`commandId`,
`statusUrl`, `outcome`, `retryAllowedAfter`) czekają na potwierdzenie w `openapi.yaml`.

Żądanie przekazuje stationCertificateId poza raportem. Certyfikat i para
kluczy są przypisane konkretnej stacji. Nie używaj jednego konta jako stacji
i operatora zmiany zamykającego kontrolę. Podpis `StageApproval` powstaje osobno.
