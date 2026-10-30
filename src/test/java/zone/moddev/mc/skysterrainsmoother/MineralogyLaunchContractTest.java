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
        assertTrue(build.contains("tasks.register('runMineralogyClient', JavaExec)"));
        assertTrue(build.contains("tasks.register('runMineralogyServer', JavaExec)"));
        assertTrue(build.contains("configureVerification(delegate, 'runClient')"));
        assertTrue(build.contains("configureVerification(delegate, 'runServer')"));
        assertTrue(build.contains("run-mineralogy"));
        assertTrue(build.contains("ATTR_EXCLUDE_TEST_CODE"));
        assertTrue(build.contains("bin/test"));
        assertFalse(build.contains("implementation "));
        assertFalse(build.contains("runtimeOnly "));
        assertFalse(build.contains("into sourceSets.main"));
        assertTrue(text("build.gradle").contains(
                "contents.replace('${MC_VERSION}', project.minecraft_version.toString())"));
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
        assertTrue(script.contains("original optional jars remain untouched"));
        assertTrue(script.contains("minecraft.accessTransformers.from(accessRules)"));
        assertTrue(script.contains("getValue('FMLAT')"));
    }

    @Test
    void probesUseSeparateGeneratedWorldsAndRequireFreshAndReloadEvidence() throws Exception {
        String script = text("gradle/mineralogy-runs.gradle");
        assertTrue(script.contains("buildDirectory.dir('mineralogy-integration-run')"));
        assertTrue(script.contains("server-port=0"));
        assertTrue(script.contains("server-ip=127.0.0.1"));
        assertTrue(script.contains("level-type=BIOMESOP"));
        assertTrue(script.contains("fresh_complete"));
        assertTrue(script.contains("reload_complete"));
        assertTrue(script.contains("requireMineralogy"));
        assertTrue(script.contains("testMineralogyShapes"));
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
