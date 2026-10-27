# Symulator IoT - osoba 4

**Język:** Python 3.11+. **Biblioteki:** eth-account, Pydantic v2, httpx;
standardowy `random` lub Faker do danych; pytest do testów.

Osoba 4 odpowiada również za testy integracyjne/systemowe oraz dokumentację.
To symulacja programowa: nie instalujemy fizycznych czujników ani PLC.
Każda stacja ma własną parę kluczy i certyfikat zgodnie z PDF;
[tożsamość stacji](../docs/station-identity.md) opisuje proponowany format.

## Co utworzyć

`pyproject.toml` z układem `src`, zależnościami i entry pointem CLI.

- [src](src/README.md): instalowalny pakiet symulatora.
- [scenarios](scenarios/README.md): deklaratywne przebiegi stacji.
- [tests](tests/README.md): modele, podpisy, HTTP i tryby działania.

## Kolejność pisania

1. Zaimplementuj ścisły model raportu zgodny ze wspólnym schematem.
2. Dodaj deterministyczne pomiary w ustalonych jednostkach całkowitych.
3. Dodaj ładowanie certyfikatu, kanonizację i podpis EIP-712; sprawdź wektor.
4. Dodaj klienta API z timeoutem, idempotentnym retry i odczytem statusu.
5. Uruchom jeden raport ręcznie, potem kolejny po potwierdzeniu pierwszego.
6. Dodaj tryb automatyczny, interaktywny i scenariusze błędów.

## Zasady

Generuj ogólną sekwencję z [production-rules](../docs/production-rules.md),
bez dopisywania konkretnego produktu lub losowych „norm przemysłowych”.
Prywatny status odczytuj według [logowania stacji](../docs/access-control.md).
Nowa autoryzacja po expiry/rewercie wymaga wyboru
[wariantu recovery](../docs/corrections.md); nie implementuj go za zespół.

Pakiet nie uruchamia sieci przy imporcie. Konfigurację wczytuje CLI i przekazuje
do klienta/signera. Rozdziel generowanie raportu, podpisywanie i wysyłanie.
Pydantic ma odrzucać nieznane pola i niejawne konwersje, np. `"true"` na bool.
Seed pozwala odtworzyć scenariusz; timestamp jest parametrem testu.

Każda stacja ma własny lokalny klucz i nonce aplikacyjny z kontraktu.
Klucze generuj lokalnie, przechowuj poza wersjonowanymi plikami z ograniczonymi
uprawnieniami. Nie używaj prawdziwych kluczy w fixture'ach ani logach.
Stacja musi zostać osobno zarejestrowana przez administratora.
Certyfikat wiąże stationId z adresem; backend weryfikuje jego wystawcę,
ważność i odwołanie. Samo utworzenie klucza nie tworzy zaufanego certyfikatu.
Symulator nie posiada klucza operatora zmiany: test pełnej kontroli potrzebuje
drugiego konta/portfela, z innym adresem niż stacja raportu.

Po timeout nie generuj nowego raportu z tym samym nonce: ponów tę samą treść
lub odczytaj status. Nie traktuj `202` jako zakończenia etapu.
Nie pinuj bezpośrednio w Pinacie i nie korzystaj z klucza relayera.

## Uruchomienie i testy po inicjalizacji

Utwórz i aktywuj `.venv`, zainstaluj `python -m pip install -e ".[dev]"`.
Planowane CLI: `python -m supply_chain_simulator --help`; ma opisywać tryb,
scenariusz, backend URL, sieć/kontrakt, seed i konfigurację stacji.
Testy: `python -m pytest`. Zakończ CLI niezerowym kodem przy błędzie, nie tylko
wydrukiem komunikatu udającego poprawne zakończenie.
