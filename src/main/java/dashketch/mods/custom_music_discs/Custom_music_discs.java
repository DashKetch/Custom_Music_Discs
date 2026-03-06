package dashketch.mods.custom_music_discs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Custom_music_discs.MODID)
public class Custom_music_discs {
    public static final String MODID = "custom_music_discs";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Registering the two items
    public static final DeferredItem<Item> BLANK_CUSTOM_DISC = ITEMS.registerSimpleItem("blank_custom_disc", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> DISC_BURNER = ITEMS.registerSimpleItem("disc_burner", new Item.Properties());

    // Registering the Creative Tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CUSTOM_DISCS_TAB = CREATIVE_MODE_TABS.register("custom_music_discs",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> BLANK_CUSTOM_DISC.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(BLANK_CUSTOM_DISC.get());
                        output.accept(DISC_BURNER.get());
                    }).build());

    public Custom_music_discs(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}