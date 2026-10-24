package dev.diorama;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import dev.diorama.compat.Widgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

/**
 * The settings in vanilla tabs, one group at a time: a checkbox for each on/off setting, two to a
 * row, then a button for each choice and a slider for each number. The minimap stays sharp on the
 * right as a live preview, so a change shows on it straight away.
 */
public final class SettingsScreen extends Screen {
	/** Right edge of the settings side on a wide screen. */
	private static final int SIDE = 340;
	/** What the preview needs on the right at the default size: a 128 minimap, its frame and margin. */
	private static final int PREVIEW_ROOM = 140;
	/** The settings' width; two checkbox columns with a gap between them. */
	private static final int CONTENT = 280;
	private static final int COLUMN_GAP = 8;
	private static final int TAB_BAR_HEIGHT = 24;

	private final @Nullable Screen parent;
	private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
	private final MinimapHud preview = new MinimapHud();
	private @Nullable TabNavigationBar tabBar;
	private List<SettingsTab> tabs = List.of();
	/** Right edge of the settings side: left of the preview where there's room, else the whole screen. */
	private int side = SIDE;

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("diorama.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Settings s = Settings.get();
		// Resizing the window runs this again: stay on the same tab.
		Tab current = this.tabManager.getCurrentTab();
		int selected = current == null ? 0 : Math.max(0, this.tabs.indexOf(current));
		this.side = Math.max(Math.min(SIDE, this.width - PREVIEW_ROOM), Math.min(this.width, CONTENT + 10));

		SettingsTab minimap = new SettingsTab("diorama.settings.minimap");
		minimap.toggle("minimap_shown", () -> s.minimap, v -> s.minimap = v);
		minimap.toggle("compass", () -> s.compass, v -> s.compass = v);
		minimap.toggle("coordinates", () -> s.coordinates, v -> s.coordinates = v);
		minimap.toggle("biome", () -> s.biome, v -> s.biome = v);
		minimap.toggle("auto_caves", () -> s.autoCaves, v -> s.autoCaves = v);
		Settings.Corner[] corners = Settings.Corner.values();
		minimap.cycle("corner", () -> s.minimapCorner.ordinal(), corners.length, i -> s.minimapCorner = corners[i],
			i -> Component.translatable("diorama.settings.corner." + corners[i].name().toLowerCase(Locale.ROOT)));
		int sizes = (Settings.MAX_SIZE - Settings.MIN_SIZE) / Settings.SIZE_STEP + 1;
		minimap.slider("size", (s.minimapSize - Settings.MIN_SIZE) / Settings.SIZE_STEP, sizes,
			i -> s.minimapSize = Settings.MIN_SIZE + i * Settings.SIZE_STEP, i -> Component.literal(Integer.toString(Settings.MIN_SIZE + i * Settings.SIZE_STEP)));
		minimap.slider("zoom", s.minimapZoom, Settings.ZOOMS.length, i -> s.minimapZoom = i, i -> Component.literal(Settings.ZOOMS[i] + "×"));
		minimap.choice("frame", Settings.Frame.values(), () -> s.minimapFrame, v -> s.minimapFrame = v);

		SettingsTab radar = new SettingsTab("diorama.settings.radar");
		radar.toggle("players", () -> s.radarPlayers, v -> s.radarPlayers = v);
		radar.toggle("hostile", () -> s.radarHostile, v -> s.radarHostile = v);
		radar.toggle("neutral", () -> s.radarNeutral, v -> s.radarNeutral = v);
		radar.toggle("passive", () -> s.radarPassive, v -> s.radarPassive = v);
		radar.toggle("type_outline", () -> s.radarTypeOutline, v -> s.radarTypeOutline = v);
		radar.choice("radar_style", Settings.RadarStyle.values(), () -> s.radarStyle, v -> s.radarStyle = v);
		int scales = (Settings.MAX_SCALE - Settings.MIN_SCALE) / Settings.SCALE_STEP + 1;
		radar.slider("icon_size", (s.radarIconScale - Settings.MIN_SCALE) / Settings.SCALE_STEP, scales,
			i -> s.radarIconScale = Settings.MIN_SCALE + i * Settings.SCALE_STEP, i -> Component.literal((Settings.MIN_SCALE + i * Settings.SCALE_STEP) + "%"));
		radar.choice("player_names", Settings.NameTags.values(), () -> s.playerNames, v -> s.playerNames = v);
		int[] ranges = Settings.RANGES;
		radar.slider("range", indexOf(ranges, s.radarRange), ranges.length, i -> s.radarRange = ranges[i], i -> Component.literal(ranges[i] + " m"));

		SettingsTab overlays = new SettingsTab("diorama.settings.overlays");
		overlays.toggle("chunk_grid", () -> s.chunkGrid, v -> s.chunkGrid = v);
		overlays.toggle("slime_chunks", () -> s.slimeChunks, v -> s.slimeChunks = v);
		Settings.SpawnSpots[] spots = Settings.SpawnSpots.values();
		overlays.cycle("spawn_spots", () -> s.spawnSpots.ordinal(), spots.length, i -> s.spawnSpots = spots[i],
			i -> Component.translatable("diorama.settings.spawn_spots." + spots[i].name().toLowerCase(Locale.ROOT)));

		SettingsTab waypoints = new SettingsTab("diorama.settings.waypoints");
		waypoints.toggle("waypoints_map", () -> s.waypointsOnMap, v -> s.waypointsOnMap = v);
		waypoints.toggle("waypoints_world", () -> s.waypointsInWorld, v -> s.waypointsInWorld = v);
		waypoints.toggle("deaths", () -> s.deathMarkers, v -> s.deathMarkers = v);
		waypoints.choice("waypoint_labels", Settings.WaypointLabels.values(), () -> s.waypointLabels, v -> s.waypointLabels = v);

		SettingsTab look = new SettingsTab("diorama.settings.look");
		look.toggle("outlines", () -> s.outlines, v -> s.outlines = v);
		look.toggle("rounded_fog", () -> s.roundedFog, v -> s.roundedFog = v);
		look.toggle("fog_of_war", () -> s.alwaysFogOfWar, v -> s.alwaysFogOfWar = v);
		Settings.Quality[] qualities = Settings.Quality.values();
		look.cycle("quality", () -> s.quality.ordinal(), qualities.length, i -> s.quality = qualities[i],
			i -> Component.translatable("diorama.settings.quality." + qualities[i].name().toLowerCase(Locale.ROOT)));
		// GPU baking needs the GPU API of newer versions; on 1.20.1 every tile is traced on the CPU.
		look.cycle("arrow_color", () -> s.arrowColor, Settings.ARROW_COLORS.length, i -> s.arrowColor = i,
			i -> Component.translatable("diorama.settings.arrow_color." + Settings.ARROW_COLOR_NAMES[i])
				.withStyle(style -> style.withColor(TextColor.fromRgb(Settings.ARROW_COLORS[i] & 0xFFFFFF))));

		// Vanilla's tab bar, over the settings side instead of the whole screen. It's drawn by hand
		// (extractRenderState), clipped to the side: its separator line runs on past the last tab.
		TabNavigationBar bar = TabNavigationBar.builder(this.tabManager, this.side).addTabs(minimap, radar, overlays, waypoints, look).build();
		bar.setWidth(this.side);
		bar.arrangeElements();
		this.tabs = List.of(minimap, radar, overlays, waypoints, look);
		this.tabBar = this.addWidget(bar);
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
			.bounds(this.side / 2 - 100, this.height - 26, 200, 20).build());
		this.tabManager.setTabArea(new ScreenRectangle(0, TAB_BAR_HEIGHT, this.side, this.listBottom() - TAB_BAR_HEIGHT));
		bar.selectTab(selected, false);
	}

	private int listBottom() {
		return this.height - 33;
	}

	/** Shows the tab with this index, as clicking it does. For tests. */
	public void selectTab(int index) {
		if (this.tabBar != null) {
			this.tabBar.selectTab(index, false);
		}
	}

	/** The settings in one tab: checkboxes two to a row, then buttons and sliders the full width. */
	private final class SettingsTab extends GridLayoutTab {
		private final List<AbstractWidget> checkboxes = new ArrayList<>();
		private final List<AbstractWidget> wide = new ArrayList<>();

		SettingsTab(String key) {
			super(Component.translatable(key));
		}

		void toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
			int column = (CONTENT - COLUMN_GAP) / 2;
			Checkbox checkbox = Widgets.checkbox(Component.translatable("diorama.settings." + key), SettingsScreen.this.font, 0, 0, column,
				get.getAsBoolean(), value -> {
					set.accept(value);
					Settings.get().save();
				});
			checkbox.setTooltip(tooltip(key));
			this.checkboxes.add(checkbox);
		}

		Button cycle(String key, IntSupplier get, int count, IntConsumer set, IntFunction<Component> name) {
			Component label = Component.translatable("diorama.settings." + key);
			Button button = Button.builder(label(label, name.apply(get.getAsInt())), b -> {
				int next = (get.getAsInt() + 1) % count;
				set.accept(next);
				Settings.get().save();
				b.setMessage(label(label, name.apply(next)));
			}).size(CONTENT, 20).tooltip(tooltip(key)).build();
			this.wide.add(button);
			return button;
		}

		/** A button that steps through an enum's values, named diorama.settings.<key>.<value>. */
		<E extends Enum<E>> Button choice(String key, E[] values, java.util.function.Supplier<E> get, Consumer<E> set) {
			return this.cycle(key, () -> get.get().ordinal(), values.length, i -> set.accept(values[i]),
				i -> Component.translatable("diorama.settings." + key + "." + values[i].name().toLowerCase(Locale.ROOT)));
		}

		void slider(String key, int index, int count, IntConsumer set, IntFunction<Component> name) {
			StepSlider slider = new StepSlider(Component.translatable("diorama.settings." + key), index, count, set, name);
			slider.setTooltip(tooltip(key));
			this.wide.add(slider);
		}

		@Override
		public void visitChildren(Consumer<AbstractWidget> consumer) {
			this.checkboxes.forEach(consumer);
			this.wide.forEach(consumer);
		}

		@Override
		public void doLayout(ScreenRectangle area) {
			int content = Math.min(CONTENT, area.width() - 10);
			int column = (content - COLUMN_GAP) / 2;
			int left = area.left() + (area.width() - content) / 2;
			int y = area.top() + 12;
			for (int i = 0; i < this.checkboxes.size(); i++) {
				this.checkboxes.get(i).setPosition(left + (i % 2) * (column + COLUMN_GAP), y + (i / 2) * 22);
			}
			if (!this.checkboxes.isEmpty()) {
				y += (this.checkboxes.size() + 1) / 2 * 22 + 10;
			}
			for (AbstractWidget widget : this.wide) {
				widget.setPosition(left, y);
				widget.setWidth(content);
				y += 24;
			}
		}
	}

	/**
	 * A slider over a list of values, one equal stretch of the track each, the way vanilla's
	 * option sliders pick a whole number. Saves when the value it points at changes.
	 */
	private static final class StepSlider extends AbstractSliderButton {
		private final Component name;
		private final int count;
		private final IntConsumer set;
		private final IntFunction<Component> valueName;
		private int index;

		StepSlider(Component name, int index, int count, IntConsumer set, IntFunction<Component> valueName) {
			super(0, 0, CONTENT, 20, Component.empty(), index <= 0 ? 0 : index >= count - 1 ? 1 : (index + 0.5) / count);
			this.name = name;
			this.count = count;
			this.set = set;
			this.valueName = valueName;
			this.index = Math.max(0, Math.min(count - 1, index));
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(label(this.name, this.valueName.apply(this.index)));
		}

		@Override
		protected void applyValue() {
			int picked = Math.min(this.count - 1, (int) Math.floor(this.value * this.count));
			if (picked != this.index) {
				this.index = picked;
				this.set.accept(picked);
				Settings.get().save();
			}
		}
	}

	private static Tooltip tooltip(String key) {
		return Tooltip.create(Component.translatable("diorama.settings." + key + ".tooltip"));
	}

	private static Component label(Component name, Component value) {
		return Component.empty().append(name).append(": ").append(value);
	}

	private static int indexOf(int[] values, int value) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == value) {
				return i;
			}
		}
		return 1;
	}

	/** The background, then the live preview, then the settings side's panel, all under the widgets. */
	private void drawBackground(GuiGraphics graphics) {
		this.renderBackground(graphics);
		// A big minimap would reach under the settings: it's cut off at their edge, which also keeps
		// its depth clear of them.
		graphics.enableScissor(this.side, 0, this.width, this.height);
		this.preview.preview(graphics, this.minecraft.getFrameTime());
		graphics.disableScissor();
		// The settings side, a darker panel between separator lines, as later versions draw a list in a world.
		graphics.fill(0, TAB_BAR_HEIGHT, this.side, this.listBottom(), 0x80000000);
		graphics.fill(0, this.listBottom(), this.side, this.listBottom() + 1, 0x40FFFFFF);
		graphics.fill(0, this.listBottom() + 1, this.side, this.listBottom() + 2, 0x80000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
		this.drawBackground(graphics);
		super.render(graphics, mouseX, mouseY, a);
		if (this.tabBar != null) {
			graphics.enableScissor(0, 0, this.side, TAB_BAR_HEIGHT);
			this.tabBar.render(graphics, mouseX, mouseY, a);
			graphics.disableScissor();
		}
	}

	// Ctrl+Tab and Ctrl+1 to 5 switch tabs, as in vanilla's tabbed screens, whatever has focus.
	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		return this.tabBar != null && this.tabBar.keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
	}

	@Override
	public void removed() {
		this.preview.close();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
