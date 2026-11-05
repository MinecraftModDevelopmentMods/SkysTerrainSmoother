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
        assertEquals("0.1.0.121111", metadata.getProperty("mod_version"));
        assertEquals("1.21.11", metadata.getProperty("minecraft_version"));
        assertEquals("61.1.0", metadata.getProperty("forge_version"));
        assertEquals("1.21.11", metadata.getProperty("mapping_version"));
        assertEquals("20251223.124241", metadata.getProperty("mcp_version"));
        assertEquals("21.0.7+6", metadata.getProperty("java_toolchain_version"));
        assertEquals("21", metadata.getProperty("gradle_java_version"));
        assertFalse(Files.exists(Paths.get("src/main/resources/mcmod.info")));
        assertTrue(Files.isRegularFile(Paths.get("src/main/resources/META-INF/mods.toml")));
        for (String sourceSet : new String[] { "main", "integrationTest", "bootstrapTest" }) {
            JsonObject pack = json(Paths.get("src/" + sourceSet + "/resources/pack.mcmeta"))
                    .getAsJsonObject("pack");
            assertEquals(94, pack.get("max_format").getAsInt(), sourceSet);
            assertEquals(94, pack.getAsJsonArray("min_format").get(0).getAsInt(), sourceSet);
            assertEquals(1, pack.getAsJsonArray("min_format").get(1).getAsInt(), sourceSet);
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
            JsonObject definition = json(assets.resolve("items/" + sand + "_slab.json"))
                    .getAsJsonObject("model");
            assertEquals("minecraft:model", definition.get("type").getAsString());
            assertEquals("skysterrainsmoother:item/" + sand + "_slab", definition.get("model").getAsString());
            JsonObject loot = json(root.resolve("data/skysterrainsmoother/loot_table/blocks/" + sand + "_slab.json"));
            assertEquals("skysterrainsmoother:" + sand + "_slab", loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void sandRecipesConserveVolumeAndKeepTheirPermanentIds() throws Exception {
        Path recipes = Paths.get("src/main/resources/data/skysterrainsmoother/recipe");
        try (java.util.stream.Stream<Path> files = Files.list(recipes)) {
            assertEquals(4, files.count());
        }
        for (String sand : new String[] { "sand", "red_sand" }) {
            JsonObject cut = json(recipes.resolve(sand + "_slab.json"));
            assertEquals("SSS", cut.getAsJsonArray("pattern").get(0).getAsString());
            assertEquals("minecraft:" + sand, cut.getAsJsonObject("key").get("S").getAsString());
            assertEquals("skysterrainsmoother:" + sand + "_slab", cut.getAsJsonObject("result").get("id").getAsString());
            assertEquals(6, cut.getAsJsonObject("result").get("count").getAsInt());
            JsonObject combine = json(recipes.resolve(sand + "_from_slabs.json"));
            assertEquals("SS", combine.getAsJsonArray("pattern").get(0).getAsString());
            assertEquals("skysterrainsmoother:" + sand + "_slab", combine.getAsJsonObject("key").get("S").getAsString());
            assertEquals("minecraft:" + sand, combine.getAsJsonObject("result").get("id").getAsString());
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
