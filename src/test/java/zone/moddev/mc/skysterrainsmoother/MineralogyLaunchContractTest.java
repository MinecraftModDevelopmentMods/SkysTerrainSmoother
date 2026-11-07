package zone.moddev.mc.skysterrainsmoother;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MineralogyLaunchContractTest {
    @Test
    void optionalProfilesReuseTheNormalLauncherWithoutAddingRuntimeDependencies() throws Exception {
        String build = text("gradle/mineralogy-runs.gradle");
        assertTrue(build.contains("mineralogyClient {"));
        assertTrue(build.contains("mineralogyServer {"));
        assertTrue(build.contains("runType 'client'"));
        assertTrue(build.contains("runType 'server'"));
        assertTrue(build.contains("run-mineralogy"));
        assertTrue(text("build.gradle").contains("ATTR_EXCLUDE_TEST_CODE"));
        assertTrue(text("build.gradle").contains("bin/test"));
        assertFalse(build.contains("implementation "));
        assertFalse(build.contains("runtimeOnly "));
        assertFalse(build.contains("into sourceSets.main"));
        assertTrue(text("build.gradle").contains("net.neoforged.gradle.userdev"));
    }

    @Test
    void sourceJarsAreChecksumLockedAndPreparationPreservesPlayerData() throws Exception {
        Properties lock = new Properties();
        lock.load(new java.io.StringReader(text("gradle/mineralogy-runtime.properties")));
        for (String mod : new String[] { "mineralogy", "orespawn" }) {
            assertTrue(lock.getProperty(mod + ".sha256").matches("[A-F0-9]{64}"));
            assertFalse(Paths.get(lock.getProperty(mod + ".path")).isAbsolute());
        }
        String script = text("gradle/mineralogy-runs.gradle");
        assertTrue(script.contains(".gradle/qualification-dependencies/"));
        assertTrue(script.contains("retainedJar.isFile() ? retainedJar : siblingJar"));
        assertTrue(script.contains("onlyIf"));
        assertTrue(script.contains("runtimeSources.values().every { it.isFile() }"));
        assertTrue(script.contains("Missing optional"));
        assertTrue(script.contains("does not match gradle/mineralogy-runtime.properties"));
        assertFalse(script.contains("delete(gameDirectory"));
        assertTrue(script.contains("project.stageTerrainContentJars(gameDirectory.asFile)"));
    }

    @Test
    void probesUseSeparateGeneratedWorldsAndRequireFreshAndReloadEvidence() throws Exception {
        String script = text("tools/run_packaged_checks.py");
        assertTrue(script.contains("Fresh checks require a new disposable directory"));
        assertTrue(script.contains("server-port=0"));
        assertTrue(script.contains("server-ip=127.0.0.1"));
        assertTrue(script.contains("Reload inputs differ from the fresh run"));
        assertTrue(script.contains("requireMineralogy"));
        assertTrue(script.contains("TERRAIN_SMOOTHER_RUNTIME_PASS"));
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
