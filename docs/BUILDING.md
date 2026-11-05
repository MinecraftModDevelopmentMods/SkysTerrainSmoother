# Building from source

This branch uses NeoForge 21.1.247, NeoGradle 7.1.38 and the Gradle 9.2.1
wrapper. Run Gradle with Temurin 21.0.7+6. The same Java version compiles and
runs the mod. The log checks use Python 3.11 or newer.

Build the Grass Slabs and Building Pieces commits listed in
`gradle/content-dependencies.properties` with their `assemble` tasks first.
Both development and packaged games use the same jars. The build checks the
source commits and jar hashes. It does not include the dependency classes
or assets in Terrain Smoother.

The default layout uses sibling checkouts. For a different layout, pass
`-PgrassSlabsDirectory=<checkout>` and `-PbuildingPiecesDirectory=<checkout>`.
When testing another local Building Pieces candidate, supply its directory
and `-PbuildingPiecesCoreVersion=<version>`. CI always uses the lock.

```text
gradlew check build javadoc verifyReleaseArtifacts writeReleaseChecksums
gradlew verifyEclipseProductionClasspath
```

If Python isn't on your path, pass `-PpythonExecutable=<python>` to the first
command. The Eclipse task prepares the processed resources and the content
jars without clearing your game settings or saves. Import the Gradle project
with Java 21 and refresh it after generating the launches. The regular
client and server launches exclude all test code.

## Trying Mineralogy

Use `runMineralogyClient` or `runMineralogyServer` for a separate development
game in `run-mineralogy`. These runs use the NeoForge jars pinned in
`gradle/mineralogy-runtime.properties`. Point Gradle to them with
`-PmineralogyRuntimeJar=<jar>` and `-PorespawnRuntimeJar=<jar>` if you keep them
elsewhere. Eclipse also generates Mineralogy client and server launches.

With Mineralogy installed and its slabs enabled, natural rock has steps and
corners from Building Pieces at smoothing levels 2 and 3. New configurations
start at level 3; existing choices are kept.

## World checks

`preparePackagedIntegrationProbe` builds separate test mods for disposable
games. The runtime tools in `tools/` check fresh worlds and reloads, including
recipes, configuration, slopes and cave floors. For packaged upgrades, pass a
sealed fixture directory to `run_packaged_checks.py`. Always use a new
directory under `build/` for the first load. The tool retains the logs and
checks that the source archive stays unchanged.

The recipe unlocks are generated from `src/main/recipe-book/recipes.json`.
The four sand recipes keep their IDs. The runtime checks exercise crafting,
inventory discovery and recipe-book persistence.

See [the release guide](RELEASING.md) for release preparation.
