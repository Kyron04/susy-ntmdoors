package com.ntmdoors.test.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import net.minecraft.Bootstrap;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeValidationTest {

    private static final Path RECIPES_DIR = Paths.get("src", "main", "resources", "data", "ntmdoors", "recipes");

    @BeforeAll
    public static void setup() {
        net.minecraft.SharedConstants.createGameVersion();
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ModSounds.registerModSounds();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModItems.registerModItems();
        ModDoors.registerModDoors();
    }

    @Test
    @DisplayName("Verify all recipe JSON files are syntactically valid and reference registered items")
    public void testAllRecipesValid() throws Exception {
        File folder = RECIPES_DIR.toFile();
        assertTrue(folder.exists() && folder.isDirectory(), "Recipes directory must exist");

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(files, "Recipe files array must not be null");
        assertTrue(files.length >= 100, "Expected at least 100 recipe JSONs, found: " + files.length);

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                assertTrue(json.has("type"), "Recipe " + file.getName() + " missing 'type'");
                String type = json.get("type").getAsString();
                assertTrue(type.equals("minecraft:crafting_shaped") || type.equals("minecraft:crafting_shapeless"),
                        "Unknown recipe type in " + file.getName() + ": " + type);

                assertTrue(json.has("result"), "Recipe " + file.getName() + " missing 'result'");
                JsonObject result = json.getAsJsonObject("result");
                assertTrue(result.has("item"), "Result in " + file.getName() + " missing 'item'");
                String resultItemId = result.get("item").getAsString();
                Identifier resultId = Identifier.tryParse(resultItemId);
                assertNotNull(resultId, "Invalid result item identifier: " + resultItemId + " in " + file.getName());

                if ("ntmdoors".equals(resultId.getNamespace())) {
                    assertTrue(Registries.ITEM.containsId(resultId) || ModItems.ITEMS.containsKey(resultId.getPath())
                                    || ModDoors.DOOR_ITEMS.containsKey(resultId.getPath()),
                            "Recipe " + file.getName() + " outputs unmapped ntmdoors item: " + resultItemId);
                }

                if (type.equals("minecraft:crafting_shaped")) {
                    assertTrue(json.has("pattern"), "Shaped recipe " + file.getName() + " missing 'pattern'");
                    assertTrue(json.has("key"), "Shaped recipe " + file.getName() + " missing 'key'");
                    JsonObject keyObj = json.getAsJsonObject("key");
                    for (Map.Entry<String, JsonElement> entry : keyObj.entrySet()) {
                        JsonObject ingredient = entry.getValue().getAsJsonObject();
                        assertTrue(ingredient.has("item"), "Key " + entry.getKey() + " in " + file.getName() + " missing 'item'");
                        String ingIdStr = ingredient.get("item").getAsString();
                        Identifier ingId = Identifier.tryParse(ingIdStr);
                        assertNotNull(ingId, "Invalid key item id: " + ingIdStr + " in " + file.getName());
                    }
                } else {
                    assertTrue(json.has("ingredients"), "Shapeless recipe " + file.getName() + " missing 'ingredients'");
                    JsonArray ingredients = json.getAsJsonArray("ingredients");
                    assertFalse(ingredients.isEmpty(), "Shapeless recipe " + file.getName() + " has empty ingredients");
                    for (JsonElement elem : ingredients) {
                        JsonObject ing = elem.getAsJsonObject();
                        assertTrue(ing.has("item"), "Ingredient in " + file.getName() + " missing 'item'");
                        String ingIdStr = ing.get("item").getAsString();
                        Identifier ingId = Identifier.tryParse(ingIdStr);
                        assertNotNull(ingId, "Invalid ingredient item id: " + ingIdStr + " in " + file.getName());
                    }
                }
            }
        }
    }
}
