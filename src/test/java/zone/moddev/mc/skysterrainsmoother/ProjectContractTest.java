package zone.moddev.mc.skysterrainsmoother;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectContractTest {
    @Test
    void onlySandPaletteIsNewAndMigrationStateAndToolchainArePreserved() throws Exception {
        String properties = text("gradle.properties");
        String build = text("build.gradle");

        assertTrue(properties.contains("mod_version=0.1.0.112021"));
        assertTrue(properties.contains("mapping_version=39-1.12"));
        assertTrue(build.contains("version '7.0.34'"));
        assertTrue(build.contains("verifyEclipseProductionClasspath"));

        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get("src/main/java"))) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

                    assertFalse(source.contains("extends WorldSavedData"));

                    if (!path.toString().contains("content")) {
                        assertFalse(source.contains("setRegistryName("));
                    }
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            });
        }

        String api = source("src/main/java/zone/moddev/mc/skysterrainsmoother/api/TerrainSmoothingApi.java");

        assertFalse(api.contains("publicstaticMaterialCatalogue"));
        assertTrue(api.contains("INITIALIZATION"));
    }

    @Test
    void generationAndResourceContracts() throws Exception {
        String engine = source("src/main/java/zone/moddev/mc/skysterrainsmoother/internal/SmoothingEngine.java");

        assertTrue(engine.contains("priority=EventPriority.LOWEST"));
        assertTrue(engine.contains("existing.contains"));
        assertTrue(engine.contains("getLoadedChunk"));
        assertFalse(engine.contains("provideChunk"));
        assertTrue(engine.contains("newint[256]"));
        assertTrue(engine.contains("newIBlockState[256]"));
        assertTrue(text("src/main/resources/pack.mcmeta").contains("\"pack_format\":3"));

        for (String workflow : new String[] { "ci.yml", "codeql-analysis.yml", "validate-gradle-build.yml",
                "release-on-tag.yml" }) {
            assertTrue(Files.isRegularFile(Paths.get(".github/workflows", workflow)));
        }

        assertTrue(text(".github/workflows/ci.yml").contains("if-no-files-found: error"));
    }

    /** Compare Java structure without tying these contracts to spacing or line breaks. */
    private static String source(String path) throws Exception {
        return text(path).replaceAll("\\s+", "");
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    @Test
    void caveFloorsRetainTwoPassLoadedOnlyAndOreSupportContracts() throws Exception {
        String engine = source("src/main/java/zone/moddev/mc/skysterrainsmoother/internal/SmoothingEngine.java");

        assertTrue(engine.contains("for(intfloorY=Math.min(surfaceY,254);floorY>=0;--floorY)"));
        assertTrue(engine.contains("height=2;height<=3"));
        assertTrue(engine.contains("Arrays.copyOf(positions,capacity)"));
        assertTrue(engine.indexOf("buffer.add(") < engine
                .indexOf("setGenerationState(owner,target,buffer.results[index])"));
        assertTrue(engine.contains("pos.getX()>>4!=owner.x"));
        assertTrue(engine.contains("pos.getZ()>>4!=owner.z"));
        assertTrue(engine.contains("section.set(pos.getX()&15,pos.getY()&15,pos.getZ()&15,state)"));
        assertFalse(engine.contains("owner.setBlockState("));
        assertTrue(engine.contains("PopulateChunkEvent.Post"));
        assertTrue(engine.contains("TickEvent.WorldTickEvent"));
        assertTrue(engine.contains("!oreSupport(support)"));
        assertTrue(engine.contains("OreDictionary.getOreIDs"));
        assertFalse(engine.contains("getChunkFromChunkCoords"));
        assertTrue(text("README.md").contains("including cave floors"));
        assertTrue(text("docs/CONFIGURATION.md").contains("no retrogen"));
    }

    @Test
    void sandResourcesAndGravityContract() throws Exception {
        String block = text("src/main/java/zone/moddev/mc/skysterrainsmoother/content/SandSlabBlock.java");

        assertTrue(block.contains("extends BlockFalling"));
        assertTrue(block.contains("EntityFallingBlock"));
        assertTrue(block.contains("BlockSand.VARIANT"));
        assertTrue(block.contains("BlockSlab.HALF"));
        assertFalse(block.contains("VERTICAL"));
        assertFalse(block.contains("TileEntity"));

        Path root = Paths.get("src/main/resources/assets/skysterrainsmoother");

        assertTrue(Files.isRegularFile(root.resolve("blockstates/sand_slab.json")));

        for (String type : new String[] { "sand", "red_sand" }) {
            for (String half : new String[] { "top", "bottom" }) {
                assertTrue(Files.isRegularFile(
                        root.resolve("models/block/" + type + "_slab" + (half.equals("top") ? "_top" : "") + ".json")));
            }
        }
        try (java.util.stream.Stream<Path> paths = Files.list(root.resolve("lang"))) {
            List<Path> languages = paths.collect(java.util.stream.Collectors.toList());

            assertEquals(18, languages.size());

            for (Path language : languages) {
                String content = new String(Files.readAllBytes(language), StandardCharsets.UTF_8);

                assertTrue(content.endsWith("\n"));
                assertFalse(content.contains("\r"));
                assertFalse(content.contains("\uFEFF"));
                assertEquals(2, content.split("\n").length);
            }
        }
    }

    @Test
    void clayAndLateOasesReusePiecesWithoutRetrogen() throws Exception {
        String materials = source("src/main/java/zone/moddev/mc/skysterrainsmoother/internal/BuiltInMaterials.java");

        assertTrue(materials.contains("vanilla(\"hardened_clay\",Blocks.HARDENED_CLAY.getDefaultState(),false,null)"));

        String engine = source("src/main/java/zone/moddev/mc/skysterrainsmoother/internal/SmoothingEngine.java");

        assertTrue(engine.contains("if(afterDecoration&&!pending.containsKey(key)){return0;}"));
        assertFalse(engine.contains("sandOnly"));
        assertTrue(engine.contains("if(afterDecoration){pending.get(key).addAll(placements);}"));
        assertTrue(text("README.md").contains("hardened clay"));
    }
}
