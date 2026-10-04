# AFK Mod: Plain-Language Guide

This guide explains what the AFK Mod does and how to use it, without technical language.
(The `README.md` file has the same information written for programmers.)

---

## 1. What is this mod?

AFK means "away from keyboard". On a server where staff allow AFK mining, you normally have to hold down
the crouch key, the left mouse button (mine) and the right mouse button (use) all the time.

**This mod holds those three things for you**, so you can step away while your character keeps mining.

It also looks after you while you are away:

- **Server restarts:** when the server restarts, the mod pauses, waits for the server to come back, then starts
  mining again by itself.
- **Getting stuck:** if your character stops moving (usually because of lag), the mod tries to get you moving again.
- **Timer:** you can tell it to stop after a set time (for example 2 hours). When the time is up it stops and
  takes you back to the server list.
- **Always visible:** a small status line is on screen the whole time it is running, so you always know what it is
  doing.

It only runs on your own computer. Nothing is installed on the server.

---

## 2. The two keys you need

| Key | What it does |
|---|---|
| **K** | Turns the mod **on** or **off**. |
| **J** | Opens the mod's settings menu. |

You can also open the settings menu by pressing **Esc** and clicking the **AFK Mod** button (just under
"Disconnect").

To change these keys: **Options → Controls → Key Binds**, then look for the **AFK Mod** section.
There are two more keys there that have no key set by default: **Open Test Lab** and **Abort test**
(see section 9).

There are no chat commands. Everything is done with keys and the menu.

---

## 3. Everyday use (step by step)

1. Join the server and walk to your mining spot.
2. (Optional) Press **J**, go to the **Timer** tab and set how long you want to AFK. See section 5.
3. Press **K**. The status line appears in the corner of the screen and your character starts crouching and
   clicking.
4. Leave the game window alone. **Do not open any menu, chat or your inventory**, and do not click into another
   program (see the tip below).
5. When you come back, press **K** again to stop.

**Tip about switching to another window:** Minecraft opens the pause menu when you click into another program,
and the mod deliberately does nothing while any menu is open. To stop that happening, press **F3 + P** in game.
This turns off "pause when the window loses focus".

### The mod turns itself off when:

- you die,
- you leave the server yourself (Esc → Disconnect, or quitting the game),
- the timer runs out,
- a restart takes far too long (15 minutes by default),
- you got disconnected without a restart warning and did not come back within 2 minutes,
- it tried 3 times to get you un-stuck and failed every time.

Whenever it turns off, it lets go of every key it was holding, so nothing is left stuck down.

---

## 4. Reading the status line (the "HUD")

While the mod is on, a small line appears in the top-left corner:

```
● AFK Mining | 1:23:45 | C L R | ↻2
```

| Part | Meaning |
|---|---|
| **● (the dot)** | **Green** = mining normally. **Red** = the server is restarting. **Yellow** = trying to get un-stuck. **Grey** = paused or waiting. |
| **1:23:45** | Time left on the timer. **∞** means there is no timer. "(paused)" means the countdown is on hold (during a restart). |
| **C L R** | **C**rouch, **L**eft click, **R**ight click. Green letter = being held. Red letter = not being held. |
| **↻2** | How many server restarts have happened since you turned it on. |

During a restart the line turns red and says **● Server restarting**, and a small red banner appears at the top
middle of the screen.

The line disappears when the mod is off, when you press **F1** (hide everything), and moves to the bottom corner
when the F3 debug screen is open.

You can change its corner, size, or switch to a bigger 3-line version on the **Display** tab (section 8).

---

## 5. The timer

- Set it on the **Timer** tab: type hours, minutes and seconds, then click **Set timer**. Click **No timer** to run
  with no time limit.
- The countdown starts when you press **K** to turn the mod on.
- **Time spent in a server restart does not count.** The timer pauses and carries on afterwards.
- When it reaches zero, the mod lets go of everything, turns itself off, and takes you back to the
  **multiplayer server list**.
- It will not start again until you press **K** yourself.
- If you change the timer while the mod is running, the countdown starts again from the new time.

---

## 6. What happens during a server restart

You do not have to do anything. This is what the mod does:

1. **The server sends a message** that contains the word "servers". The mod spots it, **stops clicking**
   (crouch stays on unless you change that setting), pauses the timer and turns the status line red.
2. **The server shows "restart queue #…"** in the middle of the screen above your hotbar. The mod watches this
   text. It counts as the same restart, so it is never counted twice.
3. **The text disappears.** The mod waits until it has been gone for 3 seconds, so a quick flicker does not fool it.
4. **The server moves you back in** (you see a "connecting" screen, then you are back in the world). This is
   normal and the mod stays on.
5. **After 3 more seconds** to let things settle, it starts crouching and clicking again and the timer carries on.
6. **For the next 15 seconds**, any more restart messages are ignored, so a leftover message cannot start a second
   restart straight away.

If the text disappears but the server never moves you, the mod carries on by itself after 30 seconds.
If the restart is still going after 15 minutes, the mod gives up, lets go of everything and turns off.

**Which messages count?** Only messages from the server itself (system messages, the text above the hotbar,
big titles on screen, boss bars). **Normal player chat is never used**, so a player typing "servers" cannot
trigger it. Any message containing the word **"whispered"** (private messages) is always ignored, even if it also
says "servers".

**Other disconnects:** if you get disconnected while mining *without* a restart message, the mod waits up to
2 minutes for you to come back, then turns off. It never reconnects you by itself.

---

## 7. Getting un-stuck (movement recovery)

In a mining farm you are always moving. If you stay in roughly the same spot for a while, something is wrong
(usually lag), so the mod steps in.

**When does it decide you are stuck?** If, over 20 seconds, you have not moved at least 3 blocks sideways
(going up or down does not count).

**What it does** (the status dot turns **yellow**, "Recovering"):

1. Lets go of crouch, left click and right click.
2. Smoothly turns you to face exactly north, south, east or west (whichever is closest). It only turns you
   left or right. It never changes whether you are looking up or down. It does not turn you back afterwards.
3. Checks the 2 blocks in front of you. If there is a drop, lava, fire, cactus, magma or similar danger,
   **it will not walk** (you are not crouching at this point, so you could fall).
4. Jumps and walks forward about 2 blocks (it gives up after 5 seconds).
5. If that worked, it starts crouching and clicking again.

If it fails, it tries again. After **3 failed tries in a row** it turns off and takes you back to the server list,
to keep you safe. A server restart always takes priority over this.

You can turn this feature off on the **Recovery** tab (**Stuck detection**).

---

## 8. The settings menu, tab by tab

Press **J** to open it. Hover over (or point at) the small circled **i** next to any setting to see what it does,
its default value and the allowed range.

At the bottom: **Save** saves and keeps the menu open. **Done** saves and closes. **Cancel** (or Esc) throws
away changes since you last saved. (Timer changes happen straight away, so Cancel does not undo them.)
If you type something not allowed, the box turns **red** with a note, and the bad value is not used.

| Tab | What is on it |
|---|---|
| **Dashboard** | A live overview: the big **ON/OFF** button, current status, time left, restart count, which keys are held, how far you have moved recently, and the last few things that happened. |
| **Timer** | Set the timer, or choose no timer. |
| **Detection** | The words the mod looks for during restarts, and how long it waits at each step. |
| **Recovery** | The "get un-stuck" settings. |
| **Stats** | Your totals (see section 10). |
| **Display** | Show/hide the status line, detailed (3-line) version, which corner, and size. |
| **Debug** | Message logging and the debug report (see section 11). |
| **Test Lab** | Built-in tests (see section 9). |

### Settings explained

Your current settings are all the defaults.

**Detection tab (restarts)**

| Setting | Default | What it means |
|---|---|---|
| Restart keywords | servers, restart queue | Words that mean "the server is restarting". Not case-sensitive. Separate several with commas. |
| Ignore keywords | whispered | Messages with these words are always ignored. |
| Queue keywords | restart queue | The text the server shows above the hotbar during a restart. |
| Queue gone time | 3 s | How long that text must stay gone before the restart counts as over. |
| Settle delay | 3 s | Extra wait after you are back in the world, before mining starts again. |
| Post-resume cooldown | 15 s | After resuming, restart messages are ignored for this long. |
| No-reconnect fallback | 30 s | If the server never moves you after the text goes away, carry on after this long. |
| Reconnect grace | 120 s | After a disconnect with no restart warning, how long to wait for you to come back. |
| Max restart wait | 15 min | The longest a restart is allowed to take before the mod gives up and turns off. |
| Release crouch on restart | off | Also stop crouching during restarts. |

**Recovery tab (getting un-stuck)**

| Setting | Default | What it means |
|---|---|---|
| Stuck detection | on | Turns the whole feature on or off. |
| Stuck window | 20 s | How long to watch before deciding you are stuck. |
| Stuck distance | 3 blocks | Moving less than this in the window counts as stuck. Raise it if it goes off when it should not. |
| Walk distance | 2 blocks | How far it walks to get you moving. |
| Walk timeout | 5 s | Gives up on one walk after this long. |
| Retries | 2 | Extra tries after the first one fails (so 3 tries in total). |
| Edge safety check | on | Refuses to walk towards drops, lava and so on. Only turn off if your path is definitely safe. |
| Turn duration | 0.5 s | How long the smooth turn takes. 0 turns instantly. |

**Display tab**

| Setting | Default | What it means |
|---|---|---|
| Show HUD | on | Show the status line. |
| Detailed HUD | off | Show 3 short lines instead of 1. |
| HUD corner | Top left | Which corner. (Bottom left can overlap chat.) |
| HUD scale | 0.75 | Size of the status line. |

**Debug tab**

| Setting | Default | What it means |
|---|---|---|
| Log all incoming messages | off | Records every message the server sends, so you can see the exact wording. |
| Redact identity in report | on | Hides the server address and your username in the debug report. |

There is one more setting only in the settings file, not in the menu: **Check interval** (how often the mod
checks things, default once per second). It does not need changing.

---

## 9. Test Lab and the self-test

The **Test Lab** tab lets you check that every feature works **without waiting for a real restart**. It fakes
the event (for example a pretend restart message) and lets the real mod react to it.

Safety rules it follows:

- It **never changes your saved settings**. Tests use temporary shortened timings that are thrown away
  afterwards.
- It **never adds to your real stats**. Anything a test does is marked `[TEST]` and kept separate.
- Nothing is sent to the server, and you are not really disconnected unless a test says so and you click to
  confirm.
- Tests that move your character have an orange **(!)** and ask first.
- When a test ends, the mod is put back exactly how it was.

To stop a running test: click **Abort** (on the Test Lab tab or the Dashboard banner), or use the *Abort test*
key if you have set one. Turning the mod off or dying also stops it.

### Running the full self-test

1. Stand somewhere safe (a creative single-player world is ideal).
2. Press **K** to turn the mod on (many tests need it on; they are skipped otherwise).
3. Press **J**, go to **Test Lab**, click **Run self-test**.
4. Wait. Each test takes real time, so the whole run takes about 3–4 minutes. A summary appears in chat.
5. The full results are saved to `config/afkmod-selftest.txt` in your Minecraft profile folder.

### Reading the results

Each test shows one of:

- **PASS**: it worked as expected.
- **FAIL**: something did not behave as expected. The line says what.
- **INFO**: not a pass/fail test, just a report for you to look at. These are normal:
  **A1** (explains how a sample message is handled), **D2** (describes the blocks in front of you) and
  **G1** (shows each look of the status line for a few seconds).

The test letters mean: **A** messages, **B** restarts, **C** key holding, **D** turning and movement,
**E** timer, **F** stats saving, **G** status line preview.

The tests that actually move your character (D3–D6) are **not** part of the self-test. Run them yourself from
the Test Lab if you want to.

**Your last self-test (4 Oct 2026):** 20 PASS, 3 INFO, 1 FAIL. The one failure, **B7 "Message during the
cooldown"**, is a timing problem in the test itself, not in the mod: the test sets two waits so short that the mod
moves on before the test gets to look. This has been fixed (one of those waits is now 2 seconds); install the new
build and run the self-test again to confirm B7 now passes.

---

## 10. Stats

The **Stats** tab shows:

- **Current session** (since you last pressed K): total time, time actually mining, time lost to restarts,
  number of restarts (with the longest and average), and how often you got stuck and were recovered.
- **Lifetime**: the same totals across every session, plus **blocks mined** (approximate, because it is counted
  on your side, not the server's) and what ended your sessions (timer, disconnect, and so on).
- **The last 20 sessions**, newest first. Point at one for details.
- **Reset lifetime stats** clears the totals and the session list. It asks first and cannot be undone.

Stats are saved in `config/afkmod-stats.json`.

---

## 11. Checking the mod recognises your server's restart message

Do this once on the real server before relying on the mod, to make sure the restart message really contains
the word the mod looks for.

1. Press **J** → **Debug** tab → turn on **Log all incoming messages** → **Done**. (The mod itself can stay off.)
2. Stay on the server until a restart is announced.
3. On the Debug tab, click **Open log folder** and open `afkmod-messages.log`.
4. Each line shows the time, where the message came from, what the mod decided, and the message. For example:

   ```
   2026-10-03 14:02:11.204 [SYSTEM] (RESTART) Servers restarting in 10 seconds
   2026-10-03 14:02:12.950 [ACTION_BAR overlay] (RESTART) restart queue #3
   2026-10-03 14:02:15.010 [SYSTEM] (IGNORED) Bob whispered to you: servers are fun
   2026-10-03 14:02:20.330 [TITLE] (NONE) Welcome back
   ```

   - **(RESTART)** = the mod would treat this as a restart.
   - **(IGNORED)** = it contained an ignore word.
   - **(NONE)** = nothing matched.
5. **The restart announcement should say (RESTART).** If it says (NONE), copy a word from that message into
   **Restart keywords** on the Detection tab. If an unrelated message says (RESTART), use a more specific word
   or add an ignore word.
6. Turn logging **off** again afterwards.

### Sending a debug report

If something goes wrong and you want help, go to the **Debug** tab and click **Copy debug report**. It copies
a summary to your clipboard and saves it as `config/afkmod-report.txt`. Your server address and username are
hidden unless you turned that off. Read it before you send it to anyone.

---

## 12. Troubleshooting

| Problem | What to try |
|---|---|
| The mod is on but nothing happens. | Close every menu, chat and inventory. The mod does nothing while one is open. |
| It stops when I click into another program. | Press **F3 + P** in game so Minecraft does not pause when the window loses focus. |
| It did not react to a restart. | Follow section 11 to check the restart message contains one of your restart keywords. |
| It thinks there is a restart when there is not. | Check the log (section 11) to find the message that set it off, then use a more specific restart keyword or add an ignore word. |
| It starts mining again too early after a restart. | Raise **Queue gone time** or **Settle delay** on the Detection tab. |
| It keeps saying I am stuck when I am fine. | Raise **Stuck distance** or **Stuck window** on the Recovery tab, or turn **Stuck detection** off. |
| It refused to walk while un-sticking me. | The edge check saw danger ahead. That is on purpose. Only turn **Edge safety check** off if your path is definitely safe. |
| It sent me back to the server list. | Either the timer ran out, or it failed to un-stick you 3 times. Check the Dashboard's recent events or the Stats tab to see which. |
| The status line covers the chat. | Move it to a top corner on the Display tab. |
| I cannot see the status line. | Check **Show HUD** is on, and press **F1** in case everything is hidden. |
| A key seems stuck down after the mod turned off. | Tap that key once. The mod releases everything when it stops, so tell the developer if this keeps happening. |

---

## 13. Where the mod keeps its files

All in the `config` folder of your Minecraft profile
(for you: `ModrinthApp\profiles\Usual One\config`):

| File | What it is |
|---|---|
| `afkmod.json` | Your settings. |
| `afkmod-stats.json` | Your stats. |
| `afkmod-selftest.txt` | Results of the last self-test. |
| `afkmod-messages.log` | The message log (only when logging is on). |
| `afkmod-report.txt` | The last debug report. |
