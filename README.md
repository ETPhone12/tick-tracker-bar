# Tick Tracker Bar

A RuneLite plugin for practising tick-perfect clicking: prayer flicking, 1-tick actions and 2t / 3t skilling.

A bar fills up over one game tick (or 2 or 3 ticks) and resets on the next tick. Every time you click, an
X appears on the bar where you clicked, so you can see whether you were early, late or right on the
tick. It counts your streak, hits and misses, and it's styled like the game's own interfaces.

The bar runs off the game's own tick (RuneLite's `GameTick` event), not a stopwatch, so it stays in step
with the server even when ticks are uneven. It starts as an in-game overlay, which only appears once
you're logged in: RuneLite doesn't draw overlays on the login, loading or world-hop screens. If you turn
on the pop-out window (see below), it shows on those screens too, with "Waiting for game ticks..." while
you're logged out or hopping, and whenever no tick has arrived for 2.4 seconds (a long loading screen or
lag).

## What you see

```
 3-TICK [■][■][■]                 HIT     <- cycle length, which tick you're on (yellow box), last result
 [██████████|██████████|███X██│▒▒▒]       <- the bar, tick dividers, green target zone, your clicks, now line
 Streak 12                    Best 30
 Hits 154  Miss 9             Acc 94%
```

- The bar is orange while it waits for your click and turns dark green once you've hit this cycle.
- On 2 and 3 tick cycles the last tick (the green zone) is the target.
- Each click leaves an X where you clicked, like the one the game draws (*Click marks* can make them
  lines instead). Their colors:
  - **green**: a hit. (As a line: white with green tips.)
  - **grey**: a click that didn't need to count: a second click in the tick you'd already hit (like
    clicking the fishing spot right after herb on tar), or a click on another tick.
  - **red**: a click on the wrong tick in a cycle you missed. These show in the faded copy of that cycle
    (with *Show last cycle's marks* on), once it's over.
  - **yellow**: the click that restarted your rhythm after a miss. It doesn't count (see below).
  - **cyan**: a click in the striped end of the bar, too late for this cycle: it counts for the next tick
    (see *Input lag* below). Right after a miss on a 2 or 3 tick cycle it restarts your rhythm like a
    yellow click, but stays cyan.
  - **faded**: where you clicked last cycle.
- The white **now line** shows where a click would land right now. Only the default *Click marks:
  X + now line* shows it.
- The border flashes green on a hit and red on a miss.

## Hits, misses and streaks

- **Hit**: you clicked during the target tick, before the bar reset. Extra clicks in the same tick don't
  count twice. (With an *Input lag* set, a click in the last part of a tick counts for the next tick
  instead, and the green zone moves earlier to match: see below.)
- **Miss**: a cycle ended without a hit while it counted: you were on a streak, it came right after a
  restart click (yellow, or cyan when it was late; see below), or you clicked on the wrong tick in it. So
  stopping costs at most one miss: standing around after that adds no more.
- **Streak**: hits in a row. **Best**: your longest streak. **Acc**: hits ÷ (hits + misses).
- **Reset**: **Shift + right-click** the overlay → *Reset stats*, the *Reset* button on the pop-out
  window, or set a *Reset hotkey*. Stats also start fresh when you restart RuneLite.

### 2 and 3 tick cycles

- With *Sync to first click* on, the first click of a streak decides which tick is the target: the bar
  shifts so that tick becomes the green zone. Start each streak with your tick manipulation (herb on tar,
  knife on logs and so on).
- Right after a miss, a click outside the green zone only restarts your rhythm (the yellow mark, or cyan
  when the click was in the striped end): the bar
  lines up with it, but it isn't a hit, and the cycle after it counts as if you were on a streak (no hit
  there is a miss). This stops a late click from counting as a miss and then straight away as a hit. A
  click that lands in the green zone is a hit as usual.
- Clicks on the other ticks are fine and stay grey. They only turn red, in the faded copy of the cycle,
  if you missed that cycle.
- With *Count: Game actions*, dropping items doesn't count, so shift-dropping between manipulations
  never gets in the way. *Count: Item use only* goes further and only counts using an item on something.
- Clicks you make while doing something else (banking, walking) count too and can cost misses. Press
  Reset when you start a practice run.
- The plugin can't tell which click was your manipulation. If you click on every tick (for example with
  *Count: Any mouse click*), one of those clicks always lands in the green zone.

### Input lag

The game reads your clicks once a tick, as the tick starts. A click has to reach the server before then,
and RuneLite only hears about a tick after the server has run it and sent it back. Together that is your
input lag: about your ping plus the server's own time. So a click in the last moments of a tick on the bar
reaches the game in time for the *next* tick.

Set *Input lag* to it and the bar allows for it:

- The last part of every tick, that long before the tick ends, counts for the next tick. On a 2 or 3 tick
  cycle the green zone moves that much earlier: a click near the end of the tick before it is already a
  hit, and one near the end of the green tick is too late.
- The end of the bar is striped. A click there is too late for the cycle: it gets a cyan mark and is scored
  when the next tick starts.
- It starts at 0, which scores every click in the tick the bar shows. The World Lag plugin (Plugin Hub) can
  measure your input lag; it found about 125 to 185 ms on normal worlds, and more on busy ones. Without it,
  try your ping (World Hopper plugin, *Display current ping*) plus about 100, more on a busy world.
- With *Count: Any mouse click*, the plugin adds 15 ms to it by itself: it sees a mouse press up to one
  20 ms client cycle before the game reads it, so you can enter the same number in every Count mode.

## The pop-out window (drag it anywhere, any monitor)

Settings → **Show as** → *Pop-out window* (or *Both*), or Shift + right-click the overlay → *Pop out*.

- Drag it by any part except the buttons, onto another monitor too. It remembers where you drag it.
  Until you've moved it (or if that spot is off-screen, say a monitor was unplugged), it appears near the
  top of the game.
- It belongs to the RuneLite window: it always stays above RuneLite and hides while RuneLite is
  minimized. *Window always on top* keeps it above other apps as well.
- It never takes keyboard focus, so clicking it doesn't stop your keys going to the game.
- *Reset* clears the stats, *X* puts it back in the game as an overlay.
- Options: always on top, lock position, opacity.

The in-game overlay moves like any RuneLite overlay: hold **Alt** and drag it. **Shift + right-click** it
for *Reset stats* and *Pop out*.

## Settings

| Setting | What it does |
|---|---|
| Tick cycle | 1, 2 or 3 ticks per bar |
| Hit window | *Last tick of cycle* (the green zone) or *Any tick in cycle* |
| Sync to first click | On 2 / 3 tick cycles, the first click of a streak sets which tick is the target (right after a miss it only restarts your rhythm, see above) |
| Count | *Game actions* (anything you do in the game except dropping items and picking an item to use), *Item use only* (using an item on something, like herb on tar), or *Any mouse click* |
| Input lag | 0 to 500 ms: clicks made closer than this to the end of a tick count for the next tick (see above) |
| Reset hotkey | Key that resets the stats |
| Show as | In-game overlay, pop-out window, or both |
| Window always on top, Lock window position, Window opacity | Pop-out window options |
| Bar width, Bar height | Size of the bar (width 160 to 800, height 8 to 60) |
| Click marks | *Lines*, *X* (like the game's click cross), or *X + now line* (also shows where a click would land right now) |
| Show stats, Show last cycle's marks | Hide the stats, or the faded copy of last cycle's marks (which also shows a missed cycle's wrong-tick clicks in red) |
| Colors | Bar, hit, miss, hit zone and background colors. They start as the game's own interface colors (the pop-out ignores the background's transparency: use *Window opacity*) |

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
elephant icon on the right). Don't use the green arrow next to `main` in `TickTrackerPluginTest.java`: it
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

## Putting it on the Plugin Hub

To use it in your normal RuneLite client (and share it), it has to go on the
[RuneLite Plugin Hub](https://github.com/runelite/plugin-hub):

1. Put this folder in a public GitHub repository.
2. Set `author=` in `runelite-plugin.properties` to your GitHub name.
3. Test it in the game.
4. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub), and on a new branch add a file
   `plugins/tick-tracker-bar` containing `repository=` (this repository's `.git` address) and `commit=`
   (the full 40-character hash of the commit to publish). Open a pull request with a short description.
5. Check the pull request's *build* and *RuneLite Plugin Hub Checks* results. To fix something, push the
   fix to this repository and update `commit=` in the same pull request instead of opening a new one.
   The RuneLite team reviews it before it goes live.
6. For updates, change the `commit=` line in that file with a new pull request.

## Code map

| File | Job |
|---|---|
| `TickTracker.java` | Timing logic: cycles, hits, misses, streaks. No RuneLite code, unit tested |
| `TickTrackerPlugin.java` | Hooks into RuneLite: game ticks, clicks, settings, the pop-out window |
| `TickSnapshot.java` | A frozen copy of the tracker's state for drawing one frame |
| `TickBarPainter.java` | Draws the bar, marks and stats (shared by the overlay and the window) |
| `BarStyle.java` | The size and color settings in one place, with the size limits |
| `TickTrackerOverlay.java` | The in-game overlay |
| `TickTrackerWindow.java` | The draggable pop-out window |
| `TickTrackerConfig.java` | The settings panel |
