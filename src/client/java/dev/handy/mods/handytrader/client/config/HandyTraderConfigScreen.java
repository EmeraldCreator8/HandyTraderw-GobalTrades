package dev.handy.mods.handytrader.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.handy.mods.handytrader.config.HandyTraderConfig;

@Environment(EnvType.CLIENT)
public class HandyTraderConfigScreen {

	public static Screen create(Screen parent) {
		HandyTraderConfig config = HandyTraderConfig.get();

		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable("config.handytrader.title"))
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable("config.handytrader.category.general"))
						.option(Option.<Boolean>createBuilder()
								.name(Component.translatable("config.handytrader.enableFavorites"))
								.description(OptionDescription.of(
										Component.translatable("config.handytrader.enableFavorites.desc")))
								.binding(true, () -> config.enableFavorites, val -> config.enableFavorites = val)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.option(Option.<Boolean>createBuilder()
								.name(Component.translatable("config.handytrader.enableBulkTrade"))
								.description(OptionDescription.of(
										Component.translatable("config.handytrader.enableBulkTrade.desc")))
								.binding(true, () -> config.enableBulkTrade, val -> config.enableBulkTrade = val)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.option(Option.<Integer>createBuilder()
								.name(Component.translatable("config.handytrader.bulkTradeMax"))
								.description(OptionDescription.of(
										Component.translatable("config.handytrader.bulkTradeMax.desc")))
								.binding(256, () -> config.bulkTradeMax, val -> config.bulkTradeMax = val)
								.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(16, 1024).step(16))
								.build())
						.build())
				.save(HandyTraderConfig::save)
				.build()
				.generateScreen(parent);
	}
}
