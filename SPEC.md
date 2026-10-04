# AFK Mod: Full Specification

Client-side Fabric AFK helper for **Minecraft Java 26.1.2**, used on a server where staff have approved AFK mining. There are NO chat commands: everything is controlled through a GUI and keybinds.

The project is built in 4 phases (see "Phases" at the bottom). Only work on the phase you are told to work on. Keep `PROGRESS.md` updated at the end of every session.

---

## Environment (verify before coding)
- Minecraft 26.1.2 is unobfuscated and uses Mojang names, so there are NO Yarn/Intermediary mappings. Use Mojang class names (e.g. `KeyMapping`, `Minecraft`, `LocalPlayer`, `Screen`). Older names such as `KeyBinding`, `InputUtil` and `KeyBindingHelper` have been renamed.
- Java 25, latest Gradle, Fabric Loom (a version that supports 26.x unobfuscated), Fabric Loader 0.19.3+, Fabric API 0.155.3+26.1.2 (or the newest for 26.1.2).
- Check https://docs.fabricmc.net/develop and the Fabric example mod / template generator for the exact current versions and build.gradle setup. Do not rely on memory for API names. Look at the actual 26.1.2 sources and Fabric API 26.1.2 and confirm each class/method exists before using it.
- Client-only mod. Mod id `afkmod`, name "AFK Mod". Create `fabric.mod.json` and any mixin config needed.
- `./gradlew build` must pass at the end of every phase. Output jar path goes in the README.

## Keybinds (registered in Minecraft's Controls menu, configurable)
- **Toggle AFK** (default `K`): turns the mod ON/OFF.
- **Open AFK menu** (default `J`): opens the GUI.

## Performance rule: check every 20 ticks
All per-tick logic (verifying crouch/LMB/RMB, applying corrections, restart end checks (queue text gone, no-reconnect fallback, max wait), settle delays, post-resume cooldown, timer-end checks) runs ONCE EVERY 20 TICKS using a tick counter in `ClientTickEvents.END_CLIENT_TICK`. The interval is configurable (`checkIntervalTicks`, default 20). NOT limited to 20 ticks: incoming-message detection (event-driven, instant), HUD rendering (every frame, only reads state), and keybind handling (instant).

## Core behaviour (state ACTIVE)
When ON and running, keep all three active:
1. Crouch (sneak)
2. Left click (attack/mine)
3. Right click (use item/eat)

The player has "toggle" mode enabled for these in Minecraft's settings. The mod must handle BOTH toggle and hold modes:
- On each 20-tick check, detect the real current state: crouch via the player's actual sneaking state, attack/use via `KeyMapping.isDown()` (verify this reflects the toggled state).
- If one is not active, activate it by simulating ONE press (one click for toggle-mode keys). Never press more than once per check per key, because that would toggle it back off.
- Write helpers like `ensureActive(key)` and `ensureInactive(key)` that work for toggle-type and normal key mappings.
- Release/toggle off everything the mod turned on whenever the mod stops, so no key is left stuck.
- Do nothing while any screen is open (the mod's GUI, chat, inventory). When it closes, re-verify the three states on the next check.
- Turn the mod OFF immediately on player death or when I manually disconnect (e.g. via the pause menu).

## Restart detection (state RESTARTING)
- **How the real server behaves:**
  1. When a restart is coming, it sends ONE normal chat/system message containing "servers".
  2. Then it shows an **action bar** text (middle of the screen, above the hotbar) like `restart queue #N`. While that text shows, the server is frozen.
  3. When the action bar text disappears, the restart is done. The server then reconnects the player: the client drops to the "connecting to server" screen and rejoins automatically. This is a server-initiated reconnect/transfer, NOT a kick and NOT a manual disconnect.
  4. There is no "all clear" message.
- **Match rule:** a message is a restart message if its text contains a restart keyword (case-insensitive substring match, formatting codes stripped). Configurable list `restartKeywords` (default `["servers", "restart queue"]`), editable in the GUI. An **action bar (overlay)** message matching `queueKeywords` (default `["restart queue"]`, editable in the GUI) also starts or refreshes a restart, even if `restartKeywords` doesn't contain it. `restartEndKeywords` is kept in the config (default empty) but is no longer used.
- **Ignore rule (takes priority over the match rule):** if a message contains "whispered" (case-insensitive), ignore it completely, even if it also contains "servers". Configurable list `ignoreKeywords` (default `["whispered"]`), editable in the GUI.
- Normal player chat is disabled on this server, so there is no player-chat option. Listen to all incoming text via Fabric `ClientReceiveMessageEvents` (game/system messages, including the overlay/action bar flag), and also title, subtitle and boss bar text via mixins if feasible. Apply the ignore rule, then the match rule.
- On detection: release left click and right click (and optionally crouch, via `releaseCrouchOnRestart`, default false), and show the compact "Server restarting" indicator (see HUD). Show no number: **never show the queue number in the mod's HUD**. Count restarts this session. If the server repeats the message (or the queue text) while already RESTARTING, do not count a new restart; just refresh a "last seen" timestamp.
- **Post-resume cooldown:** after leaving RESTARTING and returning to ACTIVE, ignore restart keyword matches (including the queue text) for `postResumeCooldownSeconds` (default 15, editable in the GUI) so a leftover or re-sent message can't immediately re-trigger a restart.
- **End detection.** Resume only when BOTH are true, in either order (the reconnect may happen before or after the text disappears, even while it still shows):
  1. **Queue text gone:** no action bar text matching `queueKeywords` is on screen. Preferred: read the client's real action bar state (accessor mixin on `Gui` for `overlayMessageString` / `overlayMessageTime`; it is drawn only while the time is above 0). Fallback if that can't be read: time since the last matching action bar message. It must be continuously gone for `queueGoneSeconds` (default 3) so flicker or refresh gaps don't end the restart early. A restart with no queue text at all counts the text as gone from the restart message on.
  2. **Rejoined, or the no-reconnect fallback:** the player rejoined (Fabric `ClientPlayConnectionEvents.JOIN`) since the restart began, OR no disconnect happened and the queue text (or last restart message) has been gone for `noReconnectFallbackSeconds` (default 30). While disconnected, the fallback never fires; only the rejoin can end the wait.
  - Then go to SETTLING: wait `settleDelaySeconds` (default 3, evaluated by the 20-tick check, counted only while the world is loaded and the player is valid), then re-verify and re-activate crouch, left click and right click. If the queue text shows up again while settling, go back to RESTARTING (same restart, not counted again).
  - **Max wait:** if the restart hasn't ended `maxRestartWaitMinutes` (default 15) after it began (e.g. the rejoin never comes), turn the mod OFF, release everything, and do nothing else.
- **Server-initiated reconnect (important):** it appears to the client as a disconnect, the connecting screen, then a rejoin. The mod must NOT turn itself off because of it.
  - Treat a disconnect as manual only if I triggered it (pause menu Disconnect button, or quitting the game) or the mod's own timer did. Everything else (kick, drop, the server's reconnect/transfer) is "unexpected".
  - While in RESTARTING, an unexpected disconnect just means "wait for the rejoin".
  - If a disconnect happens while ACTIVE and no restart message was detected, don't turn off immediately. Wait up to `reconnectGraceSeconds` (default 120) for a rejoin, then turn off if none arrives.
  - A manual disconnect by me, death, or timer end still turns the mod off immediately.
  - Persist the mod's state across the reconnect (timer paused, restart count, config: the client-side state object must survive the leave/rejoin). Reset stale per-world things like cached player/level references.
  - While the connecting/loading screens show, do nothing (the "screen open" rule). The 20-tick loop must handle a null player/level safely.
- The mod does not need to auto-reconnect after a real disconnect.

## Timer
- Set in the GUI. Default: no timer.
- The countdown starts when the mod is toggled ON. If the timer is changed while ACTIVE, restart the countdown from the new value.
- **Time spent during the whole restart (message -> queue text -> reconnect -> settle delay) does NOT count.** Pause the countdown there. Use wall-clock time (`System.nanoTime`), not ticks, so the 20-tick interval doesn't cause drift.
- When the timer ends (detected by the 20-tick check): release/toggle off everything the mod turned on, turn crouch off, fully turn the mod OFF (it only restarts when I press the hotkey again), then disconnect to the **multiplayer server list screen** (not the title screen, don't close the game). Run on the client thread. Check the exact disconnect method signature in 26.1.2. This disconnect must be recognised as intentional (not as a server reconnect).

## HUD: compact but clearly visible
Takes very little screen space but is easy to read at a glance. Use the current Fabric HUD API for 26.1.2 (check whether `HudRenderCallback` is deprecated and use the replacement).
- **Default = ONE small line** in a screen corner (default top-left) on a semi-transparent dark backing so it's readable on any background. Example: `● AFK Mining | 1:23:45 | C L R | ↻2`
  - Colored status dot: green = mining, red = server restarting, grey = paused/waiting.
  - Timer remaining (or `∞` if no timer; show paused while restarting).
  - `C L R` = crouch / left click / right click, each green when active and red when not.
  - `↻N` = restarts this session.
- **Restart indicator:** while RESTARTING, the line turns red and reads `● Server restarting`, plus a small red banner (not full-screen, not huge text) at the top center reading `Server restarting`, with a dark backing.
- Default small scale. Never draw large or screen-filling text.
- Options (also in the GUI): HUD on/off, corner position (cycle button), scale (slider, default small), and a "detailed mode" toggle that expands to 2-3 short lines. Compact one-line mode is the default.
- Hide the HUD entirely when the mod is OFF. Avoid overlapping the F3 debug screen if easy.

## GUI (opened with the "Open AFK menu" keybind)

> **Superseded** by "GUI rebuild (tabbed screen with info icons)" at the end of this file. Kept for the history of the first version.
A clean, **compact** custom `Screen` using vanilla widgets (verify the 26.1.2 widget/Screen API): a small centered single-column panel that fits a normal window.
- Big ON/OFF button for the mod, plus a live status line (state, restarts this session, timer remaining).
- **Timer section:** hour / minute / second fields (or one duration field accepting `2h30m`), "Set timer" and "No timer" buttons. Validation errors in red.
- **Detection section:** text field for restart keyword(s) (comma-separated, default `servers, restart queue`), text field for ignore keyword(s) (comma-separated, default `whispered`), text field for queue keyword(s) (comma-separated, default `restart queue`), number fields for the post-resume cooldown, `queueGoneSeconds`, `noReconnectFallbackSeconds` and `maxRestartWaitMinutes`, and a checkbox "release crouch during restart".
- **HUD section:** show/hide HUD, detailed mode toggle, corner position (cycle button), scale (slider).
- **Debug section:** a "Log all incoming messages" toggle that writes every incoming system/action bar/title/subtitle/boss bar text, with its type, to the game log and a file `afkmod-messages.log` in the config folder. Action bar messages are marked as overlay (`[ACTION_BAR overlay]`). It also logs, with timestamps, when the queue text appears and disappears and when a disconnect or join happens (`[EVENT]` lines). Works even when the mod is OFF. Also an "Open log folder" button if feasible.
- Done/Save and Cancel buttons. Changes save to `config/afkmod.json` and take effect immediately (no reload command).
- Also add an "AFK Mod" button to the pause menu (Fabric Screen API, `ScreenEvents.AFTER_INIT` on the pause screen) that opens the same GUI.
- Do not require Mod Menu or Cloth Config. Optional Mod Menu integration is allowed only if it adds no hard dependency.

## Config (`config/afkmod.json`, created with defaults if missing)
`checkIntervalTicks` (20), `restartKeywords` (["servers", "restart queue"]), `ignoreKeywords` (["whispered"]), `queueKeywords` (["restart queue"]), `restartEndKeywords` ([], unused), `restartClearTimeoutSeconds` (10, unused), `queueGoneSeconds` (3), `noReconnectFallbackSeconds` (30), `maxRestartWaitMinutes` (15), `settleDelaySeconds` (3), `postResumeCooldownSeconds` (15), `reconnectGraceSeconds` (120), `releaseCrouchOnRestart` (false), `timerSeconds` (0 = none), `debugLogging` (false), `hudEnabled`, `hudDetailed`, `hudCorner`, `hudScale`.

## Code structure and testing
- Keep logic that does not touch Minecraft in plain Java classes (state machine ACTIVE/RESTARTING/OFF including queue-text/reconnect/fallback/max-wait/grace-period/cooldown rules, duration parsing, keyword matching with the ignore rule, the pausable timer) so it can be unit tested.
- JUnit tests for: duration parsing; keyword matching (case-insensitive, formatting codes stripped, a message with "whispered" is ignored even if it contains "servers"); the timer pausing during the whole restart; state transitions for server reconnect vs manual disconnect vs timer-end disconnect; queue text gone before the reconnect; reconnect before the queue text is gone; the no-reconnect fallback; the max wait; the post-resume cooldown.
- No hidden or obfuscated behaviour. The mod must be transparent and visible on screen at all times while active.

---

## Phases

**Phase 1: Foundation.** Gradle project and build setup verified against 26.1.2, `fabric.mod.json`, the config class with all fields, and ALL the pure-Java logic with unit tests. A minimal initializer that loads the config. No keybinds, HUD, GUI or Minecraft hooks yet.

**Phase 2: In-game control.** Both keybinds registered, the 20-tick loop, `ensureActive`/`ensureInactive` for crouch/LMB/RMB (toggle and hold modes), message hooks (including the optional title/subtitle/boss bar mixins), restart and relog handling, post-resume cooldown, timer-end disconnect to the multiplayer screen, death and manual-disconnect handling, and the message debug logger (core only, no GUI toggle yet; use the config flag).

**Phase 3: HUD and GUI.** The compact HUD, the settings screen, the pause menu button, and wiring every GUI control to the config.

**Phase 4: Polish.** README (build, install, keybinds, GUI guide, how to confirm the restart keyword using debug logging), edge cases (screens open, death, manual disconnect, relog without a message, stuck keys), cleanup and a final review against this spec.

---

## Movement Recovery (stuck detection + auto recovery)

**Background:** the server sometimes lags and the player stops moving for no reason while AFK. In the farm the player is ALWAYS moving when everything works (water stream, minecart, walking path), so "not moving" means something is wrong.

### Detection
- On each 20-tick check (ACTIVE state only), record a sample (`System.nanoTime`, x, z) of the player's position. Keep samples for `stuckWindowSeconds` (default 20).
- **Stuck** = the window is fully populated (at least `stuckWindowSeconds` of samples) AND the max horizontal distance (x/z only, ignore y) between the current position and any sample in the window is less than `stuckDistanceBlocks` (default 3).
- Clear the sample window (so there are no false triggers) on: mod toggled ON, entering RESTARTING, resume from restart, any join/rejoin, death, a screen being open (and when it closes), and after each recovery attempt.
- Config: `movementCheckEnabled` (default true), `stuckWindowSeconds` (20), `stuckDistanceBlocks` (3).

### Recovery (state RECOVERING, a sub-state of ACTIVE; restart handling always takes priority and cancels recovery)
1. Switch OFF left click, right click AND crouch/shift using `ensureInactive` (works for toggle and hold modes, one press per key). Wait up to 3 s until they read inactive.
2. Turn the player to face the nearest cardinal direction EXACTLY, using a smooth, controlled rotation (NOT an instant snap). Change ONLY the yaw. NEVER change the pitch (up/down look) at any point in this feature, including during the turn. Yaw convention in Minecraft: south = 0, west = 90, north = 180 (equivalently -180), east = -90 (equivalently 270); verify this against the 26.1.2 sources.
   - Target yaw: normalize the current yaw to [0, 360), pick the nearest multiple of 90 with a deterministic tie-break (a yaw exactly on a 45-degree boundary rounds up). "Mostly north" must end up exactly north.
   - Motion: ease-in-out. The rotation ACCELERATES from rest, reaches its peak speed around the middle, then DECELERATES smoothly to a stop exactly on the target. Total duration `recoveryTurnSeconds` (default 0.5). Use a smooth easing curve (cosine ease-in-out or smootherstep) with zero speed at both ends, monotonic progress, and no overshoot or wobble.
   - Turn the short way round (at most 180 degrees) and handle wraparound correctly (for example 170 -> -180, or 350 -> 0).
   - Drive the rotation from wall-clock time (`System.nanoTime`) so it takes 0.5 s regardless of tick rate. Apply it every frame if there is a clean hook (for example at the start of rendering, keeping the yaw and the previous-yaw fields coherent); otherwise every tick with the previous-yaw field set so the camera interpolates between ticks. Make sure the server receives consistent rotation updates.
   - At the end, set the yaw to the exact target value (not accumulated float maths).
   - `recoveryTurnSeconds` = 0 means an instant snap. The rotation time does NOT count toward `recoveryWalkTimeoutSeconds`.
3. The new facing is permanent. Do NOT restore the previous yaw afterwards. Leave the player facing the cardinal direction.
4. Only after the rotation has fully finished (yaw exactly on the target), jump, and hold forward (W) until the player has moved `recoveryWalkBlocks` (default 2) horizontally from the start point, or `recoveryWalkTimeoutSeconds` (default 5) have elapsed. Release forward and jump when done. Do NOT walk back afterwards; the player stays wherever the walk ended.
5. This sequence needs per-tick precision, so it runs EVERY tick while RECOVERING. This is the one exception to the 20-tick rule; everything else stays on 20 ticks.
6. Success = moved at least `recoveryWalkBlocks`. Then re-verify and re-activate crouch, left click and right click (`ensureActive`), clear the sample window, and return to ACTIVE.
7. An attempt counts as FAILED if the walk fails (didn't move enough before the timeout) OR if after a "successful" recovery the next full window is stuck again. Allow `recoveryRetries` (default 2) retries after the first failure (3 attempts total). The attempt counter resets only after a full window shows normal movement.
8. If all attempts fail: release everything, turn the mod fully OFF, and disconnect to the multiplayer server list (an intentional disconnect, not a relog). Log the reason.
9. Safety: crouch is off during the walk, so edges are dangerous. Before walking forward, check the 2 blocks ahead at the player's level: if there is no solid ground within 1-2 blocks below, or lava, fire, cactus, magma or other harmful blocks are on the path, do NOT walk. Count it as a failed attempt. Config `recoveryEdgeCheck` (default true). Verify the 26.1.2 APIs for block/fluid state checks.
10. Cancel recovery cleanly (stop any rotation in progress and leave the yaw wherever it is, with no snapping back; release W/jump; re-verify keys) if a screen opens, the mod is toggled off, the player dies, a restart starts, or the player disconnects.

Config: `recoveryTurnSeconds` (0.5), `recoveryWalkBlocks` (2), `recoveryWalkTimeoutSeconds` (5), `recoveryRetries` (2), `recoveryEdgeCheck` (true).

### HUD/debug
The HUD status dot is yellow with "Recovering" during recovery. The debug log records the stuck detection (positions, window distance), the yaw snap (old yaw -> new yaw, direction name), each attempt and its result.

### Structure for a later "Test Lab"
The stuck detector, the recovery sequence, the edge check and the final "log out" action are each callable/overridable through clear methods (force a stuck detection, inject a failed attempt result, replace the final disconnect with a no-op for tests). No test tooling is built yet.

### Tests
Unit tests for the pure logic: window/distance calculation, target-yaw selection (including negative yaws, values like 179.9 / -179.9 / 44.99 / 45 / 359, tie-break), the rotation easing (zero speed at the start and end, a single speed peak in the middle, monotonic progress, never overshoots, reaches the target exactly at the end, total duration equals `recoveryTurnSeconds`, shortest-arc direction including wraparound such as 170 -> -180 and 350 -> 0, duration 0 = instant), attempt and retry counting, state transitions, cancellation on restart.

---

## Stats + event log

Pure-Java stats model (package `dev.afkmod.stats`, no Minecraft imports, unit tested) plus thin wiring in the client code. No GUI work in this feature; the GUI and the later Test Lab consume the API below.

### Session
A session runs from the mod being toggled ON until it turns OFF (any reason). Tracked per session:
- Start and end time (epoch ms), total wall time, **active mining time = wall time minus restart time**, total restart time.
- Restart count, longest and average restart duration. A restart lasts from the first restart detection (ACTIVE -> RESTARTING) until the mod is back in ACTIVE (end of the settle delay). A repeat of the same restart is not a new restart. A restart still open when the session ends is closed at the end time. Time spent in a relog with no restart message is NOT restart time.
- Recovery: stuck detections (every full-window stuck evaluation, including forced ones), attempts started, successes (walk completed), failures (failed walk, or stuck again after a "successful" walk that ended in giving up), and whether the session ended in a logout (the mod itself disconnected: timer end or recovery failed).
- Blocks mined: counted from Fabric's client-side `ClientPlayerBlockBreakEvents.AFTER` (fires after the client breaks a block while the mod is ON). It counts client-side breaks, so a server that rolls a break back could be over-counted slightly; this is stated in the UI/docs where shown. Items used/eaten are NOT tracked: Minecraft's stat counters only update on the client after the server answers a stats-screen request (`REQUEST_STATS`), which the mod must not send on its own, and there is no client-side "item consumed" event; right-click use attempts would be fake numbers.
- End reason: `TIMER_ENDED`, `MANUAL_TOGGLE`, `DEATH`, `MANUAL_DISCONNECT`, `RECOVERY_FAILED`, `RECONNECT_GRACE_EXPIRED`, `MAX_RESTART_WAIT_EXCEEDED`, plus `INTERRUPTED` for a session that was still open when the game stopped or crashed.

### Lifetime and history
Lifetime totals are the sum of every finished real session (session count, wall, active and restart time, restart count, longest restart, stuck/attempt/success/failure counts, logouts, blocks mined, sessions per end reason). The 20 most recent sessions are kept, newest first.

### Persistence: `config/afkmod-stats.json`
Written atomically (temp file in the same folder, then move). A missing, empty or corrupt file means starting fresh (a corrupt file is moved aside as `.corrupt`); loading and saving never throw. Saved at session end, every few minutes (3) while a session runs, and when the game stops. The periodic save also stores the open session; if the game dies, the next load turns it into a finished `INTERRUPTED` session.

### Test flag and test data
Every stats update and every event carries a `test` boolean. Test-flagged updates never touch the real session, lifetime totals or history. They go to a separate in-memory test-data structure (test counters plus injected test sessions) that `clearTestData()` empties (this also removes test-flagged events from the event log). Test data is never written to the stats file.

### Event log
In-memory ring buffer of the last 100 events: timestamp (epoch ms), type, test flag, short text. Types: `TOGGLED_ON`, `TOGGLED_OFF`, `RESTART_DETECTED`, `QUEUE_TEXT_GONE`, `RECONNECTED`, `RESUMED`, `STUCK_DETECTED`, `YAW_SNAP` (with direction), `RECOVERY_ATTEMPT`, `RECOVERY_RESULT`, `TIMER_SET`, `TIMER_ENDED`, `LOGOUT` (with reason). Every event is also written to the debug log (`[STATS]` lines in `afkmod-messages.log`) when `debugLogging` is on.

### API (for the GUI and the Test Lab)
`getCurrentSession()` (snapshot, or null when the mod is off), `getLifetime()`, `getRecentSessions()`, `getRecentEvents(n)` (the last n, oldest first), `resetLifetime()` (clears lifetime totals and the recent-session list; the GUI confirms first; the open session is unaffected), `injectTestSession(...)` (test data only), `clearTestData()`, and a way to point the store at another file (`StatsStore` takes its file path; `setFile`) so tests never touch the real file.

### Tests
Time excludes restarts, average/longest restart, restart open at session end, lifetime rollup, recent-session cap, corrupt/missing file handling, atomic save round trip, interrupted-session recovery, ring buffer cap, test-flag exclusion from real counters/lifetime/history, `clearTestData`, end-reason mapping.

---

## Test Lab (debugging toolkit)

A toolkit for testing EVERY feature of the mod without waiting for real server events. Package `dev.afkmod.testlab` (pure Java, no Minecraft imports, unit tested) plus a thin client layer (`client/GameTestEnvironment`, and the Test Lab tab of the settings screen, `client/gui/TestLabTab`).

### Principles
1. **Real code paths.** Scenarios feed the SAME handlers, state machine, recovery sequence and timer code that real events use. No parallel mock logic; only the external trigger is faked. A message goes through `AfkController.onIncomingText` (an action bar message also sets the real action bar, like the packet does), a simulated disconnect goes through the real `DisconnectTracker` classification and `AfkController.onDisconnect`, a simulated join through `AfkController.onJoin`.
2. **No network side effects.** Never really disconnect unless a scenario explicitly says so AND the caller confirmed. A "simulated reconnect" runs the mod-side handling of the real disconnect/join events without disconnecting. During every test the mod's own disconnects (timer end, recovery give-up) are replaced by a logged `WOULD DISCONNECT` no-op unless the scenario opted in.
3. **Test flag.** Every event, log line and stats effect caused by a test carries `[TEST]` and goes to the stats test data (Stats feature), never to the real counters, lifetime totals or session history. While a test runs the real session is left untouched (no start/end); if a test leaves the mod OFF and it can't be restored, the real session ends with the end reason `TEST_LAB`.
4. **Temporary overrides.** Scenarios may shorten `queueGoneSeconds`, `noReconnectFallbackSeconds`, `maxRestartWaitMinutes` (as seconds), `stuckWindowSeconds`, `settleDelaySeconds`, `reconnectGraceSeconds`, `postResumeCooldownSeconds`, `timerSeconds` and `movementCheckEnabled` through an in-memory override layer. `AfkModClient.config()` returns the effective config (saved config plus overrides, a separate copy while any override is set); `AfkModClient.savedConfig()` is the persistent object the GUI edits and saves. The layer never modifies the saved config and is cleared when the test ends or aborts.
5. **Safety.** Scenarios that need the mod ON refuse to run unless it is ON and ACTIVE, and say so. Scenarios that move the player or can disconnect need an explicit `confirmed` option (the screen shows a confirm dialog). One scenario runs at a time. **Abort** (a button and the keybind "Abort test", default unbound) cancels the running scenario, clears the overrides, releases any key the test pressed and re-verifies the mod's state. Toggling the mod (keybind or GUI), player death or a real disconnect also aborts. While a test runs the HUD shows a small `TEST: <scenario name>` tag.
6. **Results.** Every scenario ends with PASS / FAIL / INFO plus a short reason with the measured values. An aborted scenario ends as INFO "Aborted: ...". Results go to the event log (type `TEST_RESULT`, test-flagged), the debug log and an in-memory list (last 50).
7. **Restoring.** After each scenario the mod is put back as it was: if it was ON (ACTIVE) and the test left it in another state, it is reset to ACTIVE (in test mode, so no stats), and the timer's remaining time, the restart count and the post-resume cooldown are restored. Not done after an abort caused by the player (toggle, death, real disconnect).

### Scenarios
Each is a class with `id`, `name`, `description`, `requiresModOn`, `needsConfirm`, `safeForAuto` (plus `requiresWorld`, `runsInGame`). Groups and ids:
- **A. Messages.** A1 message detector (Explain mode works with the mod OFF and reports the stripped text, matched ignore/restart/queue keyword, cooldown suppression, state and what would happen, without triggering anything; Send for real needs the mod ON and goes through the real handler). A2 presets (`Servers are updating`, `whispered servers`, `restart queue #3` on the action bar, unrelated text), each checked against its expected classification.
- **B. Restart flow** (mod ON). B1 full flow with reconnect, B2 no reconnect (fallback), B3 queue text only, B4 reconnect before the text ends, B5 repeated message not counted, B6 flicker gap shorter/longer than `queueGoneSeconds`, B7 message during the cooldown ignored, B8 "whispered" ignored, B9 never reconnects (max wait overridden to seconds: OFF, released, no disconnect), B10 simulated manual disconnect (OFF at once), B11 server-initiated reconnect while ACTIVE (stays ON on rejoin; OFF when the overridden grace expires). Each asserts the transitions in order, L/R released and the timer paused during the restart, and C/L/R re-activated after resume.
- **C. Key control** (mod ON). C1 per key: mode, state, `ensureInactive`, the mod turns it back on within 2 check intervals with at most one press per check (presses are counted by instrumenting `ensureActive`/`ensureInactive`). C2 screen-open rule.
- **D. Movement recovery.** D1 yaw snap calculator plus the built-in edge-value table (mod OFF ok). D2 edge check report without moving (mod OFF ok). D3 forced recovery with per-frame yaw/pitch sampling (pitch identical, yaw monotonic without overshoot, ease-in-out speed, duration = `recoveryTurnSeconds` within 0.15 s, exact multiple of 90, walk only after the turn, distance, keys restored). D4 armed stuck detection (`stuckWindowSeconds` overridden to 5; fires within window + one check interval). D5 failed recovery path (injected failures, attempt count = 1 + `recoveryRetries`, final logout as `WOULD DISCONNECT` unless `allowRealDisconnect`). D6 rotation only (no jump/walk, optional start yaw). D3 to D6 need confirmation.
- **E. Timer** (mod ON). E1 short timer (default 10 s): "dry end" runs the whole real timer-end path except the final disconnect; "full" includes the real disconnect and needs confirmation. E2 timer pause across a B1 restart (paused duration = restart duration within 1 s).
- **F. Stats** (never the real stats file). F1 inject a fake flagged session, F2 save/load round trip on a temp file, F3 corrupt temp file handling, F4 clear all test data.
- **G. HUD preview.** G1 cycles the HUD through mining, restarting, recovering, paused, no timer, timer running, about 4 s each, compact and detailed, WITHOUT changing the real state; the HUD shows `[PREVIEW]`.
- **H. Self-test.** H1 runs every `safeForAuto` scenario in sequence (A, B1-B11, C, D1, D2, E1 dry end, E2, F, G), skipping (INFO) those that need the mod ON when it is OFF; stops cleanly on abort; always clears overrides and test data afterwards; shows the summary and saves a PASS/FAIL report to `config/afkmod-selftest.txt`.
- **I. Debug report.** I1 builds a text report (mod, Minecraft, Loader, Fabric API, Java, OS, state, effective config and active overrides, keybinds, last 100 events, last test results, last 50 debug log lines), copies it to the clipboard and saves `config/afkmod-report.txt`. The server address and the player name are redacted by default (`reportRedactIdentity`, default true).

### API and UI
- Java API on `TestLab` (via `AfkController.get().testLab()`): `listScenarios()`, `run(scenario, options)` (returns started or refused with the reason), `abort()`, `getResults()`, `isRunning()`, plus `runSelfTest()`.
- Keybinds "Open Test Lab" and "Abort test" (both default unbound). "Open Test Lab" opens the AFK settings screen on its Test Lab tab (see "GUI rebuild").
- The Test Lab tab (formerly the standalone `TestLabScreen`, now embedded in the settings screen and sharing `client/gui/TestLabActions`): plain layout. Scenarios grouped under the headings above with a Run button each (paged), a text field and message-type selector for A1 (Explain / Send) with the A2 preset buttons, option fields (yaw, restart seconds, timer seconds, E1 mode, D5 real disconnect), a results list, Abort, Run self-test and Copy debug report. Confirm dialogs for scenarios that need them. A scenario that runs in the game closes the screen first (the mod does nothing while a screen is open).

### Tests
Unit tests for the runner state machine (one at a time, refusals, steps, timeouts, abort, cleanup, suite), the override layer (set, clear, never touches the saved config), the result model and ring, the explain matcher (agrees with the real state machine), the yaw table, the rotation analyzer, the report builder (incl. redaction) and the scenario scripts run against a simulated environment built on the real `AfkStateMachine`.

---

## GUI rebuild (tabbed screen with info icons)

Replaces the single-column settings screen (`AfkSettingsScreen`) and the standalone `TestLabScreen` with one tabbed screen,
`client/gui/AfkMenuScreen`, opened by the same "Open AFK menu" keybind, the pause menu button, and (on its Test Lab tab) the
"Open Test Lab" keybind. No Mod Menu / Cloth Config dependency.

### Widgets (verified against 26.1.2)
Vanilla's tab widgets still exist and are used: `TabNavigationBar`, `TabManager`, `Tab` (the Create World screen's widgets).
Tooltips use vanilla tooltip rendering (`GuiGraphicsExtractor.setTooltipForNextFrame` with a custom `ClientTooltipPositioner`).

### Layout
- A centred panel, about 60% of the screen width, at most 420 px, at least 300 px when the window allows it (never wider than the
  screen); sizes are in scaled GUI pixels, so it follows the GUI scale. Tab bar across the top, **Save / Done / Cancel** at the bottom.
- Tabs scroll by whole rows (mouse wheel, Page Up / Page Down) when the window is too small; a row is only shown if it fits completely.
- Changes still apply immediately to the live config. Save writes `config/afkmod.json` and stays open; Done saves and closes; Cancel (or
  Esc) undoes the changes since the last save (not the timer, which acts on the running countdown). Leaving the screen any other way saves.

### Info icons (every setting)
- A small circled "i" next to the label of every setting (text field, slider, checkbox, cycle button, number field). Hovering the icon,
  hovering the label, or keyboard-focusing the icon shows a tooltip: a plain-language description (1-3 sentences), then a final line with
  the default, unit and range, e.g. `Default: 20 s - Range: 5-120 s`.
- Tooltips wrap at about 40 characters per line, stay fully on screen (clamped on all four edges) and are drawn deterministically each
  frame (no delay, no flicker).
- **One registry**, `config/SettingInfo`: config field name -> display name, description, unit, min/max (plus Test Lab option entries).
  The GUI (labels, validation, tooltips), the tooltips and the README settings table all read from it. The default is read by reflection
  from a fresh `AfkConfig` (Test Lab options: from `TestOptions.defaults()`), never typed twice. `AfkConfig.sanitize()` clamps to the same
  ranges.
- Unit tests: `SettingInfoTest` uses reflection over `AfkConfig` and FAILS if a field has no entry or an empty description, if an entry refers
  to a field that no longer exists, and checks the displayed defaults equal the real defaults. `ReadmeSettingsTableTest` fails when the README
  table (between the `SETTINGS-TABLE` markers) differs from the registry.
- Test Lab scenarios get the same icon with the scenario's description and what a PASS means (`testlab/ScenarioInfo`, checked by
  `ScenarioInfoTest`). The Test Lab's own settings (`testRestartSeconds`, `reportRedactIdentity`, and the other options) are covered too.

### Tabs
1. **Dashboard** (live, read-only except the toggle): big ON/OFF button; state with a coloured dot (green mining, red restarting, yellow
   recovering, grey off); timer remaining with a progress bar; restart count and the current restart's elapsed time; crouch / left click /
   right click indicators; movement (distance moved in the current window, last recovery result, last yaw snap); a session summary; the last 8
   events with timestamps; a `TEST RUNNING: <name>` banner with an Abort button while a test runs.
2. **Timer**: hour / minute / second fields, Set timer, No timer, validation in red.
3. **Detection**: restart, ignore and queue keywords, post-resume cooldown, `queueGoneSeconds`, `noReconnectFallbackSeconds`,
   `maxRestartWaitMinutes`, release-crouch-on-restart.
4. **Recovery**: `movementCheckEnabled`, `stuckWindowSeconds`, `stuckDistanceBlocks`, `recoveryWalkBlocks`, `recoveryWalkTimeoutSeconds`,
   `recoveryRetries`, `recoveryEdgeCheck`, `recoveryTurnSeconds`, plus the last yaw turn (direction and duration) and the last recovery result.
5. **Stats**: current session, lifetime totals, a scrollable list of the last 20 sessions, "Reset lifetime stats" with a confirmation.
6. **Display**: HUD on/off, detailed mode, corner, scale.
7. **Debug**: "Log all incoming messages", open log folder, "Copy debug report".
8. **Test Lab**: embeds the existing Test Lab (scenario list, `TestLab` API, confirm dialogs, message tester with presets, options, results,
   Abort, Run self-test, debug report); the logic is shared in `client/gui/TestLabActions`. Run buttons are disabled with the reason in a
   tooltip when the mod is OFF and the scenario needs it ON; scenarios that move the player carry a warning tag; scenarios that can
   disconnect ask for confirmation.

### Validation
Numbers must be inside their registered range; the restart and queue keyword lists must not be empty (the ignore list may be). Invalid input
shows an inline red message, is never applied to the config, and can never throw (`gui/FieldValidator`, unit tested).

### Tests (pure Java, `dev.afkmod.gui`)
Text wrapping, tooltip text and placement (clamping on every edge), field validation, row scrolling and the panel size rule.
