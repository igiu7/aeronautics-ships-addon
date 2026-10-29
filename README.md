# Aeronautics Valoris

Dodatek do **Create Aeronautics / Create Simulated** działający na **Sable** (NeoForge 1.21.1).

## Co robi

**Ciąg z żagli.** Każdy żagiel na statku (sub-levelu Sable) daje mały ciąg w stronę swojego przodu.
Liczą się żagle postawione bezpośrednio na statku oraz żagle na kontrapcjach (np. mechanical bearing) postawionych na statku.
Ramki żagli (sail frame) i śmigła z Aeronautics nie dają ciągu z wiatru.
Całkowity ciąg = `baseThrustPerSail × liczba_żagli^sailCountExponent × mnożnik_wiatru`.

**Przód żagla.**
- Zwykły żagiel Create: przód to strona *od gracza* w chwili stawiania (Create i tak zapisuje kierunek). Klucz go obraca.
- Symmetric Sail: dostaje nową właściwość `front_positive`.
- Każdy żagiel (też dostawiany do innego żagla przez placement helper) dostaje przód w stronę, w którą patrzy gracz, który go stawia. Na statku kierunek liczony jest w układzie statku.
  **Klucz (Wrench) w płaską stronę** odwraca przód/tył. Klucz w krawędź obraca jak zwykle, Shift+klucz podnosi blok.
- Po postawieniu/odwróceniu dostajesz komunikat z kierunkiem. Gdy patrzysz na żagiel, widzisz złotą strzałkę
  z przodu i napis pod celownikiem z kierunkiem i aktualnym mnożnikiem wiatru.

**Wiatr w stylu Sea of Thieves.**
- Jeden globalny wiatr na wymiar (z niebem). Co 15 minut wybiera nowy kierunek i stopniowo do niego skręca (1.5°/s). Deszcz i burza go wzmacniają.
- Gęste białe smugi wiatru pojawiają się tylko wokół żagli statków w pobliżu (serwer co sekundę wysyła pozycje żagli, także tych na łożyskach). Falują i czasem robią pętlę (własny renderer, bez dodatkowych bibliotek).
- Mnożnik ciągu zależy od kąta między **przodem żagla a wiatrem**:
  wiatr w plecy żagla → do ×2.5, bokiem → ~×1, prosto w przód → ×0.15 (przy pełnej sile wiatru; przy słabym wietrze dąży do ×1).

**Żagle na łożysku (jak reje w SoT).** Statek zawsze płynie w stronę, w którą żagiel patrzył przy złożeniu kontrapcji
(czyli "przód statku"). Obrót łożyska nie zmienia kierunku ciągu, tylko to, ile wiatru złapiesz.
Ustaw łożysko tak, żeby przód żagli był jak najbardziej z wiatrem. Obrót ponad `maxSailTurnAngle` (90°) nic już nie daje,
więc nie da się płynąć pod wiatr, odwracając żagle tyłem.
Obrócone żagle nie przechylają statku na burtę: moment od aerodynamiki Sable dla żagli jest wyłączony (`sailAerodynamicTorque = 0`), a opór i dryf zostają.

## Ster (Rudder)

Blok **Ster** to blok kinetyczny Create. Jest w zakładce Aeronautics Valoris, receptura: `andesite casing + shaft / kłoda + deski / kłoda + deski` (`AS / LP / LP`).

- **Wał wchodzi od góry.** Podłącz go do **Steering Wheel** z Create Simulated (wał w dół do steru, przez przekładnie, jeśli trzeba).
- Steering Wheel obraca wał o tyle stopni, o ile obrócisz koło. Ster liczy obrót wału i skaluje go:
  `wychylenie = obrót_wału / shaftDegreesForFullDeflection × maxDeflection`. Domyślnie ±180° koła daje ±45° płetwy.
  Jeśli w kole ustawisz inny limit kąta (scroll), ustaw tę samą wartość w configu `shaftDegreesForFullDeflection`.
  Przekładnie zmieniające prędkość (np. duże i małe koło zębate) też zmieniają skalę.
- **Stawianie:** płetwa zawsze w stronę gracza, czyli tę stronę bloku, z której na niego patrzysz. Przód statku to strona przeciwna (tam jest wspornik do kadłuba).
- Działa tylko **w wodzie** (blok steru albo blok pod nim w wodzie). Skręca już od 0 m/s.
- Skręt: `ω = maxTurnRate × skuteczność × (s + (1−s) × v / (v + speedForHalfTurnRate))`, gdzie `s = stationaryTurnFactor` (35% skrętu dostępne w miejscu).
  Domyślnie maxTurnRate = 40°/s. Szybciej oznacza szybszy obrót, ale promień koła `r = v/ω` rośnie z prędkością.
  Skuteczność jest największa przy wychyleniu 45°. Moment jest ograniczony (`torquePerSpeed × liczba sterów × (1 + v)`), więc ciężkie statki reagują wolniej.
- Na wstecznym ster działa odwrotnie i słabiej.
- Jeśli koło skręca statek w złą stronę: `invertSteering = true` w sekcji `[rudder]` server configu.
- Ster śledzi dokładny obrót koła (tak jak Mechanical Bearing), więc nie rozjeżdża się po wielu skrętach. Jeśli płetwa nie stoi prosto przy kole na 0°, kliknij ster **kluczem** (bez Shifta), żeby go wyśrodkować.
- Ster na mechanical bearingu też działa (obrót bearinga to wychylenie), ale zalecany jest wał ze Steering Wheel.

## Kotwica (Anchor Winch)

Blok **Kabestan z kotwicą**, receptura: `łańcuch` na górze, `deski / kłoda / deski` w środku, `sztabka / blok żelaza / sztabka` na dole.

- **Stawianie:** stań na pokładzie i patrz w stronę burty, za którą ma iść kotwica. Belka z kołem linowym wystaje właśnie tam. Najlepiej stawiać przy samej krawędzi pokładu.
- **Prawy klik:**
  - kotwica schowana → zrzut. Kotwica opada na linie (`dropSpeed`, 8 bloków/s) pod kołem linowym i płynie razem ze statkiem.
  - kotwica opada albo leży na dnie → wciąganie. Trwa `głębokość / raiseSpeed` (1.5 bloku/s), np. 15 bloków to 10 s. Czas pokazuje się nad paskiem.
  - podczas wciągania → ponowny zrzut.
- **Na dnie:** kotwica leży nieruchomo, żagle tego statku przestają pchać, a statek zostaje **całkowicie zatrzymany**. Prędkość pozioma i obrót są wygaszane, a statek jest ściągany z powrotem nad miejsce chwycenia, więc wiatr i żagle go nie przesuną. Pion i przechyły zostają fizyce (High Seas / Sable).
- Jeśli lina (`maxRopeLength`, 128) skończy się przed dnem, kotwica tylko wisi i nie trzyma.
- Kotwica to encja; lina jest rysowana od jej ucha do koła linowego kabestanu i podąża za statkiem.
- Zniszczenie kabestanu usuwa kotwicę.

## Stabilność

- **Stabilizator** (statki z żaglami albo sterem, unoszące się na wodzie): prostuje przechył na burtę i na dziób/rufę i tłumi kołysanie, jak ciężki balast w kilu. Nie dotyka obrotu w poziomie, więc skręcanie działa normalnie. Do 3° przechyłu zostaje (fale High Seas).
- **Kil** (gdy ster jest w wodzie): boczny poślizg jest wygaszany w środku masy, a jego część zamienia się w ruch do przodu. Statek w zakręcie płynie za dziobem, zamiast sunąć bokiem i kłaść się na burtę.
- Ustawienia są w sekcji `[stability]`.

## Komendy

- `/wind` pokazuje aktualny wiatr.
- `/wind set <kurs> [siła]` (op) ustawia i blokuje wiatr. Kurs to strona, w którą wieje: 0 = N, 90 = E, 180 = S, 270 = W. Siła 0–1.
- `/wind random` (op) odblokowuje i losuje nowy wiatr.

## Konfiguracja

- `serverconfig/aeronauticsvaloris-server.toml` (w folderze świata) zawiera ciąg, mnożniki i zachowanie wiatru.
  Na start warto ruszać `baseThrustPerSail`, `windMaxMultiplier` i `applyThrustAtCenterOfMass`.
- `config/aeronauticsvaloris-client.toml` zawiera smugi, HUD i strzałkę.

Oba pliki można też edytować w grze: Mods → Aeronautics Valoris → Config.

## Budowanie

1. Skopiuj zawartość do swojego projektu MDK (zostaw swój `gradlew`, `gradle/wrapper`).
   Jeśli Twoje MDK ma inną wersję pluginu `net.neoforged.moddev`, zostaw swoją.
2. Sable i jego API pobierają się z `maven.blamejared.com` (`sable_version` w `gradle.properties` ustaw na wersję z instancji).
   Jeśli maven nie działa, wrzuć jary Sable (+ sable-companion, veil) do `libs/`.
3. Do `runClient` wrzuć jary Create, Create Aeronautics (bundled) i Sable do `run-mods/`.
4. `./gradlew build` → `build/libs/aeronauticsvaloris-1.0.0.jar`.

Wymagane w grze: Create, Create Simulated/Aeronautics, Sable.
