# SRG Mobile v1.4.17 — naprawa natywnego GPS Androida

## Przyczyna błędu w APK v1.4.16

Wersja v1.4.16 startowała dzięki kodowi APK v1.3.5, ale **nie posiadała
natywnego mostka GPS ani pozwolenia ACCESS_FINE_LOCATION**. HTML otwarty jako
`file:///android_asset/index.html` wyświetlał więc „GPS ZABLOKOWANY W PLIKU”.
Tego błędu nie da się naprawić przez zmianę CSS, przycisku ani przez samo
udzielenie uprawnień aplikacji, która ich nie deklaruje.

## Co zawiera źródło v1.4.17

- Ten sam HTML/kursówki/dane RJ co w uruchamiającej się v1.4.16
- Natywny mostek `SRGNativeGPS` udostępniany przez `addJavascriptInterface`
- `ACCESS_FINE_LOCATION` i `ACCESS_COARSE_LOCATION` w manifeście
- Prośba o dokładną lokalizację po naciśnięciu START GPS
- Dane z systemowego `LocationManager.GPS_PROVIDER` -> `onNativeGpsFix(...)`
- Rozróżnienie stanów: uprawnienie / wyłączony GPS / brak satelitów / pozycja
- GPS bez połączenia internetowego, bez lokalizacji w tle

Ważne: to projekt źródłowy, **nie gotowy APK**. Nie kompilowano go ani nie
uruchamiano na prawdziwym Androidzie w tym środowisku.

## Budowa na Windows / Mac / Linux

1. Otwórz folder w Android Studio z Android SDK 35 i JDK 17.
2. Poczekaj na synchronizację Gradle (AGP 8.7.3 / Gradle 8.9).
3. **Build → Build APK(s)**.
4. Gotowy debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
5. Zainstaluj na tablecie, uruchom, kliknij START GPS i zezwól na **dokładną** lokalizację.
6. Włącz lokalizację systemową. Pierwsza pozycja satelitarna wymaga widocznego nieba.

Nie odinstalowuj działającej wersji 1.3.5 przed przetestowaniem nowego APK.

## Test akceptacyjny

1. Aplikacja uruchamia się do klasycznego ekranu startowego.
2. KURS → S-25/S-27 → Zatwierdź działa.
3. GPS START pokazuje stan START GNSS, a następnie POZYCJA po otrzymaniu fix.
4. GPS STOP zatrzymuje odbiór.
5. Po odmowie uprawnień pojawia się komunikat o ich braku (nie „GPS ZABLOKOWANY W PLIKU”).
6. Przejście do tła zatrzymuje GPS; powrót do aplikacji wznowi nasłuch, jeżeli był włączony.

## Build automatyczny przez GitHub Actions

Po umieszczeniu projektu jako repozytorium GitHub workflow
`.github/workflows/android-apk.yml` pozwala kompilować debug APK
na standardowym środowisku Android SDK, bez lokalnej instalacji narzędzi.
Archiwum APK można pobrać z artefaktów wykonania workflow.
