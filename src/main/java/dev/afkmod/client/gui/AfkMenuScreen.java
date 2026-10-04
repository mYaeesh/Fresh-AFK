package dev.afkmod.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.afkmod.AfkModClient;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.gui.PanelLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The AFK Mod settings screen: a centred panel (about 60% of the width, at most 420 px) under vanilla's tab bar
 * ({@link TabNavigationBar} and {@link TabManager}, the same widgets as the Create World screen), with Save, Done and
 * Cancel at the bottom. Every control writes straight into the live config, so changes apply immediately. Save writes
 * {@code config/afkmod.json}; Done saves and closes; Cancel (or Esc) puts back what was saved when the screen opened or
 * was last saved, except for the timer (an action on the running countdown). Leaving the screen any other way (for
 * example to run a Test Lab scenario) saves the live settings, so nothing is lost.
 * <p>
 * Info tooltips are drawn here with one clamped positioner; see {@link InfoIcon} and {@link SettingRows}.
 */
public final class AfkMenuScreen extends Screen {
	public static final int TAB_DASHBOARD = 0;
	public static final int TAB_TEST_LAB = 7;

	private static final int FOOTER_HEIGHT = 30;
	private static int selectedTab = TAB_DASHBOARD;

	private final @Nullable Screen parent;
	/** The settings as saved when the screen opened (or last saved), for Cancel. */
	private final AfkConfig snapshot;
	private final TestLabActions actions = new TestLabActions(this);
	private final TabManager tabManager = new TabManager(widget -> this.addRenderableWidget(widget),
			widget -> this.removeWidget(widget), this::onTabSelected, tab -> {
			});

	private List<RowTab> tabs = List.of();
	private @Nullable TabNavigationBar tabBar;
	private Button saveButton;
	private Button doneButton;
	private Button cancelButton;
	private int panelX;
	private int panelWidth;
	private int panelTop;
	private int panelBottom;
	private long savedUntilMs;

	public AfkMenuScreen(@Nullable Screen parent) {
		this(parent, selectedTab);
	}

	/** Opens on the given tab (e.g. {@link #TAB_TEST_LAB} for the standalone Test Lab keybind). */
	public AfkMenuScreen(@Nullable Screen parent, int tab) {
		super(Component.literal("AFK Mod"));
		this.parent = parent;
		this.snapshot = AfkConfig.fromJson(AfkModClient.savedConfig().toJson());
		selectedTab = tab;
		TimerTab.resetTyped();
	}

	private void onTabSelected(Tab tab) {
		int index = tabs.indexOf(tab);
		if (index >= 0) selectedTab = index;
	}

	private @Nullable RowTab currentTab() {
		return tabManager.getCurrentTab() instanceof RowTab tab ? tab : null;
	}

	@Override
	public boolean isPauseScreen() {
		// Single-player keeps running, so Test Lab scenarios started from here behave like on a server.
		return false;
	}

	@Override
	protected void init() {
		this.tabs = List.of(new DashboardTab(), new TimerTab(), new DetectionTab(), new RecoveryTab(),
				new StatsTab(this), new DisplayTab(), new DebugTab(actions), new TestLabTab(actions));
		TabNavigationBar bar = TabNavigationBar.builder(this.tabManager, this.width).addTabs(tabs.toArray(new Tab[0])).build();
		this.tabBar = bar;
		this.addRenderableWidget(bar);
		// The tab labels are short so eight fit in vanilla's 400 px tab bar; hovering shows the full name.
		for (int i = 0; i < tabs.size(); i++) {
			bar.setTabTooltip(i, Tooltip.create(Component.literal(tabs.get(i).fullTitle())));
		}
		this.saveButton = this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
				.tooltip(Tooltip.create(Component.literal("Write the settings to config/afkmod.json and stay here")))
				.bounds(0, 0, 80, 20).build());
		this.doneButton = this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> done())
				.tooltip(Tooltip.create(Component.literal("Save and close")))
				.bounds(0, 0, 80, 20).build());
		this.cancelButton = this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> cancel())
				.tooltip(Tooltip.create(Component.literal("Undo the changes made since the last save and close")))
				.bounds(0, 0, 80, 20).build());
		bar.selectTab(Math.clamp(selectedTab, 0, tabs.size() - 1), false);
		repositionElements();
	}

	@Override
	protected void setInitialFocus() {
		// Nothing is focused at first, so no tooltip pops up and no text field grabs the keyboard.
	}

	@Override
	protected void repositionElements() {
		TabNavigationBar bar = this.tabBar;
		if (bar == null) return;
		bar.updateWidth(this.width);
		int barBottom = bar.getRectangle().bottom();
		this.panelWidth = PanelLayout.panelWidth(this.width);
		this.panelX = PanelLayout.panelX(this.width, panelWidth);
		this.panelTop = barBottom + 2;
		this.panelBottom = Math.max(panelTop + 40, this.height - FOOTER_HEIGHT);
		this.tabManager.setTabArea(new ScreenRectangle(panelX, panelTop, panelWidth, panelBottom - panelTop));

		int buttonWidth = Math.max(40, Math.min(100, (panelWidth - 8) / 3));
		int total = buttonWidth * 3 + 8;
		int x = (this.width - total) / 2;
		int y = Math.min(this.height - 22, panelBottom + 5);
		saveButton.setRectangle(buttonWidth, 20, x, y);
		doneButton.setRectangle(buttonWidth, 20, x + buttonWidth + 4, y);
		cancelButton.setRectangle(buttonWidth, 20, x + 2 * (buttonWidth + 4), y);
	}

	// ---- Save / Done / Cancel ----

	private boolean saveQuietly() {
		try {
			AfkModClient.savedConfig().save(AfkModClient.configFile());
			return true;
		} catch (IOException e) {
			AfkModClient.LOGGER.error("Could not save {}", AfkModClient.configFile(), e);
			return false;
		}
	}

	private void save() {
		if (saveQuietly()) {
			snapshot.copyFrom(AfkModClient.savedConfig());
			savedUntilMs = System.currentTimeMillis() + 1500;
		}
	}

	private void done() {
		this.minecraft.setScreen(this.parent);
	}

	private void cancel() {
		AfkConfig config = AfkModClient.savedConfig();
		// "Set timer" and "No timer" act on the running countdown, so Cancel does not undo them.
		snapshot.timerSeconds = config.timerSeconds;
		config.copyFrom(snapshot);
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void onClose() {
		cancel();
	}

	@Override
	public void removed() {
		// Done, Cancel, or leaving to run a Test Lab scenario: the live settings (possibly reverted) are saved.
		saveQuietly();
	}

	// ---- input ----

	@Override
	public boolean keyPressed(KeyEvent event) {
		TabNavigationBar bar = this.tabBar;
		if (bar != null && bar.keyPressed(event)) return true;
		RowTab tab = currentTab();
		if (tab != null) {
			if (event.key() == InputConstants.KEY_PAGEUP) {
				tab.scroll(-3);
				return true;
			}
			if (event.key() == InputConstants.KEY_PAGEDOWN) {
				tab.scroll(3);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		RowTab tab = currentTab();
		if (tab != null && mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= panelTop && mouseY < panelBottom) {
			// Handled here first, so scrolling over a cycle button scrolls the list instead of changing the value.
			tab.scroll(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---- drawing ----

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		RowTab tab = currentTab();
		graphics.fill(panelX - 3, panelTop, panelX + panelWidth + 3, panelBottom, 0xC0101010);
		graphics.outline(panelX - 3, panelTop, panelWidth + 6, panelBottom - panelTop, 0xFF555555);
		if (tab != null) tab.relayout();
		saveButton.setMessage(Component.literal(System.currentTimeMillis() < savedUntilMs ? "Saved" : "Save"));
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (tab == null) return;
		tab.draw(graphics, mouseX, mouseY);

		// The hovered label or icon wins; otherwise a keyboard-focused icon shows its tooltip.
		TooltipRequest request = tab.tooltipAt(mouseX, mouseY);
		if (request == null && getFocused() instanceof InfoIcon icon && icon.visible) {
			request = new TooltipRequest(icon.text(), icon.getX() + InfoIcon.SIZE, icon.getY() + InfoIcon.SIZE / 2);
		}
		if (request != null) showTooltip(graphics, request);
	}

	/** Vanilla tooltip rendering with our own wrapping (about 40 characters) and a positioner that clamps to the screen. */
	private void showTooltip(GuiGraphicsExtractor graphics, TooltipRequest request) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (String line : request.text().body()) lines.add(Component.literal(line).getVisualOrderText());
		for (String line : request.text().footer()) {
			lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY).getVisualOrderText());
		}
		graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), ClampingTooltipPositioner.INSTANCE,
				request.anchorX(), request.anchorY(), true, null);
	}
}
