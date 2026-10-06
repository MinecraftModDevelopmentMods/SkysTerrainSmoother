package zone.moddev.mc.skysterrainsmoother;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProjectContractTest {
    @Test void projectRegistersNoContentOrMigrationStateAndPreservesToolchain() throws Exception {
        String properties=text("gradle.properties"),build=text("build.gradle");
        assertTrue(properties.contains("mod_version=0.1.0.110021"));assertTrue(properties.contains("mapping_version=29-1.10.2"));
        assertTrue(build.contains("version '7.0.34'"));assertTrue(build.contains("verifyEclipseProductionClasspath"));
        try(java.util.stream.Stream<Path> paths=Files.walk(Paths.get("src/main/java"))) {
            paths.filter(Files::isRegularFile).forEach(path->{try{
                String source=new String(Files.readAllBytes(path),StandardCharsets.UTF_8);
                assertFalse(source.contains("GameRegistry.register"));assertFalse(source.contains("extends WorldSavedData"));
                assertFalse(source.contains("setRegistryName("));
            }catch(Exception failure){throw new AssertionError(failure);}});
        }
        String api=text("src/main/java/zone/moddev/mc/skysterrainsmoother/api/TerrainSmoothingApi.java");
        assertFalse(api.contains("public static MaterialCatalogue"));assertTrue(api.contains("INITIALIZATION"));
    }
    @Test void generationAndResourceContracts() throws Exception {
        String engine=text("src/main/java/zone/moddev/mc/skysterrainsmoother/internal/SmoothingEngine.java");
        assertTrue(engine.contains("priority=EventPriority.LOWEST"));assertTrue(engine.contains("existing.contains"));
        assertTrue(engine.contains("getLoadedChunk"));assertFalse(engine.contains("provideChunk"));
        assertTrue(engine.contains("new int[256]"));assertTrue(engine.contains("new IBlockState[256]"));
        assertTrue(text("src/main/resources/pack.mcmeta").contains("\"pack_format\":2"));
        for(String workflow:new String[]{"ci.yml","codeql-analysis.yml","validate-gradle-build.yml","release-on-tag.yml"})assertTrue(Files.isRegularFile(Paths.get(".github/workflows",workflow)));
        assertTrue(text(".github/workflows/ci.yml").contains("if-no-files-found: error"));
    }
    private static String text(String path)throws Exception{return new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);}
}
