# Turning Stepwgn_NRMZ25_Android_Studio.zip into an .APK (4GB RAM Friendly)

Because full Android Studio uses 6–8 GB of RAM, use either of these two methods so your 4GB RAM system never lags or freezes:

## Method 1: Zero Local RAM — Free GitHub Actions Cloud Compiler (Takes ~90 Seconds)
1. Create a free account/repository at https://github.com/new (can be Private or Public).
2. Unzip `Stepwgn_NRMZ25_Android_Studio.zip` and upload all files/folders (including `.github/workflows/build-apk.yml`) to the repository.
3. Click the **Actions** tab at the top of your GitHub repository.
4. Click the completed **Build Stepwgn NR-MZ25 13-Band DSQ APK** workflow run and download **Stepwgn-NRMZ25-13Band-DSQ-APK** under *Artifacts*.
5. Copy `app-debug.apk` to your USB flash drive and install it via the Stepwgn head unit's File Manager.

## Method 2: Low-RAM Command-Line Build (Caps RAM at 1 GB, No Android Studio GUI)
This project includes a custom `gradle.properties` that restricts the Java/Kotlin compiler to `1024 MB` of RAM with `--no-daemon`:
```bash
gradle assembleDebug --no-daemon --max-workers=2
```
The compiled `.apk` will be output to `app/build/outputs/apk/debug/app-debug.apk`.
