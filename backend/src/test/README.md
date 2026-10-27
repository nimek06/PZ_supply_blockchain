# Testy backendu

**Języki:** Java 21 oraz YAML/JSON dla zasobów.

- [java](java/README.md): testy JUnit.
- [resources](resources/README.md): profil testowy i konfiguracja stubów.

Rozdziel szybkie testy domeny od integracyjnych. Jeżeli używasz sufiksu `IT`,
skonfiguruj Maven Failsafe, żeby `verify` rzeczywiście je wykonywało;
testy `*Test` domyślnie obsługuje Surefire.
