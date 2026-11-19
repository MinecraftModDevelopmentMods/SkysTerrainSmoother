"""Exercise the workflow metadata reader with shared and separate JDKs."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest


class JavaSetupSelectorsTest(unittest.TestCase):
    def test_workflows_keep_the_installer_selector_and_separate_gradle_jdk(self):
        bash = Path(r"C:\Program Files\Git\bin\bash.exe") if os.name == "nt" else shutil.which("bash")
        for workflow in ["ci.yml", "codeql-analysis.yml"]:
            text = (Path(".github/workflows") / workflow).read_text()
            start = text.index('          value() { sed -n')
            end = text.index('          echo "artifact_prefix=', start)
            script = textwrap.dedent(text[start:end])
            cases = [
                ("25", "25.0.3+9", "25.0.3+9.0.LTS", "25", "25.0.3+9", "", "25.0.3+9.0.LTS"),
                ("8", "8.0.502+7", "8.0.502+7", "17", "17", "", "17"),
                ("8", "8.0.502+7", "8.0.502+7", "17", "17", "17.0.15+6", "17.0.15+6"),
            ]
            for java, toolchain, selector, gradle, gradle_toolchain, gradle_selector, expected in cases:
                with self.subTest(workflow=workflow, java=java, gradle_selector=gradle_selector):
                    with tempfile.TemporaryDirectory() as temporary:
                        root = Path(temporary)
                        metadata = dict(java_version=java, java_toolchain_version=toolchain,
                                        java_setup_version=selector, gradle_java_version=gradle,
                                        gradle_java_toolchain_version=gradle_toolchain,
                                        gradle_java_setup_version=gradle_selector)
                        (root / "gradle.properties").write_text("\n".join(key + "=" + value for key, value in metadata.items()) + "\n")
                        output = root / "output"
                        env = dict(os.environ, GITHUB_OUTPUT=output.as_posix())
                        subprocess.run([str(bash), "-c", script], cwd=root, env=env, check=True, capture_output=True)
                        values = dict(line.split("=", 1) for line in output.read_text().splitlines())
                        self.assertEqual(values["java_setup_version"], selector)
                        self.assertEqual(values["gradle_java_version"], expected)
