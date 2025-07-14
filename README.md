## Clash Meta for Android

A Graphical user interface of [Clash.Meta](https://github.com/MetaCubeX/Clash.Meta) for Android

### Feature

Feature of [Clash.Meta](https://github.com/MetaCubeX/Clash.Meta)

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
height="80">](https://f-droid.org/packages/com.github.metacubex.clash.meta/)

### Requirement

- Android 5.0+ (minimum)
- Android 7.0+ (recommend)
- `armeabi-v7a` , `arm64-v8a`, `x86` or `x86_64` Architecture

### Build

1. Update submodules

   ```bash
   git submodule update --init --recursive
   ```
2. Install **OpenJDK 11**, **Android SDK**, **CMake** and **Golang**
3. Create `local.properties` in project root with

   ```properties
   sdk.dir=/path/to/android-sdk
   ```
4. Create `signing.properties` in project root with

   ```properties
   keystore.path=/path/to/keystore/file
   keystore.password=<key store password>
   key.alias=<key alias>
   key.password=<key password>
   ```
5. Build

   ```bash
   ./gradlew app:assembleMeta-AlphaRelease
   ```

### Automation

APP package name is `com.github.metacubex.clash.meta`

- Toggle Clash.Meta service status
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.github.metacubex.clash.meta.action.TOGGLE_CLASH`
- Start Clash.Meta service
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.github.metacubex.clash.meta.action.START_CLASH`
- Stop Clash.Meta service
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.github.metacubex.clash.meta.action.STOP_CLASH`
- Toggle active profile (cycle through all profiles)
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.github.metacubex.clash.meta.action.TOGGLE_PROFILE`
  - This cycles through all available profiles in order and selects the next one
  - Only changes the active profile without starting/stopping the service
  - Logs detailed profile information for debugging
- Refresh all profiles (update all imported profiles)
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.github.metacubex.clash.meta.action.REFRESH_PROFILE`
  - Updates all imported profiles from their sources (URL-based profiles)
  - Skips file-based profiles that cannot be updated
  - Logs detailed refresh progress and results for each profile
- Import a profile
  - URL Scheme `clash://install-config?url=<encoded URI>` or `clashmeta://install-config?url=<encoded URI>`

#### Automation Examples

```bash
# Toggle Clash.Meta service status
am start -n com.github.metacubex.clash.meta/com.github.kr328.clash.ExternalControlActivity -a com.github.metacubex.clash.meta.action.TOGGLE_CLASH

# Start Clash.Meta service
am start -n com.github.metacubex.clash.meta/com.github.kr328.clash.ExternalControlActivity -a com.github.metacubex.clash.meta.action.START_CLASH

# Stop Clash.Meta service
am start -n com.github.metacubex.clash.meta/com.github.kr328.clash.ExternalControlActivity -a com.github.metacubex.clash.meta.action.STOP_CLASH

# Toggle active profile (cycle through profiles)
am start -n com.github.metacubex.clash.meta/com.github.kr328.clash.ExternalControlActivity -a com.github.metacubex.clash.meta.action.TOGGLE_PROFILE

# Refresh all profiles (update all imported profiles)
am start -n com.github.metacubex.clash.meta/com.github.kr328.clash.ExternalControlActivity -a com.github.metacubex.clash.meta.action.REFRESH_PROFILE

# Import a profile with URL scheme
am start -a android.intent.action.VIEW -d "clashmeta://install-config?url=http%3A%2F%2F192.168.1.118%3A59996%2Fclash%2Fdns_reject.yaml"
```

### Contribution and Project Maintenance

#### Meta Kernel

- CMFA uses the kernel from `android-real` branch under `MetaCubeX/Clash.Meta`, which is a merge of the main `Alpha` branch and `android-open`.
  - If you want to contribute to the kernel, make PRs to `Alpha` branch of the Meta kernel repository.
  - If you want to contribute Android-specific patches to the kernel, make PRs to  `android-open` branch of the Meta kernel repository.

#### Maintenance

- When `MetaCubeX/Clash.Meta` kernel is updated to a new version, the `Update Dependencies` actions in this repo will be triggered automatically.
  - It will pull the new version of the meta kernel, update all the golang dependencies, and create a PR without manual intervention.
  - If there is any compile error in PR, you need to fix it before merging. Alternatively, you may merge the PR directly.
- Manually triggering `Build Pre-Release` actions will compile and publish a `PreRelease` version.
- Manually triggering `Build Release` actions will compile, tag and publish a `Release` version.
  - You must fill the blank `Release Tag` with the tag you want to release in the format of `v1.2.3`.
  - `versionName` and `versionCode` in `build.gradle.kts` will be automatically bumped to the tag you filled above.

### Built-in Profile Sync Feature

This version includes a built-in profile synchronization feature that allows users to quickly add predefined DNS configuration profiles from a local server.

#### Features

- **Smart Sync**: The sync function intelligently handles network failures and only adds successfully accessible profiles
- **Duplicate Prevention**: Automatically checks for existing profiles with the same name to avoid duplicates
- **Individual Processing**: Each profile is processed independently - if one fails, others can still be added successfully
- **Detailed Feedback**: Provides clear status messages about the sync process and results

#### Usage

1. Open the main application interface
2. Tap the "About" button to access the about dialog
3. Use the "SyncProfile" function to automatically add the following DNS profiles:
   - `dns_67`: Optimized DNS configuration for enhanced performance
   - `dns_65`: Alternative DNS setup with different providers
   - `dns_64`: Backup DNS configuration
   - `dns_62`: Additional DNS profile for specific scenarios

#### Network Resilience

- **Timeout Handling**: Uses short connection timeouts (5 seconds) to quickly detect unreachable servers
- **Graceful Degradation**: If the profile server is unavailable, the app continues to function normally
- **Partial Success**: Successfully adds available profiles even if some are unreachable
- **Error Logging**: Detailed error information is logged for troubleshooting

#### Configuration

The profile URLs are currently configured for a local development server:

```
http://192.168.1.118:59996/clash/dns_67.yaml
http://192.168.1.118:59996/clash/dns_65.yaml
http://192.168.1.118:59996/clash/dns_64.yaml
http://192.168.1.118:59996/clash/dns_62.yaml
```

Users can modify these URLs in the source code (`MainActivity.kt`) to point to their own profile servers if needed.

### Development Log - OpenJDK 24 Upgrade & Network Sync Fix

This section documents the complete process of upgrading from OpenJDK 11 to OpenJDK 24 and fixing the built-in profile synchronization functionality.

#### Issues Encountered

**1. OpenJDK 24 Compatibility Problems**

- **SDK Path Issue**: Hidden characters in `local.properties` caused "Trailing char" errors
- **JVM Access Restrictions**: Native method access warnings with OpenJDK 24
- **Kotlin JVM Target Mismatch**: Kotlin compiler targeting JVM 23 while Java targeting 1.8
- **TLS Protocol Issues**: Network connection failures due to TLS protocol incompatibility

**2. Dependency & Build Issues**

- **Missing Git Submodules**: Clash core code not properly initialized
- **OkHttp3 Dependency Missing**: Required for network requests in sync functionality
- **Build Configuration**: Deprecated Gradle features causing compatibility warnings

**3. Network Sync Functionality Failures**

- **Cleartext Traffic Blocked**: Android security policy preventing HTTP requests
- **User-Agent Inconsistency**: Different User-Agent strings causing server rejections
- **Request Method Issues**: HEAD requests not supported by some servers

#### Solutions Implemented

**1. OpenJDK 24 Compatibility Fixes**

*File: `gradle.properties`*

```properties
# Added JVM parameters for OpenJDK 24 compatibility
org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -Dhttps.protocols=TLSv1.2,TLSv1.3
# Suppress Java source/target version warnings
android.javaCompile.suppressSourceTargetDeprecationWarning=true
```

*File: `build.gradle.kts` (root)*

```kotlin
// Added Kotlin JVM target configuration
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
```

*File: `local.properties`*

```properties
# Fixed SDK path (removed hidden characters)
sdk.dir=C:/Users/keizman/AppData/Local/Android/Sdk
```

**2. Dependency Management**

*File: `gradle/libs.versions.toml`*

```toml
# Added OkHttp3 dependency
okhttp = "4.12.0"

[libraries]
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
```

*File: `app/build.gradle.kts`*

```kotlin
dependencies {
    // Added OkHttp3 for network requests
    implementation(libs.okhttp)
}
```

**3. Network Security Configuration**

*File: `app/src/main/res/xml/network_security_config.xml`*

```xml
<!-- Enabled cleartext traffic for HTTP requests -->
<base-config cleartextTrafficPermitted="true">
    <trust-anchors>
        <certificates src="system" />
        <certificates src="user" />
    </trust-anchors>
</base-config>
```

**4. Profile Sync Implementation**

*File: `app/src/main/java/com/github/kr328/clash/MainActivity.kt`*

- Implemented robust profile synchronization with individual error handling
- Added comprehensive logging and user feedback
- Used consistent User-Agent: `"ClashforWindows/0.19.23"`
- Changed from HEAD to GET requests for better server compatibility
- Added duplicate profile detection to prevent conflicts

#### Root Cause Analysis

**Primary Issue: Android Security Policy**
The main reason for sync failure was Android's default network security policy that blocks cleartext HTTP traffic starting from API level 28 (Android 9). While manual profile installation worked through a different code path, the synchronization feature was blocked by this security restriction.

**Secondary Issues:**

1. **OpenJDK 24 Compatibility**: Newer JDK versions have stricter access controls requiring explicit permissions
2. **Request Method**: Some servers don't properly support HEAD requests, requiring GET instead

#### Key Files Modified


| File                                                       | Purpose             | Changes                                                   |
| ---------------------------------------------------------- | ------------------- | --------------------------------------------------------- |
| `gradle.properties`                                        | Build configuration | Added JVM parameters, TLS protocols, warning suppressions |
| `build.gradle.kts`                                         | Root build script   | Added Kotlin JVM target configuration                     |
| `local.properties`                                         | SDK configuration   | Fixed path format, removed hidden characters              |
| `gradle/libs.versions.toml`                                | Dependency versions | Added OkHttp3 library definition                          |
| `app/build.gradle.kts`                                     | App dependencies    | Added OkHttp3 implementation                              |
| `app/src/main/res/xml/network_security_config.xml`         | Network security    | Enabled cleartext traffic                                 |
| `app/src/main/java/com/github/kr328/clash/MainActivity.kt` | Main logic          | Implemented profile sync with error handling              |

#### Build Commands Used

```bash
# Initialize submodules
git submodule update --init --recursive

# Clean previous builds
./gradlew clean

# Build release APK
./gradlew app:assembleMetaRelease
```

#### Testing & Validation

**Build Verification:**

- Successfully built with OpenJDK 24
- Generated APKs for all target architectures:
  - `cmfa-2.11.13-meta-universal-release.apk` (61MB)
  - `cmfa-2.11.13-meta-arm64-v8a-release.apk` (25MB)
  - `cmfa-2.11.13-meta-armeabi-v7a-release.apk` (25MB)
  - `cmfa-2.11.13-meta-x86_64-release.apk` (25MB)
  - `cmfa-2.11.13-meta-x86-release.apk` (26MB)
