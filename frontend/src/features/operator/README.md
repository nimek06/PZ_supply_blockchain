# Panel operatora

**Język:** TypeScript/TSX.

Plan: `OperatorPage.tsx`, wyszukiwanie/skaner partii, podsumowanie etapu,
formularze dozwolonych operacji i status polecenia.
Korzystaj z hooks batches i komponentów QR/timeline.

Zacznij od odczytu partii; następnie dodaj autoryzowane tworzenie partii,
jeśli operator ma tę rolę. Podpisaną telemetrię dostarcza stacja/symulator:
przeglądarka nie może jej sfabrykować kluczem relayera.
Jeśli wymagane będą ręczne raporty operatora, najpierw ustal osobny protokół.

Po `202` blokuj przypadkowe duplikaty, ale pozwalaj odzyskać status po odświeżeniu.
Walidacja klienta jest UX, nie zamiennikiem backendu. Testuj brak uprawnień,
złą partię, konflikt etapu i failure polecenia.

Panel ma umożliwiać zmianę statusu detalu zgodnie z PDF: wskazanie partii
ręcznie/QR, wybór dozwolonej operacji i podgląd jej wyniku.
Zamknięcie kontroli prowadzi do widoku QA, gdzie operator zmiany podpisuje
`StageApproval` dla raportu stacji własnym, niezależnym kluczem.
Nie dodawaj przycisku ustawiającego dowolny status w bazie.
