# HRT Budzik TCL — TEST 0.1

Aplikacja na TCL 55C6KS / Google TV. Trzy stacje HR1, HR2, HR3 oraz własny URL. Test budzenia ze standby po 2 lub 15 minutach. Docelowy alarm na wybrane dni i godzinę.

**Wersja eksperymentalna. Nie używaj jako jedynego budzika.** Chromecast/YouTube może wybudzić TV przez Wi-Fi, ale nie dowodzi to możliwości wybudzania ekranu przez Android AlarmManager. Wynik trzeba przetestować na telewizorze. Nie ma gwarancji ani wybudzenia ekranu, ani odtwarzania dźwięku w stanie czuwania TCL.

## Co jest zaimplementowane

- Nawigacja pilotem, aplikacja w menu Google TV (Leanback).
- Stacje HRT1, HRT2, HRT3 (AAC) i własny URL HTTPS/HTTP.
- Odtwarzacz ExoPlayer/Media3 i zatrzymanie przyciskiem.
- Dokładne alarmy `AlarmManager.setAlarmClock`: jednorazowy test za 2 / 15 minut, lub alarm tygodniowy.
- `BroadcastReceiver` i `ForegroundService` typu mediaPlayback. Próba włączenia ekranu poprzez powiadomienie alarmowe full-screen.
- Dziennik: czy alarm dotarł do BroadcastReceiver, czy odtwarzacz wystartował, czy pełnoekranowe Activity zostało pokazane, oraz ewentualny błąd strumienia.
- Odtworzenie alarmu tygodniowego po restarcie systemu / zmianie czasu, o ile Android dostarczy odpowiednie zdarzenie.
- Głośność odtwarzacza 60% bazowego poziomu urządzenia, sterowanie poziomem TV pilotem.

## Zbudowanie APK w Windows (Android Studio)

1. Zainstaluj Android Studio z Android SDK Platform 35 i Java/JDK 17 (Android Studio zawiera JBR).
2. Rozpakuj projekt, otwórz PowerShell w katalogu projektu i uruchom `powershell -ExecutionPolicy Bypass -File .\build_windows.ps1`. Skrypt pobiera **oficjalny Gradle 8.9** z services.gradle.org, a zależności Android z Google/Maven Central. Potrzebny internet. Alternatywnie otwórz katalog projektu w Android Studio i zsynchronizuj Gradle, ustawiając wersję Gradle 8.9.
3. Wynik kompilacji: `HRT_Budzik_TCL_debug.apk` w katalogu projektu. Podpisany automatycznym kluczem debug (do instalacji testowej).
4. Zainstaluj APK na TCL przez ADB (`adb install -r HRT_Budzik_TCL_debug.apk`) lub menedżer plików obsługujący instalację APK. Na TV może być konieczne pozwolenie na instalację z nieznanych źródeł.

## Pierwszy test

1. Na TCL pozostaw ustawienie `Zasilanie i energia -> Tryb czuwania -> Stan gotowości sieci` (tak już działa Cast). Wyłączaj tylko krótkim Power.
2. Otwórz **HRT Budzik**. Wybierz **Uprawnienia**, przyznaj `Alarmy i przypomnienia`, `Powiadomienia` oraz, jeśli dostępne, `Pełnoekranowe intencje`.
3. Wybierz `HR1` i **Odtwórz teraz**; sprawdź, czy gra. W razie błędu spróbuj z alternatywnym URL.
4. Wybierz **Test za 2 minuty** i sprawdź informację `Następny alarm: ...`. Krótko naciśnij Power na pilocie (standby).
5. Poczekaj >= 3 minuty. Sprawdź, czy radio gra i czy ekran się uruchomił. Jeśli nie — włącz TV pilotem, uruchom HRT Budzik i odczytaj dziennik.
6. Jeśli test 2 minut działa, wykonaj następnie test 15 minut, a później kilkugodzinny test. Dopiero potem używaj harmonogramu tygodniowego.

## Interpretacja logów

- `ALARM OTRZYMANY` = Android wykonał alarm nawet jeśli TV nie uruchomił ekranu.
- `Zażądano uruchomienia RadioService` = wysłano polecenie uruchomienia usługi.
- `STREAM GOTOWY / odtwarzanie` = dekoder streamu osiągnął READY (nie gwarantuje, że fizycznie słyszalny podczas standby).
- `ALARM ACTIVITY WIDOCZNA` = próba ekranowego alarmu dotarła do aktywności (może nie oznaczać faktycznego zaświecenia matrycy).
- `BŁĄD ...` = w aplikacji zapisany błąd uruchomienia, uprawnień lub streamu.

## Ograniczenia

- Możliwe, że alarm obudzi CPU, ale TCL nadal nie uruchomi matrycy i/lub wzmacniacza audio. Bez uprawnień systemowych nie da się zagwarantować pełnego wybudzenia sprzętu.
- Rejestracja alarmów po ponownym uruchomieniu systemu zależy od polityk firmware i dostępności dokładnych alarmów.
- Dla tej wersji używamy publicznego API Androida, bez roota, bez ingerencji w firmware TCL.
- Wersja 0.1 nie zawiera fade-in ani automatycznego gaszenia ekranu podczas radia.
- Aplikacji nie testowano jeszcze na fizycznym TCL; może wymagać poprawek po pierwszych testach.

## Projekt

Android (Java 17), Gradle 8.9, Android Gradle Plugin 8.7.3, minSdk 26, targetSdk 34, Media3 ExoPlayer 1.5.1.
Brak biblioteki UI poza platformowym Androidem. Bez serwerów, kont i zewnętrznego sterownika.
