package dev.handy.mods.handytrader.client.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import dev.handy.mods.handytrader.client.TradeFavorites;
import dev.handy.mods.handytrader.client.TradeHash;
import dev.handy.mods.handytrader.config.HandyTraderConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

	@Shadow private int shopItem;
	@Shadow int scrollOff;

	/**
	 * Vanilla's trade-selection routine: sets the selection hint, auto-fills the payment
	 * slots from the player's inventory via {@code tryMoveItems(shopItem)}, and sends the
	 * select-trade packet (which our {@link #handytrader$remapSelectTradeIndex} remaps from
	 * sorted back to server order). Shadowed so the bulk-trade loop can reuse it instead of
	 * reimplementing selection. Private on {@code MerchantScreen}; the throwing body is a
	 * placeholder discarded at mixin-apply time.
	 */
	@Shadow private void postButtonClick() { throw new AssertionError(); }

	/** Result slot index in {@link MerchantMenu} (PAYMENT1=0, PAYMENT2=1, RESULT=2). */
	@Unique private static final int RESULT_SLOT = 2;
	@Unique private UUID handytrader$villagerUUID;
	@Unique private boolean handytrader$needsSort = true;
	/** Maps sorted index -> original server index. Null when no reordering active. */
	@Unique private int[] handytrader$sortedToActual;
	/** Snapshot of the server's original offer order, captured before first sort. */
	@Unique private List<MerchantOffer> handytrader$originalOffers;
	/** Tracks the MerchantOffers reference to detect server-side replacements (restock/level-up). */
	@Unique private MerchantOffers handytrader$lastKnownOffers;

	@Unique private static final int BOOKMARK_SIZE = 7;
	@Unique private static final int BOOKMARK_INSET = 1;
	@Unique private static final int BUTTON_X_OFFSET = 5;
	@Unique private static final int BUTTON_Y_OFFSET = 17;
	@Unique private static final int BUTTON_WIDTH = 88;
	@Unique private static final int BUTTON_HEIGHT = 20;
	@Unique private static final int VISIBLE_BUTTONS = 7;

	@Unique
	private record BookmarkPalette(int fill, int highlight, int shadow) {}

	@Unique private static final BookmarkPalette STAR_NORMAL =
			new BookmarkPalette(0xFFDAA520, 0xFFFFD700, 0xFF8B6914);
	@Unique private static final BookmarkPalette STAR_HOVER =
			new BookmarkPalette(0xFFFFE850, 0xFFFFFF80, 0xFFC89020);
	@Unique private static final BookmarkPalette EMPTY_HOVER =
			new BookmarkPalette(0x50FFD700, 0x70FFE850, 0x50B8860B);

	protected MerchantScreenMixin(MerchantMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	@Inject(method = "init()V", at = @At("TAIL"))
	private void handytrader$captureVillager(CallbackInfo ci) {
		Entity target = Minecraft.getInstance().crosshairPickEntity;
		if (target != null) {
			this.handytrader$villagerUUID = target.getUUID();
		}
		this.handytrader$needsSort = true;
		this.handytrader$originalOffers = null;
		this.handytrader$lastKnownOffers = null;
	}

	// -- Sort favorites to top --
	//
	// This hook lives on extractContents HEAD rather than tick() despite the
	// per-frame call rate, because MerchantScreen does not declare its own
	// tick() — it inherits from Screen — and Mixin only resolves @Inject
	// targets against methods declared on the target class, not inherited
	// ones. A tick()V inject on this mixin fails with "could not find any
	// targets matching tick()V" at class load. Per-frame cost here is
	// negligible (one ref-equality check + one boolean read) thanks to the
	// needsSort gate; the body only runs on actual triggers.
	@Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
	private void handytrader$sortIfNeeded(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
										   float partialTick, CallbackInfo ci) {
		// Detect server-side offer replacement (restock, level-up, etc.)
		MerchantOffers currentOffers = this.menu.getOffers();
		if (handytrader$lastKnownOffers != null && currentOffers != handytrader$lastKnownOffers) {
			handytrader$originalOffers = null;
			handytrader$needsSort = true;
		}
		handytrader$lastKnownOffers = currentOffers;

		if (!handytrader$needsSort) return;
		handytrader$needsSort = false;

		// On a re-sort (bookmark toggle or offer refresh), track which actual trade
		// was selected so we can preserve the selection across the reorder.
		boolean isResort = handytrader$sortedToActual != null;
		int oldActualIndex = -1;
		if (isResort && shopItem >= 0 && shopItem < handytrader$sortedToActual.length) {
			oldActualIndex = handytrader$sortedToActual[shopItem];
		}

		// Capture the server's original order on first sort (or after server refresh).
		// This stays as a side-effect — it's lazy memoization, not part of the sort
		// decision proper.
		if (handytrader$originalOffers == null || handytrader$originalOffers.size() != currentOffers.size()) {
			handytrader$originalOffers = new ArrayList<>(currentOffers);
		}

		SortMapping mapping = handytrader$computeSortMapping();
		handytrader$applyMapping(currentOffers, mapping);

		if (isResort && oldActualIndex >= 0) {
			// Find where the previously selected trade moved in the new sort order
			if (mapping.sortedToActual() != null) {
				for (int i = 0; i < mapping.sortedToActual().length; i++) {
					if (mapping.sortedToActual()[i] == oldActualIndex) {
						shopItem = i;
						break;
					}
				}
			} else {
				// No favorites — offers restored to original order
				shopItem = oldActualIndex;
			}
			// Update the container's selection hint so the result slot stays correct.
			// Do NOT call postButtonClick() here — it sends a network packet and moves
			// items during rendering, causing race conditions with the server that
			// make items flash in and out of payment slots.
			this.menu.setSelectionHint(shopItem);
		}
		// On initial sort (screen open): shopItem stays at 0, which selects the
		// first trade in sorted order (the top favorite). No setSelectionHint needed
		// since the container's default selectionHint is also 0.
	}

	/**
	 * Result of a sort decision. {@code sortedToActual} is the index map from
	 * sorted-client-order back to the server's original order, or {@code null} when
	 * the original order should be preserved (no villager, favorites disabled, no
	 * favorites set, or empty offers).
	 */
	@Unique
	private record SortMapping(int[] sortedToActual) {
		@Unique
		private static final SortMapping ORIGINAL_ORDER = new SortMapping(null);
	}

	/**
	 * Compute the sort mapping from {@code handytrader$originalOffers}. Pure with
	 * respect to the live offers list — only reads {@code originalOffers}, the
	 * villager UUID, the favorites store, and config; never mutates instance state.
	 */
	@Unique
	private SortMapping handytrader$computeSortMapping() {
		if (handytrader$originalOffers == null || handytrader$originalOffers.isEmpty()) {
			return SortMapping.ORIGINAL_ORDER;
		}
		if (handytrader$villagerUUID == null || !HandyTraderConfig.get().enableFavorites) {
			return SortMapping.ORIGINAL_ORDER;
		}

		int n = handytrader$originalOffers.size();
		List<Integer> favoriteIndices = new ArrayList<>();
		List<Integer> otherIndices = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			MerchantOffer offer = handytrader$originalOffers.get(i);
			String hash = TradeHash.hash(offer);
			boolean isFav = TradeFavorites.isFavorite(handytrader$villagerUUID, hash);
			if (!isFav) {
				// Pre-2.1.0-beta.3 favorites were stored under a hashCode-based hash
				// that won't match the new SHA-1 form. If this villager has one,
				// rewrite it to the new format so the next session is clean.
				String legacy = TradeHash.legacyHash(offer);
				if (TradeFavorites.migrateLegacyHash(handytrader$villagerUUID, legacy, hash)) {
					isFav = true;
				}
			}
			if (isFav) {
				favoriteIndices.add(i);
			} else {
				otherIndices.add(i);
			}
		}
		if (favoriteIndices.isEmpty()) return SortMapping.ORIGINAL_ORDER;

		int[] sortedToActual = new int[n];
		int idx = 0;
		for (int i : favoriteIndices) sortedToActual[idx++] = i;
		for (int i : otherIndices) sortedToActual[idx++] = i;
		return new SortMapping(sortedToActual);
	}

	/** Apply a sort mapping to the live offers list and update the cached mapping field. */
	@Unique
	private void handytrader$applyMapping(MerchantOffers liveOffers, SortMapping mapping) {
		handytrader$sortedToActual = mapping.sortedToActual();
		liveOffers.clear();
		if (mapping.sortedToActual() == null) {
			liveOffers.addAll(handytrader$originalOffers);
		} else {
			for (int srcIdx : mapping.sortedToActual()) {
				liveOffers.add(handytrader$originalOffers.get(srcIdx));
			}
		}
	}

	/**
	 * Remap the trade index in the server-bound select-trade packet from sorted
	 * order back to the server's original order.
	 *
	 * This wraps only the {@code new ServerboundSelectTradePacket(index)} construction
	 * inside vanilla {@code postButtonClick} via MixinExtras, rather than cancelling the
	 * whole method. The earlier full-method-replace ({@code @Inject} + {@code ci.cancel()})
	 * broke compatibility with mods that inject at the TAIL/RETURN of postButtonClick —
	 * notably Villager Trading Plus (Easier Villager Trading), whose "trade all on click"
	 * runs at {@code @At("RETURN")} of this method. Cancelling at HEAD meant RETURN was
	 * never reached, so its one-click bulk trade silently fell back to vanilla behaviour
	 * (see issue #20).
	 *
	 * By only swapping the packet's index we let vanilla run to completion: setSelectionHint
	 * and tryMoveItems still receive the sorted {@code shopItem} (correct for the reordered
	 * client offer list), the server receives the original index (correct trade selected),
	 * and downstream injectors fire normally.
	 *
	 * {@code @WrapOperation} is also a non-consuming injector, so other mods can wrap the
	 * same construction without conflicting. We previously hit a dead {@code @ModifyArg} on
	 * this same constructor; WrapOperation does not share that failure mode.
	 */
	@WrapOperation(
			method = "postButtonClick()V",
			at = @At(value = "NEW", target = "(I)Lnet/minecraft/network/protocol/game/ServerboundSelectTradePacket;"))
	private ServerboundSelectTradePacket handytrader$remapSelectTradeIndex(
			int index, Operation<ServerboundSelectTradePacket> original) {
		if (handytrader$sortedToActual != null
				&& index >= 0 && index < handytrader$sortedToActual.length) {
			index = handytrader$sortedToActual[index];
		}
		return original.call(index);
	}

	// -- Render favorites overlay --

	@Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
	private void handytrader$renderFavorites(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
											  float partialTick, CallbackInfo ci) {
		if (handytrader$villagerUUID == null) return;
		if (!HandyTraderConfig.get().enableFavorites) return;

		MerchantOffers offers = this.menu.getOffers();
		if (offers.isEmpty()) return;

		int buttonX = this.leftPos + BUTTON_X_OFFSET;
		int buttonStartY = this.topPos + BUTTON_Y_OFFSET;

		for (int i = 0; i < VISIBLE_BUTTONS; i++) {
			int offerIndex = i + this.scrollOff;
			if (offerIndex >= offers.size()) break;

			MerchantOffer offer = offers.get(offerIndex);
			String tradeHash = TradeHash.hash(offer);
			boolean isFavorite = TradeFavorites.isFavorite(handytrader$villagerUUID, tradeHash);

			int buttonY = buttonStartY + i * BUTTON_HEIGHT;
			int cornerX = buttonX + BOOKMARK_INSET;
			int cornerY = buttonY + BOOKMARK_INSET + 1;
			int hitSize = BOOKMARK_SIZE + BOOKMARK_INSET + 1;
			boolean isCornerHovered = mouseX >= buttonX && mouseX < buttonX + hitSize
					&& mouseY >= buttonY && mouseY < buttonY + hitSize;

			if (isFavorite) {
				BookmarkPalette palette = isCornerHovered ? STAR_HOVER : STAR_NORMAL;
				handytrader$drawBookmarkCorner(guiGraphics, cornerX, cornerY, palette);
			} else if (isCornerHovered) {
				handytrader$drawBookmarkCorner(guiGraphics, cornerX, cornerY, EMPTY_HOVER);
			}
		}
	}

	// -- Bookmark-corner hover tooltip (discoverability) --

	/**
	 * Show a hint when the cursor is over our bookmark corner: what a click does, and — for
	 * favorites — the otherwise-invisible shift-click bulk-trade gesture.
	 *
	 * This runs at {@code @At("HEAD")}, before vanilla sets the trade button's item tooltip.
	 * The tooltip slot is first-wins (it ignores later writers unless forced), so setting ours
	 * here both wins on the corner and leaves vanilla's trade-info tooltip intact everywhere
	 * else on the button (where we don't set anything).
	 */
	@Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
	private void handytrader$cornerTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
										   float partialTick, CallbackInfo ci) {
		if (handytrader$villagerUUID == null) return;
		if (!HandyTraderConfig.get().enableFavorites) return;

		MerchantOffers offers = this.menu.getOffers();
		if (offers.isEmpty()) return;

		int buttonX = this.leftPos + BUTTON_X_OFFSET;
		int buttonStartY = this.topPos + BUTTON_Y_OFFSET;
		int hitSize = BOOKMARK_SIZE + BOOKMARK_INSET + 1;

		for (int i = 0; i < VISIBLE_BUTTONS; i++) {
			int offerIndex = i + this.scrollOff;
			if (offerIndex >= offers.size()) break;

			int buttonY = buttonStartY + i * BUTTON_HEIGHT;
			if (mouseX < buttonX || mouseX >= buttonX + hitSize
					|| mouseY < buttonY || mouseY >= buttonY + hitSize) {
				continue;
			}

			MerchantOffer offer = offers.get(offerIndex);
			boolean isFavorite = TradeFavorites.isFavorite(
					handytrader$villagerUUID, TradeHash.hash(offer));

			List<Component> lines = new ArrayList<>(2);
			if (isFavorite) {
				lines.add(Component.translatable("tooltip.handytrader.unfavorite"));
				if (HandyTraderConfig.get().enableBulkTrade) {
					lines.add(Component.translatable("tooltip.handytrader.bulkHint")
							.withStyle(ChatFormatting.GRAY));
				}
			} else {
				lines.add(Component.translatable("tooltip.handytrader.favorite"));
			}
			guiGraphics.setTooltipForNextFrame(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
			return;
		}
	}

	@Unique
	private void handytrader$drawBookmarkCorner(GuiGraphicsExtractor g, int x, int y,
												 BookmarkPalette palette) {
		// Fill the triangle body
		for (int row = 0; row < BOOKMARK_SIZE; row++) {
			int width = BOOKMARK_SIZE - row;
			g.fill(x, y + row, x + width, y + row + 1, palette.fill());
		}
		// Highlight: top edge
		g.fill(x, y, x + BOOKMARK_SIZE, y + 1, palette.highlight());
		// Highlight: left edge
		g.fill(x, y, x + 1, y + BOOKMARK_SIZE, palette.highlight());
		// Shadow: diagonal hypotenuse
		for (int i = 0; i < BOOKMARK_SIZE; i++) {
			g.fill(x + BOOKMARK_SIZE - 1 - i, y + i,
					x + BOOKMARK_SIZE - i, y + i + 1, palette.shadow());
		}
	}

	// -- Bookmark click handling --

	@Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
	private void handytrader$onBookmarkClick(MouseButtonEvent event, boolean bl,
											  CallbackInfoReturnable<Boolean> cir) {
		if (handytrader$villagerUUID == null) return;
		if (!HandyTraderConfig.get().enableFavorites) return;
		if (event.button() != 0) return;

		double mouseX = event.x();
		double mouseY = event.y();
		MerchantOffers offers = this.menu.getOffers();
		int buttonX = this.leftPos + BUTTON_X_OFFSET;
		int buttonStartY = this.topPos + BUTTON_Y_OFFSET;

		for (int i = 0; i < VISIBLE_BUTTONS; i++) {
			int offerIndex = i + this.scrollOff;
			if (offerIndex >= offers.size()) break;

			int buttonY = buttonStartY + i * BUTTON_HEIGHT;

			// Bulk-trade: Shift-click anywhere on a favorited trade's button repeats it
			// until the inputs run out or the villager locks the trade. Shift takes
			// priority over the corner toggle so the whole button is the bulk target.
			if (HandyTraderConfig.get().enableBulkTrade
					&& event.hasShiftDown()
					&& mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH
					&& mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT) {
				MerchantOffer offer = offers.get(offerIndex);
				String hash = TradeHash.hash(offer);
				if (TradeFavorites.isFavorite(handytrader$villagerUUID, hash)) {
					handytrader$bulkTrade(offerIndex);
					cir.setReturnValue(true);
					return;
				}
			}

			// Bookmark corner (top-left of the button): plain click toggles the favorite.
			int hitSize = BOOKMARK_SIZE + BOOKMARK_INSET + 1;
			if (mouseX >= buttonX && mouseX < buttonX + hitSize
					&& mouseY >= buttonY && mouseY < buttonY + hitSize) {
				MerchantOffer offer = offers.get(offerIndex);
				String tradeHash = TradeHash.hash(offer);
				TradeFavorites.toggleFavorite(handytrader$villagerUUID, tradeHash);
				TradeFavorites.save();

				// Trigger re-sort on next frame
				handytrader$needsSort = true;

				Minecraft.getInstance().player.playSound(
						SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, 1.2F);

				cir.setReturnValue(true);
				return;
			}
		}
	}

	/**
	 * Repeat the trade at {@code sortedIndex} (client/sorted order) until the player runs
	 * out of inputs, the trade goes out of stock, or the configured cap is hit.
	 *
	 * Each pass mirrors exactly what a human does — {@link #postButtonClick()} selects the
	 * trade and auto-fills the payment slots (our {@code @WrapOperation} remaps the outgoing
	 * index to server order), then a {@code QUICK_MOVE} on the result slot takes the output,
	 * just like a shift-click. Every step sends the same real packets in order, so the server
	 * validates each trade — no custom packets, no dupe/loss risk if the client mispredicts.
	 *
	 * The bulk loop is modeled on Giselbaer's Easier Villager Trading / Villager Trading Plus
	 * (MIT licensed).
	 */
	@Unique
	private void handytrader$bulkTrade(int sortedIndex) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		int max = HandyTraderConfig.get().bulkTradeMax;
		if (max <= 0) max = 256;

		MerchantOffers offers = this.menu.getOffers();
		if (sortedIndex >= offers.size()) return;
		MerchantOffer offer = offers.get(sortedIndex);

		// A trade can only run (maxUses - uses) more times before it locks until the
		// villager restocks. Cap to that up front so we don't fire clicks the server
		// will just reject. The client's offer carries the server's use counts.
		int remainingUses = Math.max(0, offer.getMaxUses() - offer.getUses());
		int limit = Math.min(max, remainingUses);

		this.shopItem = sortedIndex;
		Slot resultSlot = this.menu.getSlot(RESULT_SLOT);

		int traded = 0;
		while (traded < limit) {
			if (offer.isOutOfStock()) break;

			// Select the trade + auto-fill payment from inventory + notify the server.
			this.postButtonClick();

			// Payment couldn't be satisfied (inventory exhausted) — nothing left to take.
			if (!resultSlot.hasItem()) break;

			// Take the trade output exactly like a shift-click on the result slot.
			this.slotClicked(resultSlot, RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
			traded++;
		}

		if (traded > 0) {
			mc.player.playSound(SoundEvents.VILLAGER_YES, 0.5F, 1.0F);
		}
	}
}
