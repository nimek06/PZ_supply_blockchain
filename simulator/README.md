# Symulator IoT - osoba 4

**Język:** Python 3.11-3.12 (venv). **Biblioteki:** eth-account, Pydantic v2, httpx;
standardowy `random` lub Faker do danych; pytest do testów.

Osoba 4 odpowiada również za testy integracyjne/systemowe oraz dokumentację.
To symulacja programowa: nie instalujemy fizycznych czujników ani PLC.
Każda stacja ma własną parę kluczy i certyfikat JSON (poświadczenie audytowe, musi zgadzać się
z rejestrem stacji on-chain) zgodnie z PDF;
[tożsamość stacji](../docs/station-identity.md) opisuje format. Zmiany v2: [changes-v2](../docs/changes-v2.md) - wiążące.
Osoba 4 utrzymuje też narzędzie wystawcy certyfikatów, compose i wektory.

## Co utworzyć

`pyproject.toml` z układem `src`, zależnościami i entry pointem CLI.

- [src](src/README.md): instalowalny pakiet symulatora.
- [scenarios](scenarios/README.md): deklaratywne przebiegi stacji.
- [tests](tests/README.md): modele, podpisy, HTTP i tryby działania.

## Kolejność pisania

1. Zaimplementuj ścisły model raportu zgodny ze wspólnym schematem.
2. Dodaj deterministyczne pomiary w ustalonych jednostkach całkowitych.
3. Dodaj ładowanie certyfikatu, kanonizację i podpis EIP-712 `StageCompletion` (bez nonce); sprawdź wspólny wektor.
4. Dodaj klienta API z timeoutem, idempotentnym retry i odczytem statusu.
5. Uruchom jeden raport ręcznie, potem kolejny po potwierdzeniu pierwszego.
6. Dodaj tryb automatyczny, interaktywny i scenariusze błędów.

## Zasady

Generuj ogólną sekwencję z [production-rules](../docs/production-rules.md),
bez dopisywania konkretnego produktu lub losowych „norm przemysłowych”.
Prywatny status odczytuj według [logowania stacji](../docs/access-control.md).
Po wygaśnięciu `deadline` nowy raport tego samego etapu wolno zgłosić dopiero wg reguły zastępowania
z [changes-v2](../docs/changes-v2.md) (DEC-09 zamknięta, bez unieważniania nonce).

Pakiet nie uruchamia sieci przy imporcie. Konfigurację wczytuje CLI i przekazuje
do klienta/signera. Rozdziel generowanie raportu, podpisywanie i wysyłanie.
Pydantic ma odrzucać nieznane pola i niejawne konwersje, np. `"true"` na bool.
Seed pozwala odtworzyć scenariusz; timestamp jest parametrem testu.

Każda stacja ma własny lokalny klucz; `StageCompletion` nie ma nonce - replay chroni
`deadline`, domena EIP-712 i jedno ukończenie na `(batchId, stageId)`.
Klucze generuj lokalnie, przechowuj poza wersjonowanymi plikami z ograniczonymi
uprawnieniami. Nie używaj prawdziwych kluczy w fixture'ach ani logach.
Stacja musi zostać osobno zarejestrowana w rejestrze on-chain przez administratora
(`registerStation`: `stageMask`, `expiresAt`; `STATION_ROLE` nie istnieje).
Certyfikat wiąże stationId z adresem; backend weryfikuje jego wystawcę,
ważność, odwołanie i zgodność z rejestrem. Samo utworzenie klucza nie tworzy zaufanego certyfikatu.
Symulator nie posiada klucza operatora zmiany: test pełnej kontroli potrzebuje
drugiego konta/portfela, z innym adresem niż stacja raportu
(podpis `StageApproval` składa operator, nie symulator).
Raport z `qa_passed=false` kończy się `outcome=REJECTED` (`202`), nie `422`.
Numer partii to `B-RRRR-NNNN` nadawany przez backend.

Po timeout nie generuj nowego raportu: ponów tę samą treść
lub odczytaj status. Nie traktuj `202` jako zakończenia etapu.
Nie pinuj bezpośrednio w Pinacie/Kubo i nie korzystaj z klucza relayera.
Pętle scenariuszy z Pinatą: najpierw sprawdź limity planu (lokalnie/CI używa Kubo).

## Uruchomienie i testy po inicjalizacji

Utwórz i aktywuj `.venv` (Python 3.11-3.12), zainstaluj `python -m pip install -e ".[dev]"`.
Planowane CLI: `python -m supply_chain_simulator --help`; ma opisywać tryb,
scenariusz, backend URL, sieć/kontrakt, seed i konfigurację stacji.
Testy: `python -m pytest`. Zakończ CLI niezerowym kodem przy błędzie, nie tylko
wydrukiem komunikatu udającego poprawne zakończenie.
