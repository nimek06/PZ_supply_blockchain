# Pakiet symulatora

**Język:** Python 3.11-3.12, kod z adnotacjami typów.

Planowane moduły:
| Plik | Zakres |
|---|---|
| `__init__.py` | pakiet, bez efektów ubocznych |
| `__main__.py`, `cli.py` | argumenty, wybór trybu, exit codes |
| `config.py` | walidacja adresów, sieci, backend URL i sekretów |
| `identity.py` | klucz i certyfikat stacji, identyfikator poświadczenia |
| `models.py` | ścisłe modele Pydantic raportu i obwiedni |
| `telemetry.py` | deterministyczne pomiary i kontrolowane anomalie |
| `signing.py` | JCS, Keccak, typed data EIP-712 `StageCompletion` (bez nonce) i eth-account |
| `client.py` | HTTP, timeout, idempotentne retry, status polecenia |
| `runner.py` | kolejność kroków i tryb auto/interaktywny |

Najpierw model i test podpisu, potem HTTP, na końcu pętla/scenariusze.
Nie łącz całej aplikacji w jeden skrypt. Użyj API EIP-712 eth-account zgodnego
z przypiętą wersją; `encode_defunct` nie jest poprawnym zamiennikiem.

Runner po `202` czeka na status (`CONFIRMED`, `outcome`) przed nowym krokiem; `REJECTED` nie przesuwa partii.
Timeout wymaga ponowienia identycznego żądania, nie losowania nowego raportu.
Wstrzykuj zegar/seed/klienta do testów; zakończenie pętli zamyka klienta HTTP.
Logi podają stację, commandId i status, nigdy klucz prywatny.

Żądanie przekazuje stationCertificateId poza raportem. Certyfikat i para
kluczy są przypisane konkretnej stacji. Nie używaj jednego konta jako stacji
i operatora zmiany zamykającego kontrolę. Podpis `StageApproval` powstaje osobno.
