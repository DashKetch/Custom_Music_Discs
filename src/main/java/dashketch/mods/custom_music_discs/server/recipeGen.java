package dashketch.mods.custom_music_discs.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import dashketch.mods.custom_music_discs.item.ModItems;

import java.util.concurrent.CompletableFuture;

public class recipeGen extends RecipeProvider {

    public recipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput output) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BLANK_DISC, 1)
                .requires(Items.DISC_FRAGMENT_5, 4)
                .requires(Items.SLIME_BALL)
                .unlockedBy("has_disc_fragment", has(Items.DISC_FRAGMENT_5))
                .save(output, ResourceLocation.fromNamespaceAndPath("custom_music_discs", "blank_custom_disc"));

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.DISC_BURNER, 1)
                .pattern("Z#Z")
                .pattern("#X#")
                .pattern("#Y#")
                .define('#', Items.IRON_INGOT)
                .define('X', Items.DIAMOND)
                .define('Y', Items.REDSTONE_BLOCK)
                .define('Z', Items.IRON_NUGGET)
                .unlockedBy("has_diamond", has(Items.DIAMOND))
                .save(output, ResourceLocation.fromNamespaceAndPath("custom_music_discs", "disc_burner"));
    }
}