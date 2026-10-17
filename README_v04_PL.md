# HRT Budzik TCL v0.4 — instalacja i testy

Przygotowane dla TCL 55C6KS / Google TV. Wersja eksperymentalna, budzik nie gwarantuje punktualnosci.

## Co nowego
- Glosnosc odtwarzacza dla alarmu: stala albo narastanie start -> cel przez 1–60 min.
- Nowe ustawienie „Tylko dzwiek” po automatycznym wybudzeniu i uruchomieniu strumienia.
- Przycisk „Test wygaszenia teraz” do sprawdzenia menu TCL podczas odtwarzania.
- Zmiana: wersja aplikacji 0.4 (package nadal pl.hrt.budzik).

## Jak dziala
1. Alarm Androida uruchamia RadioService i lokalne ADB `input keyevent 224`.
2. Regulator ExoPlayer ustawia poczatkowa glosnosc i od stanu STREAM GOTOWY zwieksza ja co sekunde.
3. Jesli zaznaczono „Tylko dzwiek”, ok. 5 s po uzyskaniu stanu STREAM GOTOWY aplikacja sprawdza interactive i wysyla **dokladnie testowana na tym telewizorze** sekwencje lokalnego ADB.
4. TCL wlacza „Tylko dzwiek”; ekran gasnie, a radio powinno nadal grac.

## Uwaga o glosnosci
Wartosci 0–100% steruja gain ExoPlayer. To NIE JEST glosnosc systemowa TCL np. poziom 15/100 na pilocie. Ustaw glosnosc telewizora pilotem na bezpiecznym, komfortowym poziomie, nastepnie reguluj poziom i narastanie w aplikacji. Test „Odtworz teraz” uzywa poziomu docelowego, bez narastania. Narastanie wlacza sie przy alarmach.

## Ograniczenia
- Uzywa lokalnego ADB `127.0.0.1:5555`: **Debugowanie USB** musi pozostac wlaczone, a klucz aplikacji autoryzowany.
- Sekwencja wygaszania ma wspolrzedne sprawdzone na TCL 55C6KS 1920x1080. Zmiana ukladu TCL po aktualizacji moze spowodowac klikniecie w inne menu. Z tego powodu ekran jest domyslnie nie wygaszany (checkbox OFF).
- ADB moze przestac dzialac po restarcie TV lub zmianie ustawien debugowania.
- `setAlarmClock()` bywa opozniany przez firmware TCL o kilka minut.
- Nalezy zostawic ustawienia: standby sieciowe lub bez ekranu; TV w standby (krotkie POWER).
- Port 5555 nasluchuje w sieci LAN i stwarza ryzyko bezpieczenstwa. Uzywaj tylko w zaufanej sieci domowej.
- Nie testowano pelnej wersji 0.4 na fizycznym telewizorze. W razie niepowodzenia zrzut dziennika bedzie potrzebny.

## Pierwszy test
1. Zbuduj `:app:assembleDebug` przez dotychczasowy GitHub Actions.
2. Zainstaluj na TCL. Gdy podpis nowego APK rozni sie od poprzedniej kompilacji, Android wymaga odinstalowania starej aplikacji (tracisz ustawienia i klucz RSA — trzeba ponownie autoryzowac lokalne ADB).
3. Uruchom program, przetestuj HR1, wylacz radio.
4. Ustaw start 5%, cel 45%, czas 3 min, zaznacz narastanie oraz „Tylko dzwiek”; kliknij „Zapisz ustawienia”.
5. Zaplanuj test za 2 min, wylacz TV krotkim POWER.
6. Oczekuj na alarm (moze nastapic opoznienie o kilka minut), radio i wygaszenie ekranu. Jesli nie, wroc do programu i wyslij dziennik.

## GitHub
Przeslij pliki z ZIP `TYLKO_ZMIANY` zachowujac sciezki. Zmiany sa w:
- `app/src/main/java/pl/hrt/budzik/LocalAdbClient.java`
- `app/src/main/java/pl/hrt/budzik/TclAudioOnly.java` (NOWY)
- `app/src/main/java/pl/hrt/budzik/RadioService.java`
- `app/src/main/java/pl/hrt/budzik/MainActivity.java`
- `app/src/main/res/layout/activity_main.xml`
- `app/build.gradle`
Nie trzeba aktualizowac `.github/workflows/build-apk.yml`.
