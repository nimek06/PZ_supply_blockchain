# Scenariusze linii

**Język:** JSON, walidowany modelem Python.

Plan: `happy-path.json`, `qa-failure.json`, `out-of-order.json`,
`retry-after-timeout.json`. Opisz stacje, seed, kolejność kroków,
parametry/anomalie oraz oczekiwane wyniki. Nazwy etapów mapuj do uzgodnionej listy.
Nie dodawaj executable Python ani `eval` do danych scenariusza.

Scenariusze nie zawierają kluczy. Adres/sekret stacji pochodzą z konfiguracji.
Timestamp, nonce i podpis tworzy runner; zapisany stary podpis szybko wygasa.
Test negatywny ma jawnie określać spodziewany błąd i brak przejścia stanu.
Scenariusz retry powtarza tę samą treść, nie dwa losowe raporty.

Dodaj scenariusz kontroli z podpisem stacji i osobnym zatwierdzeniem operatora
zmiany oraz negatywne warianty bez drugiego klucza i z tym samym adresem.
Osobny przypadek obejmuje odwołany certyfikat stacji.
Podpisu operatora nie generuj kluczem stacji ukrytym pod inną nazwą.
