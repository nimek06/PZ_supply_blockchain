# Pakiet symulatora

**Język:** Python 3.11-3.12, kod z adnotacjami typów.

Moduły pakietu (stan 2026-10-31 - gotowe: `__init__`, `__main__`, `cli`, `models`,
`canonical`, `signing`, `vector`, `certificate`; pozostałe planowane):
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
| `telemetry.py` | deterministyczne pomiary i kontrolowane anomalie |
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

Żądanie przekazuje stationCertificateId poza raportem. Certyfikat i para
kluczy są przypisane konkretnej stacji. Nie używaj jednego konta jako stacji
i operatora zmiany zamykającego kontrolę. Podpis `StageApproval` powstaje osobno.
