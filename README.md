# Fresh AFK

A **client-side** Fabric mod for **Minecraft Java 26.1.2** that keeps you crouching, mining (left click) and using
(right click) while you are AFK on a server where staff have approved AFK mining. It survives server restarts, can
stop itself on a timer, and is fully visible: a small HUD line is on screen the whole time it is active.

There are **no chat commands**. Everything is done with two keybinds and a settings screen.

See `SPEC.md` for the full design and `PROGRESS.md` for the development log.

## Build

Requires **JDK 25**. Gradle itself must run on Java 25 (Fabric Loom 1.18 needs it).

```sh
# Git Bash / Linux / macOS (adjust the path to your JDK 25)
JAVA_HOME="C:/Program Files/Java/jdk-25.0.2" ./gradlew build

# PowerShell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"; .\gradlew.bat build
```

`build` compiles the mod and runs the JUnit tests. Output jar:

```
build/libs/afkmod-0.2.0.jar
```

(`afkmod-0.2.0-sources.jar` next to it is only the sources; do not install that one.)

To try it without installing: `./gradlew runClient` starts a dev client with the mod loaded.

## Install

1. Install **Java 25** (Minecraft 26.1.2 runs on it; the launcher normally provides it).
2. Install **Fabric Loader 0.19.3 or newer** for Minecraft 26.1.2 with the Fabric installer
   (https://fabricmc.net/use/installer/) and pick the Fabric profile in the launcher. (The mod is built and
   unit-tested against Loader 0.19.5; 0.19.3 and 0.19.4 are allowed by `fabric.mod.json` but have not been tried.)
3. Download **Fabric API 0.155.3+26.1.2** (or the newest build for 26.1.2) and put it in your `mods` folder.
4. Put `build/libs/afkmod-0.2.0.jar` in the same `mods` folder.
5. Start the game with the Fabric profile. Mod ID `afkmod`, name "Fresh AFK".

Mod Menu and Cloth Config are **not** required.

The mod is client-only: nothing needs installing on the server. Its files are all in the `config` folder:
`afkmod.json` (settings), `afkmod-stats.json` (statistics), `afkmod-messages.log` (debug log, only with **Log all
incoming messages** on), `afkmod-report.txt` (the debug report) and `afkmod-selftest.txt` (the last self-test).

## Keybinds

They are in **Options > Controls > Key Binds** under the **Fresh AFK** category and can be rebound.

| Action | Default | What it does |
|---|---|---|
| Toggle AFK | `K` | Turns the mod ON/OFF. |
| Open AFK menu | `J` | Opens the settings screen (only when no other screen is open). |
| Open Test Lab | unbound | Opens the same screen on its **Test Lab** tab. |
| Abort test | unbound | Cancels a running Test Lab scenario. |

The settings screen can also be opened from the **Fresh AFK** button on the pause menu (Esc), right under Disconnect.

## What it does

While **ON** and running, every check (every 20 ticks, about 1 second) the mod makes sure crouch, left click and
right click are all active. It works with both **Toggle** and **Hold** modes for those keys (Options > Controls). It
presses a key at most once per check, so a toggle key is never flipped back off.

- **Order:** crouch and left click go on first. Right click always goes on **last**, and only once you are really
  sneaking (shift is down), usually one tick later. If right click is ever on while crouch is off, the mod releases
  right click, turns crouch back on, then presses right click again.
- **Hold becomes Toggle while the mod is on:** if crouch, left click or right click is set to **Hold** in
  Options > Controls, the mod switches it to **Toggle** when you turn the mod on and puts it back to **Hold** when the
  mod turns off (any reason: keybind, timer, death, disconnect, quitting the game). Keys already on Toggle are left
  alone. Which ones it switched is kept in `config/afkmod-holdmodes.txt`, so if the game crashes while the mod is on,
  they are put back to Hold the next time the game starts.
- It does nothing while any screen is open (the GUI, chat, inventory). It re-checks on the next check after the screen
  closes.
- It turns itself **OFF at once** if you die, if you disconnect yourself (pause menu, "Save and Quit"), or when the
  timer ends. Everything it turned on is released, so no key is left stuck.
- Restarts: see below.

### Restart handling

This is how a restart looks on the server this mod was written for, and what the mod does at each step:

| # | What the server does | What the mod does |
|---|---|---|
| 1 | Sends **one chat/system message containing "servers"**. | Matches the restart keyword `servers`. State goes `ACTIVE -> RESTARTING`, left and right click are released, the restart counter goes up by one, the timer pauses, the HUD turns red. |
| 2 | Shows an **action bar** text `restart queue #N` (middle of the screen, above the hotbar) while it is frozen. | Matches the queue keyword `restart queue` on the action bar. This refreshes the same restart (it is never counted twice) and the mod remembers the text is on screen. It reads the real action bar state of the client, so it knows exactly when the text is gone. |
| 3 | The text disappears: the restart is over. | The mod waits until the text has been gone for 3 s (`queueGoneSeconds`), so a flicker cannot end the restart early. |
| 4 | **Reconnects you**: connecting screen, then an automatic rejoin. This is a server transfer, not a kick. | The mod does **not** turn off. It does nothing while the connecting screens are showing. When you are back in the world and the text has been gone for 3 s, it goes `RESTARTING -> SETTLING`. The two can happen in either order. |
| 5 | Nothing (there is no "all clear" message). | After the settle delay (3 s) it presses crouch, left and right click again (`SETTLING -> ACTIVE`), and the timer continues. For the next 15 s restart messages are ignored (post-resume cooldown). |

The mod watches all incoming text: system/game messages, the action bar, titles, subtitles and boss bars. Player chat is
never used for detection. The rules in detail:

1. A message containing a **restart keyword** (default `servers, restart queue`, case-insensitive, colour codes
   stripped), or an **action bar** message containing a **queue keyword** (default `restart queue`), switches the mod
   to **RESTARTING**: left and right click are released (crouch too if "Release crouch during restart" is on) and the
   HUD turns red, `● Server restarting`. The queue number is never shown in the mod's HUD.
2. A message containing an **ignore keyword** (default `whispered`) is ignored completely, even if it also contains
   `servers`. Ignore always wins.
3. The server's reconnect does **not** turn the mod off. Only the pause menu Disconnect (or quitting) and the mod's own
   timer count as a manual disconnect.
4. The restart ends when **both** are true, in either order: the queue text has been gone from the screen for 3 s
   (`queueGoneSeconds`), **and** you have rejoined. If the text goes away and no reconnect happens at all, it resumes
   after 30 s (`noReconnectFallbackSeconds`). Then it waits the settle delay (3 s) and presses crouch/left/right again.
5. If the restart hasn't ended after 15 minutes (`maxRestartWaitMinutes`), e.g. the rejoin never comes, the mod turns
   itself OFF and releases everything.
6. For the next 15 s (post-resume cooldown) restart keywords are ignored, so a leftover or re-sent message cannot
   immediately start another restart.

The restart counter (`↻N` on the HUD) counts each restart once per game session; repeated messages during the same
restart are not counted.

A disconnect while ACTIVE with **no** restart message is treated as a possible relog: the mod waits up to
`reconnectGraceSeconds` (default 120) for you to rejoin, then turns off. A relog that arrives with no disconnect at
all is handled the same way as a rejoin (settle delay, then the cooldown).

### Auto reconnect

While waiting in that grace period, the mod tries to **join the same multiplayer server again**: the first attempt
`reconnectDelaySeconds` (10 s) after the connection was lost, then every 10 s, at most `reconnectAttempts` (5) times.
The grace period still applies, so the attempts must fit inside it or the mod turns off. It does nothing for a restart
(the server reconnects you itself), for the timer ending, or when you leave on purpose, and it never reconnects
single-player or Realms. Turn it off with **Auto reconnect** in the Detection tab.

### Alt-tab

Normally Minecraft opens the pause menu when its window loses focus ("pause on lost focus"), which stops the mod
from working. While the mod is ON, that pause is skipped (setting **Keep running when unfocused**, on by default), so
you can switch to another window and it keeps going. Your own Minecraft option is not changed: with the mod OFF the
game pauses as usual. A window that is in the background may render at a lower frame rate; that does not affect the mod.

### Timer

The countdown starts when the mod is turned ON. Time spent during a restart (message, queue text, reconnect, settle
delay) and the reconnect grace period **does not count**: the countdown is paused. When it ends the mod releases everything, turns
itself fully OFF, and disconnects you to the **multiplayer server list**. It only starts again when you press the
toggle key.

Turning the mod OFF by hand (keybind, GUI, death, disconnect) only **pauses** the timer: the remaining time is kept and
the countdown carries on from there the next time you turn the mod ON. A timer that has run out starts over from its full
length. **Reset timer** (Timer tab) starts it over from the configured length whenever you like (paused while the mod is OFF).

**Presets** are named lengths (defaults `30 min`, `1 hr`, `4 hr`, `8 hr`). In the Timer tab you can rename or change
each one in place (length as `2h30m`, `45m` or `1:30:00`), press **Use** to apply it, **X** to delete it, or type a length
in the fields, give it a name and press **Add preset**. They are saved in `config/afkmod.json` as `timerPresets`.

### Movement recovery

In the farm you are always moving, so standing still means something is wrong (usually server lag).

**Detection.** On each 20-tick check (ACTIVE only) the mod records your x/z position (height is ignored). If, over a
full 20 s window (`stuckWindowSeconds`), you never got 3 blocks (`stuckDistanceBlocks`) away from where you are now,
you are stuck. The window is thrown away (so there are no false alarms) when the mod is turned on, a restart starts or
ends, you rejoin, you die, a screen opens or closes, and after every recovery attempt.

**Recovery** (HUD: yellow `● Recovering`). Restart handling always wins and cancels it. The steps:

1. Crouch, left click and right click are switched off (one press each, so it works for toggle and hold keys). It
   waits up to 3 s until they read off.
2. **The turn.** You turn to face the nearest of south (0), west (90), north (180) or east (270 = -90) **exactly**.
   - Only the yaw (left-right) changes. **The pitch (up/down look) is never read or written.**
   - The nearest direction is chosen from your yaw normalised to 0-360; a yaw exactly halfway (45, 135, ...) rounds up.
   - You turn the short way round (at most 180 degrees; for example 170 -> 180 or 350 -> 360 = south), never the long way.
   - The motion is a smooth **ease-in-out** (smootherstep): it starts from rest, is fastest in the middle and eases to a
     stop on the target, with no overshoot or wobble. It takes `recoveryTurnSeconds` (0.5 s) measured on the wall clock,
     so it is 0.5 s at any frame rate. It is applied every rendered frame (not only 20 times a second), with the
     previous-yaw field kept equal so the camera never shows a stale value, and the server receives the new rotation
     with the normal movement packets. At the end the yaw is set to the exact target value. Only `recoveryTurnSeconds = 0`
     snaps instantly; no other code path does.
   - The new facing is permanent: the mod never turns you back. The turn time does not count towards the walk timeout.
3. Only when the turn is finished, the mod checks the 2 blocks ahead (a drop, lava, fire, cactus, magma and similar
   count as unsafe; `recoveryEdgeCheck`). If it is unsafe it does not walk and the attempt counts as failed.
4. It jumps and holds forward until you moved `recoveryWalkBlocks` (2) or `recoveryWalkTimeoutSeconds` (5 s) pass,
   then releases both. It does not walk back.
5. On success crouch, left and right click come back on and the window starts fresh. This sequence runs every tick
   while RECOVERING; it is the only thing that does.

A failed walk, an unsafe path, keys that would not switch off, or being stuck again after a "successful" recovery counts as
a failed attempt. After `recoveryRetries` (2) retries, so 3 attempts in all, the mod turns OFF and disconnects you to
the server list (an intentional disconnect). A full window of normal movement resets the count. A restart, a screen
opening, death, toggling the mod off or a disconnect cancels a recovery at once: the turn stops where it is (no
snapping back), forward and jump are released. Turn the feature off with **Stuck detection** on the Recovery tab.

## HUD

A small line in a screen corner (top-left by default) on a semi-transparent dark backing, hidden when the mod is OFF:

```
● AFK Mining | 1:23:45 | C L R | ↻2
```

- Dot: green = mining, red = server restarting, yellow = movement recovery, grey = paused/waiting.
- Timer remaining, or `∞` for no timer (`(paused)` while the countdown is paused).
- `C L R` = crouch / left click / right click. Green = active, red = not.
- `↻N` = restarts this session.

While restarting the line turns red and reads `● Server restarting`, and a small red banner appears at the top centre.
It moves to the bottom corner while the F3 debug screen is open. It is hidden by F1.

## GUI guide

Open it with `J` or the pause menu's **Fresh AFK** button. It is a centred panel (about 60% of the window width, at most
420 px, following the GUI scale) under vanilla's own tab bar (the same widgets as the Create World screen), with
**Save**, **Done** and **Cancel** at the bottom. Hover a tab for its full name. The mouse wheel and Page Up / Page Down
scroll a tab when the window is too small to show every row.

**Info icons.** Every setting has a small circled **i** next to its label. Hover the icon (or the label), or focus the
icon with the Tab key, to see what the setting does, then a last line with its default, unit and range (for example
`Default: 20 ticks - Range: 1-200 ticks`). The texts, defaults and ranges all come from one registry
(`SettingInfo`), so the tooltip can never disagree with the code. The Test Lab scenarios have the same icon, showing
the scenario's description and what a PASS means.

| Tab | What is on it |
|---|---|
| **Dashboard** | Live and read-only apart from the big **ON/OFF** button: state with a coloured dot, timer remaining with a progress bar, restart count and the current restart's elapsed time, crouch / left click / right click indicators, movement (distance moved in the current window, last recovery result, last yaw snap), the session summary and the last 8 events with timestamps. While a Test Lab scenario runs, a `TEST RUNNING: <name>` banner with an **Abort** button. |
| **Timer** | Hour, minute and second fields, **Set timer**, **No timer** and **Reset timer**, then the **Presets** list (rename, change, **Use**, delete, or **Add preset**). Invalid input shows in red. Changing the timer restarts the countdown; it also shows while paused (AFK off). |
| **Detection** | Grouped as Restart keywords (restart, ignore and queue, comma-separated), Restart timing (post-resume cooldown, queue-gone time, no-reconnect fallback, max restart wait, settle delay, "release crouch on restart"), Connection (reconnect grace, **Auto reconnect**, attempts, delay) and Window (**Keep running when unfocused**). |
| **Recovery** | Stuck detection and the recovery walk settings, plus the last yaw turn and the last recovery result. |
| **Stats** | The current session, lifetime totals, the last 20 sessions (hover one for details) and **Reset lifetime stats** (asks first). |
| **Display** | A live **HUD preview** (drawn by the same code as the real HUD), a legend of the state colours (green mining, yellow recovering, red restarting, orange reconnecting, aqua resuming, grey off), then HUD on/off, detailed mode, corner and scale. |
| **Debug** | **Log all incoming messages**, **Open log folder**, **Copy debug report**, and **Export settings** / **Import settings** (to and from `config/afkmod-export.json`; an import replaces every setting, out-of-range values are corrected, and Cancel undoes it except for the timer length). |
| **Test Lab** | The message tester with presets, the options, every scenario grouped A to I with a **Run** button (disabled, with the reason in a tooltip, when the mod is OFF and the scenario needs it ON), the self-test, Abort and the results list. Scenarios that move the player carry a `(!)` tag; those that can disconnect ask for confirmation. |

Validation: numbers must be inside their range and the restart and queue keyword lists cannot be empty. A bad value
turns the field red with a message under it and is **not** applied, so bad input can never reach the config.

**Save** writes `config/afkmod.json` and stays open. **Done** saves and closes. **Cancel** (or Esc) undoes the changes
made since the last save. Timer actions apply to the running countdown, so Cancel does not undo them. All other changes
take effect immediately, no reload needed, and leaving the screen any other way (for example to run a Test Lab
scenario) saves them too.

## Test Lab and the self-test

The **Test Lab** tab tests every feature without waiting for a real restart. Scenarios feed the **same** handlers the
real events use (a fake message goes through the real message handler, a fake disconnect through the real disconnect
classification, and so on); only the trigger is faked. Nothing is sent to the server and nothing is really disconnected
unless a scenario says so and you confirmed it.

- **Run** a scenario with its button. Only one runs at a time. While it runs the HUD shows a small `TEST: <name>` tag.
  Scenarios that need the mod ON (and mining) have a greyed **Run** button when it is OFF; hover it to see why.
  Scenarios that move you carry an orange `(!)` and those that can disconnect ask you to confirm first.
- **Abort** (the button, the Dashboard banner, or the unbound-by-default *Abort test* keybind) cancels the scenario,
  clears all temporary overrides, releases any key the test pressed and re-checks the mod's state. Toggling the mod,
  dying or a real disconnect also aborts.
- **Results** are PASS / FAIL / INFO with the measured values, and are kept for the last 50 runs.
- **Overrides never reach your settings.** To keep tests short, scenarios shorten some timings (queue-gone, settle
  delay, stuck window, timer, ...) in a temporary in-memory copy. `config/afkmod.json` is never touched, and the copy is
  dropped when the test ends or is aborted. (If you change a setting while a test runs, it takes effect after the test.)
- **Test data never reaches your stats.** Everything a test causes is flagged `[TEST]` and goes to a separate in-memory
  area that is never written to `afkmod-stats.json`.
- **After a test** the mod is put back as it was: still ACTIVE, with the same restart count, timer and cooldown.

| Group | Scenarios |
|---|---|
| A. Messages | **A1** message tester: type a text, pick the source, then *Explain* (works with the mod OFF; shows the stripped text, which ignore/restart/queue keyword matched, the cooldown and what would happen, without triggering anything) or *Send for real* (mod ON; goes through the real handler). **A2** presets (`Servers are updating`, `whispered servers`, `restart queue #3` on the action bar, unrelated text) checked against their expected result. |
| B. Restart flow (mod ON) | Full flow with reconnect, no reconnect (fallback), queue text only, reconnect before the text ends, a repeated message not counted, flicker shorter/longer than `queueGoneSeconds`, a message during the cooldown, "whispered", never reconnects (max wait), a manual disconnect, a server-initiated reconnect while ACTIVE. |
| C. Keys (mod ON) | **C1** per key: mode, state, switch it off, and the mod must turn it back on within 2 checks with at most one press per check. **C2** the screen-open rule. |
| D. Movement | **D1** yaw table (edge values, mod OFF ok). **D2** edge-check report without moving. **D3** forced recovery with per-frame sampling of yaw and pitch (pitch identical, yaw monotonic with no overshoot, ease-in-out speed, duration within 0.15 s of `recoveryTurnSeconds`, exact multiple of 90, walk only after the turn). **D4** armed stuck detection. **D5** failed-recovery path (the final logout is only logged as `WOULD DISCONNECT` unless you tick *Real disconnect*). **D6** rotation only. D3-D6 move you and ask for confirmation. |
| E. Timer (mod ON) | **E1** short timer, *Dry end* runs everything except the final disconnect, *Full* really disconnects (asks first). **E2** the timer pauses across a restart. |
| F. Stats | Inject a fake session, save/load round trip and corrupt-file handling on a **temp file**, clear test data. |
| G. HUD preview | Cycles the HUD through mining, restarting, recovering, paused, no timer and timer running (compact and detailed), marked `[PREVIEW]`, without changing the real state. |
| H. Self-test | **Run self-test** runs every scenario marked safe for automatic running (A, B, C, D1, D2, E1 dry end, E2, F, G) one after another. Scenarios that need the mod ON are skipped (INFO) if it is OFF, so **turn the mod ON while standing in a safe spot first**. It stops cleanly on Abort, always clears overrides and test data, shows a summary in chat and saves `config/afkmod-selftest.txt` with PASS/FAIL per scenario. |
| I. Debug report | **I1** builds the report (see below) and copies it. |

**Suggested first run in single-player:** open a creative world, press `K`, open the menu (`J`), go to **Test Lab**,
press **Run self-test**, wait for the summary in chat (the scenarios run one after another, each with real waiting
time, so it takes a while) and open `config/afkmod-selftest.txt`. Every
line should be PASS; anything FAIL or INFO tells you what to look at.

## Reading the debug logs and sending a debug report

**Logs.** Turn on **Log all incoming messages** (Debug tab). Then `config/afkmod-messages.log` gets one line per event,
each with a timestamp down to the millisecond, and the same lines appear in `logs/latest.log`:

| Tag | Meaning |
|---|---|
| `[SYSTEM]`, `[ACTION_BAR overlay]`, `[TITLE]`, `[SUBTITLE]`, `[BOSS_BAR]`, `[CHAT]` | An incoming text and where it came from, followed by `(RESTART)`, `(IGNORED)` or `(NONE)`: what the mod decided. `CHAT` is only logged for diagnosis. |
| `[EVENT]` | Things that happened: `Queue text appeared/disappeared`, `Disconnected (UNEXPECTED) while RESTARTING`, `Joined world while ...`, `Stuck detected: ...`, `Recovery attempt 1 of 3 started`, `Recovery: turning yaw 163.40 -> 180.0 (north) over 0.50 s`, `Recovery attempt 1 result: SUCCESS`. |
| `[STATS]` | The mod's event log (toggled on/off, restart detected, resumed, timer set/ended, logout, ...). |
| `[TEST]` | Anything caused by the Test Lab. It is never real. |

State changes are always written to `logs/latest.log` as `FROM -> TO (REASON)`, for example
`ACTIVE -> RESTARTING (RESTART_DETECTED)`, `RESTARTING -> SETTLING (REJOINED)`, `-> OFF (TIMER_END)`, even with
logging off. The `Disconnected (...)` line shows how the mod classified a disconnect: `UNEXPECTED` is what a server
reconnect should show; `CLIENT_INITIATED` means a pause-menu disconnect or quitting.

**Debug report.** On the Debug tab (or the Test Lab tab) press **Copy debug report**. It is copied to the clipboard
and saved as `config/afkmod-report.txt`. It contains the mod, Minecraft, Loader, Fabric API, Java and OS versions, the
current state, the effective settings and any active test overrides, the keybinds (and whether Sneak/Attack/Use are in
toggle mode), the last 100 events, the last test results and the last 50 debug log lines. The **server address and your
player name are replaced by placeholders** unless you switch off *Redact identity in report*. Paste the text where you
need to send it; read it first.

## Confirming the restart keyword with debug logging

Do this once on the real server **before relying on the mod**, so you know the restart text really contains your
keyword.

1. Press `J`, open the **Debug** tab, turn **Log all incoming messages** ON, press Done. (The mod can stay OFF.)
2. Stay on the server until a restart is announced.
3. Open `config/afkmod-messages.log` (the **Open log folder** button opens `config`) or search `logs/latest.log` for
   `[message]`.
4. Each line looks like:

   ```
   2026-10-03 14:02:11.204 [SYSTEM] (RESTART) Servers restarting in 10 seconds
   2026-10-03 14:02:12.950 [ACTION_BAR overlay] (RESTART) restart queue #3
   2026-10-03 14:02:12.951 [EVENT] Queue text appeared
   2026-10-03 14:02:15.010 [SYSTEM] (IGNORED) Bob whispered to you: servers are fun
   2026-10-03 14:02:20.330 [TITLE] (NONE) Welcome back
   ```

   The bracket is where the text came from: `SYSTEM`, `ACTION_BAR overlay`, `TITLE`, `SUBTITLE`, `BOSS_BAR` (and
   `CHAT`, logged for diagnosis only, never used for detection). The parentheses are what the mod decided:
   - `RESTART`: it would start or refresh RESTARTING (only if the mod is ON and not in the post-resume cooldown).
   - `IGNORED`: matched an ignore keyword.
   - `NONE`: no match.
5. **The restart announcement should be tagged `RESTART`.** If it shows `NONE`, add a word that really appears in the
   message to **Restart keywords** (Detection tab) (the log shows the exact text and source). If an unrelated message shows `RESTART`,
   make the keyword more specific or add an ignore word. The queue text should show as `[ACTION_BAR overlay]`; if its
   wording differs, change **Queue keywords**.
6. `[EVENT]` lines record when the queue text appears and disappears and when you disconnect or join, so you can see
   the whole restart timeline.
7. Turn **Log all incoming messages** OFF again when done. Line breaks inside a message are written as `\n`.

## Settings reference

`config/afkmod.json` is created with defaults if missing. Hand-edited values outside the ranges below are clamped
when the file is loaded. Every setting except **Check interval** has a control in the settings screen with an info icon
showing the same text as this table. (Check interval is file-only: it changes how often the mod's checks run, and 20 is
right for nearly everyone.)

<!-- SETTINGS-TABLE:START (generated from SettingInfo, see ReadmeSettingsTableTest) -->
| Setting | Config key | Default | Range | What it does |
|---|---|---|---|---|
| Check interval | `checkIntervalTicks` | 20 ticks | 1-200 ticks | How often the mod re-checks crouch, clicking, movement and timers. 20 ticks = 1 second. Lower reacts faster but does more work; 20 is plenty. |
| Restart keywords | `restartKeywords` | servers, restart queue |  | Words that mark a message as a server-restart warning. If an incoming server message (chat, action bar, title...) contains any of them, the mod enters restart mode. Comma-separated, not case-sensitive, matches parts of words. |
| Ignore keywords | `ignoreKeywords` | whispered |  | Messages containing any of these words are completely ignored, even if they also contain a restart keyword. Use it to block false triggers such as private messages. |
| Queue keywords | `queueKeywords` | restart queue |  | The text your server shows above the hotbar during a restart. The mod waits until this text has disappeared before it treats the restart as finished. |
| Queue gone time | `queueGoneSeconds` | 3 s | 0-3600 s | How long the queue text must stay gone before the mod believes the restart is over. Prevents resuming during brief flickers. Raise it if the mod resumes too early. |
| Settle delay | `settleDelaySeconds` | 3 s | 0-3600 s | Extra wait after the restart ends and you are back in the world, before the mod re-activates crouch and clicking. Gives the server a moment to settle. |
| Post-resume cooldown | `postResumeCooldownSeconds` | 15 s | 0-3600 s | After resuming, restart messages are ignored for this long, so a leftover message can't start a second restart straight away. |
| No-reconnect fallback | `noReconnectFallbackSeconds` | 30 s | 0-3600 s | If the queue text disappears but the server never reconnects you, the mod resumes by itself after this long. |
| Reconnect grace | `reconnectGraceSeconds` | 120 s | 0-3600 s | If you get disconnected without a restart warning, the mod waits this long for you to rejoin before giving up and switching off. |
| Max restart wait | `maxRestartWaitMinutes` | 15 min | 1-600 min | The longest the mod will wait for a restart to finish. After that it releases everything and switches off. |
| Release crouch on restart | `releaseCrouchOnRestart` | off |  | Also stop crouching while the server restarts. By default only left and right click are released. |
| Timer | `timerSeconds` | 0 s (none) | 0 s-100 h | The AFK countdown. When it reaches zero the mod stops everything and disconnects to the server list. Time spent in server restarts does not count. 0 means no timer. |
| Timer presets | `timerPresets` | 30 min, 1 hr, 4 hr, 8 hr |  | Named timer lengths shown as one-click buttons in the Timer tab. Add, rename, change or delete them there. Names are at most 24 characters. |
| Keep running when unfocused | `keepRunningUnfocused` | on |  | Stops Minecraft from pausing when you switch to another window (alt-tab) while the mod is ON, so it keeps mining. Minecraft's own 'pause on lost focus' option is left as it is and applies again when the mod is OFF. |
| Auto reconnect | `autoReconnect` | on |  | If the connection is lost while the mod is ON (not a restart the server handles itself, not the timer ending, not you leaving), try to join the same server again. Single-player and Realms are not reconnected. |
| Reconnect attempts | `reconnectAttempts` | 5 | 1-50 | The most times the mod tries to rejoin after one connection loss. All attempts must fit inside the Reconnect grace, because the mod switches off when the grace runs out. |
| Reconnect delay | `reconnectDelaySeconds` | 10 s | 1-600 s | How long to wait after the connection is lost before the first attempt, and between attempts. |
| Log all incoming messages | `debugLogging` | off |  | Writes every incoming message to afkmod-messages.log so you can see the exact text your server sends. Turn it on when setting up keywords. |
| Show HUD | `hudEnabled` | on |  | Show or hide the on-screen status line. |
| Detailed HUD | `hudDetailed` | off |  | Show 2-3 lines of status instead of one compact line. |
| HUD corner | `hudCorner` | Top left |  | Which screen corner the status line sits in. |
| HUD scale | `hudScale` | 0.75 x | 0.5-2 x | Size of the status line. Smaller takes less screen space. |
| Stuck detection | `movementCheckEnabled` | on |  | Turns stuck detection on or off. When on, the mod notices if you have stopped moving and tries to get you going again. |
| Stuck window | `stuckWindowSeconds` | 20 s | 1-600 s | How long you must stay under the distance below before the mod decides you are stuck. |
| Stuck distance | `stuckDistanceBlocks` | 3 blocks | 0.1-64 blocks | If you moved less than this many blocks sideways (height is ignored) during the window, you count as stuck. Raise it if you get false alarms on a slow path. |
| Walk distance | `recoveryWalkBlocks` | 2 blocks | 0.1-64 blocks | How far the mod walks forward to get you un-stuck. |
| Walk timeout | `recoveryWalkTimeoutSeconds` | 5 s | 0.5-120 s | Gives up on a walk attempt if it hasn't covered the distance within this time. The turn before the walk doesn't count. |
| Retries | `recoveryRetries` | 2 | 0-20 | How many more times to try after the first failed attempt. When every attempt fails, the mod stops and disconnects to the server list. |
| Edge safety check | `recoveryEdgeCheck` | on |  | Before walking, checks the blocks ahead for drops, lava, fire or cactus and refuses to walk if it is unsafe, because you are not crouching during the walk. Turn it off only if you know the path is safe. |
| Turn duration | `recoveryTurnSeconds` | 0.5 s | 0-10 s | How long the turn to face the nearest north, east, south or west takes. The turn speeds up and slows down smoothly. Only the left-right direction changes, never up or down. 0 turns instantly. |
| Redact identity in report | `reportRedactIdentity` | on |  | Hides your server address and username in the debug report so you can share it safely. |
<!-- SETTINGS-TABLE:END -->

This table is generated from the `SettingInfo` registry, the same source the GUI tooltips use. A unit test fails when
it is out of date; `java -cp ... dev.afkmod.config.SettingInfo` prints the current table.

Two more fields stay in the file but are **unused** (the server sends no "all clear" message, and the queue-text logic
replaced the clear timeout): `restartEndKeywords` (`[]`) and `restartClearTimeoutSeconds` (10).

## Known limits

- Vanilla only keeps mining while the window is focused and no screen is open. Alt-tabbing opens the pause menu unless
  "pause on lost focus" is off (F3+P). While a screen is open the mod deliberately does nothing.
- The bottom-left HUD corner can overlap the chat. Use a top corner if it bothers you.
- If the server never comes back after a restart, the mod turns itself off after `maxRestartWaitMinutes` (15).

## License

MIT. See [`LICENSE`](LICENSE).
