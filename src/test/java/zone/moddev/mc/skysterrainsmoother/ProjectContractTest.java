package zone.moddev.mc.skysterrainsmoother;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectContractTest {
    @Test
    void targetMetadataAndToolchainMatchTheNativeRuntime() throws Exception {
        Properties metadata = new Properties();
        metadata.load(new java.io.StringReader(text(Paths.get("gradle.properties"))));
        assertEquals("0.1.0.116051", metadata.getProperty("mod_version"));
        assertEquals("1.16.5", metadata.getProperty("minecraft_version"));
        assertEquals("36.2.34", metadata.getProperty("forge_version"));
        assertEquals("1.16.5", metadata.getProperty("mapping_version"));
        assertEquals("8.0.502+7", metadata.getProperty("java_toolchain_version"));
        assertEquals("17", metadata.getProperty("gradle_java_version"));
        assertFalse(Files.exists(Paths.get("src/main/resources/mcmod.info")));
        assertTrue(Files.isRegularFile(Paths.get("src/main/resources/META-INF/mods.toml")));
        for (String sourceSet : new String[] { "main", "integrationTest", "bootstrapTest" }) {
            assertEquals(6, json(Paths.get("src/" + sourceSet + "/resources/pack.mcmeta"))
                    .getAsJsonObject("pack").get("pack_format").getAsInt(), sourceSet);
        }
    }

    @Test
    void bothSandColoursHaveNativeModelsAndTheirOwnLoot() throws Exception {
        Path root = Paths.get("src/main/resources");
        for (String sand : new String[] { "sand", "red_sand" }) {
            Path assets = root.resolve("assets/skysterrainsmoother");
            JsonObject variants = json(assets.resolve("blockstates/" + sand + "_slab.json")).getAsJsonObject("variants");
            assertEquals(2, variants.size());
            for (String half : new String[] { "top", "bottom" }) {
                String suffix = half.equals("top") ? "_top" : "";
                assertEquals("skysterrainsmoother:block/" + sand + "_slab" + suffix,
                        variants.getAsJsonObject("type=" + half).get("model").getAsString());
                JsonObject model = json(assets.resolve("models/block/" + sand + "_slab" + suffix + ".json"));
                assertEquals(half.equals("top") ? "minecraft:block/slab_top" : "minecraft:block/slab", model.get("parent").getAsString());
                for (String face : new String[] { "top", "bottom", "side" }) {
                    assertEquals("minecraft:block/" + sand, model.getAsJsonObject("textures").get(face).getAsString());
                }
            }
            JsonObject loot = json(root.resolve("data/skysterrainsmoother/loot_tables/blocks/" + sand + "_slab.json"));
            assertEquals("skysterrainsmoother:" + sand + "_slab", loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void sandRecipesConserveVolumeAndKeepTheirPermanentIds() throws Exception {
        Path recipes = Paths.get("src/main/resources/data/skysterrainsmoother/recipes");
        try (java.util.stream.Stream<Path> files = Files.list(recipes)) {
            assertEquals(4, files.count());
        }
        for (String sand : new String[] { "sand", "red_sand" }) {
            JsonObject cut = json(recipes.resolve(sand + "_slab.json"));
            assertEquals("SSS", cut.getAsJsonArray("pattern").get(0).getAsString());
            assertEquals("minecraft:" + sand, cut.getAsJsonObject("key").getAsJsonObject("S").get("item").getAsString());
            assertEquals("skysterrainsmoother:" + sand + "_slab", cut.getAsJsonObject("result").get("item").getAsString());
            assertEquals(6, cut.getAsJsonObject("result").get("count").getAsInt());
            JsonObject combine = json(recipes.resolve(sand + "_from_slabs.json"));
            assertEquals("SS", combine.getAsJsonArray("pattern").get(0).getAsString());
            assertEquals("skysterrainsmoother:" + sand + "_slab", combine.getAsJsonObject("key").getAsJsonObject("S").get("item").getAsString());
            assertEquals("minecraft:" + sand, combine.getAsJsonObject("result").get("item").getAsString());
            assertFalse(combine.getAsJsonObject("result").has("count"));
        }
    }

    @Test
    void eighteenUtf8LanguagesRetainNamesAndConfigHelp() throws Exception {
        Path languages = Paths.get("src/main/resources/assets/skysterrainsmoother/lang");
        try (java.util.stream.Stream<Path> files = Files.list(languages)) {
            java.util.List<Path> paths = files.collect(java.util.stream.Collectors.toList());
            assertEquals(18, paths.size());
            for (Path path : paths) {
                String source = text(path);
                assertTrue(path.toString().endsWith(".json"));
                assertFalse(source.startsWith("\uFEFF"));
                assertFalse(source.contains("\r"));
                assertTrue(source.endsWith("\n"));
                JsonObject values = json(path);
                assertEquals(8, values.size());
                for (String sand : new String[] { "sand", "red_sand" }) {
                    assertFalse(values.get("block.skysterrainsmoother." + sand + "_slab").getAsString().trim().isEmpty());
                }
            }
        }
    }

    private static JsonObject json(Path path) throws Exception {
        return new JsonParser().parse(text(path)).getAsJsonObject();
    }

    private static String text(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
