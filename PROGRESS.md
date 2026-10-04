# AFK Mod: Progress

## Status: Phase 4 (Polish) complete, plus the restart-handling update, Movement Recovery, Stats + event log, the Test Lab and the GUI rebuild. Nothing has been run in-game yet

`./gradlew clean build` passes with 252 JUnit tests and 0 failures (run 2026-10-04, after the final review; see "Final review and polish" at the end).
The restart rules in the Phase 1/2 notes below are **superseded** by "Restart handling update" further down.
Phases 2 to 4 **have not been run in-game yet**. Use "How to test Phase 2/3" and the "Real server checklist" below.
Output jar: `build/libs/afkmod-0.1.0.jar`.

> **Build needs JDK 25.** On this machine `JAVA_HOME` points at `C:\Java\jdk-20`, which fails with
> "Dependency requires at least JVM runtime version 25". Build with:
> `JAVA_HOME="C:/Program Files/Java/jdk-25.0.2" ./gradlew build` (Git Bash), or point `JAVA_HOME` at JDK 25 permanently.

## Phase 1: done

### Build setup (verified, not from memory)
Every version below was copied from the official `FabricMC/fabric-example-mod` **26.1.2 branch** and cross-checked
against `meta.fabricmc.net` and `maven.fabricmc.net`:

| Item | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.5 (latest stable; the spec's minimum was 0.19.3) |
| Fabric Loom | `1.18-SNAPSHOT` (resolves to 1.18.2) |
| Fabric API | 0.155.3+26.1.2 (newest for 26.1.2) |
| Gradle (wrapper) | 9.7.1 (wrapper files taken from the example mod) |
| Java | 25 (`options.release = 25`) |

- The mod is client-only (`"environment": "client"`, only a `client` entrypoint). There's a single `main` source set,
  because the example's `splitEnvironmentSourceSets()` isn't needed for a client-only mod.
- Tests use `net.fabricmc:fabric-loader-junit`, as described on docs.fabricmc.net "Automated Testing". It brings in the
  JUnit Jupiter engine and the platform launcher.
- There's no mixin config yet, because nothing needs a mixin in Phase 1. Add `afkmod.mixins.json` in Phase 2 for the
  title, subtitle and boss bar hooks.

### Files
- `src/main/resources/fabric.mod.json`: id `afkmod`, name "AFK Mod", depends on loader >=0.19.5, minecraft ~26.1.2,
  java >=25 and fabric-api.
- `dev.afkmod.AfkModClient`: minimal `ClientModInitializer` that loads `config/afkmod.json`. It exposes `config()` and
  `configFile()`.
- `dev.afkmod.config.AfkConfig`: has all 15 spec fields with their spec defaults, and uses Gson.
  - `load` creates the file if it's missing, writes it back so new fields appear, and moves a corrupt file aside as
    `.bak`.
  - `save` writes through a temporary `.tmp` file.
  - `sanitize` clamps bad values from a hand-edited file.
- `dev.afkmod.config.HudCorner`: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT`, with `next()` for the GUI
  cycle button.
- `dev.afkmod.logic.DurationParser`
  - Accepts `2h30m`, `1h 5m 3s`, `45m`, `90s`, `1:30:00`, `30:00` and a bare number of seconds.
  - `fromFields(h, m, s)` builds a duration from the three GUI fields.
  - `format()` returns `H:MM:SS`.
  - Errors are `DurationParseException`, with short messages meant to be shown in red in the GUI.
  - The maximum is 100h.
- `dev.afkmod.logic.KeywordMatcher`: case-insensitive substring match after stripping `§` codes. Precedence is
  **ignore > restart-end > restart**. `parseList` and `joinList` handle the comma-separated GUI fields.
- `dev.afkmod.logic.PausableTimer`: wall-clock countdown with an injected `LongSupplier` clock (`System::nanoTime` in
  game). It can pause, resume, and restart from a new value.
- `dev.afkmod.logic.AfkStateMachine`: the core logic, with no Minecraft imports. Details below.

### State machine design (for Phase 2 to wire up)
States are `OFF`, `ACTIVE`, `RESTARTING`, `SETTLING` (the settle delay after a restart ends) and `RECONNECTING`
(the grace period after an unexplained disconnect). The timer only runs in `ACTIVE`.

Inputs the Minecraft layer must call:

| Call | When |
|---|---|
| `toggle()` / `turnOn()` / `turnOff()` | Keybind and GUI |
| `onMessage(text)` | Every incoming system, action bar, title, subtitle and boss bar text. It returns the `KeywordMatcher.Result`, which is useful for the debug log. |
| `onJoin()` | `ClientPlayConnectionEvents.JOIN` |
| `onDisconnect(cause)` | `CLIENT_INITIATED` for the pause menu or a manual disconnect, `MOD_INITIATED` for the mod's own timer-end disconnect, `UNEXPECTED` for anything else, including the restart relog |
| `onDeath()` | Player death |
| `tick(worldReady, worldResponding)` | Every `checkIntervalTicks`. `worldResponding` should mean the server is ticking again, for example world game time advanced since the last check. |
| `setTimerSeconds(s)` | GUI. It writes `config.timerSeconds` and restarts the countdown if the mod is on. |

Outputs: a `Listener.onTransition(from, to, reason)` fires on every transition.
- On **leaving ACTIVE**, Phase 2 should release LMB and RMB, plus crouch when the target state is OFF or
  `releaseCrouchOnRestart` is set.
- On `Reason.TIMER_END`, Phase 2 should release everything, then disconnect to the multiplayer screen on the client
  thread and report it as `MOD_INITIATED`.

The config is read live through a `Supplier<AfkConfig>`, so GUI changes take effect immediately.

Rules that are covered by tests:
- A restart message in ACTIVE moves to RESTARTING and adds 1 to the count. A repeat while RESTARTING or SETTLING only
  refreshes "last seen" and isn't counted again.
- An `UNEXPECTED` disconnect while RESTARTING keeps the mod on and waits for the rejoin. The clear timeout can't fire
  while disconnected.
- An `UNEXPECTED` disconnect while ACTIVE moves to RECONNECTING. A rejoin within `reconnectGraceSeconds` moves to
  SETTLING and then ACTIVE. Otherwise the mod turns OFF (`GRACE_EXPIRED`).
- `CLIENT_INITIATED` and `MOD_INITIATED` disconnects, death, and timer end turn the mod OFF immediately. A later rejoin
  never turns it back on.
- RESTARTING ends on (a) a rejoin, (b) a restart-end keyword, or (c) `restartClearTimeoutSeconds` with no message
  while the world is responding. All three go through SETTLING, whose delay only counts while `worldReady` and resets
  if the world becomes not ready.
- The post-resume cooldown starts on SETTLING → ACTIVE and uses the current config value. It's cleared when the mod is
  toggled off and on.
- The timer is paused during RESTARTING, SETTLING and RECONNECTING. Changing it while ACTIVE restarts the countdown,
  and changing it while paused restarts it paused.

### Decisions I made where the spec was silent (change if you disagree)
1. **The timer also pauses during the reconnect grace period** after an unexplained disconnect. That time isn't
   mining either.
2. **The post-resume cooldown also applies after a relog without a restart message** (RECONNECTING → SETTLING →
   ACTIVE). Every path back to ACTIVE after a wait gets the cooldown.
3. **The restart count is per game session.** It isn't reset when the mod is toggled.
4. **There's no timeout while RESTARTING and waiting for the rejoin.** This follows the spec ("ignore the disconnect
   and wait for the rejoin"). If the server never comes back, the mod stays in RESTARTING with no keys pressed until it
   is toggled off.
5. **The HUD scale defaults to 0.75**, within the range 0.5 to 2.0, because the spec says "default small scale".
6. **Restart-end keywords are checked before restart keywords**, so an "all clear" message containing "servers" isn't
   treated as a new restart. Ignore keywords still win over both.

## API notes for 26.1.2 (found while setting up)
- The Loom plugin id is now **`net.fabricmc.fabric-loom`**; the old id was `fabric-loom`. There is **no `mappings`
  line**, and dependencies use plain **`implementation`**, not `modImplementation`, because the game is unobfuscated.
- Loom 1.18 **requires Gradle itself to run on a Java 25 JVM**. Gradle on Java 20 failed at configuration time.
- In Fabric API 0.155.3+26.1.2 jars:
  - **`HudRenderCallback` does not exist anymore.** The HUD API is
    `net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry` (with `HudElement` and
    `VanillaHudElements`). Use this in Phase 3.
  - **`KeyBindingHelper` is gone.** The replacement is `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper`.
    Use this in Phase 2.
  - These classes are present: `client.message.v1.ClientReceiveMessageEvents`,
    `client.networking.v1.ClientPlayConnectionEvents`, `client.event.lifecycle.v1.ClientTickEvents`,
    `client.screen.v1.ScreenEvents` and `client.screen.v1.Screens`.
  - I only checked that these classes exist. Method signatures still need checking against the sources in Phase 2/3.
- The example mod uses `net.minecraft.resources.Identifier` (`Identifier.fromNamespaceAndPath`), not
  `ResourceLocation`.
- Gradle warns that the project sits in OneDrive, which can slow builds or lock files. It works, but consider moving
  the project.

## Phase 2: done (in-game control)

### How every 26.1.2 API was verified
- I ran `./gradlew genSources` (Loom + Vineflower) and read the decompiled 26.1.2 sources for every Minecraft class
  used.
- I ran `javap` on the Fabric API 0.155.3 jars for every Fabric class used.
- All 7 mixin target methods were confirmed with `javap` to exist exactly once with the expected parameter types.
- Nothing was written from memory.

### New files
| File | What it does |
|---|---|
| `client/AfkController` | The session-lifetime singleton. It registers the keybinds, runs the tick loop, listens to Fabric events, reacts to state-machine transitions and does the timer-end disconnect. `AfkModClient` calls `AfkController.init()` after loading the config. |
| `client/KeyControl` | `ensureActive`/`ensureInactive`/`isActive` for `CROUCH`, `ATTACK` and `USE`. |
| `client/MessageLog` | The debug message logger. |
| `client/BossBarTextReader` | Reads text from boss bar packets. It's kept out of the mixin so the mixin has no inner classes. |
| `mixin/ClientPacketListenerMixin` | Title, subtitle, action bar packet, boss bar, death (combat kill) and server game time. |
| `mixin/ClientLevelMixin` | Marks a client-side quit. |
| `logic/DisconnectTracker` | Pure Java, tested. Classifies a disconnect as `MOD_INITIATED`, `CLIENT_INITIATED` or `UNEXPECTED`. |
| `logic/ServerTimeWatcher` | Pure Java, tested. Gives the "world is responding again" signal. |
| `logic/MessageSource` | `SYSTEM`, `ACTION_BAR`, `TITLE`, `SUBTITLE`, `BOSS_BAR`, `CHAT`. |
| `afkmod.mixins.json` | Mixin config (`JAVA_25`, `defaultRequire: 1`), registered under `"mixins"` in `fabric.mod.json`. |
| `assets/afkmod/lang/en_us.json` | Key and category names. |

### How each piece works (with the verified 26.1.2 facts)
- **Keybinds:**
  - Both are registered with `KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM,
    InputConstants.KEY_K / KEY_J, category))`.
  - The category is `KeyMapping.Category.register(Identifier.fromNamespaceAndPath("afkmod", "afkmod"))`. Its lang key
    is `key.category.afkmod.afkmod`.
  - Keybinds are read every tick with `consumeClick()`, so they respond instantly.
  - **J only shows an action bar note for now**, because the GUI is Phase 3.
- **The tick loop:**
  - It runs from `ClientTickEvents.END_CLIENT_TICK`. A counter runs the check every `checkIntervalTicks`, default 20.
  - Turning the mod on, or returning to ACTIVE, forces a check on the next tick.
  - The check does three things in order:
    1. A death fallback (`isDeadOrDying`).
    2. `machine.tick(worldReady, worldResponding)`.
    3. If no screen is open, key correction. ACTIVE presses C, L and R. RESTARTING, SETTLING and RECONNECTING keep
       L and R released, plus C when `releaseCrouchOnRestart` is set.
- **Toggle mode and hold mode:**
  - `options.keyShift`, `keyAttack` and `keyUse` are all `ToggleKeyMapping`s. Each is driven by
    `options.toggleCrouch()`, `toggleAttack()` or `toggleUse()`.
  - In toggle mode, `setDown(true)` **flips** the state and `setDown(false)` is ignored. In hold mode, `setDown` sets
    the state directly. **`isDown()` is the effective state in both modes**, so it does reflect the toggled state.
  - `ensureActive` looks at the state first and only calls `setDown(true)` if the key is off. Crouch also counts as
    on if `player.isShiftKeyDown()` is true.
  - `ensureInactive` presses once in toggle mode (`setDown(true)` flips it off) and calls `setDown(false)` in hold
    mode.
  - Each helper presses at most once per call, so a key can never be toggled back off.
- **Screen restore quirk (vanilla):**
  - `setScreen` calls `KeyMapping.releaseAll()`. Closing a screen calls `restoreToggleStatesOnScreenClosed()`, which
    turns keyboard-bound toggle keys back on (this affects crouch).
  - When the mod releases a key while a screen is open, it clears that memory with the public
    `ToggleKeyMapping.shouldRestoreStateOnScreenClosed()`. Without this, crouch would come back on by itself after
    death or after the mod turns off.
- **Messages and the ignore/match rules:**
  - `ClientReceiveMessageEvents.GAME` covers system messages and the action bar (`overlay=true`).
  - The mixins add `ClientPacketListener.setTitleText`, `setSubtitleText`, `setActionBarText` (the dedicated action
    bar packet, which doesn't go through the chat listener) and `handleBossUpdate` (only the `add` and `updateName`
    operations).
  - All of them go to `AfkStateMachine.onMessage`, which applies the ignore rule first and the match rule second.
  - Player chat (`CHAT`) is only written to the debug log and is never used for detection.
  - Every hook is at TAIL. Each handler starts with `ensureRunningOnSameThread`, which re-queues the packet and throws
    on the Netty thread, so TAIL only runs on the client thread.
- **Manual disconnect vs. relog:**
  - In 26.1.2, every client-side quit calls `ClientLevel.disconnect(Component)` first. This covers `PauseScreen`
    "Disconnect" / "Save and Quit", the `DeathScreen` "Title screen" button, and closing the game.
  - Server kicks (`ClientCommonPacketListenerImpl.onDisconnect` calling `minecraft.disconnect(...)`) never call it,
    and neither do proxy relogs (`handleConfigurationStart` leads to `clearClientLevel`).
  - `ClientLevelMixin` marks those calls. Fabric's `DISCONNECT` event, which can fire on the Netty thread from
    `channelInactive`, reads the cause and passes it to the client thread with `mc.execute`.
  - The timer-end disconnect is marked `MOD_INITIATED`, which wins over the client-quit mark.
  - **A proxy relog (reconfiguration) fires no `DISCONNECT`, only a new `JOIN`** (`JOIN` fires at the RETURN of
    `handleLogin`). Fabric's `ConnectionMixin` only calls `endSession` on a protocol switch. RESTARTING still ends on
    that JOIN.
- **"World responding" for the restart clear timeout:**
  - `ClientLevel.tickTime()` advances the game time locally even while the server is frozen, so the mod uses the
    **server-sent** `ClientboundSetTimePacket.gameTime()` (through the `handleSetTime` hook).
  - The world counts as responding when that value advanced since the previous check.
- **Death:**
  - The instant path is the `handlePlayerCombatKill` TAIL hook, when `packet.playerId()` is our own entity id.
  - The fallback is `isDeadOrDying()` in the check.
- **Timer end:**
  - The keys are released and the state goes to OFF. The disconnect runs on the same tick, after the state-machine
    call returns.
  - The sequence is `level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE)` followed by
    `mc.disconnect(new JoinMultiplayerScreen(new TitleScreen()), false)`.
  - Vanilla's `disconnectFromWorld` sends single-player to the **title** screen, so the mod calls
    `disconnect(Screen, boolean)` directly. That way it always lands on the server list. For an integrated server,
    `disconnect` still halts the server and saves the world.
- **Per-world reset:**
  - Nothing per-world is cached. The player and level are read from `Minecraft` every time.
  - On each JOIN, the disconnect marks, the server-time baseline and the tick counter are reset.
  - The state machine lives for the whole session, so it survives the relog.
- **Debug logger:**
  - It's controlled by `debugLogging` in `config/afkmod.json` and works even when the mod is OFF.
  - Each line goes to the game log as `[message] SOURCE (RESULT): text` and is appended to
    `config/afkmod-messages.log` as `yyyy-MM-dd HH:mm:ss [SOURCE] (RESULT) text`. RESULT is `NONE`, `IGNORED`,
    `RESTART` or `RESTART_END`. Newlines in a message are written as `\n`.
- **Temporary feedback:** until the HUD exists, every state change shows a short action bar line, for example
  "AFK Mod: mining" or "AFK Mod: OFF (timer finished)". Transitions are also logged as INFO lines, e.g.
  `ACTIVE -> RESTARTING (RESTART_DETECTED)`.

### Known limits (vanilla behaviour, not bugs)
- **Unfocused window:** vanilla only keeps mining while the mouse is grabbed and no screen is open
  (`Minecraft.handleKeybinds`). Alt-tabbing opens the pause menu unless "Pause on lost focus" is off (F3+P). While a
  screen is open the mod deliberately does nothing.
- **Proxy relog while ACTIVE with no restart message:** there's no disconnect event, only a JOIN. (Fixed in Phase 4:
  the JOIN now moves ACTIVE -> SETTLING, so the timer pauses and the settle delay and cooldown apply.)
- **Config edits** only take effect after a restart until the Phase 3 GUI exists.

## How to test Phase 2 in a test world
Run it with `JAVA_HOME="C:/Program Files/Java/jdk-25.0.2" ./gradlew runClient`, or put
`build/libs/afkmod-0.1.0.jar` and Fabric API in a 26.1.2 Fabric instance.

**Setup:**
1. Create a single-player **Creative** world with cheats on.
2. Stand facing a block, with food or blocks in your hand.
3. Press **F3+P** so the game doesn't pause when you click away.
4. To watch the log, open `logs/latest.log` (in `run/` for `runClient`).
5. Restart the game after editing `config/afkmod.json`.

| # | Behaviour | Steps | Expected |
|---|---|---|---|
| 1 | Keybinds registered | Options > Controls > Key Binds, scroll to "AFK Mod". | "Toggle AFK" = K and "Open AFK menu" = J. Both can be rebound. J shows "settings menu isn't built yet". |
| 2 | Toggle on/off | Press K, wait 2 s, press K again. | ON: the action bar shows "AFK Mod: mining", you crouch, the block breaks repeatedly and right-click is held. OFF: all three stop, "OFF (toggled off)". |
| 3 | Toggle mode, no double press | Options > Controls: set Sneak, Attack and Use to **Toggle**. Press K and watch for 10 s or more. | All three stay on steadily. A check every second never flickers them off. |
| 4 | Hold mode | Set Sneak, Attack and Use to **Hold** and repeat test 3, then press K. | Same steady result. After K, nothing stays stuck. |
| 5 | Correction | While ON in toggle mode, tap Shift yourself (crouch turns off). | Within about 1 s the mod turns crouch back on with one press. |
| 6 | Screens | While ON, open chat (T) or the inventory (E) for a few seconds, then close it. | Nothing is pressed while it's open. Within about 1 s after closing, crouch, attack and use are back on. |
| 7 | Restart detection | While ON, run `/tellraw @a "Servers restarting in 10s"`. | Attack and use release at once. "server restarting". The log shows `ACTIVE -> RESTARTING (RESTART_DETECTED)`. Crouch stays on (`releaseCrouchOnRestart` is false). |
| 8 | Clear timeout + settle | After test 7, wait. | About 10 s after the last message: `-> SETTLING (RESTART_CLEAR_TIMEOUT)`. About 3 s later: `-> ACTIVE (SETTLED)`, and the keys are pressed again. |
| 9 | Repeat not counted | Send the message from test 7 three times within 10 s. | Only one `RESTART_DETECTED` line. Each repeat pushes the 10 s clear timeout back. |
| 10 | Server freeze | Run `/tick freeze`, then send the message from test 7. Wait 20 s, then run `/tick unfreeze`. | It stays RESTARTING while frozen. It moves to SETTLING about 10–11 s after unfreeze. |
| 11 | Ignore rule | While ON, run `/tellraw @a "Bob whispered: servers are fun"`. | Nothing happens; the mod stays ACTIVE. |
| 12 | Post-resume cooldown | Within 15 s of test 8 reaching ACTIVE, send the message from test 7. Then send it again after 15 s. | The first is ignored. The second triggers RESTARTING. |
| 13 | Title / subtitle / action bar | While ON, try each of these, waiting for ACTIVE plus 15 s between them: `/title @a title "Servers restarting"`, `/title @a subtitle "servers"` (then `/title @a title "x"` to show it), and `/title @a actionbar "servers restarting"`. | Each triggers RESTARTING. |
| 14 | Boss bar | Run `/bossbar add afk:r "Servers restarting"`. Afterwards run `/bossbar remove afk:r`. | RESTARTING when the bar is added (the packet arrives once you're added with `/bossbar set afk:r players @a`). A rename with `/bossbar set afk:r name "servers"` also counts. |
| 15 | Debug logger | Set `"debugLogging": true`, restart, and repeat tests 7, 11, 13 and 14 with the mod **OFF**. | `config/afkmod-messages.log` gets lines like `... [SYSTEM] (RESTART) Servers restarting in 10s`, `[SYSTEM] (IGNORED) Bob whispered...`, `[TITLE] (RESTART) ...` and `[BOSS_BAR] (RESTART) ...`, and the game log shows the same. With `false`, nothing is written. |
| 16 | Timer end | Set `"timerSeconds": 30`, restart, press K. | After about 30 s: the keys release, crouch turns off, the world saves, and you land on the **Multiplayer server list** (not the title screen). The log shows `-> OFF (TIMER_END)`. Rejoining doesn't turn the mod back on. |
| 17 | Timer pauses in restart | With `timerSeconds: 30`, press K, then do test 7 at about 10 s and let it resume. | The disconnect happens about 30 s of ACTIVE time after you pressed K. The roughly 13 s in RESTARTING and SETTLING isn't counted. |
| 18 | Death | Survival, mod ON, run `/kill`. | OFF at once ("OFF (you died)"). After respawning, crouch and attack are **not** turned back on. |
| 19 | Manual disconnect | Mod ON, press Esc, then **Save and Quit to Title** or **Disconnect**. | The log shows `Disconnected (CLIENT_INITIATED)` then `-> OFF (MANUAL_DISCONNECT)`. Rejoining leaves it OFF. |
| 20 | Unexpected disconnect + grace | Needs a dedicated 26.1.2 server. Mod ON, then `/kick <you>` from the server console, or stop the server. | `Disconnected (UNEXPECTED)` then `-> RECONNECTING`. Rejoin within 120 s and you get SETTLING, then ACTIVE after 3 s. Stay out longer than 120 s and you get `-> OFF (GRACE_EXPIRED)`. |
| 21 | Restart relog | Only possible on the real server or a Velocity/Bungee test network: restart message, then a relog or server switch. | It stays RESTARTING through the relog. `-> SETTLING (REJOINED)`, then ACTIVE 3 s after the world loads. |

## Phase 3: done (HUD and GUI)

### How every 26.1.2 API was verified
Read from the decompiled 26.1.2 sources (`genSources` jar) and `javap` on the Fabric API 0.155.3 jars. Nothing from memory.
- **Rendering was renamed in 26.1.2.** `GuiGraphics` is now `GuiGraphicsExtractor`, and drawing is "extract" based:
  `Screen.extractRenderState(graphics, mouseX, mouseY, a)`, `graphics.text(font, str, x, y, argbColor)`,
  `graphics.fill`, `graphics.outline`, `graphics.centeredText`, `graphics.pose()` (a JOML `Matrix3x2fStack`: use
  `pushMatrix`, `translate`, `scale`, `popMatrix`). Colours need the alpha byte (`0xFFxxxxxx`).
- **HUD:** `HudRenderCallback` is gone. `HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, id,
  element)` with `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`.
- **Widgets:** `Button.builder(msg, onPress).bounds(x, y, w, h).build()`, `EditBox(font, x, y, w, h, narration)` with
  `setResponder`/`setHint`/`setTextColor`, `Checkbox.builder(msg, font).pos(x, y).selected(b).onValueChange(...)`,
  `CycleButton.onOffBuilder(initial).create(x, y, w, h, name, listener)` and
  `CycleButton.builder(stringifier, default).withValues(...)`, and `AbstractSliderButton` with `updateMessage()` and
  `applyValue()`.
- **Pause menu:** `ScreenEvents.AFTER_INIT` (`afterInit(Minecraft, Screen, int, int)`) plus `Screens.getWidgets(screen)`,
  whose list adds the widget to the screen. `PauseScreen.showsPauseMenu()` is true only for the real pause menu.
- **Other:** `Util.getPlatform().openPath(Path)` in `net.minecraft.util.Util`, and
  `mc.gui.getDebugOverlay().showDebugScreen()` for F3.

### New and changed files
| File | What it does |
|---|---|
| `logic/HudText` | Pure Java, tested (`HudTextTest`, 7 tests). Builds the HUD as coloured segments. Compact is one line, detailed is three. |
| `client/AfkHud` | The HUD element: backing, scale, corner, restart banner. Reads state only, nothing is pressed or computed here. |
| `client/AfkSettingsScreen` | The settings screen. |
| `client/AfkController` | J opens the screen. Registers the HUD and the pause menu button. The temporary action bar messages are removed. |
| `config/AfkConfig` | Added `copyFrom` (used by Cancel). |

### HUD behaviour
- Compact (default): `● AFK Mining | 1:23:45 | C L R | ↻2` on a semi-transparent dark backing. The dot is green when
  mining, red while restarting and grey while resuming/reconnecting. `∞` means no timer. The timer shows `(paused)`
  in SETTLING/RECONNECTING. C, L and R are green when active and red when not.
- While RESTARTING the line turns red and reads just `● Server restarting`, plus a small red banner at the top centre
  (normal-size text, 13 px tall, dark backing).
- Detailed mode is three short lines: status and timer, `Keys: C L R`, and `↻ N restarts` (with `| cooldown` during the
  post-resume cooldown).
- Corner and scale come from the config (`hudCorner`, `hudScale`, 0.5 to 2.0, default 0.75). The scale applies to
  the block, never the banner.
- Hidden when the mod is OFF, when `hudEnabled` is false, and when the player hides the GUI (F1).
- **F3:** while the debug screen is open the top corners draw at the bottom instead, so they don't overlap it.

### Settings screen (superseded by "GUI rebuild" below)
One centred column, 224 px wide and about 250 px tall, so it fits at GUI scale 4 on a 1080p window. Sections:
- **Top:** a big ON/OFF button (disabled when OFF and not in a world) and a live status line (state, restarts, timer).
- **Timer:** one duration field accepting `2h30m`, `45m`, `1:30:00` and so on. "Set timer" and "No timer" buttons. Parse
  errors show in red under it. Timer changes go through `machine().setTimerSeconds()`, so a running countdown restarts.
- **Detection:** restart words and ignore words (comma-separated), a cooldown field (0 to 3600, red text when invalid),
  and the "Release crouch during restart" checkbox.
- **HUD:** Show HUD, Detailed, a Corner cycle button and a scale slider (0.05 steps).
- **Debug:** "Log messages" toggle and an "Open log folder" button.
- **Done / Cancel.**

How the controls reach the config: every control writes straight into the live `AfkConfig`, so the HUD and detection
change immediately, with no reload. **Done** saves `config/afkmod.json`. **Cancel** (and Esc) restores the settings from
when the screen opened, then saves. The timer is the one exception: "Set timer" and "No timer" are actions on the
running countdown, so Cancel doesn't undo them.

### Decisions I made where the spec was silent (change if you disagree)
1. **Cancel/Esc saves too.** It reverts the settings to what they were when the screen opened and writes the file, so
   the timer you set stays persisted.
2. **The restart-end keywords have no GUI field** (the spec's Detection section doesn't list one). Edit
   `restartEndKeywords` in `config/afkmod.json`; it is read on the next restart event after you reload the game.
3. **The pause menu button** goes directly under the Disconnect button, copying its width.
4. **J only opens the screen when no other screen is open.** The pause menu button covers the other case.
5. **Clearing the restart words field disables restart detection** (it gives an empty list). The default comes back
   only if the config file is edited to remove the key.

### Known limits
- The bottom corners can overlap the chat (bottom left) in vanilla. Use a top corner if that bothers you.
- At GUI scale 4 on a very short window the Done/Cancel row could fall off the bottom. Esc still works as Cancel.
- `●`, `↻` and `∞` come from the vanilla Unifont fallback. I couldn't confirm they render without running the game.
- Nothing from Phase 3 has been run in-game yet (this session had no display).

## How to test Phase 3 in a test world
Use the same setup as Phase 2 (`runClient`, Creative world, F3+P).

| # | Behaviour | Steps | Expected |
|---|---|---|---|
| 1 | HUD hidden when OFF | Join the world. | No HUD. |
| 2 | Compact HUD | Press K. | One small line top-left: green dot, `AFK Mining`, `∞`, `C L R` green, `↻0`. No action bar messages any more. |
| 3 | C L R colours | Tap Shift while ON (toggle mode). | `C` goes red, then green again within about 1 s. |
| 4 | Menu key | Press J. | The settings screen opens, HUD visible behind it. |
| 5 | Pause menu button | Esc. | An "AFK Mod" button under Disconnect; it opens the same screen. Cancel returns to the pause menu. |
| 6 | ON/OFF button | Click it. | The mod turns ON/OFF; the label and status line follow. |
| 7 | Timer | Type `1m`, Set timer. Then type `abc`, Set timer. Then No timer. | The HUD shows a countdown (`1:00`); `abc` shows a red error; No timer gives `∞`. |
| 8 | HUD options | Toggle Show HUD, Detailed, cycle Corner, drag the slider. | The HUD changes immediately. Detailed is 3 lines. The scale stays between 0.5x and 2.0x. |
| 9 | Restart banner | `/tellraw @a "Servers restarting"` while ON. | Line turns red: `● Server restarting`, plus a small red banner at the top centre. |
| 10 | Detection fields | Set restart words to `reboot`, ignore words to `ignored`, cooldown to `5`. Then `/tellraw @a "Servers"` and `/tellraw @a "reboot"`. | `Servers` is ignored now; `reboot` triggers a restart. |
| 11 | Cooldown validation | Type `abc` in cooldown. | The text turns red and the old value stays. |
| 12 | Crouch checkbox | Tick it and trigger a restart. | Crouch is released during RESTARTING. |
| 13 | Debug | Turn "Log messages" on and send a message. Click "Open log folder". | `config/afkmod-messages.log` gets the line; the config folder opens. |
| 14 | Done / Cancel | Change the corner, press Cancel; change it again, press Done; restart the game. | Cancel reverts it. Done keeps it, and it's in `afkmod.json` after the restart. |
| 15 | F3 | Press F3 with the HUD in a top corner. | The HUD moves to the bottom corner while F3 is open. |

## Phase 4: done (polish)

- **README.md** rewritten: build (JDK 25), install (Fabric Loader + Fabric API), keybinds, behaviour, HUD, GUI guide,
  how to confirm the restart keyword with debug logging, config table, known limits.
- **Spec review, line by line.** Checked: stuck keys, screens open, death, manual disconnect, relog without a message,
  post-resume cooldown, timer pausing. All were already covered except two gaps, now fixed:
  1. **Relog with no disconnect event and no message** (proxy relog while ACTIVE): the mod stayed ACTIVE, so the timer
     kept running and there was no settle delay or cooldown. `AfkStateMachine.onJoin()` now treats a JOIN while ACTIVE
     as a rejoin: ACTIVE -> SETTLING (`REJOINED`), timer paused, then ACTIVE with the cooldown. Not counted as a restart.
     New test: `joinWhileActiveIsARelogThatPausesTimerAndSettlesWithCooldown`. (This replaces the old "Known limit" in
     Phase 2 where the timer kept running through such a relog.)
  2. **`MessageLog`** had a no-op `replace("\n", "\n")`, so a multi-line message would split the log into several lines.
     It now writes `\n` as the spec/progress notes say.
- Confirmed OK by reading the code: keys are released on every transition to OFF (death, manual disconnect, timer end,
  toggle), including while a screen is open (the screen-restore memory is cleared); nothing is pressed while a screen is
  open; death works via the packet hook plus the 20-tick fallback; manual vs timer vs relog disconnects are told apart by
  `DisconnectTracker`; the timer pauses in RESTARTING, SETTLING and RECONNECTING.
- Not changed (design decisions, listed so you can veto): a restart message in SETTLING goes back to RESTARTING
  (the server is still announcing it) without counting again; there is no timeout while waiting for the rejoin.

## Restart handling update (2026-10-03, real server behaviour)

The real server: one chat/system message with "servers", then an **action bar** text `restart queue #N` while it is
frozen, then the text disappears and the server **reconnects** you (connecting screen, automatic rejoin). There is no
"all clear" message. The restart logic now follows that.

### What changed
| Area | Change |
|---|---|
| Config | New: `queueKeywords` (`["restart queue"]`), `queueGoneSeconds` (3), `noReconnectFallbackSeconds` (30), `maxRestartWaitMinutes` (15, min 1). `restartKeywords` default is now `["servers", "restart queue"]`. `restartEndKeywords` and `restartClearTimeoutSeconds` stay in the file but are **unused**. |
| Start | Chat/system "servers" as before (ignore rule first). An action bar message matching `queueKeywords` also starts/refreshes a restart, **even if `restartKeywords` lacks it**, so an existing `afkmod.json` that still has `["servers"]` works without editing. Chat text containing "restart queue" only counts if it is in `restartKeywords`. |
| End | RESTARTING ends only when the queue text has been gone for `queueGoneSeconds` **and** (a JOIN happened since the restart began **or**, with no disconnect, `noReconnectFallbackSeconds` passed since the text/last restart message). Either order works. While disconnected the fallback can't fire. Then SETTLING (settle delay starts at once, since the world is ready), then ACTIVE with the cooldown. |
| Max wait | `maxRestartWaitMinutes` after the restart began and still not resumed: `-> OFF (RESTART_TIMEOUT)`, everything released, nothing else. |
| Queue text on screen | `mixin/GuiAccessor` (new) reads `Gui.overlayMessageString` and `Gui.overlayMessageTime`. Verified in the 26.1.2 sources: `setOverlayMessage` sets the time to 60, `tick` counts it down, and the text is drawn only while it is above 0. Read in the 20-tick check. If reading ever fails, the fallback is "a queue message arrived in the last 3 s" (vanilla display time). |
| Back to RESTARTING | Queue text visible again while SETTLING: `SETTLING -> RESTARTING (QUEUE_TEXT_SHOWN)`, same restart, not counted, rejoin remembered. |
| Reconnect vs manual | Re-verified in 26.1.2: a server transfer (`handleTransfer`) and kicks close the connection without `ClientLevel.disconnect`, so they are `UNEXPECTED`. `ClientLevel.disconnect` is only called by the pause menu Disconnect, quitting the game and a resource-pack recovery abort. No code change was needed there. |
| Removed | The "world responding" signal (`handleSetTime` hook) is no longer wired in. `logic/ServerTimeWatcher` and its test are left in place but unused; delete them if you like. |
| Debug log | Action bar lines are tagged `[ACTION_BAR overlay]`. New `[EVENT]` lines: `Queue text appeared`, `Queue text disappeared`, `Disconnected (CAUSE) while STATE`, `Joined world while STATE`. Timestamps now have milliseconds. Events also go to the game log as `[event] ...`. |
| GUI | Panel is 300 px wide (was 224) and about 268 px tall. Detection adds **Queue words** and one row of number fields: **Cooldown** (s), **Gone** (s), **No-rejoin** (s), **Max (min)**. Each has a tooltip; bad values turn red and are not applied. |
| HUD | Unchanged. It never shows the queue number. |
| Tests | 11 new state-machine tests (queue gone before reconnect, reconnect while the text still shows, JOIN without disconnect, flicker, no-reconnect fallback with and without queue text, message-timing fallback, max wait while disconnected and while the text never goes, old-config overlay trigger, whispered queue text, text reappearing while settling). The restart-end and clear-timeout tests were replaced, and relog tests adjusted. Config tests cover the new defaults and sanitizing. |

### Decisions I made (change if you disagree)
1. **The max wait counts from the first restart message**, including time with the queue text still showing.
2. **The no-reconnect fallback is measured from the last restart signal** (queue text seen or a restart message), so a
   repeated "servers" countdown message also pushes it back.
3. **A restart with no queue text at all** (only the chat message) resumes on the rejoin 3 s after the message, or
   after 30 s via the fallback.
4. **Max (min) is 1 to 600** in the GUI; the other new fields are 0 to 3600 seconds.

### How to test in a test world (needs the queue text)
Use `/title @a actionbar "restart queue #3"` to imitate the queue text. Vanilla shows it for 3 s, so repeat it every
1 to 2 s (a repeating command block with a delay, or type it a few times).
| # | Steps | Expected |
|---|---|---|
| 1 | Mod ON, then only the action bar command (no chat message). | `RESTARTING`, `[EVENT] Queue text appeared` in the debug log. |
| 2 | Keep it showing 10 s, then stop. Don't disconnect. | `Queue text disappeared` about 3 s after the last one. About 30 s after the text went: `-> SETTLING (NO_RECONNECT_FALLBACK)`, then ACTIVE 3 s later. |
| 3 | Restart again (after the 15 s cooldown), and while the text shows press Esc > Disconnect. | OFF (`MANUAL_DISCONNECT`). A manual disconnect still turns it off. |
| 4 | Set **Max (min)** to 1, then show the text continuously for over a minute. | `-> OFF (RESTART_TIMEOUT)`, nothing pressed. |
| 5 | Only possible on the real server (or a Velocity test network with a transfer): a real restart. | See checklist item 6. |

## Real server checklist
Test in this order. Turn on **Log messages** first and keep `logs/latest.log` open: each transition is logged as
`FROM -> TO (REASON)`.

1. **Confirm the keyword.** Mod OFF, Log messages ON, wait for a restart announcement. The line in
   `config/afkmod-messages.log` must be tagged `RESTART`. Note its source (`SYSTEM`, `TITLE`, `BOSS_BAR`...).
2. **Does the whisper format get ignored?** Find a real whisper line in the log: it must be tagged `IGNORED` (and
   must contain "whispered"). If the server words whispers differently, add that word to the ignore field.
3. **No false positives.** Over a normal session no other line should be tagged `RESTART` (e.g. a join message with
   "servers", a plugin tip). Fix with a more specific keyword or an ignore word.
4. **Keys on the real server.** Turn the mod ON in toggle mode: crouch, attack and use stay on for a few minutes with
   no flicker. Check the HUD `C L R` stay green. Repeat once with hold mode.
5. **Screens.** Open chat/inventory while ON: nothing is pressed; about 1 s after closing, `C L R` come back.
6. **A real restart, start to end** (the main test). Expected: RESTARTING on the "servers" message (L and R released,
   red HUD, banner, no queue number anywhere in the mod's HUD). The debug log shows `[ACTION_BAR overlay] (RESTART)
   restart queue #N` lines and `[EVENT] Queue text appeared`, then `Queue text disappeared`, then
   `Disconnected (UNEXPECTED) while RESTARTING` and `Joined world while RESTARTING`. The mod is not turned off by the
   reconnect, goes `-> SETTLING (REJOINED)` once the text has been gone 3 s, then ACTIVE about 3 s later with keys
   pressed again. The `↻` count is +1, and there's no new restart for 15 s afterwards. If the disconnect shows as
   `CLIENT_INITIATED`, report it: that would mean the server's reconnect goes through a client-side quit path.
7. **Timer and the restart.** Set a timer (e.g. 20m) before a restart. After the restart the remaining time should be
   about what it was at the first restart message: the message, queue text, reconnect and settle delay don't count
   (the HUD shows `(paused)` while waiting).
8. **Relog without a message.** If the server ever relogs you with no text, expect ACTIVE -> RECONNECTING or ->
   SETTLING (`REJOINED`) and then ACTIVE, not OFF.
9. **Timer end.** Set 1 to 2 minutes. At the end you land on the multiplayer server list, crouch/attack/use are off,
   the mod is OFF, and rejoining leaves it OFF.
10. **Death.** Mod ON, die (or ask staff for a safe `/kill`): OFF at once, and after respawn nothing is pressed.
11. **Manual disconnect.** Mod ON, Esc > Disconnect: OFF, still OFF after rejoining.
12. **Kick/connection drop.** Mod ON, drop the connection (e.g. turn off Wi-Fi briefly): `-> RECONNECTING`; reconnect by
    hand within 120 s and it resumes after the settle delay. After 120 s it turns OFF. (The mod doesn't auto-reconnect.)
13. **HUD.** Readable on the server's world, `●`, `↻` and `∞` render (they use a font fallback and were never seen
    in-game), no overlap with the F3 screen or server UI, the pause menu button appears.
14. **Rules.** Check with staff that the mod's visible HUD and the AFK mining are within their approval, as intended.

## Movement Recovery (2026-10-03)

Stuck detection plus automatic recovery, as specified in SPEC.md "Movement Recovery". Config file only (no GUI
controls yet). Debug lines go to the game log as `[event] ...` and, with Log messages on, to
`config/afkmod-messages.log` as `[EVENT] ...`.

### Verified against the 26.1.2 sources (decompiled with genSources, nothing from memory)
- **Yaw convention:** `Direction` 2D data is SOUTH=0, WEST=1, NORTH=2, EAST=3 and `toYRot()` = data x 90, so south = 0,
  west = 90, north = 180, east = 270 (= -90). `fromYRot` rounds `floor(yaw/90 + 0.5)`, the same tie-break the mod uses
  (exactly 45 rounds up).
- **Per-frame hook:** `Minecraft.runTick` calls `mouseHandler.handleAccumulatedMovement()` once per frame, after the
  client ticks and right before `renderFrame`. That is where vanilla turns the player with the mouse
  (`Entity.turn`). New `mixin/MouseHandlerMixin` (TAIL; the method has no early returns) calls
  `AfkController.onFrame()`. Each frame the turn sets `yRot` **and** `yRotO` to the eased value, so the camera shows
  exactly that yaw at any partial tick. The pitch is never read or written. Like `Entity.turn`, it calls
  `vehicle.onPassengerTurned` when riding (minecart).
- **Server sync:** `LocalPlayer.sendPosition()` (every tick) sends `Rot`/`PosRot` whenever the yaw differs from
  `yRotLast`, with the unchanged pitch. No extra packets are needed.
- **Walking:** `KeyboardInput.tick()` reads `options.keyUp.isDown()` and `options.keyJump.isDown()` (plain
  `KeyMapping`s), so the walk is `setDown(true/false)` on those two.
- **Edge check:** `level.getBlockState(pos)`, `state.getFluidState().is(FluidTags.LAVA)`, `state.is(BlockTags.FIRE /
  CAMPFIRES)`, `state.is(Blocks.CACTUS / MAGMA_BLOCK / SWEET_BERRY_BUSH / WITHER_ROSE / POWDER_SNOW / POINTED_DRIPSTONE)`
  (`is` comes from `TypedInstance`), and `state.getCollisionShape(level, pos).isEmpty()` for "solid". Positions:
  `player.blockPosition().relative(Direction.fromYRot(yaw), n).above(dy)`.

### New and changed files
| File | What it does |
|---|---|
| `logic/YawMath` | Normalize to [0, 360), nearest cardinal (tie rounds up), direction name, shortest delta, target in the player's winding. |
| `logic/YawRotation` | Wall-clock smootherstep turn (`6t^5 - 15t^4 + 10t^3`: zero speed and acceleration at both ends, peak speed in the middle, monotonic, no overshoot). Returns the exact target once the duration has passed; duration 0 = instant snap. |
| `logic/StuckDetector` | Sliding window of (nanoTime, x, z) samples. Stuck = the window spans at least `stuckWindowSeconds` and the max x/z distance from the current position to any sample is below `stuckDistanceBlocks`. `forceStuck()` for a Test Lab. |
| `logic/EdgeCheck` | Pure safety rule for the 2 blocks ahead, through a `Probe` (PASSABLE / SOLID / HAZARD). |
| `logic/RecoveryAttempts` | Attempt/retry counting and the "stuck again after a success" rule. |
| `logic/RecoverySequence` | One attempt, ticked every tick: release keys (wait up to 3 s), turn, edge check, jump + walk. All game access goes through an `Actions` interface; `injectOutcome()` for a Test Lab. |
| `logic/AfkStateMachine` | New state `RECOVERING` and reasons `STUCK_DETECTED`, `RECOVERED`, `RECOVERY_FAILED`, `RECOVERY_CANCELLED`, `RECOVERY_GAVE_UP`. New `startRecovery()`, `endRecovery(reason)`, `giveUpRecovery()`, `isActiveOrRecovering()`. In RECOVERING a restart message / queue text, join, disconnect, death, toggle and timer end behave exactly as in ACTIVE, so restart handling always wins. |
| `client/MovementRecovery` | Connects the pure classes to the game: sampling in the 20-tick check (ACTIVE only), the every-tick sequence while RECOVERING, the frame hook, the real edge check, logging. Test Lab hooks: `forceStuckDetection()`, `injectAttemptOutcome(outcome)`, `setPathCheck(fn)`, `setLogOutAction(runnable)` (e.g. a no-op instead of the disconnect). |
| `client/AfkController` | Owns `MovementRecovery` (`recovery()`), runs it every tick in RECOVERING and in each check, forwards frames and joins, and clears the window on every transition. A failed recovery disconnects to the server list the same way the timer does (marked `MOD_INITIATED`). |
| `mixin/MouseHandlerMixin` | The per-frame hook (registered in `afkmod.mixins.json`). |
| `logic/HudText` | RECOVERING: yellow dot, label `Recovering`. |
| `config/AfkConfig` | `movementCheckEnabled` (true), `stuckWindowSeconds` (20, min 1), `stuckDistanceBlocks` (3, 0.1-64), `recoveryTurnSeconds` (0.5, 0-10), `recoveryWalkBlocks` (2, 0.1-64), `recoveryWalkTimeoutSeconds` (5, 0.5-120), `recoveryRetries` (2, 0-20), `recoveryEdgeCheck` (true). |
| Tests | 65 new: `YawMathTest`, `YawRotationTest`, `StuckDetectorTest`, `EdgeCheckTest`, `RecoveryAttemptsTest`, `RecoverySequenceTest` (incl. a restart cancelling a running attempt), 9 RECOVERING tests in `AfkStateMachineTest`, config and HUD tests. |

### When the window is cleared
On every state transition (toggle on, entering RESTARTING, resuming, the join -> SETTLING relog, death, every
recovery start/end), on every JOIN (also the one inside RESTARTING), while a screen is open and once more when it
closes, when the world isn't ready, and while `movementCheckEnabled` is false.

### Debug log lines
`Stuck detected: 21 samples over 20s, max distance 0.42 < 3.00 blocks, oldest X, Z, now X, Z`, `Recovery attempt 1 of
3 started`, `Recovery: turning yaw 163.40 -> 180.0 (north) over 0.50 s`, `Recovery: jumping and walking from X, Z`,
`Recovery: walked 2.05 blocks` / `walk timed out after 0.30 blocks` / `path ahead unsafe (NO_GROUND), not walking`,
`Recovery attempt 1 result: SUCCESS`, `Recovery cancelled: a screen opened`, `Recovery: a full window shows normal
movement, attempt counter reset`, `Recovery failed, turning the mod off and disconnecting: attempt 3 failed
(WALK_TIMEOUT)`. Transitions are logged as before, e.g. `ACTIVE -> RECOVERING (STUCK_DETECTED)`.

### Decisions I made where the spec was silent (change if you disagree)
1. **After a failed attempt the mod goes back to ACTIVE** (keys on again, window cleared) and the next attempt only
   starts when the next full window is stuck too. It doesn't retry immediately, which gives a lagging server time to
   catch up. So a truly stuck player is disconnected after about 3 windows (about a minute), not within seconds.
2. **Keys that still read active after 3 s count as a failed attempt** (`KEYS_NOT_RELEASED`). While waiting, the
   release is re-checked once a second (`ensureInactive` only presses a key that is still on).
3. **Jump is held for the whole walk** together with forward (repeated jumps help over slabs and out of water), and
   both are released at the end.
4. **The timer keeps running during RECOVERING** (it is a sub-state of ACTIVE), and an active post-resume cooldown
   carries through it.
5. **The attempt counter also resets when the mod turns OFF** (toggle, death, manual disconnect...), besides the
   normal window. A restart doesn't reset it.
6. **The final yaw stays in the player's winding**: from 170 the target is 180, from -170 it is -180, from 530 it is
   540. All are exactly north (F3 shows the wrapped value). This avoids a 360-degree jump in the fields the game
   interpolates.
7. **Edge check details:** "solid" = has a collision shape (slabs, farmland, glass...). Water and plants are
   passable, so the ground under water is what counts. A solid block at feet height ahead (a wall or step) counts as
   ground, not a drop. Hazards: lava, fire, soul fire, campfires, cactus, magma, sweet berry bush, wither rose, powder
   snow, pointed dripstone, at feet or head height, or as the ground. Unloaded chunks read as air, so as unsafe.
8. **No GUI controls** for the new settings yet; edit `config/afkmod.json` (the new fields are written into it on the
   next game start).

## How to test Movement Recovery in a test world
Setup as before (`runClient`, F3+P, Log messages ON, watch `logs/latest.log`). **Tip:** to test faster, set
`"stuckWindowSeconds": 5` in `config/afkmod.json` and restart the game. To watch the turn, set
`"recoveryTurnSeconds": 2` and watch "Facing" in F3.

**Making yourself stuck on purpose:** just turn the mod on and stand still. In a test world, mining in place *is*
"not moving", so a full window without moving 3 blocks triggers the recovery.

| # | Steps | Expected |
|---|---|---|
| 1 | Flat open ground. Look roughly north (F3 yaw about 160) and down about 30 degrees, press K, wait one window. | `Stuck detected ...`, HUD yellow `● Recovering`. Crouch, attack and use switch off, you turn smoothly (speeds up, slows down, no wobble) to exactly 180.0 (north). **Your up/down look doesn't change.** Then you jump and walk 2 blocks, stop, and C L R come back on. Log: `result: SUCCESS`, `RECOVERING -> ACTIVE (RECOVERED)`. You keep facing north. |
| 2 | Keep standing still after test 1. | The next window is stuck again, which counts as a failure: attempt 2 runs, then attempt 3. When the window after attempt 3 is stuck too: `Recovery failed, ... disconnecting`, `-> OFF (RECOVERY_GAVE_UP)`, and you land on the **multiplayer server list**. Rejoining leaves the mod OFF. |
| 3 | Counter reset: after test 1, walk around yourself (more than 3 blocks) for a full window, then stand still. | `a full window shows normal movement, attempt counter reset`. The next recovery is `attempt 1 of 3` again. |
| 4 | Walk blocked: build a 2-block-high glass box around you (1x1), press K. | Each attempt walks into the glass for 5 s: `walk timed out`, `RECOVERY_FAILED`, keys back on. After 3 failed attempts (about 3 windows): OFF and server list. |
| 5 | Edge check: stand on the edge of a 3+ block drop, roughly facing it. | You turn, then `path ahead unsafe (NO_GROUND), not walking`, counted as a failed attempt. Repeat with lava, fire, cactus or magma 1-2 blocks ahead: `HAZARD_ON_PATH` / `HAZARD_BELOW`. A 1-block step down is allowed. With `"recoveryEdgeCheck": false` it walks anyway (careful). |
| 6 | Restart cancels: during the walk in test 4, run `/tellraw @a "Servers restarting"`. | `RECOVERING -> RESTARTING (RESTART_DETECTED)`, `Recovery: cancelled during WALKING`, you stop walking at once, and the turn isn't undone. |
| 7 | Screen cancels: during a recovery, press T (chat). | `Recovery cancelled: a screen opened`, `-> ACTIVE (RECOVERY_CANCELLED)`, W/jump released. After closing chat, C L R come back on and a new window starts. |
| 8 | Cancel during the turn: `"recoveryTurnSeconds": 3`, then press K (toggle off) while turning. | The rotation stops where it is (no snap back or forward), everything released, mod OFF. |
| 9 | Wraparound: start with F3 yaw about -170, then about 350 (= -10). | You turn about 10 degrees the short way (to -180 / north and to 0 / south), never the long way round. |
| 10 | Instant snap: `"recoveryTurnSeconds": 0`. | The yaw jumps straight to the cardinal direction, then the walk starts. |
| 11 | Moving farm: stand in a water stream or ride a minecart on a loop while ON. | No `Stuck detected` while you keep moving more than 3 blocks per window. |
| 12 | Disabled: `"movementCheckEnabled": false`. | Never recovers, even when standing still. |
| 13 | Death / disconnect: `/kill` during a recovery, or Esc > Disconnect during one. | OFF at once, W/jump released, and no disconnect-to-server-list from the recovery. |

## Stats + event log (2026-10-03)

Spec: SPEC.md "Stats + event log" (appended first). No GUI work was done; the API below is ready for the GUI and the
later Test Lab. Persisted to `config/afkmod-stats.json`.

### Block and item counters: what I investigated (nothing faked)
- **Minecraft's stat counters: not usable.** The client `StatsCounter` is only filled by `ClientboundAwardStatsPacket`
  (`ClientPacketListener.handleAwardStats`), which the server sends in answer to `REQUEST_STATS`, and vanilla only sends
  that from the stats screen. So the counters do NOT update live on the client. Sending `REQUEST_STATS` ourselves on a timer
  would be an extra hidden packet to the server, so I didn't.
- **Blocks mined: tracked.** Fabric API has a client-side event, `ClientPlayerBlockBreakEvents.AFTER` (in
  `fabric-events-interaction-v0`, signature `afterBlockBreak(ClientLevel, LocalPlayer, BlockPos, BlockState)`), which fires
  after the client's own `MultiPlayerGameMode.destroyBlock`. It is counted only while the mod is ACTIVE or RECOVERING.
  **Caveat:** it's the client's break, not a server confirmation, so if the server ever rolls a break back and the mod
  breaks the block again, that block counts twice. Fine for a rough "blocks mined" number; treat it as approximate.
- **Items used / eaten: skipped.** There is no client-side "item consumed" event. `UseItemCallback` only fires on right-click
  attempts (the mod holds right click all the time, so that would be a made-up number), and the real stat counters are
  not live (above). I would rather show nothing than a fake number.

### New code (`dev.afkmod.stats`, pure Java, no Minecraft imports)
| File | What it does |
|---|---|
| `AfkStats` | The facade and the API. Current session, lifetime, 20 recent sessions, 100-event ring buffer, test data, periodic save. |
| `StatsSession` | Live accumulator for one session (also the test-data counters). Monotonic nano clock for durations. |
| `SessionRecord`, `LifetimeStats`, `StatsData` | Gson-friendly snapshot/totals/file root (public fields, durations in ms). |
| `StatsStore` | Reads and writes the file. Atomic write (temp file in the same folder, then move). Never throws. |
| `StatsRecorder` | Maps state-machine transitions to session start/end, restart open/close and events. |
| `EndReason`, `EventType`, `StatsEvent` | Enums and the event record. `EndReason.fromTransition(Reason)` maps OFF reasons. |

Client wiring: `AfkController` owns `AfkStats` (`stats()`), feeds transitions, joins, queue-text-gone, the timer (new
`AfkStateMachine.setTimerListener`), the logout, block breaks, auto-save (in the 20-tick check) and
`ClientLifecycleEvents.CLIENT_STOPPING` (`shutdown()`). `MovementRecovery` records stuck detections, attempts, results and
the yaw snap (new default method `RecoverySequence.Actions.turnStarted`). `MessageLog.statsEvent` writes every event as
`[STATS] ...` to `afkmod-messages.log` and the game log when `debugLogging` is on.

### API (for the GUI and the Test Lab), on `AfkController.get().stats()`
`getCurrentSession()` (snapshot or null), `getLifetime()`, `getRecentSessions()` (newest first), `getRecentEvents(n)` (last n,
oldest first), `resetLifetime()` (clears lifetime AND the recent list, saves; the GUI must confirm first),
`injectTestSession(SessionRecord)`, `clearTestData()`, `getTestSessions()`, `getTestCounters()`, `setStoreFile(Path)` (tests
point it at a temp file), `saveNow()`, plus `event(type, text, test)` and the test-flagged updates
(`restartStarted/Ended(test)`, `stuckDetected(test)`, `recoveryAttempt(test)`, `recoveryResult(ok, test)`).
`MovementRecovery.setStatsTest(true)` flags everything the recovery records as test data (Test Lab hook).

### Decisions I made where the spec was silent (change if you disagree)
1. **Restart time** runs from the restart detection (ACTIVE -> RESTARTING) until the mod is back in ACTIVE (end of the settle
   delay). A relog with no restart message (RECONNECTING/SETTLING) is not restart time, so it counts as active time. A restart
   still open when the session ends is closed at the end time.
2. **`endedInLogout`** is derived from the end reason: true for timer end and recovery failed (the two cases where the mod
   itself disconnects). It is not re-checked against whether the disconnect call really ran.
3. **Extra end reason `INTERRUPTED`** for a session still open when the game stops or crashes. The periodic save also stores
   the open session; on the next load it becomes a finished INTERRUPTED session (and only once). `CLIENT_STOPPING` ends an
   open session this way too.
4. **Recovery counters:** attempts count when an attempt starts. Success/failure counts come from the attempt result (walk
   done or not). A cancelled attempt (screen opened, restart) counts as an attempt but as neither success nor failure. If the
   window after a "successful" walk is stuck again and that was the last allowed try, one failure is added at that point
   (the earlier success stays counted).
5. **`resetLifetime()` also clears the recent-session list**, otherwise the list would add up to more than the lifetime
   totals. The running session is untouched.
6. **`clearTestData()` also removes test-flagged events** from the event log. Test data is never saved to the file.
7. **Events are only recorded while the mod is on** for joins and queue-text-gone (so a join with the mod off isn't logged).
8. **Corrupt file:** moved aside as `afkmod-stats.json.corrupt` and stats start fresh. Auto-save interval is 3 minutes.
9. **Test sessions** are capped at 50 in memory.

### Tests (58 new, in `src/test/java/dev/afkmod/stats`)
`AfkStatsTest` (time excludes restarts, average/longest, restart open at end, lifetime rollup, 20-session cap, snapshots are
copies, reset, test-flag exclusion from counters/lifetime/history/file, clearTestData, injectTestSession, ring buffer 100,
sink failures swallowed, save/reload, auto-save interval, crash recovery as INTERRUPTED, shutdown, setStoreFile),
`StatsStoreTest` (missing/empty/corrupt/binary/wrong-type/null-field files, trimming, save after corruption, failed write
never throws, no leftover temp file, JSON field names), `StatsRecorderTest` (transition mapping, restart open/close incl.
SETTLING -> RESTARTING, relog is not restart time, a real `AfkStateMachine` restart scenario) and `EndReasonTest`.
Everything uses temp files; no test touches `config/afkmod-stats.json`.

### How to check it in game (not run yet)
Mod ON for a minute, OFF, then open `config/afkmod-stats.json`: one entry in `recent` with `endReason: MANUAL_TOGGLE`, and
`blocksMined` matching roughly what you mined. With **Log messages** on, `afkmod-messages.log` gets `[STATS] TOGGLED_ON ...`
lines. Kill the game while the mod is ON (after 3+ minutes so the periodic save ran) and start again: that session appears as
`INTERRUPTED`.

## GUI rebuild (2026-10-04)

The single-column settings screen and the standalone Test Lab screen are replaced by one tabbed screen, `client/gui/AfkMenuScreen`
(same `J` keybind and pause menu button; the "Open Test Lab" keybind opens it on the Test Lab tab). Spec: SPEC.md "GUI rebuild".
**Nothing was run in-game** (no display): it compiles and the pure logic is unit tested, so please go through "What to check visually".

### Verified against 26.1.2 (decompiled sources and `javap`, nothing from memory)
- **Vanilla's tab widgets still exist and are used:** `TabNavigationBar` (`builder(tabManager, width).addTabs(...)`, `selectTab`,
  `setTabTooltip`, `updateWidth`), `TabManager` (`setTabArea`, `setCurrentTab`) and `Tab` (as in `CreateWorldScreen`). My tabs implement `Tab`
  through `RowTab`. Like `CreateWorldScreen`, the screen overrides `repositionElements()` so a resize does not rebuild the widgets.
- The tab bar is capped by vanilla at 400 px (372 px of tabs), so 8 tabs get about 46 px each. The labels are therefore short
  (`Dash, Timer, Detect, Recover, Stats, Display, Debug, Lab`); each tab has a tooltip with its full name and its first row is a heading.
- **Tooltips:** vanilla's `Tooltip.create` wraps at 170 px and its default positioner does not clamp the top and left edges, so tooltips are
  drawn with `GuiGraphicsExtractor.setTooltipForNextFrame(font, lines, Optional.empty(), positioner, x, y, replaceExisting=true, null)`:
  my own wrapping at 40 characters and a `ClientTooltipPositioner` (`ClampingTooltipPositioner`, using the pure `TooltipPlacement`) that
  clamps on all four edges. They are re-requested every frame from the pointer position, so there is no delay and no flicker.
- Widgets: `AbstractWidget` (the info icon subclasses it: `extractWidgetRenderState`, `updateWidgetNarration`), `Checkbox`, `CycleButton`,
  `EditBox`, `AbstractSliderButton`, `ConfirmScreen`, `InputConstants.KEY_PAGEUP/PAGEDOWN`, `ScreenRectangle`.

### New code
| File | What it does |
|---|---|
| `config/SettingInfo` | **The registry**: config field name -> display name, description, unit, min/max (plus the Test Lab options). Defaults are read by reflection from `new AfkConfig()` / `TestOptions.defaults()`. Also generates the README table. |
| `gui/TextWrap`, `TooltipText`, `TooltipPlacement`, `FieldValidator`, `RowScroll`, `PanelLayout` | Pure Java, tested (`GuiHelpersTest`): 40-char wrapping, tooltip content, on-screen placement, validation that never throws, whole-row scrolling, panel width (60%, 300 to 420). |
| `testlab/ScenarioInfo` | What a PASS means for every scenario, plus the warning and tag for scenarios that move the player or can disconnect. |
| `client/gui/AfkMenuScreen` | The screen: tab bar, panel, Save/Done/Cancel, tooltips, scrolling. |
| `client/gui/RowTab`, `Row`, `Rows`, `SettingRows`, `InfoIcon` | The row framework: label + info icon + control + inline error. |
| `client/gui/DashboardTab`, `TimerTab`, `DetectionTab`, `RecoveryTab`, `StatsTab`, `DisplayTab`, `DebugTab`, `TestLabTab` | The eight tabs. |
| `client/gui/TestLabActions` | The Test Lab logic from the old `TestLabScreen` (options, run, confirm dialogs, self-test, abort, report), shared by the tab. |
| Small additions | `AfkStateMachine.restartElapsedSeconds()/isInRestart()`, `MovementRecovery.lastYawTurn()`, `HudCorner.displayName()`, `MessageSource.displayName()`. `AfkConfig.sanitize()` now also clamps to the registry's upper bounds. |
| Removed | `AfkSettingsScreen`, `TestLabScreen` (the rest of the Test Lab is untouched). |
| Tests | `SettingInfoTest` (reflection over `AfkConfig`: every field has an entry and a description, no stale entries, displayed defaults equal real defaults, sanitize matches the ranges), `ReadmeSettingsTableTest`, `GuiHelpersTest`, `ScenarioInfoTest`. 251 tests in total. |

### Decisions I made where the request was silent (change if you disagree)
1. **Tab labels are short** (see above), because vanilla's bar cannot be wider than 400 px.
2. **Restart and queue keywords must be non-empty; ignore keywords may be empty** (an empty ignore list is a valid setting). Invalid input is
   shown in red and is never applied.
3. **`checkIntervalTicks`, `settleDelaySeconds`, `reconnectGraceSeconds` have no GUI control** (they are not in your tab lists). They are in the
   registry, so they have tooltip texts and appear in the README table. Say if you want them on the Detection tab.
4. **Unused fields** `restartEndKeywords` and `restartClearTimeoutSeconds` have registry entries (the test requires every field) marked unused;
   they are left out of the README table and the GUI.
5. **Scrolling is by whole rows** (a row is only shown if it fits). Keyboard Tab only reaches rows that are shown; Page Up/Down scroll.
   Scrolling over a cycle button scrolls the list instead of changing the value.
6. **Cancel reverts to the last saved state**, not only to how the screen opened (Save moves the baseline). Leaving the screen any other way
   (including to run a scenario) saves the live settings, so edits are never silently lost.
7. **The screen no longer pauses single-player** (`isPauseScreen` is false, like the old Test Lab screen), so Test Lab scenarios behave like on a
   server. The old settings screen did pause. The mod still does nothing while any screen is open.
8. **Test Lab options are validated like settings**: running with an invalid option field is refused with a message instead of silently using a default.
9. **The message tester starts empty** (hint: "e.g. Servers are updating") so the tooltip's default ("empty") is true.
10. **The Test Lab keybind opens the same screen on the Test Lab tab**; there is no separate Test Lab screen any more.

### What to check visually
1. **Open it:** `J`, and Esc then **AFK Mod**. Eight tabs across the top; hover one for its full name. A dark panel under the tab strip, Save / Done / Cancel under the panel.
2. **Panel size:** at GUI scale 2 on a 1080p window it should be 420 px wide (not wider); on a small window (for example 854x480 at GUI scale 2 to 4) it should still be readable and centred, with the footer buttons visible. Resize the window with the screen open: the typed text must stay.
3. **Scrolling:** on a small window rows move off the bottom; mouse wheel and Page Up/Down scroll by whole rows, a thin scrollbar appears at the right, nothing is drawn half-cut.
4. **Info icons:** every setting row (all tabs, including the Test Lab options and every scenario) has a blue circled "i" right after its label, and it looks like an "i" (not a blob). Hover the icon and the label: the same tooltip. It wraps at about 40 characters, ends with a `Default: ... - Range: ...` line, and does not flicker. Move the pointer to the bottom-right and top-left rows: the tooltip must stay fully on screen. Press Tab to focus an icon: it gets a white outline and shows its tooltip.
5. **Dashboard:** the dot colour (green mining, red restarting, yellow recovering, grey off), the timer bar, `current restart ... elapsed` during a restart, the C/L/R dots, the movement line, the last 8 events with times, and the `TEST RUNNING` banner with Abort during a Test Lab run. Check the `●` character renders.
6. **Validation:** type `abc`, `-1` or a too-big number in a number field: red text and a red message under it, and the value is not applied (check with Save and `config/afkmod.json`). Empty the restart or queue keywords: red message. Timer: `x` or all zeros then Set timer: red message.
7. **Save / Done / Cancel:** change the HUD corner, Cancel (it reverts); change it, Save (button says "Saved", stay open), change again, Cancel (back to the saved one); Done keeps it.
8. **Stats tab:** the lists fill in after a session; hover a session for details; Reset lifetime stats asks first.
9. **Test Lab tab:** with the mod OFF, scenarios that need it ON have a greyed Run button whose tooltip says why. D3, D4, D6 show an orange `(!)` tag and ask for confirmation; D5 with Real disconnect and E1 Full ask (D5 twice). The results list fills in; the Test Lab keybind (bind it first) opens straight on this tab.
10. **Pause menu button** still sits under Disconnect and Cancel/Done returns to the pause menu.

### Test Lab (found while rebuilding the GUI)
The Test Lab core (package `dev.afkmod.testlab`, `client/GameTestEnvironment`, scenarios A to I) was written in an earlier session that ran out
of time before PROGRESS.md got a section for it. The code and its tests are in the build and pass; I did not re-review it beyond reusing its API
for the GUI. Run the self-test from the Test Lab tab as a first in-game check.

## What's left
- The Stats and Test Lab GUI now exist (see "GUI rebuild"); they have not been seen in-game yet.
- Run the "How to test" guides in a test world and the "Real server checklist" above. Movement Recovery also needs the
  test table above (especially the turn smoothness, the pitch staying unchanged, and the edge check).
- On the real server, check that a recovery walk can't take you somewhere harmful in your farm layout (shallow
  edges, e.g. a 1-block drop into a water stream, are allowed). Nothing has been run in-game
  yet, only compiled and unit tested. The restart-handling update in particular (the `GuiAccessor` read of the action
  bar, and the reconnect being classed `UNEXPECTED`) needs checklist item 6 on the real server.
- Optional cleanup: `logic/ServerTimeWatcher` and `ServerTimeWatcherTest` are unused now.

## Final review and polish (2026-10-04)

Read the whole code base against SPEC.md. Nothing was run in-game (no display), so everything below is "read and unit tested".

### Fixed
1. **Timer changed during a recovery stayed paused.** `AfkStateMachine.setTimerSeconds` restarted the countdown paused for every
   state except plain ACTIVE, so setting a timer while RECOVERING (a sub-state of ACTIVE) froze it until the recovery ended.
   Now it is paused only outside ACTIVE/RECOVERING. Test: `changingTimerDuringRecoveryKeepsItRunning`.
2. **Detection tab was missing two settings.** `settleDelaySeconds` and `reconnectGraceSeconds` had registry entries (tooltips, README
   table) but no GUI row. They are on the Detection tab now, with their info icons. Only `checkIntervalTicks` stays file-only (and the
   two unused fields); the README says so.
3. **Loader requirement** in `fabric.mod.json` was `>=0.19.5`; the spec says 0.19.3+, so it is `>=0.19.3` (built and tested against
   0.19.5 only).
4. **README** extended: install (Java 25, Loader 0.19.3+, Fabric API, jar path), the restart timeline on the real server, movement
   recovery in detail (smooth 0.5 s ease-in-out turn, tie-break, shortest arc, pitch never touched), Test Lab and self-test guide, how to
   read the logs, the debug report, and a "Settings reference" (table generated from `SettingInfo`, test-checked).

### Checked and found correct (read line by line)
- **Stuck keys:** every transition to OFF releases attack/use/crouch, also with a screen open (screen-restore memory cleared); a recovery
  left early (restart, death, toggle, screen, disconnect, null player) releases W and jump through `RecoverySequence.cancel`; the Test Lab
  cleanup releases forward/jump and re-verifies the keys.
- **Screen-open rule:** `runCheck` returns before touching any key when `mc.screen != null`; the stuck window is cleared while a screen is
  open and once more when it closes; RECOVERING cancels on a screen.
- **Death:** packet hook plus the `isDeadOrDying()` fallback; OFF at once; a running recovery is cancelled by the transition.
- **Manual vs server disconnect:** `ClientLevelMixin` marks only client-side quits; the mod's own disconnect wins; kicks, transfers and
  relogs are `UNEXPECTED`; RESTARTING waits for the rejoin, ACTIVE waits `reconnectGraceSeconds`.
- **Restart priority:** `onMessage`, `onJoin`, `onDisconnect` treat RECOVERING exactly like ACTIVE; the stuck check runs in ACTIVE only.
- **AFK timer:** paused in RESTARTING, SETTLING and RECONNECTING, resumed in ACTIVE/RECOVERING, wall clock.
- **Post-resume cooldown:** starts on SETTLING -> ACTIVE, survives RECOVERING, cleared on toggle.
- **Test overrides:** only `AfkModClient.savedConfig()` is ever saved; the override layer works on a copy; `maxRestartWaitSecondsOverride` is
  transient. `AfkMenuScreen` and `TimerTab` only save the saved config.
- **Test data:** in test mode `StatsRecorder` never starts or ends the real session, every update and event is flagged, `blockMined` is
  skipped, and test data is never written to the stats file.
- **20-tick rule:** the only per-tick work is key consumption, `recovery.onTick` (RECOVERING only), the idle-cheap Test Lab hooks and the
  per-frame yaw application. Timeouts, settle delay, cooldown, timer end, key verification and sampling are all in the 20-tick check.
- **Null safety:** `runCheck` computes `worldReady` (player, level, connection) before touching the player; the recovery hooks and
  `GameActions` all null-check the player; `KeyControl` needs only `mc.options`; the disconnect helper bails out without a connection.
- **Turn smoothness:** `YawRotation` is smootherstep on the wall clock with an exact end value and duration `recoveryTurnSeconds`; `setYaw`
  writes only `yRot`/`yRotO` (no `xRot` anywhere in the main sources). The only other instant yaw write is `setYawForTest`, which the Test
  Lab uses to set a *start* yaw for D1/D6 (not part of any recovery path).
- **Registry and tooltips:** every `AfkConfig` field has a `SettingInfo` entry (test-enforced); every GUI setting is a `SettingRow` with an
  info icon; the `i` tooltips are placed by `TooltipPlacement` (clamped on all four edges; an oversized one sits at the top-left margin).

### Not verifiable without launching Minecraft
- The whole "What to check visually" list of the GUI rebuild (layout, the `i` icon look, tooltip placement at small window sizes, glyphs `●` `↻` `∞`).
- Whether vanilla's own button tooltips (Debug, Stats and footer buttons) stay on screen at tiny sizes (only the `i` tooltips use the clamping positioner).
- Toggle vs hold behaviour of the three keys in the real client, and that closing a screen never re-presses a released key.
- That the mixins apply at runtime (`GuiAccessor`, `MouseHandlerMixin`, `ClientPacketListenerMixin`, `ClientLevelMixin`): they compile, but Mixin is only applied at game start.
- The smooth turn looking smooth, the pitch staying unchanged, the edge check against real blocks, the recovery walk.
- That the real server's reconnect arrives as `UNEXPECTED` and the queue text is read correctly.
- The Test Lab self-test result in a real world (its scenario scripts are unit tested against a simulated environment built on the real state machine).
- Fabric Loader 0.19.3 / 0.19.4 (only 0.19.5 was used).

### Checklist: single-player (creative world, F3+P so it doesn't pause, Log messages ON)
1. Controls shows the AFK Mod category: Toggle AFK `K`, Open AFK menu `J`, Open Test Lab and Abort test (unbound).
2. `J` and Esc > AFK Mod open the menu with 8 tabs; every setting row has a blue `i`; hover the icon and the label: the tooltip wraps at about 40
   characters, ends with `Default: ...` and stays fully on screen at every edge and at a small window (e.g. 854x480, GUI scale 3-4); the footer
   buttons stay visible; scrolling by whole rows works.
3. Number fields: `abc`, `-1`, too big -> red message and not applied; empty restart/queue words -> red. Save / Done / Cancel behave (Cancel
   reverts to the last save). The Detection tab shows settle delay and reconnect grace.
4. `K` in toggle mode, then hold mode: C L R stay on for a minute without flicker; tap Shift -> back on within about 1 s; open chat/inventory ->
   nothing pressed, back about 1 s after closing; `K` again -> everything released, nothing stuck.
5. Restart simulation: `/tellraw @a "Servers restarting"`, then a repeating `/title @a actionbar "restart queue #3"`; stop it; about 30 s later it
   resumes (no reconnect); `↻1`; a second message within 15 s is ignored; `whispered servers` is ignored.
6. Timer 1m: it pauses during the simulated restart; at 0 you land on the multiplayer list, everything released, the mod OFF.
7. Death (`/kill` in survival) and Esc > Disconnect: OFF at once, nothing pressed afterwards.
8. Recovery (set the stuck window to 5 s to go faster): stand still with the mod ON -> yellow Recovering, smooth turn of about 0.5 s to an exact
   cardinal yaw (F3), pitch unchanged, jump + walk 2 blocks, C L R back. Standing still again fails 3 times -> server list. Also try an edge, lava,
   a glass box, opening chat during the turn, and `recoveryTurnSeconds` 0 (snaps).
9. Test Lab: with the mod ON run the self-test and open `config/afkmod-selftest.txt` (all PASS); Abort part-way leaves the mod ACTIVE with no key
   stuck; `afkmod.json` and `afkmod-stats.json` contain no test data.
10. Stats tab fills in after a session; Copy debug report -> clipboard + `config/afkmod-report.txt` with `<server>` / `<player>`.

### Checklist: first supervised session on the real server (staff watching)
1. Mod OFF, Log messages ON: the restart message is tagged `(RESTART)` in `afkmod-messages.log`, the `restart queue #N` line is
   `[ACTION_BAR overlay]`, whispers are `(IGNORED)`, nothing else is `(RESTART)`.
2. Mod ON in a safe spot for a few minutes: C L R steady, HUD readable and not over the server's UI, `●` `↻` `∞` render.
3. A real restart: RESTARTING on "servers", L/R released, no queue number in the HUD, `Disconnected (UNEXPECTED)`, `Joined world while
   RESTARTING`, `-> SETTLING (REJOINED)`, ACTIVE about 3 s later, `↻` +1, timer paused during it, a leftover message in the next 15 s ignored.
   If the disconnect shows `CLIENT_INITIATED`, stop and send the debug report.
4. A short timer (1-2 min) once: lands on the server list, OFF, still OFF after rejoining.
5. Pause-menu Disconnect: OFF and stays OFF. Briefly drop the Wi-Fi: RECONNECTING, resumes after a rejoin within 120 s.
6. Watch the first recovery that fires (edge check against your farm layout) and keep `recoveryEdgeCheck` on.
7. Copy the debug report if anything looked off.

---

## Session 2026-10-05: key order and Hold -> Toggle (0.2.0)

Renamed to **Fresh AFK** (display name only; mod id, package and config files keep `afkmod`), MIT license, published at
https://github.com/mYaeesh/Fresh-AFK with a GitHub Actions build.

### Right click last, only with shift held
- New `logic/ActivationOrder` (pure Java, `ActivationOrderTest`): crouch and left click are pressed first; right click
  last and only when crouch is on **and confirmed** (`LocalPlayer.isShiftKeyDown()`, or the crouch key down for 2+
  ticks so it can't stall, e.g. in a vehicle). If right click is on while crouch is fully off, right click is released,
  crouch pressed, and right click pressed again once crouch is confirmed.
- While right click waits, `runCheck` requests the next check on the next tick (same trick as after a resume), so the
  delay is about one tick and each check still sends at most one press per key (Test Lab C1 stays valid).
- Releases already go right click before crouch (`onTransition`, `MovementRecovery.releaseKeys`).

### Hold -> Toggle while the mod is on
- New `logic/HoldModeOverride` (pure Java, `HoldModeOverrideTest`). Leaving OFF switches every control on "Hold"
  (`options.toggleCrouch/toggleAttack/toggleUse`) to "Toggle"; entering OFF releases the keys first (still as toggle
  keys) and then puts exactly those back to "Hold" and calls `options.save()`. Controls already on Toggle are untouched.
  `ToggleKeyMapping` reads the option live (`needsToggle` supplier), so the switch takes effect at once.
- The switched controls are written to `config/afkmod-holdmodes.txt` (one name per line) and deleted on restore.
  `CLIENT_STARTED` restores leftovers after a crash; `CLIENT_STOPPING` restores on a normal quit.
- Verified with `javap` on the 26.1.2 jar: `OptionInstance.set`, `Options.save`, `Options.toggleCrouch/Attack/Use`,
  `LocalPlayer.isShiftKeyDown`.

### In-game checklist
1. Controls: set Sneak, Attack and Use to **Hold**. Press K: all three show **Toggle** in Controls while ON; HUD C L R
   green, with R turning green just after C.
2. Press K again: all three are back to **Hold**, nothing stuck; `options.txt` says `toggleCrouch:false` etc.
3. Mixed (Sneak Toggle, Use Hold): only Use is switched and restored.
4. Mod ON, then kill the game from the task manager: `config/afkmod-holdmodes.txt` exists; start the game: back to Hold,
   file gone.
5. With the mod ON, open chat, press shift once in-world after closing (crouch off): right click turns off, crouch back
   on, then right click on again.
