# Oś czasu produkcji

**Język:** TypeScript/TSX.

Plan: `BatchTimeline.tsx`, `StageTimelineItem.tsx`, testy obok.
Przyjmij typowaną historię i osobny status oczekującego polecenia.
Pokazuj etap, stację, czas, rezultat, CID, reportHash i odnośnik do transakcji
w zakresie przewidzianym dla publicznego użytkownika.

Sortowanie jest stabilne według pozycji logu, nie lokalnego czasu przeglądarki.
Nie oznaczaj pending jako completed. Obsłuż brak historii, błąd odczytu,
nieaktualną projekcję oraz błąd integralności raportu.
Nie pobieraj historii bezpośrednio z komponentu; query hook należy do batches.
