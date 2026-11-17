"""Check that NeoGradle cleaning and model tasks use separate invocations."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest


class WorkflowCommandsTest(unittest.TestCase):
    def test_ci_build_cleans_before_running_model_tasks(self):
        workflow = Path(".github/workflows/ci.yml").read_text()
        step = workflow.split("- name: Build, test, and audit release artifacts", 1)[1]
        match = re.search(r"\n        run: ([|>])-?\n((?:          [^\n]*\n|\n)+)", step)
        self.assertIsNotNone(match, "The build step must contain a shell script")
        script = textwrap.dedent(match.group(2)).strip()
        if match.group(1) == ">":
            script = " ".join(script.splitlines())

        bash = Path(r"C:\Program Files\Git\bin\bash.exe") if os.name == "nt" else shutil.which("bash")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            wrapper = root / "gradlew"
            wrapper.write_text('#!/bin/bash\nprintf "%s\\n" "$*" >> calls\n')
            wrapper.chmod(0o755)
            subprocess.run([str(bash), "-e", "-c", script], cwd=root, check=True, capture_output=True)
            calls = (root / "calls").read_text().splitlines()
        self.assertEqual(len(calls), 2, "Clean must finish before NeoGradle configures the build")
        self.assertEqual(calls[0].split()[0], "clean")
        self.assertIn("check", calls[1].split())
        self.assertIn("verifyEclipseProductionClasspath", calls[1].split())
        self.assertIn("-Declipse.application=org.eclipse.ui.ide.workbench", calls[1].split())
        self.assertNotIn("./gradlew", " ".join(calls))
