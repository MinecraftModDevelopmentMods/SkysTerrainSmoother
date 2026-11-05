"""Recognise only checksum-bound, unused vanilla registry entries from upgrade fixtures."""

import hashlib
from collections import Counter
import json
from pathlib import Path
import re
from zipfile import ZipFile


EMPTY_CAPABILITIES = "Failed to parse saved data for 'SavedDataType[capabilities]': No key data in MapLike[{}]"


def profile_for(directory):
    manifest = Path(directory) / "source-inputs.json"
    if not manifest.is_file():
        return None
    source = json.loads(manifest.read_text(encoding="utf-8"))
    root = Path(__file__).resolve().parent.parent
    if source.get("mode") != "server":
        return None
    policy = json.loads((root / "tools/forge-61-registry-notices.json").read_text(encoding="utf-8"))
    profile = policy.get(source.get("world_sha256"))
    if profile is None:
        return None
    fixture = root / profile["fixture"]
    saved_manifest = fixture.parent / "source-family-fixture.json"
    provenance_path = fixture.parent / "provenance.json"
    if provenance_path.is_file():
        provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
        source_artifacts = provenance["source_artifacts"]
    else:
        source_artifacts = json.loads((fixture.parent / "source-artifacts.json").read_text(encoding="utf-8"))
    if (hashlib.sha256(fixture.read_bytes()).hexdigest().upper() != profile["sha256"]
            or hashlib.sha256(saved_manifest.read_bytes()).hexdigest().upper() != profile["manifest_sha256"]
            or source.get("manifest_sha256") != profile["manifest_sha256"]
            or source_artifacts != profile["source_artifacts"]
            or profile.get("non_biome_saved_uses", profile["saved_uses"]) != 0 or profile["inspected_nbt_records"] <= 0):
        raise ValueError("The unused-entry proof does not match the upgrade fixture")
    proof = profile.get("native_biome_contract")
    if proof is not None:
        contract = root / proof["path"]
        data = json.loads(contract.read_text(encoding="utf-8"))
        if (hashlib.sha256(contract.read_bytes()).hexdigest().upper() != proof["sha256"]
                or data["source_world_sha256"] != profile["sha256"]
                or data["checked_cells"] != proof["checked_cells"]
                or data["native_renamed_cells"] != proof["renamed_cells"]
                or profile["saved_uses"] != proof["renamed_cells"]):
            raise ValueError("The complete biome proof does not match this fixture")
    if profile["target"] != "Forge 61.1.0":
        raise ValueError("The registry notice proof is for another Forge version")
    # A spelling can occur in more than one registry. The save census must
    # distinguish those uses rather than treating a water block as a painting.
    known_uses = {
        ("minecraft:painting_variant", "minecraft:water"): "block_or_fluid_not_painting",
        ("minecraft:painting_variant", "minecraft:skeleton"): "spawner_entity_not_painting",
        ("minecraft:painting_variant", "minecraft:stage"): "block_property_not_painting",
    }
    for name, typed in profile["typed_uses"].items():
        kind = typed["kind"]
        if kind == "vanilla_biome_conversion" and name in profile["mappings"].get("minecraft:worldgen/biome", {}):
            if proof and typed["cells"] == proof["checked_cells"] and typed["renamed"] == proof["renamed_cells"]:
                continue
        elif (kind == "vanilla_block_rename" and name == "minecraft:grass"
                and name in profile["mappings"].get("minecraft:block", {}) and typed["positions"] > 0):
            continue
        elif any(kind == expected and name == entry and entry in profile["mappings"].get(registry, {})
                 for (registry, entry), expected in known_uses.items()):
            if typed["before"] >= 0 and typed["after"] >= 0 and typed["before"] + typed["after"] > 0:
                continue
        raise ValueError("Unverified saved use of an unidentified registry entry: " + name)
    empty = profile.get("empty_capabilities", {})
    if empty:
        originals, rewritten = empty["original"], empty["rewritten"]
        allowed = {"data/capabilities.dat", "DIM-1/data/capabilities.dat", "DIM1/data/capabilities.dat"}
        if not originals or originals.keys() != rewritten.keys() or not originals.keys() <= allowed:
            raise ValueError("Unexpected empty capability proof paths")
        with ZipFile(fixture) as archive:
            for name, expected in originals.items():
                archived = hashlib.sha256(archive.read("world/" + name)).hexdigest().upper()
                saved = (Path(directory) / "world" / name)
                if (not saved.is_file() or archived != expected
                        or hashlib.sha256(saved.read_bytes()).hexdigest().upper() != rewritten[name]):
                    raise ValueError("Forge did not rewrite the proven empty capability record: " + name)
        profile["empty_capabilities_count"] = len(originals)
    return profile


def audit_text(text, profile=None):
    text = text.replace("\r\n", "\n")
    observed = {}
    occurrences = Counter()
    expected_reads = profile.get("notice_reads", 1) if profile else 1
    registry_reads = profile.get("registry_reads", {}) if profile else {}
    expected_thread = profile.get("notice_thread", "main") if profile else "main"
    pattern = r"Unidentified mapping from registry (minecraft:[a-z_/]+)\n(.*?)(?:\n\n)"
    for registry, body in re.findall(pattern, text, re.S):
        entries = {}
        for row in body.splitlines():
            if not row.strip():
                continue
            entry = re.fullmatch(r"\s*(minecraft:[a-z0-9_./-]+): (\d+)", row)
            if not entry or entry[1] in entries:
                raise ValueError("Malformed or repeated registry entry: " + row)
            entries[entry[1]] = int(entry[2])
        occurrences[registry] += 1
        if occurrences[registry] > registry_reads.get(registry, expected_reads):
            raise ValueError("Repeated native registry notice: " + registry)
        if registry in observed and entries != observed[registry]:
            raise ValueError("Changed native registry entries between reads: " + registry)
        observed[registry] = entries
    if observed and (profile is None or observed != profile["mappings"]):
        raise ValueError("Unexpected missing registry entries: " + repr(observed))
    if observed and any(count != registry_reads.get(registry, expected_reads)
                        for registry, count in occurrences.items()):
        raise ValueError("The native registry read sequence is incomplete")
    headers = {"Unidentified mapping from registry " + registry for registry in observed}
    if observed:
        headers.add("There are unidentified mappings in this world - we are going to attempt to process anyway")
    header_reads = {"Unidentified mapping from registry " + registry:
                    registry_reads.get(registry, expected_reads) for registry in observed}
    seen = Counter()
    capabilities = 0
    for line in text.splitlines():
        if not re.search(r"/(?:ERROR|FATAL)\]|\[STDERR[/\]]", line):
            continue
        message = re.fullmatch(r"\[[^\r\n]+\] \[" + re.escape(expected_thread) + r"/ERROR\]"
                               r"(?: \[net\.minecraftforge\.registries\.GameData/REGISTRIES\])?: (.+)", line)
        if message and message[1] in headers and seen[message[1]] < header_reads.get(message[1], expected_reads):
            seen[message[1]] += 1
        elif (profile and profile.get("empty_capabilities_count", 0)
                and re.fullmatch(r"\[[^\r\n]+\] \[Server thread/ERROR\]"
                                 r"(?: \[net\.minecraft\.world\.level\.storage\.DimensionDataStorage/\])?: "
                                 + re.escape(EMPTY_CAPABILITIES), line)):
            capabilities += 1
        else:
            raise ValueError("Unexpected native failure: " + line)
    if capabilities and capabilities != profile["empty_capabilities_count"]:
        raise ValueError("Unexpected number of empty Forge capability records")
    if set(seen) != headers or any(count != header_reads.get(header, expected_reads)
                                   for header, count in seen.items()):
        raise ValueError("The native registry notice is incomplete")
    for problem in ("Encountered an unexpected exception", "Exception stopping the server",
                    "MissingModsException", "NoSuchMethodError", "NoClassDefFoundError",
                    "ExceptionInInitializerError", "cascading worldgen lag", "Unable to load model",
                    "Unable to load blockstate", "outside of the generating region",
                    "java.lang.AssertionError", "java.lang.IllegalStateException:"):
        if problem in text:
            raise ValueError("Unexpected native failure: " + problem)
    return sorted(headers) + [EMPTY_CAPABILITIES] * capabilities
