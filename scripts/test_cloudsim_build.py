"""Verified-build cache checks use temporary inputs and controlled child contracts."""
import hashlib
import fcntl
import importlib
import importlib.metadata
import json
import os
from pathlib import Path
import sys
import shutil
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

try:
    import cloudsim_build as build
except ModuleNotFoundError:
    build = None


class FixtureControl:
    def __init__(self, root):
        self.root = root
        self.env = {**os.environ, 'MAVEN_USER_HOME': str(root/'.cloudsim/maven')}
        self.toolchain = {
            'java': {'path': str(root/'jdk/bin/java'), 'version': 'openjdk version "21.0.1"'},
            'javac': {'path': str(root/'jdk/bin/javac'), 'version': 'javac 21.0.1'},
            'release_sha256': 'fixture-jdk-release',
        }
        self.commands = []
        self.frames = []
        self.polls = 0
        self.failure = None
        self.on_run = None

    def render(self, stage, progress=None, pid=None, force=False):
        self.frames.append((stage, progress or {}))

    def present(self, method, *args):
        if method != 'poll' or args != (self,):
            raise AssertionError('Unexpected presentation contract')
        self.polls += 1

    def check(self):
        pass

    def run(self, command, log, stage, cwd=None):
        self.commands.append((command, log, stage, cwd))
        log.write_text('Controlled successful verification\n')
        if self.on_run:
            self.on_run(command)
        if command[0] == str(self.root/'mvnw'):
            target = self.root/'target'
            target.mkdir(exist_ok=True)
            (target/'cloudsim-ho-research-v2-2.0.0.jar').write_bytes(b'verified fixture JAR')
        if isinstance(self.failure, dict):
            return self.failure.get('maven' if command[0] == str(self.root/'mvnw') else 'python', 0)
        return self.failure if self.failure is not None else 0


class VerifiedBuildTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(build, 'Verified-build helper has not been implemented')
        self.temp = tempfile.TemporaryDirectory(prefix='verified build checks ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()/'project'
        for directory in ('src/main', '.mvn/wrapper', 'scripts', '.git/refs/heads', 'jdk/bin'):
            (self.root/directory).mkdir(parents=True, exist_ok=True)
        for name, content in {
            'pom.xml': '<project/>',
            'mvnw': 'controlled Maven wrapper',
            '.mvn/wrapper/maven-wrapper.properties': 'distributionUrl=fixture',
            'src/main/App.java': 'class App {}',
            'scripts/test_fixture.py': 'import unittest',
            'scripts/requirements-tui.txt': '',
            '.git/HEAD': 'ref: refs/heads/main\n',
            '.git/refs/heads/main': 'a'*40+'\n',
            'jdk/bin/java': 'controlled Java executable bytes',
            'jdk/bin/javac': 'controlled Javac executable bytes',
            'jdk/release': 'JAVA_VERSION="21.0.1"\n',
        }.items():
            (self.root/name).write_text(content)
        self.control = FixtureControl(self.root)
        self.metadata = {'source_revision': 'a'*40, 'source_dirty': False,
                         'source_status': '', 'java': str(self.root/'jdk/bin/java')}
        self.outer = self.root/'outputs/first'
        self.outer.mkdir(parents=True)

    def verify(self, **kwargs):
        return build.ensure_verified_build(self.root, self.control, self.metadata,
                                           self.outer, **kwargs)

    def next_attempt(self):
        self.control.commands.clear()
        self.metadata.pop('build', None)
        self.outer = self.root/'outputs'/str(len(list((self.root/'outputs').iterdir())))
        self.outer.mkdir()

    def assert_reusable(self):
        self.next_attempt()
        self.verify()
        self.assertEqual(self.metadata['build'], 'REUSED_VERIFIED')
        self.assertEqual(self.control.commands, [])

    def test_full_verification_retains_logs_and_receipt_for_the_exact_jar(self):
        jar = self.verify()
        self.assertEqual(jar.read_bytes(), b'verified fixture JAR')
        self.assertTrue(jar.is_relative_to(self.root/'.cloudsim/builds'))
        self.assertEqual([record[0] for record in self.control.commands], [
            [str(self.root/'mvnw'), '-B', '-Dmaven.repo.local='+str(self.root/'.cloudsim/maven/repository'), 'clean', 'verify'],
            [sys.executable, '-B', '-m', 'unittest', 'discover', '-s', 'scripts', '-p', 'test_*.py'],
        ])
        self.assertTrue(all(record[3] == self.root for record in self.control.commands))
        self.assertTrue((self.outer/'build.log').is_file())
        self.assertTrue((self.outer/'python-tests.log').is_file())
        receipt = json.loads((self.root/'.cloudsim/verified-build.json').read_text())
        self.assertEqual(receipt['tests'], 'PASS')
        self.assertEqual(receipt['artifact']['path'], str(jar))
        self.assertEqual(receipt['artifact']['sha256'], hashlib.sha256(jar.read_bytes()).hexdigest())
        self.assertEqual(json.loads((self.outer/'build-receipt.json').read_text()), receipt)
        self.assertEqual(self.metadata['tests'], 'PASS')
        self.assertEqual(self.metadata['build'], 'VERIFIED')

    def test_matching_receipt_reuses_previous_pass_without_children(self):
        first = self.verify()
        original = json.loads((self.outer/'build-receipt.json').read_text())
        self.next_attempt()
        self.assertEqual(self.verify(), first)
        self.assertEqual(self.control.commands, [])
        self.assertEqual(self.metadata['build'], 'REUSED_VERIFIED')
        self.assertEqual(self.metadata['tests'], 'PASS')
        self.assertEqual(self.metadata['build_receipt']['tested_at'], original['tested_at'])
        self.assertEqual(self.metadata['build_receipt']['logs'], original['logs'])
        self.assertEqual(json.loads((self.outer/'build-receipt.json').read_text()), original)
        self.assertTrue(any('PASS' in frame[1].get('detail', '') and 'reused' in frame[1].get('detail', '').lower()
                            for frame in self.control.frames))

    def test_runtime_modules_changed_during_verification_reject_receipt(self):
        modules = self.root/'jdk/lib/modules'
        modules.parent.mkdir()
        modules.write_bytes(b'original runtime modules')
        def mutate(command):
            if command[0] == sys.executable: modules.write_bytes(b'changed runtime modules')
        self.control.on_run = mutate
        with self.assertRaisesRegex(build.BuildFailure, 'inputs changed'):
            self.verify()
        self.assertFalse((self.root/'.cloudsim/verified-build.json').exists())

    def test_shell_entrypoint_changes_invalidate_test_verification(self):
        launcher = self.root/'cloudsim.sh'
        launcher.write_text('original launcher')
        self.verify()
        self.next_attempt()
        launcher.write_text('changed launcher')
        self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')

    def test_macos_native_library_changes_invalidate_test_verification(self):
        library = self.root/'jdk/lib/server/libjvm.dylib'
        library.parent.mkdir(parents=True)
        library.write_bytes(b'original macOS JVM')
        self.verify()
        self.next_attempt()
        library.write_bytes(b'changed macOS JVM')
        self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')

    def test_maven_overrides_cannot_skip_or_filter_verified_tests(self):
        for name in ('MAVEN_ARGS', 'MAVEN_OPTS'):
            with self.subTest(name=name):
                self.control.env[name] = '-Dmaven.test.skip=true'
                with self.assertRaisesRegex(build.BuildFailure, name): self.verify()
                self.assertEqual(self.control.commands, [])
                self.control.env.pop(name)

    def test_missing_java_suite_reports_cannot_receive_verification_pass(self):
        tests=self.root/'src/test/java'
        tests.mkdir(parents=True)
        (tests/'RequiredTest.java').write_text('class RequiredTest {}')
        with self.assertRaisesRegex(build.BuildFailure, 'RequiredTest'): self.verify()
        self.assertEqual(self.metadata['tests'], 'FAILED')
        self.assertFalse((self.root/'.cloudsim/verified-build.json').exists())

    def test_installed_managed_maven_does_not_spawn_bootstrap_on_reuse(self):
        (self.root/'.mvn/wrapper/maven-wrapper.properties').write_text('distributionUrl=https://example.test/maven.zip')
        binary=build._maven_distribution(self.root,self.control.env)/'bin/mvn'
        binary.parent.mkdir(parents=True)
        binary.write_text('installed Maven')
        self.verify()
        self.assertEqual(len(self.control.commands),2)
        self.assert_reusable()

    def test_bootstrap_selects_requested_distribution_when_old_version_exists(self):
        properties=self.root/'.mvn/wrapper/maven-wrapper.properties'
        properties.write_text('distributionUrl=https://example.test/apache-maven-old-bin.zip')
        old=build._maven_distribution(self.root,self.control.env)/'bin/mvn'
        old.parent.mkdir(parents=True);old.write_text('old Maven')
        properties.write_text('distributionUrl=https://example.test/apache-maven-new-bin.zip')
        def install(command):
            if command[-1]=='-v':
                new=build._maven_distribution(self.root,self.control.env)
                (new/'bin').mkdir(parents=True);(new/'bin/mvn').write_text('new Maven')
                (new/'conf').mkdir();(new/'conf/settings.xml').write_text('<settings/>')
        self.control.on_run=install
        self.verify()
        self.assertEqual(self.control.commands[0][0][-1],'-v')
        self.assert_reusable()

    def test_python_alias_cannot_reuse_another_environments_verified_pass(self):
        self.verify()
        alias=self.root/'alternate-python'
        alias.symlink_to(Path(sys.executable).resolve())
        self.next_attempt()
        self.verify(python=str(alias))
        self.assertEqual(self.metadata['build'],'VERIFIED')
        self.assertFalse(self.metadata['build_receipt']['reusable'])

    def test_transitive_distribution_change_invalidates_previous_python_pass(self):
        from types import SimpleNamespace
        def distribution(version):
            return SimpleNamespace(metadata={'Name': 'rich'}, version=version)
        with patch.object(importlib.metadata, 'distributions', return_value=[distribution('14.2.0')]): self.verify()
        self.next_attempt()
        with patch.object(importlib.metadata, 'distributions', return_value=[distribution('14.3.0')]): self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')

    def test_source_bytes_are_rechecked_even_with_same_size_mtime_and_git_status(self):
        self.verify()
        self.assert_reusable()
        path = self.root/'src/main/App.java'
        before = path.stat()
        path.write_text('class Bpp {}')
        os.utime(path, ns=(before.st_atime_ns, before.st_mtime_ns))
        self.next_attempt()
        self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')
        self.assertEqual(len(self.control.commands), 2)

    def test_added_and_deleted_untracked_input_files_invalidate_receipt(self):
        self.verify()
        self.assert_reusable()
        added = self.root/'src/main/untracked.properties'
        added.write_text('actual new bytes')
        self.next_attempt()
        self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')
        self.assert_reusable()
        added.unlink()
        self.next_attempt()
        self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')

    def test_each_build_and_test_input_family_invalidates_receipt(self):
        for name in ('pom.xml', 'mvnw', '.mvn/wrapper/maven-wrapper.properties',
                     'scripts/test_fixture.py', 'scripts/requirements-tui.txt'):
            with self.subTest(name=name):
                self.verify()
                self.assert_reusable()
                path = self.root/name
                path.write_bytes(path.read_bytes()+b'\n')
                self.next_attempt()
                self.verify()
                self.assertEqual(self.metadata['build'], 'VERIFIED')
                self.next_attempt()

    def test_java_javac_identity_and_python_version_invalidate_receipt(self):
        self.verify()
        self.assert_reusable()
        for tool in ('java', 'javac'):
            self.control.toolchain[tool]['version'] += ' changed'
            self.next_attempt()
            self.verify()
            self.assertEqual(self.metadata['build'], 'VERIFIED')
            self.assert_reusable()
        self.next_attempt()
        with patch.object(build.sys, 'version', 'changed Python version'):
            self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')

    def test_actual_java_javac_and_release_bytes_are_rechecked(self):
        for name in ('jdk/bin/java', 'jdk/bin/javac', 'jdk/release'):
            with self.subTest(name=name):
                self.verify()
                self.assert_reusable()
                path = self.root/name
                path.write_bytes(path.read_bytes()+b'changed')
                self.next_attempt()
                self.verify()
                self.assertEqual(self.metadata['build'], 'VERIFIED')
                self.next_attempt()

    def test_disappeared_selected_toolchain_file_disables_reuse(self):
        self.verify()
        (self.root/'jdk/bin/javac').unlink()
        self.next_attempt()
        self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')
        self.assertFalse((self.root/'.cloudsim/verified-build.json').exists())

    def test_installed_pinned_ui_dependency_version_is_part_of_receipt(self):
        (self.root/'scripts/requirements-tui.txt').write_text('textual==8.2.8\n')
        with patch.object(importlib.metadata, 'version', return_value='8.2.8'):
            self.verify()
            self.assert_reusable()
        self.next_attempt()
        with patch.object(importlib.metadata, 'version', return_value='8.2.9'):
            self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')

    def test_head_revision_changes_invalidate_even_when_source_bytes_are_identical(self):
        self.verify()
        self.assert_reusable()
        (self.root/'.git/refs/heads/main').write_text('b'*40+'\n')
        self.metadata['source_revision'] = 'b'*40
        self.next_attempt()
        self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')

    def test_missing_provenance_revision_is_inferred_without_extra_children(self):
        del self.metadata['source_revision']
        self.verify()
        self.assertEqual(self.metadata.get('source_revision'), 'a'*40)
        self.assertEqual(len(self.control.commands), 2)
        self.assert_reusable()

    def test_supplied_revision_mismatch_stops_before_build_children(self):
        self.metadata['source_revision'] = 'b'*40
        with self.assertRaisesRegex(build.BuildFailure, '[Rr]evision.*changed|[Rr]evision.*mismatch'):
            self.verify()
        self.assertEqual(self.metadata['tests'], 'FAILED')
        self.assertEqual(self.control.commands, [])

    def test_changed_artifact_hash_is_rejected_and_target_cleaning_preserves_cached_jar(self):
        first = self.verify()
        target = self.root/'target/cloudsim-ho-research-v2-2.0.0.jar'
        target.unlink()
        self.next_attempt()
        self.assertEqual(self.verify(), first)
        first.write_bytes(b'corrupted cache artifact')
        self.next_attempt()
        second = self.verify()
        self.assertEqual(self.metadata['build'], 'VERIFIED')
        self.assertEqual(second.read_bytes(), b'verified fixture JAR')

    def test_corrupt_failed_and_package_only_receipts_are_never_reused(self):
        for kind in ('corrupt', 'failed', 'package-only', 'inconsistent-inputs', 'not-reusable', 'invalid-timestamp'):
            with self.subTest(kind=kind):
                self.verify()
                path = self.root/'.cloudsim/verified-build.json'
                receipt = json.loads(path.read_text())
                if kind == 'corrupt':
                    path.write_text('{not JSON')
                elif kind == 'failed':
                    receipt['tests'] = 'FAILED'
                    path.write_text(json.dumps(receipt))
                elif kind == 'package-only':
                    receipt['verification'] = {'maven': ['package'], 'python': []}
                    path.write_text(json.dumps(receipt))
                elif kind == 'inconsistent-inputs':
                    receipt['inputs']['source_revision'] = 'b'*40
                    path.write_text(json.dumps(receipt))
                elif kind == 'not-reusable':
                    receipt['reusable'] = False
                    path.write_text(json.dumps(receipt))
                else:
                    receipt['tested_at'] = 'not a timestamp'
                    path.write_text(json.dumps(receipt))
                self.next_attempt()
                self.verify()
                self.assertEqual(self.metadata['build'], 'VERIFIED')
                self.next_attempt()

    def test_force_runs_complete_verification_and_keeps_previous_artifact_immutable(self):
        first = self.verify()
        self.next_attempt()
        second = self.verify(force=True)
        self.assertEqual(self.metadata['build'], 'VERIFIED')
        self.assertEqual(len(self.control.commands), 2)
        self.assertNotEqual(first, second)
        self.assertEqual(first.read_bytes(), b'verified fixture JAR')

    def test_incomplete_project_or_toolchain_never_creates_reusable_receipt(self):
        for kind in ('project', 'toolchain'):
            with self.subTest(kind=kind):
                if kind == 'project':
                    (self.root/'pom.xml').unlink()
                else:
                    (self.root/'pom.xml').write_text('<project/>')
                    del self.control.toolchain['javac']
                self.verify()
                self.next_attempt()
                self.verify()
                self.assertEqual(self.metadata['build'], 'VERIFIED')
                self.assertFalse((self.root/'.cloudsim/verified-build.json').exists())
                self.next_attempt()

    def test_failed_child_preserves_exit_code_and_never_leaves_a_trustworthy_receipt(self):
        for stage, code in [('maven', 7), ('python', 9)]:
            with self.subTest(stage=stage):
                self.control.failure = {stage: code}
                with self.assertRaises(build.BuildFailure) as result:
                    self.verify()
                self.assertEqual(result.exception.exit_code, code)
                self.assertEqual(self.metadata['tests'], 'FAILED')
                self.assertFalse((self.root/'.cloudsim/verified-build.json').exists())
                self.next_attempt()

    def test_input_mutation_during_verification_fails_instead_of_recording_stale_pass(self):
        self.control.on_run = lambda _: (self.root/'src/main/App.java').write_text('class Changed {}')
        with self.assertRaisesRegex(build.BuildFailure, 'changed'):
            self.verify()
        self.assertEqual(self.metadata['tests'], 'FAILED')
        self.assertFalse((self.root/'.cloudsim/verified-build.json').exists())

    def test_head_mutation_during_verification_is_rejected(self):
        self.control.on_run = lambda _: (self.root/'.git/refs/heads/main').write_text('b'*40+'\n')
        with self.assertRaisesRegex(build.BuildFailure, 'changed'):
            self.verify()
        self.assertEqual(self.metadata['tests'], 'FAILED')

    def test_source_mutation_while_retaining_artifact_is_rejected(self):
        original_copy = build.shutil.copyfile
        def changed_copy(source, destination):
            result = original_copy(source, destination)
            (self.root/'src/main/App.java').write_text('class Changed {}')
            return result
        with patch.object(build.shutil, 'copyfile', side_effect=changed_copy):
            with self.assertRaisesRegex(build.BuildFailure, 'changed'):
                self.verify()
        self.assertEqual(self.metadata['tests'], 'FAILED')
        self.assertFalse((self.root/'.cloudsim/verified-build.json').exists())

    def test_artifact_mutation_after_maven_verification_is_rejected(self):
        def replace_jar(command):
            if command[0] != str(self.root/'mvnw'):
                (self.root/'target/cloudsim-ho-research-v2-2.0.0.jar').write_bytes(b'untested replacement JAR')
        self.control.on_run = replace_jar
        with self.assertRaisesRegex(build.BuildFailure, '[Aa]rtifact.*changed|JAR.*changed'):
            self.verify()
        self.assertEqual(self.metadata['tests'], 'FAILED')
        self.assertFalse((self.root/'.cloudsim/verified-build.json').exists())

    def test_artifact_mutation_during_retained_copy_is_rejected(self):
        original_copy = build.shutil.copyfile
        def changed_copy(source, destination):
            result = original_copy(source, destination)
            source.write_bytes(b'untested replacement JAR')
            return result
        with patch.object(build.shutil, 'copyfile', side_effect=changed_copy):
            with self.assertRaisesRegex(build.BuildFailure, '[Aa]rtifact.*changed|JAR.*changed'):
                self.verify()
        self.assertEqual(self.metadata['tests'], 'FAILED')
        self.assertFalse((self.root/'.cloudsim/verified-build.json').exists())

    def test_cooperating_build_waits_then_reuses_completed_receipt(self):
        started, release = threading.Event(), threading.Event()
        failures = []
        def first_build():
            try:
                self.verify()
            except BaseException as error:
                failures.append(error)
        def pause_maven(command):
            if command[0] == str(self.root/'mvnw'):
                started.set()
                if not release.wait(3):
                    raise AssertionError('Timed out waiting for controlled lock test')
        self.control.on_run = pause_maven
        first = threading.Thread(target=first_build)
        first.start()
        self.addCleanup(lambda: release.set())
        self.addCleanup(lambda: first.join(4))
        self.assertTrue(started.wait(2))
        second_control = FixtureControl(self.root)
        second_metadata = dict(self.metadata)
        second_outer = self.root/'outputs/second'; second_outer.mkdir()
        result = []
        def second_build():
            try:
                result.append(build.ensure_verified_build(self.root, second_control, second_metadata, second_outer))
            except BaseException as error:
                failures.append(error)
        second = threading.Thread(target=second_build)
        second.start()
        self.addCleanup(lambda: second.join(4))
        deadline = time.monotonic()+2
        while not second_control.frames and time.monotonic() < deadline:
            time.sleep(.01)
        self.assertTrue(second_control.frames, 'Waiting build did not present cancellable lock stage')
        self.assertEqual(second_control.commands, [])
        release.set()
        first.join(3); second.join(3)
        self.assertFalse(first.is_alive()); self.assertFalse(second.is_alive())
        self.assertEqual(failures, [])
        self.assertEqual(len(result), 1)
        self.assertEqual(second_metadata['build'], 'REUSED_VERIFIED')
        self.assertEqual(second_control.commands, [])

    def test_lock_wait_polls_cancellation_without_launching_children(self):
        cache = self.root/'.cloudsim'; cache.mkdir()
        with (cache/'build-cache.lock').open('a+b') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            class Cancelled(Exception):
                pass
            def cancel_after_poll():
                if self.control.polls >= 2:
                    raise Cancelled('cancel requested')
            self.control.check = cancel_after_poll
            with self.assertRaisesRegex(Cancelled, 'cancel requested'):
                self.verify()
        self.assertEqual(self.control.commands, [])
        self.assertGreaterEqual(self.control.polls, 2)
        self.assertEqual(self.metadata['tests'], 'FAILED')

    def test_public_lock_serializes_target_mutation_and_copy_with_verified_builds(self):
        started, release = threading.Event(), threading.Event()
        failures = []
        def pause_maven(command):
            if command[0] == str(self.root/'mvnw'):
                started.set()
                if not release.wait(3):
                    raise AssertionError('Timed out waiting for controlled lock test')
        self.control.on_run = pause_maven
        def verify_in_thread():
            try:
                self.verify()
            except BaseException as error:
                failures.append(error)
        builder = threading.Thread(target=verify_in_thread)
        builder.start()
        self.assertTrue(started.wait(2))
        copier_control = FixtureControl(self.root)
        destination = self.root/'outputs/diagnostic.jar'
        def mutate_and_copy():
            try:
                with build.build_lock(self.root, copier_control):
                    target = self.root/'target/cloudsim-ho-research-v2-2.0.0.jar'
                    target.write_bytes(b'diagnostic package-only artifact')
                    shutil.copyfile(target, destination)
            except BaseException as error:
                failures.append(error)
        copier = threading.Thread(target=mutate_and_copy)
        copier.start()
        def cleanup_threads():
            release.set()
            builder.join(4); copier.join(4)
        self.addCleanup(cleanup_threads)
        deadline = time.monotonic()+2
        while not copier_control.frames and time.monotonic() < deadline:
            time.sleep(.01)
        self.assertTrue(copier_control.frames)
        self.assertFalse(destination.exists())
        release.set()
        builder.join(3); copier.join(3)
        self.assertFalse(builder.is_alive()); self.assertFalse(copier.is_alive())
        self.assertEqual(failures, [])
        self.assertEqual(destination.read_bytes(), b'diagnostic package-only artifact')
        receipt = json.loads((self.root/'.cloudsim/verified-build.json').read_text())
        self.assertEqual(Path(receipt['artifact']['path']).read_bytes(), b'verified fixture JAR')


if __name__ == '__main__':
    unittest.main()
