# Build & kiadás a Google Play Console alá (tesztelésre)

Ez a leírás lépésről lépésre végigvezet, hogyan fordítsd le a **Nagyító** alkalmazást és hogyan juttasd fel a **Google Play Console** egy tesztsávjára (internal / closed testing) — külön a **Windows + VSCode** és a **Linux + Android Studio** munkafolyamatra.

A leírás feltételezi a programozás alapjainak ismeretét, de az Android-build folyamatot részletesen elmagyarázza.

---

## 0. A projekt tényei (amit tudni érdemes)

| Tulajdonság | Érték | Hol van definiálva |
|---|---|---|
| Csomagnév (`applicationId`) | `com.aistudio.magnifier.nwzkpq` | `app/build.gradle.kts` |
| `versionCode` / `versionName` | `30` / `30.0` | `app/build.gradle.kts` |
| `minSdk` / `targetSdk` / `compileSdk` | 24 / 35 / 36.1 | `app/build.gradle.kts` |
| Kimenet a Play-hez | **AAB** (Android App Bundle, `.aab`) | — |
| Release aláírás forrása | `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` **környezeti változók**, fix `upload` kulcs-alias | `app/build.gradle.kts` `signingConfigs.release` |
| Kód-tömörítés release-ben | R8 **be** (`isMinifyEnabled = true`, `isShrinkResources = true`) | `app/build.gradle.kts` |

Fontos következmények:
- **Nincs Gradle wrapper a repóban** (`gradlew` / `gradlew.bat` hiányzik) — ezt a **0.b lépésben** pótolni kell, különben nincs egységes build-parancs.
- **Nincs commitolt keystore** (helyesen — titkos). Neked kell létrehoznod egy *upload keystore*-t (**1. lépés**).
- A `keyAlias` a build-fájlban fixen **`upload`** — a keystore-ban PONTOSAN ilyen nevű kulcs kell legyen.
- Nincs szükség Gemini/Firebase API-kulcsra: ezek a függőségek el lettek távolítva, az app hálózat nélkül működik.

> ⚠️ **Az `applicationId` az első Play-feltöltés után VÉGLEGES** — nem lehet később megváltoztatni. A jelenlegi `com.aistudio.magnifier.nwzkpq` egy generált placeholder. Ha „szép" csomagnevet szeretnél (pl. `com.tigra.nagyito`), **most, az első feltöltés ELŐTT** írd át az `app/build.gradle.kts`-ben, és a `namespace`-t is érdemes egységesíteni. (Konfidencia: magas — ez a Play egyik dokumentált, megváltoztathatatlan mezője.)

---

## 1. Közös előfeltétel: upload keystore létrehozása (egyszeri)

A Play-re feltöltött csomagot alá kell írni. A modern Play **Play App Signing**-et használ: te egy *upload key*-jel írsz alá, a Google pedig a saját *app signing key*-ével írja alá a felhasználókhoz kikerülő verziót. Neked tehát egy **upload keystore**-t kell készítened — egyetlen egyszer, és utána gondosan meg kell őrizned.

A `keytool` a JDK része (lásd a JDK-telepítést lentebb). Futtasd a projekt gyökerében:

```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

Windows PowerShellben egy sorban (a `\` helyett):
```powershell
keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

- Az `-alias upload` **kötelezően `upload`** legyen (a build-fájl ezt keresi).
- Kér egy **store password**-öt és egy **key password**-öt (lehet ugyanaz) — jegyezd meg őket, ezek lesznek a `STORE_PASSWORD` / `KEY_PASSWORD`.
- A `-validity 10000` ~27 év érvényesség (a Play elvárja a hosszú lejáratot).

> 🔒 **Biztonsági mentés:** a `my-upload-key.jks` fájlt és a jelszavakat tedd biztonságos helyre (pl. jelszókezelő). Ha elveszted, az upload key visszaállítható a Play support segítségével, de kényelmetlen. A `.jks` fájlt **soha ne commitold** — a `.gitignore` a `local.properties`-t és a `debug.keystore`-t már kizárja, de az upload keystore-t is tartsd a repón kívül vagy vedd fel a `.gitignore`-ba.

---

## 2. Közös előfeltétel: a signing környezeti változók beállítása

A release build a három környezeti változóból olvassa az aláírás adatait. Állítsd be őket **abban a shellben / úgy, ahonnan a buildet indítod** (VSCode terminál, vagy az Android Studiót indító shell).

**Linux / macOS (bash/zsh):**
```bash
export KEYSTORE_PATH="/abszolut/ut/my-upload-key.jks"
export STORE_PASSWORD="a_store_jelszo"
export KEY_PASSWORD="a_key_jelszo"
```
(Tartóssá tételhez tedd a `~/.bashrc` / `~/.zshrc` fájlba — de akkor a jelszó ott lesz olvasható; mérlegeld.)

**Windows (PowerShell) — csak az aktuális munkamenetre:**
```powershell
$env:KEYSTORE_PATH = "C:\keys\my-upload-key.jks"
$env:STORE_PASSWORD = "a_store_jelszo"
$env:KEY_PASSWORD   = "a_key_jelszo"
```

**Windows — tartósan (új terminálokban is él, `setx`):**
```powershell
setx KEYSTORE_PATH "C:\keys\my-upload-key.jks"
setx STORE_PASSWORD "a_store_jelszo"
setx KEY_PASSWORD "a_key_jelszo"
```
> A `setx` csak az **ezután** indított terminálokra hat, a jelenlegire nem. `setx` után nyiss új terminált.

Ha a `KEYSTORE_PATH` nincs beállítva, a build a `my-upload-key.jks`-t keresi a projekt gyökerében. Ha a jelszavak hiányoznak, a release build hibával áll le — ez normális jelzés, hogy a változók nincsenek beállítva.

---

## A) Windows + VSCode munkafolyamat

A VSCode nem hoz magával Android-eszközöket, ezért ezeket kézzel kell telepíteni.

### A.1. JDK telepítése
- Telepíts **JDK 17-et vagy újabbat** (pl. [Temurin/Adoptium](https://adoptium.net)). A projekt Gradle 9.x-et használ, ami JDK 17+ igényel. (Konfidencia: magas — a build JDK 17–25 alatt igazoltan fordul.)
- Ellenőrzés: `java -version` és `keytool -help` működik-e.

### A.2. Android SDK telepítése (Android Studio nélkül)
1. Töltsd le a **Command-line Tools**-t: <https://developer.android.com/studio#command-line-tools-only>.
2. Csomagold ki egy mappába, pl. `C:\Android\cmdline-tools\latest\` (a `bin`, `lib` stb. közvetlenül a `latest` alatt legyen).
3. Fogadd el a licenceket, és telepítsd a szükséges csomagokat (PowerShell):
   ```powershell
   $env:ANDROID_HOME = "C:\Android"
   & "C:\Android\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
   & "C:\Android\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36" "platforms;android-36.1" "build-tools;36.0.0" "platform-tools"
   ```
   > A `compileSdk = 36.1` miatt kell a `platforms;android-36.1` és `android-36` is.

### A.3. A projekt SDK-útjának megadása
Hozz létre egy `local.properties` fájlt a projekt gyökerében (a `.gitignore` már kizárja):
```properties
sdk.dir=C:\\Android
```
(A kettős backslash Windowson szándékos.)

### A.4. Gradle wrapper pótlása (0.b lépés — egyszeri)
Mivel a repóban nincs wrapper, telepíts egy Gradle-t, és generáld le:
1. Telepítsd a Gradle-t (pl. [Gradle 9.6.1](https://gradle.org/releases/) kézzel kicsomagolva és PATH-ra téve, vagy csomagkezelővel: `choco install gradle` / `scoop install gradle`).
2. A projekt gyökerében:
   ```powershell
   gradle wrapper --gradle-version 9.6.1
   ```
   Ez létrehozza a `gradlew`, `gradlew.bat` és `gradle/wrapper/` fájlokat. **Érdemes commitolni** — utána már mindenki a `gradlew`-t használja, külön Gradle-telepítés nélkül.

### A.5. Build: AAB készítése a Play-hez
A signing környezeti változók (2. lépés) beállítása után, a projekt gyökerében:
```powershell
.\gradlew.bat :app:bundleRelease
```
- Kimenet: **`app\build\outputs\bundle\release\app-release.aab`** — ezt töltöd fel a Play Console-ra.

Ha wrapper helyett a rendszer-Gradle-t használod, akkor `gradle :app:bundleRelease`.

### A.6. (Opcionális) Release APK gyors eszköz-teszthez
Ha nem a Play-en át, hanem közvetlenül a telefonodra akarod tenni:
```powershell
.\gradlew.bat :app:assembleRelease
```
- Kimenet: `app\build\outputs\apk\release\app-release.apk`
- Telepítés USB-n át (Developer options → USB debugging bekapcsolva):
  ```powershell
  & "C:\Android\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk
  ```

---

## B) Linux + Android Studio munkafolyamat

Az Android Studio (AS) magával hozza a Gradle-t és az SDK-kezelőt, így kevesebb a kézi lépés.

### B.1. Projekt megnyitása
1. Android Studio → **Open** → válaszd a projekt gyökérmappáját.
2. Az AS felajánlja a hiányzó SDK-komponensek telepítését — fogadd el. A **SDK Manager**ben (Settings → Languages & Frameworks → Android SDK) győződj meg róla, hogy telepítve van:
   - **Android SDK Platform 36** és **36.1** (a `compileSdk` miatt),
   - **Android SDK Build-Tools 36.0.0**,
   - **Android SDK Platform-Tools**.
3. A `local.properties`-t (SDK-út) az AS automatikusan létrehozza.

### B.2. Gradle wrapper pótlása (0.b lépés — egyszeri)
Mivel a repóban nincs wrapper, az AS a beépített Gradle-jét használja, de a terminálos build-hez érdemes wrappert generálni. Az AS alsó **Terminal** paneljében:
```bash
gradle wrapper --gradle-version 9.6.1   # ha van rendszer-gradle
# vagy AS-en belül a beépített Gradle-lel:
./gradlew wrapper --gradle-version 9.6.1 2>/dev/null || true
```
Ha nincs rendszer-Gradle, a legegyszerűbb: az AS bármelyik Gradle-taszkjának első futtatásakor letölti a megfelelő Gradle-t; a wrapper-fájlokat utána a fenti paranccsal (vagy egy másik gépről átmásolva) is pótolhatod. **Commitold** a wrappert.

### B.3. Build: AAB készítése — két mód

**(i) Terminálból (ajánlott, determinista):**
A signing környezeti változók (2. lépés) beállítása után, az AS Terminaljában:
```bash
./gradlew :app:bundleRelease
```
- Kimenet: **`app/build/outputs/bundle/release/app-release.aab`**.
- Fontos: a környezeti változóknak abban a shellben kell élniük, **amelyikből az Android Studio (vagy a terminál) indult**. Ha az AS-t az asztali ikonról indítottad, lehet, hogy nem látja a `~/.bashrc`-ben beállított változókat — ekkor indítsd az AS-t terminálból, ahol előtte `export`-áltad őket.

**(ii) Android Studio grafikus varázslóval:**
- **Build → Generate Signed App Bundle / APK → Android App Bundle**.
- Válaszd ki a `my-upload-key.jks`-t, add meg a jelszavakat és az `upload` aliast.
- Ez a varázsló maga kezeli az aláírást, így ehhez nem feltétlenül kellenek a környezeti változók.
- (Konfidencia: közepes — a varázsló és a build-fájlbeli `signingConfig` együttélése AS-verziónként kissé eltérhet; ha ütközést jelez, használd a determinista terminálos módot a beállított env-változókkal.)

### B.4. (Opcionális) Release APK eszközre
```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 3. Feltöltés a Google Play Console-ra (tesztelés)

Ez a rész platformfüggetlen — a fent elkészített `app-release.aab`-t töltöd fel.

### 3.1. App létrehozása (egyszeri)
1. Menj a [Play Console](https://play.google.com/console)-ra (Google Play Developer fiók szükséges, egyszeri regisztrációs díjjal).
2. **Create app** → add meg a nevet (**Nagyító**), nyelvet (magyar), app/game típust, ingyenes/fizetős.
3. Töltsd ki a kötelező kezdő nyilatkozatokat (Content rating, Target audience, Data safety, Privacy policy). Az áruházi leírásokhoz kész szöveg van a repóban: **`PLAY_STORE.md`** (rövid + hosszú leírás, EN + HU).

### 3.2. Play App Signing
- Az első AAB feltöltésekor a Play felajánlja a **Play App Signing** bekapcsolását — fogadd el. Innentől a te aláírásod az *upload key*, a Google pedig a saját kulcsával írja alá a publikált appot.

### 3.3. Tesztsáv választása és feltöltés
A **Testing** menüben három szint közül választhatsz:
- **Internal testing** — leggyorsabb (percek), max. 100 tesztelő, ideális gyors iterációhoz. **Ezt ajánlom első körben.**
- **Closed testing** — nagyobb, meghívott csoport (pl. e-mail lista).
- **Open testing** — bárki csatlakozhat a linkkel.

Lépések (Internal testing példa):
1. **Testing → Internal testing → Create new release**.
2. **Upload** → húzd be az `app-release.aab`-t.
3. Add meg a **release notes**-ot (mi változott).
4. **Save → Review release → Start rollout to Internal testing**.
5. **Testers** fül: hozz létre egy e-mail listát a tesztelőkkel, mentsd.
6. Másold ki az **opt-in URL**-t, és küldd el a tesztelőknek. Ők a linken belépve telepíthetik az appot a Play Store-ból.

> A feldolgozás után pár perc, mire a tesztelők látják. Ha a Console „hibás verziót" jelez, nézd meg a lentebbi figyelmeztetéseket (targetSdk, versionCode).

---

## 4. Minden ÚJABB feltöltésnél: verzió emelése

A Play **nem fogad el kétszer azonos `versionCode`-ot**. Minden új feltöltés előtt emeld az `app/build.gradle.kts`-ben:
```kotlin
versionCode = 31          // eddig 30 → növeld eggyel
versionName = "30.1"      // ember-olvasható verzió, tetszőleges séma
```
Majd építsd újra az AAB-t (A.5 / B.3) és töltsd fel.

---

## 5. Fontos figyelmeztetések és hibaelhárítás

- **R8 tesztelés kötelező:** a release build kódtömörítést (R8) használ (`isMinifyEnabled = true`). Ez ritkán elronthat reflexióra/erőforrásra épülő működést. **Mindig próbáld ki a release AAB/APK-t valódi eszközön feltöltés előtt** (pl. `bundletool`-lal telepítve, lásd lentebb), ne csak a debug buildet. Ha valami eltűnik/összeomlik release-ben, de debugban nem, az R8 a gyanús — a szabályokat a `app/proguard-rules.pro`-ban lehet finomítani.
- **targetSdk:** a Play folyamatosan emeli az új appokra kötelező minimum `targetSdk`-t. A jelenlegi `targetSdk = 35` 2025-ben megfelelő; ha a Console elutasítja, emeld a kért szintre.
- **AAB vs APK:** a Play Console **AAB**-t vár (`bundleRelease`). Az APK (`assembleRelease`) csak közvetlen eszköz-telepítéshez / gyors teszthez jó.
- **AAB tesztelése eszközön feltöltés nélkül** (opcionális, [`bundletool`](https://developer.android.com/tools/bundletool)):
  ```bash
  bundletool build-apks --bundle=app-release.aab --output=app.apks \
    --ks=my-upload-key.jks --ks-key-alias=upload --mode=universal
  bundletool install-apks --apks=app.apks
  ```
- **„Keystore was tampered with" / rossz jelszó:** ellenőrizd a `STORE_PASSWORD` / `KEY_PASSWORD` értékét és hogy a keystore aliasa tényleg `upload`.
- **„SDK location not found":** hiányzik/rossz a `local.properties` `sdk.dir`, vagy nincs `ANDROID_HOME`.
- **A build nem találja a Gradle-t:** nem generáltál wrappert (0.b) — vagy generáld le, vagy telepíts rendszer-Gradle-t (9.6.1).
- **Windows sorvégek:** a repó `.gitattributes`-szal LF-re normalizál; ez a build szempontjából közömbös, csak a git-diffet tartja tisztán.

---

## Gyors összefoglaló (checklist)

1. [ ] JDK 17+ telepítve (`java -version`).
2. [ ] Android SDK: platform 36 + 36.1, build-tools 36.0.0, platform-tools.
3. [ ] `local.properties` `sdk.dir`-rel (VSCode) / AS auto.
4. [ ] Gradle wrapper legenerálva és commitolva (`gradle wrapper --gradle-version 9.6.1`).
5. [ ] Upload keystore létrehozva `upload` aliasszal, biztonságba mentve.
6. [ ] `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` env-változók beállítva.
7. [ ] (Első feltöltés előtt) `applicationId` véglegesítve.
8. [ ] `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
9. [ ] Release AAB kipróbálva valódi eszközön (R8 miatt).
10. [ ] Play Console → app létrehozva, Play App Signing be, Internal testing → AAB feltöltve → tesztelők + opt-in link.
11. [ ] Következő feltöltésnél `versionCode` emelve.
