"""Negative controls for Forge 40 server completion and native settings."""

from pathlib import Path
import shutil
import tempfile
import unittest

from native_client_log import audit_server, classify_server


NOTICE = ('[Thread-1/FATAL] [net.minecraftforge.common.ForgeConfig/CORE]: '
          'Forge config just got changed on the file system!')
DEBUG = ('[Thread-1/DEBUG] [net.minecraftforge.fml.config.ConfigFileTypeHandler/CONFIG]: '
         'Config file forge-server.toml changed, sending notifies')
START = '[Server thread/INFO] [MinecraftServer/]: Preparing level "world"'
LOG = '\n'.join((START, 'Done (1.0s)!'))
DETAILS = '\n'.join((DEBUG, START, 'Done (1.0s)!'))


class NativeServerLogTest(unittest.TestCase):
    def test_clean_native_startup_is_accepted(self):
        receipt = classify_server(LOG, DETAILS, True)
        self.assertEqual(receipt['native_server_config_notices'], 0)
        self.assertEqual(receipt['notices'], [])

    def test_config_change_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(LOG, DETAILS, False)

    def test_late_notice_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server('\n'.join((START, NOTICE)), DETAILS, True)

    def test_repeated_notices_are_rejected(self):
        with self.assertRaises(ValueError):
            classify_server('\n'.join((NOTICE, NOTICE, START)), DETAILS, True)

    def test_previous_target_notices_are_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(NOTICE + '\n' + LOG, DETAILS, True)

    def test_missing_debug_evidence_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(LOG, '', True)

    def test_unrelated_error_and_stderr_are_rejected(self):
        for extra in ('[Server thread/ERROR] [AnotherMod/]: Failed',
                      '[STDERR/]: java.lang.AssertionError'):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                classify_server(LOG + '\n' + extra, DETAILS, True)

    def test_wrong_logger_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(NOTICE.replace('ForgeConfig/CORE', 'OtherConfig/CORE') + '\n' + LOG, DETAILS, True)


class NativeRegistryNoticeTest(unittest.TestCase):
    def setUp(self):
        import json
        from native_registry_notices import audit_text
        root = Path(__file__).resolve().parent.parent
        self.profile = json.loads((root / "tools/native-log-profiles.json").read_text(encoding="utf-8"))["profiles"][
            "B812D07B20C34EE058D155EC8592FFE8AB36DB386FF04A3687BBE9419A81BA5F"]
        self.check = audit_text
        self.entity_notice = "minecraft:zombie_pigman: " + str(
            self.profile["mappings"]["minecraft:entity_type"]["minecraft:zombie_pigman"])
        prefix = "[time] [main/ERROR] [net.minecraftforge.registries.GameData/REGISTRIES]: "
        self.valid = "".join(prefix + "Unidentified mapping from registry " + registry + "\n" +
                             "\n".join("\t" + name + ": " + str(number) for name, number in entries.items()) + "\n\n"
                             for registry, entries in self.profile["mappings"].items())
        self.valid += prefix + "There are unidentified mappings in this world - we are going to attempt to process anyway\n"

    def test_exact_native_notices_and_a_clean_reload_pass(self):
        self.assertEqual(len(self.check(self.valid, self.profile)), 6)
        self.assertEqual(self.check("A clean native reload", None), [])

    def test_target_client_requires_one_exact_snapshot_read(self):
        profile = dict(self.profile, notice_thread="Render thread", notice_reads=1)
        one_read = self.valid.replace("[main/ERROR]", "[Render thread/ERROR]")
        self.assertEqual(len(self.check(one_read, profile)), 6)
        for text in (one_read * 2, one_read * 3, self.valid,
                     one_read.replace(self.entity_notice, self.entity_notice + "0")):
            with self.subTest(text=text[:80]), self.assertRaises(ValueError):
                self.check(text, profile)

    def test_every_notice_boundary_is_enforced(self):
        candidates = [
            (self.valid, None),
            (self.valid.replace(self.entity_notice, self.entity_notice + "0"), self.profile),
            (self.valid.replace(self.entity_notice, "minecraft:unknown: 57"), self.profile),
            (self.valid.replace(self.entity_notice, self.entity_notice + "\n\tminecraft:unknown: 58"), self.profile),
            (self.valid.replace("[main/ERROR]", "[Server thread/ERROR]"), self.profile),
            (self.valid.replace("GameData/REGISTRIES", "Other/REGISTRIES"), self.profile),
            (self.valid.replace("/ERROR]", "/FATAL]"), self.profile),
            (self.valid.replace(self.entity_notice, "malformed entry"), self.profile),
            (self.valid + self.valid, self.profile),
            (self.valid + "[time] [main/ERROR] [Other/]: Failed", self.profile),
            (self.valid + "[STDERR/]: hidden stack trace", self.profile),
            (self.valid + "Unable to load model", self.profile),
            (self.valid + "cascading worldgen lag", self.profile),
            (self.valid.replace("There are unidentified mappings in this world - we are going to attempt to process anyway", "MissingModsException"), self.profile),
        ]
        for index, (text, profile) in enumerate(candidates):
            if profile is not None:
                self.assertNotEqual(self.valid, text, "The negative control must alter the native notice")
            with self.subTest(boundary=index), self.assertRaises(ValueError):
                self.check(text, profile)


class NativeServerFilesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.config = self.directory / 'world/serverconfig/forge-server.toml'
        self.config.parent.mkdir(parents=True)
        defaults = Path(__file__).resolve().parent.parent / 'src/bootstrapTest/defaultconfigs'
        shutil.copyfile(defaults / 'defaultconfigs/forge-server.toml', self.config)
        logs = self.directory / 'logs'
        logs.mkdir()
        (logs / 'latest.log').write_text(LOG, encoding='utf-8')
        (logs / 'debug.log').write_text(DETAILS, encoding='utf-8')

    def test_unchanged_defaults_pass(self):
        self.assertEqual(audit_server(self.directory, 'test')['native_server_config_notices'], 0)

    def test_changed_defaults_fail(self):
        original = self.config.read_text(encoding='utf-8')
        changed = original.replace('fullBoundingBoxLadders = false', 'fullBoundingBoxLadders = true')
        self.assertNotEqual(original, changed)
        self.config.write_text(changed, encoding='utf-8')
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')

    def test_missing_defaults_fail(self):
        self.config.unlink()
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')

    def test_rolled_error_cannot_be_hidden_by_a_clean_latest_log(self):
        import gzip
        with gzip.open(self.directory / "logs/previous.log.gz", "wt", encoding="utf-8") as output:
            output.write("[Server thread/ERROR] [other/]: A real failure\n")
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')

    def test_empty_complete_logs_do_not_count_as_evidence(self):
        from native_client_log import audit_complete_logs
        import tempfile
        with tempfile.TemporaryDirectory() as empty:
            with self.assertRaises(ValueError):
                audit_complete_logs(Path(empty))

    def test_crash_report_fails(self):
        crashes = self.directory / 'crash-reports'
        crashes.mkdir()
        (crashes / 'failure.txt').write_text('Simulated failure')
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')

    def test_forge_61_notices_are_bound_to_the_exact_server_fixture(self):
        import json
        from native_registry_notices import audit_text, profile_for

        root = Path(__file__).resolve().parent.parent
        policy = json.loads((root / 'tools/forge-61-registry-notices.json').read_text(encoding='utf-8'))
        profile = next(value for value in policy.values() if 'forward-1.20.6-family/' in value['fixture'])
        inputs = self.directory / 'source-inputs.json'
        source = {'mode': 'server', 'world_sha256': profile['sha256'],
                  'manifest_sha256': profile['manifest_sha256']}
        inputs.write_text(json.dumps(source), encoding='utf-8')
        capability = self.directory / 'world/data/capabilities.dat'
        capability.parent.mkdir(parents=True, exist_ok=True)
        # Bytes written by the bare Forge 61 control from the same empty saved data.
        rewritten = bytes.fromhex(
            '1f8b08000000000000ffe36260e0626049492c4984520c0ccc0cdc2e4046586a5171667e1e0383903d03003fed72de26000000')
        capability.write_bytes(rewritten)
        resolved = profile_for(self.directory)
        self.assertEqual(dict(profile, empty_capabilities_count=1), resolved)
        capability.write_bytes(rewritten + b'bad')
        with self.assertRaises(ValueError):
            profile_for(self.directory)
        capability.write_bytes(rewritten)

        prefix = '[time] [main/ERROR] [net.minecraftforge.registries.GameData/REGISTRIES]: '
        notices = ''.join(prefix + 'Unidentified mapping from registry ' + registry + '\n' +
                          '\n'.join(f'\t{name}: {ordinal}' for name, ordinal in names.items()) + '\n\n'
                          for registry, names in profile['mappings'].items())
        notices += prefix + 'There are unidentified mappings in this world - we are going to attempt to process anyway'
        self.assertIn('minecraft:aztec2: 3', notices)
        self.assertEqual(len(audit_text(notices, resolved)), len(profile['mappings']) + 1)
        from native_registry_notices import EMPTY_CAPABILITIES
        empty = '\n[time] [Server thread/ERROR]: ' + EMPTY_CAPABILITIES
        self.assertIn(EMPTY_CAPABILITIES, audit_text(notices + empty, resolved))
        with self.assertRaises(ValueError):
            audit_text(notices + empty + empty, resolved)
        with self.assertRaises(ValueError):
            audit_text(empty, None)
        with self.assertRaises(ValueError):
            audit_text(notices.replace('minecraft:aztec2: 3', 'minecraft:aztec2: 4'), resolved)

        from zipfile import ZipFile
        with ZipFile(root / profile['fixture']) as archive:
            capability.write_bytes(archive.read('world/data/capabilities.dat'))
        current = profile_for(self.directory, forge64=True)
        self.assertIsNone(current)
        for old_error in (empty, notices):
            with self.assertRaises(ValueError):
                audit_text(old_error, current)
        capability.write_bytes(rewritten)
        with self.assertRaises(ValueError):
            profile_for(self.directory, forge64=True)

        inputs.write_text(json.dumps(dict(source, mode='client')), encoding='utf-8')
        self.assertIsNone(profile_for(self.directory))
        inputs.write_text(json.dumps(dict(source, manifest_sha256='0' * 64)), encoding='utf-8')
        with self.assertRaises(ValueError):
            profile_for(self.directory)


if __name__ == '__main__':
    unittest.main()
