# Setup guide

## 1. Push to GitHub
Create a repository (for example `CookingHandbook`), push this folder to the `main` branch.
`.github/workflows/build.yml` builds a release APK and AAB on every push and uploads them as workflow artifacts.
Push a tag such as `v1.0.0` to also attach them to a GitHub Release.

## 2. Signing (required for Google Play)
Create an upload key once:

    keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000

Add these **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 upload.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | key password |

Without them the build still works, but the APK is signed with the debug key (test only, Play will reject the AAB).
Never commit the `.jks` file.

## 3. AdMob
The project ships with Google's **test** ad ids. Before publishing:
1. Create an app and an **Adaptive banner** unit in AdMob.
2. Add secrets `ADMOB_APP_ID` (`ca-app-pub-…~…`) and `ADMOB_BANNER_ID` (`ca-app-pub-…/…`).
3. In AdMob → Privacy & messaging, create and publish a **GDPR** (and US states) message – the app already calls Google's UMP SDK.
4. Never click your own live ads.

## 4. Privacy policy page
Settings → Pages → Source: **GitHub Actions**. `.github/workflows/pages.yml` publishes the `docs/` folder, so the policy is at
`https://<user>.github.io/<repo>/privacy-policy.html`. Paste that URL in Play Console → App content → Privacy policy.
The same URL is compiled into the app (Settings → Privacy Policy) automatically from `GITHUB_REPOSITORY`.

## 5. Play Console checklist
- Target API 36 / `minSdk` 26 (already set). First upload must be the AAB; enrol in Play App Signing.
- **Data safety**: collects *Device or other IDs* (Advertising ID) and *App activity / diagnostics* through AdMob – shared with Google for advertising, optional (consent). Data is encrypted in transit. No account, no data deletion needed.
- **Ads**: declare "Contains ads". **Content rating**: questionnaire → recipes/food, no violence (Everyone). **Target audience**: 18+ or 13+ (do not select under-13 – AdMob/UMP setup here is not for the Families programme).
- Store assets are in `store/` (icon 512, feature graphic 1024×500, screenshots). Replace the screenshots with real emulator captures when you have them.
- Listing text: `README.md` (Vietnamese and English, both under Play's 4,000 character limit).

## 6. Updating the bundled recipe snapshot (optional)
`python3 tools/build_seed.py` refreshes `app/src/main/assets/dishes_seed.json`. The app also pulls changes from GitHub by itself
(on every start and about every 12 hours in the background), so this only matters for first-run offline content.

## 7. Testing In-App Update
In-App Update only works for builds installed from Google Play. Use **Internal app sharing** or an internal testing track,
install an older `versionCode`, upload a higher one, then open the older app.
