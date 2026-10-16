# Budowa HRT Budzik TCL w chmurze - bez Android Studio

Repozytorium GitHub moze byc **prywatne**. Projekt Java/Android TV kompiluje sie na GitHub Actions i zapisuje testowy APK jako artefakt.

1. Otworz https://github.com/new. Nazwa np. `hrt-budzik-tcl`, wybierz Private, utworz repozytorium. Mozesz zaznaczyc Add README, jesli potrzebujesz przycisku `Add file`.
2. Rozpakuj ZIP na komputerze. Na stronie repozytorium wybierz `Add file` -> `Upload files`. Przeciagnij **zawartosc** rozpakowanego folderu, nie sam plik ZIP, zachowujac katalogi `app`, `gradle`, `.github` i pliki `build.gradle`, `settings.gradle`.
3. Zatwierdz przeslanie plikow przyciskiem `Commit changes`.
4. Wejdz do zakladki `Actions` -> `HRT Budzik - zbuduj APK` -> `Run workflow` -> zielony przycisk `Run workflow`. Jesli zadanie wystartowalo automatycznie po wyslaniu plikow, mozesz skorzystac z juz uruchomionego przebiegu.
5. Poczekaj na wynik budowy. Zielony symbol oznacza sukces kompilacji. W szczegolach tego przebiegu na dole znajdziesz `Artifacts` -> `HRT_Budzik_TCL_APK`. Pobierz ZIP z APK i rozpakuj.
6. Skopiuj `app-debug.apk` na pendrive lub wyslij przez ADB Wi-Fi, nastepnie zainstaluj na TCL, wlaczajac uprawnienia do instalacji APK z danego menedzera plikow.

Ta procedura **nie jest jeszcze potwierdzona udana kompilacja w GitHub Actions**. W razie bledu zbierz log z zakladki Actions, aby poprawic projekt.

WAZNE: Budzik standby pozostaje eksperymentalny. Skuteczne wybudzenie przez Cast nie dowodzi, ze alarm z `AlarmManager` uruchomi ekran i audio podczas standby. Nie stosuj jako jedynego budzika.

Bezpieczenstwo: nie wgrywaj publicznie wlasnych hasel, tokenow API ani plikow podpisywania. W tym projekcie nie ma takich danych. GitHub Actions uzywa klucza debug do testowej kompilacji.
