# Testy frontendowe

**Język:** TypeScript/TSX.

Plan: Vitest + React Testing Library dla komponentów, setup w `setup.ts`,
stuby odpowiedzi HTTP, Playwright dla pełnych widoków/ścieżek.
Testy pojedynczych komponentów mogą być obok ich kodu jako `*.test.tsx`.
Nie utrzymuj tych samych testów w dwóch miejscach.

Sprawdź loading/empty/error/pending/confirmed, odświeżanie po eventach,
QR parser, odmowę kamery (HTTPS), złą sieć, rejection portfela i reportHash mismatch.
Testuj także responsywny widok stacji z ręcznym ID i kontrolę z dwoma kluczami:
brak podpisu stacji, brak podpisu operatora, ten sam adres po obu stronach i approval
innego raportu nie mogą dawać sukcesu; `qaPassed=false` pokazuje `REJECTED`. Panel QA obsługuje operatora zmiany.
Używaj shared/fixtures oraz kontrolowanych zegarów, nie prawdziwej Pinaty.

E2E może korzystać z jawnie uruchomionego lokalnego API/Anvil i testowych kont.
Nie przechowuj w repozytorium profilu prawdziwej przeglądarki ani wallet seed.
Runner testów w CI jest jednorazowy, nie watch. Skrypt test trzeba dopiero dodać.
