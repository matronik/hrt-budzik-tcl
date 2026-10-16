# HRT Budzik TCL v0.3 — lokalne ADB, wybudzanie bez komputera

## Co nowego

Na TCL 55C6KS wybudzanie wyslane z komputera przez `adb shell input keyevent 224` dziala,
a alarm ustawiony przez aplikacje uruchamia sie na telewizorze. Wersja v0.3 laczy obie rzeczy:
- alarm Androida odpala radio HRT;
- niezalezny klient ADB wewnatrz aplikacji laczy sie do `127.0.0.1:5555`;
- po autoryzacji aplikacja wysyla `input keyevent 224` (KEYCODE_WAKEUP).

**To eksperyment.** Zanim zobaczymy test na TCL, nie wiemy czy lokalne polaczenie
ADB jest akceptowane w standby i czy wyslany klawisz wlaczy fizyczny ekran.

## Krok 1 — aktualizacja repo GitHub

Przy korzystaniu z istniejącego repozytorium HRT Budzik v0.2:
1. Podmien w `app/src/main/java/pl/hrt/budzik/` pliki `AlarmReceiver.java`,
   `MainActivity.java`, `AlarmActivity.java`.
2. Dodaj w tym samym katalogu nowy plik `LocalAdbClient.java`.
3. Zmien `app/src/main/res/layout/activity_main.xml`.
4. Zmien `app/build.gradle` (versionCode 3).
5. W Actions poczekaj na build i pobierz artifact APK.

Mozesz tez uzyc pelnego archiwum projektu v0.3, w ktorym sa wszystkie pliki.
Nie musisz ponownie tworzyc ukrytego katalogu `.github` w starym repo.

UWAGA: GitHub Actions moze generowac inny klucz podpisu debug niz poprzednia kompilacja.
W takim przypadku instalacja aktualizacji nie powiedzie sie; odinstaluj stary
HRT Budzik, a potem zainstaluj v0.3. Wczesniejsze ustawienia i dziennik wtedy znikna.

## Krok 2 — ustawienia TCL

1. Wlacz **Debugowanie USB** w Opcjach programistycznych.
2. Ustaw tryb czuwania **Stan gotowosci sieci** lub **Bez ekranu**.
3. Telewizor ma dzialac w zaufanej sieci domowej (port TCP 5555 jest dostepny w LAN).

## Krok 3 — jednorazowa autoryzacja (WAZNE)

1. Otworz HRT Budzik v0.3 na WŁĄCZONYM telewizorze.
2. Nacisnij przycisk **Sprawdz / autoryzuj lokalne ADB**.
3. Jesli TV wyswietli pytanie `Zezwalac na debugowanie USB?` z nowym
   odciskiem RSA, zaznacz **Zawsze zezwalaj z tego komputera** (tym razem
   chodzi o KLUCZ WYGENEROWANY PRZEZ APLIKACJE), potem **Zezwalaj**.
4. Pierwsza proba moze zakonczyc sie timeoutem. Nacisnij test ponownie.
5. W dzienniku musi pojawic sie wpis podobny do:
   `TEST lokalnego ADB: ADB localhost: shell wykonany; wynik: 31`
   (liczba 31 to przykladowy poziom API TCL).

Nie zatwierdzaj nieznanych kluczy RSA w podejrzanych sytuacjach.

## Krok 4 — test budzika

1. Zatrzymaj reczne odtwarzanie radia.
2. Ustaw **Test za 2 min**.
3. Uspij TV krotkim przyciskiem Power (bez dlugiego przytrzymania).
4. Sprawdz czy TV uruchomi ekran i czy HR1 zagra samoczynnie.
5. Jezeli TV sie nie obudzi, wlacz go pilotem i przeslij zdjecie dziennika.

Szukamy wpisow:
- `ALARM OTRZYMANY`
- `Lokalny ADB: proba wyslania KEYCODE_WAKEUP przez 127.0.0.1:5555`
- `Lokalny ADB: ADB localhost: shell wykonany`
- `1200 ms po lokalnym ADB: interactive=true/false`

`ADB localhost: shell wykonany` oznacza, ze komenda zostala przekazana
usludze shell, niekoniecznie ze ekran fizycznie sie wybudzil.

## Bezpieczenstwo i ograniczenia

- Gdy wlaczone jest sieciowe ADB, port 5555 bywa otwarty na calej sieci lokalnej.
  Autoryzacja RSA jest wymagana, ale samo pozostawienie debugowania zmniejsza
  bezpieczenstwo odbiornika. Uzywaj tylko w zaufanej sieci i wylacz po testach,
  jesli nie chcesz korzystac z tej funkcji.
- Bez wlaczonego ADB aplikacja nie wybudzi TV ta metoda.
- Po factory reset, czyszczeniu danych aplikacji lub reinstalacji moze byc wymagana
  ponowna autoryzacja klucza.
- Po restarcie telewizora port 5555 moze przestac nasluchiwac i wymagac ponownego wlaczenia debugowania.
- Program nie otwiera ADB na zewnatrz i nie uruchamia skryptow roota; korzysta
  tylko z uwierzytelnionego localhost (127.0.0.1).
- Nie traktuj wersji 0.3 jako pewnego budzika zanim wiele testow w standby nie wyjdzie poprawnie.

## Weryfikacja

Kod `LocalAdbClient.java` zostal sprawdzony kompilatorem Java oraz
przetestowany w symulacji rozmowy ADB TCP: autoryzacja RSA,
format klucza publicznego, handshake, uruchomienie `shell:`.
Nie testowano pelnego APK na fizycznym TCL ani nie kompilowano Android SDK w tym srodowisku.
