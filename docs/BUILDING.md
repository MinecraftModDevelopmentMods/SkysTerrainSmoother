# Building from source

Use the Gradle 9.6.1 wrapper with Java 17 and Temurin 8.0.502+7 for production.
Set `STS_JAVA8_HOME` to the Java 8 installation. This branch targets
Minecraft 1.10.2 with ForgeGradle 7.0.34.

Build the exact Grass Slabs and Building Pieces source commits listed in
`gradle/content-dependencies.properties` with their `jar` tasks first.

The default layout uses sibling checkouts. For another layout, pass
`-PgrassSlabsDirectory=<checkout>` and
`-PbuildingPiecesDirectory=<checkout>`.
Then run:

```text
gradlew check build javadoc verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

Development dependencies use deobfuscated jars. Packaged games use the normal
main jars; dependency classes and assets are not bundled. CI builds the exact
pinned commits rather than moving branches.

A local-only `buildingPiecesCoreVersion` override can test another core
candidate. It requires an explicit core directory and is rejected in CI.
For example, to use the local 0.3.7 candidate:

```text
gradlew genEclipseRuns eclipse verifyEclipseProductionClasspath "-PbuildingPiecesDirectory=../../SkysBuildingPieces110/SkysBuildingPieces" "-PbuildingPiecesCoreVersion=0.3.7.110021"
```

This saves the selected directory and version in Eclipse's ignored Buildship
preferences, so Gradle refreshes and launches use the same dependency. Refresh
the Gradle project in Eclipse afterwards. It does not change the pinned CI
commits. Terminal builds still need the same two properties.

Use `genEclipseRuns eclipse` to prepare the Java 8 IDE setup. Ordinary launches
exclude unit tests and build-only runtime probes. Publication uses the guarded MMD release workflow; ordinary builds do not publish.

## Testing with Mineralogy

Run `gradlew genEclipseRuns eclipse`, then refresh the Eclipse project. Select
`runMineralogyClient` under Java Application in Run Configurations. The matching
`runMineralogyServer` profile is available for a dedicated server. From a terminal,
use `gradlew runMineralogyClient` or `gradlew runMineralogyServer`.

These profiles use a separate `run-mineralogy` folder and load Mineralogy plus
its OreSpawn dependency. The BOP project also loads its usual Biomes O Plenty jar.
Ordinary client and server profiles keep their existing mod sets and worlds.
Runtime probes and unit tests are excluded from these manual profiles.

The expected release jars and their checksums are listed in
`gradle/mineralogy-runtime.properties`. The default paths use sibling checkouts;
for a different layout, pass `-PmineralogyRuntimeJar=<jar>` and
`-PorespawnRuntimeJar=<jar>` when generating the profiles. Missing optional jars
do not prevent an ordinary build; `prepareMineralogyDevelopmentRun` explains
what is needed before the combined profile can run. The jars are copied, not
modified. Their access rules are translated for the mapped development game.

If a sibling build has moved on, keep the qualified jars under
`.gradle/qualification-dependencies/` with their original filenames.
The profiles prefer those copies, but still require the locked checksums.
An explicit runtime-jar property takes precedence over either location.

New configurations use smoothing level 3. An existing saved level is kept, so
choose level 3 in Mods > Config and restart if that test folder already has a
different setting. With Building Pieces 0.3.7, Mineralogy rocks use the core's
steps at level 2 and corners at level 3, without a separate add-on.

`gradlew integrationMineralogyTest` checks fresh-world and reload behaviour in
a disposable build folder. `integrationMineralogyClientProcess` checks client
resources there. Neither task opens the manual profile's saved worlds.

For an unreleased Grass Slabs candidate, pass both `-PgrassSlabsDirectory=<checkout>` and `-PgrassSlabsCoreVersion=<version>`, alongside the existing Building Pieces override. These explicit choices are retained by Eclipse and rejected in CI.
