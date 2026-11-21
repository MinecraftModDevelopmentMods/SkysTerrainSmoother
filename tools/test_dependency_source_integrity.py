"""Run dependency build steps against small Git fixtures and check source integrity."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

PROJECT_ROOT = Path(__file__).resolve().parents[1]


def bash_executable():
    if os.name == 'nt':
        git = Path(shutil.which('git')).resolve()
        candidate = git.parent.parent / 'bin/bash.exe'
        if candidate.is_file():
            return str(candidate)
    return shutil.which('bash') or 'bash'


def shell_steps(relative):
    text = (PROJECT_ROOT / relative).read_text(encoding='utf-8')
    marker = '      - name: Build development dependencies\n'
    results = []
    for section in text.split(marker)[1:]:
        section = section.split('\n      - name:', 1)[0]
        body = section.split('        run: |\n', 1)[1]
        results.append('\n'.join(line[10:] if line.startswith('          ') else line
                                 for line in body.splitlines()) + '\n')
    return results


class DependencySourceIntegrityTest(unittest.TestCase):
    def run_step(self, relative, index, expected_steps):
        steps = shell_steps(relative)
        self.assertEqual(expected_steps, len(steps), relative)
        body = steps[index]
        names = re.findall(r'\(cd dependencies/([A-Za-z]+)\s', body)
        self.assertTrue(names, 'No content dependencies were built')
        with tempfile.TemporaryDirectory(prefix='sky-workflow-sources-') as temporary:
            workspace = Path(temporary)
            calls = workspace / 'wrapper-calls.txt'
            before = {}
            (workspace / 'gradle').mkdir()
            (workspace / 'gradle/content-dependencies.properties').write_text(
                ''.join(name + '.version=fixture\n' for name in ['grass', 'pieces', 'terrain', 'bopPieces']),
                encoding='utf-8')
            for name in names:
                root = workspace / 'dependencies' / name
                root.mkdir(parents=True)
                wrapper = root / 'gradlew'
                wrapper.write_text('#!/usr/bin/env bash\nset -euo pipefail\n'
                                   'printf "%s:%s\\n" "${PWD##*/}" "$*" >> "$WRAPPER_CALLS"\n',
                                   encoding='utf-8', newline='\n')
                wrapper.chmod(0o644 if name == 'grass' else 0o755)
                def git(*args):
                    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()
                git('init', '--quiet', '--initial-branch=fixture')
                git('config', 'user.name', 'Workflow fixture')
                git('config', 'user.email', 'fixture@example.invalid')
                git('config', 'core.autocrlf', 'false')
                git('config', 'commit.gpgsign', 'false')
                git('add', '-f', 'gradlew')
                git('commit', '--quiet', '-m', 'Add fixture wrapper')
                before[name] = (wrapper.read_bytes(), git('rev-parse', 'HEAD'))
            result = subprocess.run([bash_executable(), '-eu', '-c', body], cwd=workspace,
                                    env={**os.environ, 'WRAPPER_CALLS': str(calls), 'GITHUB_WORKSPACE': str(workspace)},
                                    text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual(names, [line.split(':', 1)[0] for line in calls.read_text().splitlines()])
            for name in names:
                root = workspace / 'dependencies' / name
                status = subprocess.check_output(['git', '-C', str(root), 'status', '--porcelain',
                                                   '--untracked-files=no'], text=True).strip()
                self.assertEqual('', status, f'{relative} step {index} changed {name}: {status}')
                self.assertEqual(before[name][0], (root / 'gradlew').read_bytes())
                self.assertEqual(before[name][1], subprocess.check_output(
                    ['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip())

    def test_cold_bootstrap_keeps_dependency_sources_clean(self):
        self.run_step('.github/workflows/ci.yml', 0, 2)

    def test_ci_keeps_dependency_sources_clean(self):
        self.run_step('.github/workflows/ci.yml', 1, 2)

    def test_codeql_keeps_dependency_sources_clean(self):
        self.run_step('.github/workflows/codeql-analysis.yml', 0, 1)

    def test_release_build_keeps_dependency_sources_clean(self):
        self.run_step('.github/workflows/deploy-release.yml', 0, 2)

    def test_maven_publication_keeps_dependency_sources_clean(self):
        self.run_step('.github/workflows/deploy-release.yml', 1, 2)


if __name__ == '__main__':
    unittest.main()
