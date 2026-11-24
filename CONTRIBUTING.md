# Working on Tick Tracker Bar

This is for changing the plugin and trying your changes. To use it, install it from RuneLite's Plugin Hub
(see the [README](README.md)).

## Running it

RuneLite's normal client only loads plugins from the Plugin Hub, so to try this one you start RuneLite
from this project with its `run` task. That starts RuneLite in developer mode, which you need to log in
with a Jagex account. You need a Java Development Kit (JDK) **11, 17 or 21**, for example
[Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21). Don't use JDK 24 or newer,
including the JDK 25 that adoptium.net's home page offers: this project's build tools (Gradle 8.10, the
same as RuneLite's plugin template) can't run on it.

**On Windows, the quickest way:** double-click `Start Tick Tracker test.cmd` in this folder. It looks for a
JDK 17 or 21 in the usual places (the Temurin, Oracle and Microsoft installers, and the JDKs IntelliJ
downloads) and runs the `run` task. If it can't find one, use one of the ways below.

**With IntelliJ IDEA:** open this folder and let it import the Gradle project. In
*Settings → Build, Execution, Deployment → Build Tools → Gradle*, set *Gradle JVM* to a JDK 17 or 21 (that
menu can also download one). Then open `build.gradle` and click the green arrow next to
`tasks.register('run', JavaExec)`, or double-click *Tasks → other → run* in the Gradle tool window (the
elephant icon on the right). Don't use the green arrow next to `main` in `TickTrackerBarPluginTest.java`: it
starts RuneLite without developer mode, so there's no way to log in with a Jagex account.

**From PowerShell**, in this folder. First point `JAVA_HOME` at your JDK's folder. The Temurin installer
puts JDKs in `C:\Program Files\Eclipse Adoptium`, and IntelliJ puts the ones it downloads in
`$env:USERPROFILE\.jdks`. This lists both:

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium", "$env:USERPROFILE\.jdks" -ErrorAction SilentlyContinue
```

Then run these two lines, with your JDK's folder in the first one (the folder here is only an example):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"
.\gradlew.bat run
```

For a JDK that IntelliJ downloaded, the first line looks like
`$env:JAVA_HOME = "$env:USERPROFILE\.jdks\<folder name>"`.

**Logging in with a Jagex account:** always start the client with the `run` task as above. The first
time, click the *Jagex Accounts* button in RuneLite's sidebar, then *Add Jagex Account*, and log in in the
window that opens. After that, each time you start it, pick your character in that panel and press
*Play Now*. RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide has the details.

**Try it without the game:** run `src/test/java/com/ticktrackerbar/TickTrackerDemo.java` (in IntelliJ, the
green arrow). It opens the pop-out bar with a fake 600 ms tick and a practice window where clicks and key
presses count as actions. Pass `2` or `3` as the program argument for the 2 or 3 tick cycle.

**Tests:** `.\gradlew.bat test`. The painter test also saves preview pictures to `build/previews`.

### If the build fails

- **`Unsupported class file major version 68`** (or 69, 70...), often with "BUG! exception in phase
  'semantic analysis'": your JDK is too new. Point `JAVA_HOME` (or IntelliJ's *Gradle JVM*) at JDK 17 or
  21 and run it again.
- **`PKIX path building failed`**: your antivirus (Norton, Avast, AVG...) is re-signing HTTPS traffic with
  a certificate Java doesn't know. Either turn off the antivirus's HTTPS scanning, or add this line to
  `%USERPROFILE%\.gradle\gradle.properties` so Gradle uses the Windows certificate store:

  ```
  systemProp.javax.net.ssl.trustStoreType=Windows-ROOT
  ```

  The same scanning also breaks the game's login in the test client ("Login authentication error ...
  PKIX path building failed" in RuneLite's log). On Windows the `run` task already starts the client with
  the Windows certificate store for that reason.

## Updating the Plugin Hub version

The Plugin Hub builds the commit named in `plugins/tick-tracker-bar` in
[runelite/plugin-hub](https://github.com/runelite/plugin-hub). To publish a new version:

1. Push your changes to this repository and test them in the game.
2. In your fork of runelite/plugin-hub, on a branch made from the current upstream master, set `commit=` in
   `plugins/tick-tracker-bar` to the full 40-character hash of the commit to publish, and open a pull
   request.
3. Check the pull request's *build* and *RuneLite Plugin Hub Checks* results. To fix something, push the fix
   here and update `commit=` in the same pull request instead of opening a new one.

## Code map

| File | Job |
|---|---|
| `TickTracker.java` | Timing logic: cycles, hits, misses, streaks. No RuneLite code, unit tested |
| `TickTrackerBarPlugin.java` | Hooks into RuneLite: game ticks, clicks, settings, the pop-out window |
| `TickSnapshot.java` | A frozen copy of the tracker's state for drawing one frame |
| `TickBarPainter.java` | Draws the bar, marks and stats (shared by the overlay and the window) |
| `BarStyle.java` | The size and color settings in one place, with the size limits |
| `TickTrackerBarOverlay.java` | The in-game overlay |
| `TickTrackerWindow.java` | The draggable pop-out window |
| `TickTrackerConfig.java` | The settings panel |
