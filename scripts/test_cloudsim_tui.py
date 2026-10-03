"""Interactive behavior and real launcher regressions; no simulation substitutes."""
import curses
import fcntl
import io
import os
from pathlib import Path
import pty
import re
import select
import signal
import struct
import subprocess
import sys
import tempfile
import termios
import time
import unittest
import json
from unittest.mock import patch

import cloudsim
import cloudsim_tui as tui

ROOT = Path(__file__).resolve().parents[1]


class Screen:
    def __init__(self, rows=32, columns=120):
        self.rows, self.columns = rows, columns
        self.keys = []
        self.lines = {}

    def getmaxyx(self): return self.rows, self.columns
    def erase(self): self.lines = {}
    def refresh(self): pass
    def nodelay(self, value): pass
    def keypad(self, value): pass
    def getch(self): return self.keys.pop(0) if self.keys else -1
    def get_wch(self):
        if not self.keys: raise curses.error('no input')
        key = self.keys.pop(0)
        return chr(key) if isinstance(key, int) and 0 <= key < 256 else key
    def addstr(self, row, column, text, attr=0):
        assert 0 <= row < self.rows
        assert column + tui.cell_width(text) < self.columns
        self.lines[row] = self.lines.get(row, '') + text


class NavigationTests(unittest.TestCase):
    def setUp(self):
        self.screen = Screen()
        self.app = tui.Application(self.screen)

    def keys(self, *keys):
        for key in keys: self.app.handle_key(key)

    def test_home_keeps_focus_and_does_not_launch_on_selection(self):
        with patch.object(cloudsim, 'execute', side_effect=AssertionError('premature execution')):
            self.keys(curses.KEY_DOWN, 10)
            self.assertEqual(self.app.state, 'config')
            self.assertEqual(self.app.action, 'check')
            self.keys(27)
            self.assertEqual(self.app.home_index, 1)

    def test_back_retains_profile_edits_and_field_focus(self):
        self.keys(10, 10)
        self.assertEqual(self.app.profile, 'smoke')
        self.app.values['heap_mib'] = '2048'
        self.app.focus = 1
        self.keys(27, 10)
        self.assertEqual(self.app.values['heap_mib'], '2048')
        self.assertEqual(self.app.focus, 1)

    def test_invalid_heap_retains_edit_with_inline_error(self):
        self.keys(10, 10)
        self.app.values['heap_mib'] = '64'
        self.assertFalse(self.app.review())
        self.assertIn('512', self.app.error)
        self.assertEqual(self.app.values['heap_mib'], '64')
        self.assertEqual(self.app.state, 'config')

    def test_unicode_text_matching_special_key_numbers_stays_text(self):
        self.app.configure('check')
        self.app.focus = 1
        self.app.handle_key('\n')
        self.app.handle_key('\x15')
        text = '/tmp/\u0103'+chr(curses.KEY_ENTER)+chr(curses.KEY_RESIZE)
        for character in text: self.app.handle_key(character)
        self.assertTrue(self.app.editing)
        self.assertEqual(self.app.buffer, text)
        self.app.handle_key('\n')
        self.assertEqual(self.app.values['output_dir'], text)
        self.assertEqual(self.app.focus, 1)

    def test_frozen_settings_are_visible_and_read_only(self):
        self.keys(10, 10)
        self.assertNotIn('population', self.app.fields())
        self.app.draw()
        text = '\n'.join(self.screen.lines.values())
        self.assertIn('read-only', text)
        self.assertIn('4 cases', text)

    def test_stress_default_custom_and_dispatch_parity(self):
        self.keys(10, curses.KEY_END, 10)
        self.app.values.update(preset='micro', search='Custom', vms='60', hosts='12',
                               population='10', iterations='2', replications='1', seed='7',
                               time_limit='1m', heap_mib='512', output_dir='/tmp/tui-parity', skip_build=True)
        self.assertIn('population', self.app.fields())
        self.assertTrue(self.app.review())
        options = self.app.options
        self.assertEqual((options.vms, options.hosts, options.population, options.iterations,
                          options.replications, options.seed, options.time_limit), (60, 12, 10, 2, 1, 7, 60))
        self.assertEqual(options.output_dir, Path('/tmp/tui-parity'))
        self.assertTrue(options.skip_build)
        self.assertEqual(self.app.confirm_index, 0)
        self.keys(10)
        self.assertEqual(self.app.state, 'config')
        self.app.values['search'] = 'Default'
        self.app.review()
        self.assertEqual((self.app.options.population, self.app.options.iterations,
                          self.app.options.replications), (30, 40, 5))

    def test_no_execute_before_explicit_start_and_repeat_retains_outcomes(self):
        executed = []
        def execute(options, *, dashboard, on_complete, invocation):
            executed.append((options.action, invocation))
            dashboard.render('validation', {'detail': 'Independent validation'})
            on_complete({'status': 'failed', 'exit_code': 1, 'error': 'invalid evidence'})
            dashboard.close()
            return 1
        with patch.object(cloudsim, 'execute', execute):
            self.keys(curses.KEY_DOWN, 10)
            self.app.review()
            self.assertEqual(executed, [])
            self.keys(9, 10)
            self.assertEqual(self.app.state, 'complete')
            self.assertEqual(self.app.history[-1]['exit_code'], 1)
            self.keys(10)
            self.assertEqual(self.app.state, 'home')
            self.keys(10)
            self.app.review()
            self.keys(9, 10)
            self.assertEqual(len(self.app.history), 2)
            self.assertEqual(executed[0][1]['mode'], 'interactive')
            self.assertEqual(executed[0][1]['arguments'], [])
            self.assertIn('--check', executed[0][1]['interactive_choices'])

    def test_profile_outcome_records_both_test_gates_and_skip_build(self):
        for runner, arguments in ((cloudsim.shared, []), (cloudsim.stress, ['--preset', 'micro'])):
            for failure, skip, expected in ((None, False, 'PASS'), ('build.log', False, 'FAILED'),
                                            ('python-tests.log', False, 'FAILED'), (None, True, 'NOT_RUN')):
                with self.subTest(runner=runner.__name__, failure=failure, skip=skip), tempfile.TemporaryDirectory() as directory:
                    base = Path(directory)
                    outcome = []
                    def run(control, command, log, stage, **kwargs):
                        log.parent.mkdir(parents=True, exist_ok=True)
                        log.write_text('abc' if log.name == 'source-revision.log' else '')
                        control.last_exit_code = 9 if log.name == failure else 0
                        return control.last_exit_code
                    # Stop after gates at the missing packaged JAR; no simulation.
                    with patch.object(runner, 'ROOT', base), patch.object(cloudsim.shared, 'check_environment', return_value=(Path('/jdk/bin/java'), 8*1024**3)), patch.object(cloudsim.shared.ProcessControl, 'run', run):
                        runner.main([*arguments, '--output-dir', str(base/'outputs'), *(['--skip-build'] if skip else [])],
                                    dashboard=tui.TerminalDashboard(Screen()), on_complete=outcome.append)
                    self.assertEqual(outcome[0].get('tests'), expected)
                    self.assertEqual(outcome[0]['status'], 'failed')


class PresentationTests(unittest.TestCase):
    def test_validation_completion_discloses_individual_and_campaign_evidence(self):
        from test_stress_validator import StressValidatorTest
        fixture = StressValidatorTest(); fixture.setUp(); self.addCleanup(fixture.doCleanups)
        with tempfile.TemporaryDirectory() as directory:
            screen = Screen(24, 80)
            app = tui.Application(screen)
            app.configure('validate')
            app.values.update(validate=str(fixture.directory), output_dir=directory)
            self.assertTrue(app.review())
            app.start()  # Actual dispatcher and independent scientific validator.
            self.assertEqual(app.badge(), 'VALIDATED')
            result = app.outcome['validation_result']
            self.assertEqual(result['scope'], 'individual_run')
            self.assertIn('unavailable', result['artifact_binding'])
            # The UI consumes the same documented outcome for a whole campaign;
            # this fixture asserts display, not scientific campaign validation.
            campaign = {'scope': 'campaign', 'validation': 'PASS',
                'artifact_binding': 'retained JAR SHA-256 verified (identity, not authenticity)',
                'limitations': 'Independent validators retain their documented evidence limits; this is not optimizer replay.',
                'validated_runs': ['/retained/calibration-100/artifacts/stress-one',
                                   '/retained/production/artifacts/stress-two']}
            for outcome in (result, campaign):
                with self.subTest(scope=outcome['scope']):
                    app.outcome['validation_result'] = outcome
                    app.detail_scroll = 0
                    visible = []
                    for _ in range(30):
                        app.draw(); visible.extend(screen.lines.values())
                        app.handle_key(curses.KEY_NPAGE)
                    text = '\n'.join(visible)
                    self.assertIn('Validation scope: '+outcome['scope'], text)
                    self.assertIn('Artifact binding:', text)
                    # Remove line-wrap boundaries when checking complete values.
                    flattened = ''.join(line.strip() for line in visible)
                    for value in (outcome['artifact_binding'], outcome['limitations'], *outcome['validated_runs']):
                        self.assertIn(value.replace(' ', ''), flattened.replace(' ', ''))

    def test_real_progress_snapshots_feed_fixed_metrics_and_live_deadline(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            frozen = parent/'run-one'; frozen.mkdir()
            (frozen/'run.json').write_text(json.dumps({'profile': 'smoke', 'state': 'RUNNING',
                'attempted_cases': 1, 'successful_cases': 0, 'cases': [
                    {'scenario': 'Micro', 'algorithm': 'HO', 'replication': 3, 'phase': 'main'}]}))
            progress = cloudsim.shared.read_progress(parent, profile='smoke')
            self.assertEqual((progress.get('total'), progress.get('algorithm'), progress.get('scenario'),
                              progress.get('replication')), (4, 'HO', 'Micro', 3))
            stress = parent/'stress-one'; stress.mkdir()
            (stress/'progress.json').write_text(json.dumps({'successful_cases': 0,
                'completed_evaluations': 123, 'expected_evaluations': 36310,
                'current_case': {'algorithm': 'HO', 'replication': 4, 'evaluation': 123}}))
            config = cloudsim.stress.parse_args(['--preset', 'micro'])
            progress = cloudsim.stress.progress(parent, config, time.monotonic()+120,
                                               cloudsim.stress.estimate(config, []))
            self.assertEqual(progress.get('evaluations'), 123)
            self.assertEqual(progress.get('algorithm'), 'HO')
            self.assertGreater(progress.get('deadline_remaining', 0), 119)
            screen = Screen()
            app = tui.Application(screen)
            app.configure('profile', 'stress')
            app.state = 'running'
            app.dashboard.render('production', progress, force=True)
            text = '\n'.join(screen.lines.values())
            for expected in ('Evaluations: 123', 'Algorithm: HO', 'Replication: 4', 'remaining:'):
                self.assertIn(expected, text)

    def test_none_metrics_are_unavailable_and_failed_validation_cannot_be_success(self):
        screen = Screen()
        app = tui.Application(screen)
        app.configure('profile', 'smoke')
        app.state = 'running'
        app.dashboard.render('validation', {'done': None, 'total': None})
        self.assertNotIn('None', '\n'.join(screen.lines.values()))
        app.outcome = {'status': 'failed', 'validation': 'PASS', 'exit_code': 0}
        self.assertEqual(app.badge(), 'FAILED')

    def test_review_preserves_full_cli_and_warnings_on_small_supported_screen(self):
        screen = Screen(24, 80)
        app = tui.Application(screen)
        app.configure('profile', 'stress')
        app.values.update(preset='xlarge', vms='20000', hosts='4000', output_dir='/tmp/'+'a'*140)
        with patch.object(cloudsim.shared, 'physical_memory', return_value=4*1024**3), patch.object(cloudsim.shared, 'usable_memory', side_effect=ValueError('unknown')):
            self.assertTrue(app.review())
        self.assertIn('unknown', '\n'.join(app.preview_lines()))
        self.assertIn('below 8 GiB', '\n'.join(app.preview_lines()))
        visible = []
        for _ in range(20):
            app.draw()
            visible.extend(screen.lines.values())
            app.handle_key(curses.KEY_NPAGE)
        self.assertIn('a'*60, '\n'.join(visible))
        self.assertIn('Combined:', '\n'.join(visible))

    def test_dumb_terminal_and_missing_curses_offer_plain_action(self):
        output = io.StringIO()
        with patch.dict(os.environ, {'TERM': 'dumb'}), patch('sys.stderr', output):
            self.assertEqual(tui.run(), 2)
        self.assertIn('--plain', output.getvalue())
        with patch.object(tui, 'curses', None), patch('sys.stderr', output):
            self.assertEqual(tui.run(), 2)

    def test_log_suffix_is_bounded_and_sanitized_even_for_giant_line(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'log'
            path.write_bytes(b'x' * (3*1024*1024) + b'\n\x1b[31mred\x1b[0m\x1b]0;owned\x07\x00\r\n' + 'wide \u754ce\u0301'.encode())
            lines = tui.LogTail.read(path, max_bytes=1024, max_lines=3)
            self.assertLessEqual(sum(len(line) for line in lines), 1024)
            self.assertFalse(any('\x1b' in line or '\x00' in line or 'owned' in line for line in lines))
            self.assertIn('red', ''.join(lines))
            path.write_bytes(b'new\n')
            self.assertEqual(tui.LogTail.read(path), ['new'])
            path.unlink()
            self.assertEqual(tui.LogTail.read(path), [])

    def test_unicode_clipping_uses_cells_and_ascii_fallback(self):
        self.assertEqual(tui.clip('a\u754ce\u0301z', 4), 'a\u754ce\u0301')
        self.assertEqual(tui.cell_width('a\u754ce\u0301'), 4)
        self.assertTrue(tui.clip('\u754ce\u0301', 8, ascii_only=True).isascii())

    def test_supported_and_too_small_layout_unknown_measurements(self):
        for rows, columns in ((32, 120), (24, 80), (10, 40)):
            with self.subTest(size=(rows, columns)), patch.dict(os.environ, {'NO_COLOR': '1'}):
                screen = Screen(rows, columns)
                app = tui.Application(screen)
                app.state = 'running'
                app.dashboard.render('validation', {'detail': 'checking evidence'}, force=True)
                text = '\n'.join(screen.lines.values())
                if columns < 80: self.assertIn('Resize', text)
                else: self.assertIn('unavailable', text)
                self.assertFalse(app.color)

    def test_scroll_pauses_follow_and_end_resumes_without_cancelling(self):
        app = tui.Application(Screen())
        app.state = 'running'
        app.handle_key(curses.KEY_PPAGE)
        self.assertFalse(app.dashboard.follow)
        app.handle_key(curses.KEY_END)
        self.assertTrue(app.dashboard.follow)
        app.handle_key(27)
        self.assertEqual(app.state, 'running')
        self.assertEqual(app.cancel_index, 0)
        self.assertTrue(app.cancel_dialog)


class LauncherPtyTests(unittest.TestCase):
    def test_actual_shell_utf8_path_edit_review_and_back_preserve_characters(self):
        with tempfile.TemporaryDirectory() as directory:
            session = PtySession(self, Path(directory), 'success')
            try:
                session.send(b'\t\r')
                session.expect(b'Configure check')
                session.send(b'\t\r\x15'+('/tmp/caf\u00e9'.encode('utf-8'))+b'\r')
                session.expect('/tmp/caf\u00e9'.encode('utf-8'))
                session.send(b'\x1bOF\r')
                session.expect(b'Review settings')
                session.expect('/tmp/caf\u00e9'.encode('utf-8'))
                self.assertNotIn('caf\u00c3\u00a9', session.terminal.text())
                self.assertFalse(session.output.exists())  # Review never launches.
                session.send(b'\x1b')
                session.expect(b'Configure check')
                session.expect('/tmp/caf\u00e9'.encode('utf-8'))
                session.send(b'\x1b')
                session.expect(b'Choose an action')
                session.send(b'q')
                self.assertEqual(session.process.wait(timeout=5), 0)
                self.assertEqual(termios.tcgetattr(session.slave), session.before)
            finally: session.close()

    def test_six_real_terminal_states_at_target_sizes_and_monochrome(self):
        for rows, columns, monochrome in ((32, 120, False), (24, 80, False), (24, 80, True)):
            with self.subTest(size=(rows, columns), monochrome=monochrome), tempfile.TemporaryDirectory() as directory:
                session = PtySession(self, Path(directory), 'cancel', rows, columns, monochrome)
                try:
                    session.capture('home')
                    session.send(b'\t\r')
                    session.expect(b'Configure check')
                    session.capture('configuration')
                    session.send(b'\t\r\x15'+str(session.output).encode()+b'\r')
                    session.expect(str(session.output).encode())
                    session.send(b'\x1bOF\r')
                    session.expect(b'Review settings')
                    session.capture('confirmation')
                    session.send(b'\t\r')
                    session.wait_pid()
                    session.expect(b'RUNNING')
                    session.capture('running')
                    session.send(b'c\t\r')
                    session.expect(b'CANCELLED')
                    session.mode.write_text('fail')
                    session.send(b'\r')
                    session.expect(b'Choose an action')
                    session.start_check(repeat=True)
                    session.expect(b'FAILED')
                    session.capture('failure')
                    session.mode.write_text('success')
                    session.send(b'\r')
                    session.expect(b'Choose an action')
                    session.start_check(repeat=True)
                    session.expect(b'CHECK PASSED')
                    session.capture('completion')
                    session.send(b'q')
                    self.assertEqual(session.process.wait(timeout=5), 0)
                    self.assertEqual(termios.tcgetattr(session.slave), session.before)
                finally: session.close()

    def test_cancel_resize_failure_second_action_and_child_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            session = PtySession(self, Path(directory), 'cancel')
            try:
                session.start_check()
                session.wait_pid()
                session.resize(10, 40)
                session.expect(b'Resize terminal')
                session.send(b'\x1b')
                session.expect(b'Continue')
                session.send(b'\r')  # Safe default keeps child alive.
                self.assertIsNone(session.process.poll())
                session.resize(32, 120)
                session.send(b'c\t\r')
                session.expect(b'CANCELLED')
                session.assert_children_gone()
                session.expect(b'exit 130')
                session.mode.write_text('fail')
                session.send(b'\r')
                session.expect(b'Choose an action')
                session.start_check(repeat=True)
                session.expect(b'FAILED')
                session.expect(b'must be version 21')
                session.mode.write_text('success')
                session.send(b'\r')
                session.expect(b'Choose an action')
                session.start_check(repeat=True)
                session.expect(b'CHECK PASSED')
                session.send(b'q')
                self.assertEqual(session.process.wait(timeout=5), 0)
                self.assertEqual(termios.tcgetattr(session.slave), session.before)
                metadata = [json.loads(p.read_text()) for p in session.output.glob('check-*/runner.json')]
                self.assertEqual(sorted(m['exit_code'] for m in metadata), [0, 9, 130])
            finally: session.close()

    def test_sigint_retains_outcome_and_sigterm_exits_after_descendant_cleanup(self):
        for number in (signal.SIGINT, signal.SIGTERM):
            with self.subTest(signal=number), tempfile.TemporaryDirectory() as directory:
                session = PtySession(self, Path(directory), 'cancel')
                try:
                    session.start_check()
                    session.wait_pid()
                    os.kill(session.process.pid, number)
                    if number == signal.SIGINT:
                        session.expect(b'CANCELLED')
                        session.send(b'q')
                        expected = 0
                    else: expected = 143
                    self.assertEqual(session.process.wait(timeout=8), expected)
                    session.assert_children_gone()
                    self.assertEqual(termios.tcgetattr(session.slave), session.before)
                finally: session.close()

    def test_actual_shell_starts_persistent_screen_and_restores_terminal(self):
        master, slave = pty.openpty()
        fcntl.ioctl(slave, termios.TIOCSWINSZ, struct.pack('HHHH', 24, 80, 0, 0))
        before = termios.tcgetattr(slave)
        process = subprocess.Popen([str(ROOT/'cloudsim.sh')], stdin=slave, stdout=slave,
                                   stderr=slave, cwd=ROOT, env={**os.environ, 'TERM': 'xterm-256color'})
        output = b''
        try:
            deadline = time.monotonic()+8
            while b'Run experiment' not in output and time.monotonic() < deadline:
                if select.select([master], [], [], .1)[0]: output += os.read(master, 65536)
                if process.poll() is not None: break
            self.assertIn(b'Run experiment', output)
            os.write(master, b'q')
            self.assertEqual(process.wait(timeout=5), 0)
            self.assertEqual(termios.tcgetattr(slave), before)
        finally:
            if process.poll() is None: process.kill(); process.wait()
            os.close(master); os.close(slave)


class PtySession:
    """Drive the real Bash entry point against controlled JDK children."""
    def __init__(self, test, directory, mode, rows=24, columns=80, monochrome=True):
        self.test, self.directory = test, directory
        self.mode = directory/'mode'
        self.mode.write_text(mode)
        self.pids = directory/'pids'
        self.output = directory/'outputs'
        bindir = directory/'jdk/bin'
        bindir.mkdir(parents=True)
        java = bindir/'java'
        java.write_text('#!'+sys.executable+'\n'+'''import os, pathlib, signal, subprocess, sys, time
mode = pathlib.Path(os.environ['TUI_TEST_MODE']).read_text()
if mode == 'fail':
    print('java version "17.0"', flush=True)
    sys.exit(9)
if mode == 'cancel':
    child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])
    pathlib.Path(os.environ['TUI_TEST_PIDS']).write_text(str(os.getpid())+' '+str(child.pid))
    def stop(number, frame):
        child.terminate()
        child.wait(timeout=3)
        sys.exit(128+number)
    signal.signal(signal.SIGTERM, stop)
    while True: time.sleep(.1)
print('openjdk version "21.0.2"', flush=True)
''')
        java.chmod(0o755)
        javac = bindir/'javac'
        javac.write_text('#!'+sys.executable+'\nprint("javac 21.0.2")\n')
        javac.chmod(0o755)
        # Only controlled action sessions stub machine headroom. The separate
        # real-launcher startup test has no bootstrap or production test hooks.
        bootstrap = directory/'bootstrap'
        bootstrap.mkdir()
        (bootstrap/'sitecustomize.py').write_text('import sys\nsys.path.insert(0, '+repr(str(ROOT/'scripts'))+')\n'
            'import research_runner\nresearch_runner.usable_memory = lambda proc=None: 8*1024**3\n')
        self.master, self.slave = pty.openpty()
        self.resize(rows, columns)
        self.monochrome = monochrome
        self.before = termios.tcgetattr(self.slave)
        environment = {**os.environ, 'TERM': 'xterm-256color', 'JAVA_HOME': str(directory/'jdk'),
                       'TUI_TEST_MODE': str(self.mode), 'TUI_TEST_PIDS': str(self.pids),
                       'PYTHONPATH': str(bootstrap), 'PYTHONDONTWRITEBYTECODE': '1'}
        for name in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'): environment.pop(name, None)
        if monochrome: environment['NO_COLOR'] = '1'
        else: environment.pop('NO_COLOR', None)
        self.process = subprocess.Popen([str(ROOT/'cloudsim.sh')], cwd=ROOT,
            stdin=self.slave, stdout=self.slave, stderr=self.slave,
            env=environment)
        self.captured = b''
        self.pending = b''
        self.terminal = TerminalCapture(rows, columns)
        self.expect(b'Run experiment')

    def send(self, data):
        self.pending = b''
        os.write(self.master, data)

    def expect(self, token):
        deadline = time.monotonic()+8
        while token.decode() not in self.terminal.text() and time.monotonic() < deadline:
            if select.select([self.master], [], [], .05)[0]:
                data = os.read(self.master, 65536)
                self.pending += data; self.captured += data; self.terminal.feed(data)
            if self.process.poll() is not None: break
        self.test.assertIn(token.decode(), self.terminal.text(), self.terminal.text())

    def resize(self, rows, columns):
        fcntl.ioctl(self.slave, termios.TIOCSWINSZ, struct.pack('HHHH', rows, columns, 0, 0))
        if hasattr(self, 'terminal'): self.terminal.resize(rows, columns)
        if hasattr(self, 'process'): os.kill(self.process.pid, signal.SIGWINCH)

    def start_check(self, repeat=False):
        self.send(b'\r' if repeat else b'\t\r')
        self.expect(b'Configure check')
        if not repeat:
            self.send(b'\t\r\x15'+str(self.output).encode()+b'\r')
            self.expect(str(self.output).encode())
        self.send(b'\x1bOF\r')  # xterm End selects Review.
        self.expect(b'Review settings')
        self.send(b'\t\r')

    def wait_pid(self):
        deadline = time.monotonic()+8
        while not self.pids.exists() and time.monotonic() < deadline:
            if select.select([self.master], [], [], .05)[0]:
                data = os.read(self.master, 65536)
                self.captured += data; self.terminal.feed(data)
        self.test.assertTrue(self.pids.exists(), self.captured.decode(errors='replace'))

    def assert_children_gone(self):
        for pid in map(int, self.pids.read_text().split()):
            self.test.assertFalse(Path(f'/proc/{pid}').exists(), f'Owned PID {pid} remains')

    def close(self):
        if self.process.poll() is None:
            self.process.terminate()
            try: self.process.wait(timeout=8)
            except subprocess.TimeoutExpired: self.process.kill(); self.process.wait()
        os.close(self.master); os.close(self.slave)

    def capture(self, state):
        # Wait for the rest of a curses frame after expect saw its first token.
        while select.select([self.master], [], [], .08)[0]:
            data = os.read(self.master, 65536)
            self.captured += data; self.terminal.feed(data)
        self.test.assertIn('CLOUDSIM', self.terminal.text())
        for line in self.terminal.text().splitlines(): self.test.assertLess(len(line), self.terminal.columns)
        destination = os.environ.get('TUI_CAPTURE_DIR')
        if destination:
            folder = Path(destination)
            folder.mkdir(parents=True, exist_ok=True)
            name = f'{self.terminal.columns}x{self.terminal.rows}{"-mono" if self.monochrome else ""}-{state}'
            (folder/(name+'.txt')).write_text(self.terminal.text()+'\n')
            (folder/(name+'.pty')).write_bytes(self.captured)


class TerminalCapture:
    """Decode curses' real PTY output for assertions and readable evidence."""
    def __init__(self, rows, columns):
        self.row = self.column = 0
        self.buffer = ''
        self.resize(rows, columns)

    def resize(self, rows, columns):
        self.rows, self.columns = rows, columns
        self.cells = [[' ']*columns for _ in range(rows)]

    def text(self): return '\n'.join(''.join(row).rstrip() for row in self.cells)

    def feed(self, data):
        self.buffer += data.decode(errors='replace')
        while self.buffer:
            char = self.buffer[0]
            if char == '\x1b':
                match = re.match(r'\x1b\[([0-?]*)([ -/]*)([@-~])', self.buffer)
                if match:
                    params, _, action = match.groups()
                    self.buffer = self.buffer[match.end():]
                    if params.startswith('?'): continue
                    values = [int(v) if v else 0 for v in params.split(';')]
                    first = values[0] or 1
                    if action in ('H', 'f'):
                        self.row = first-1
                        self.column = (values[1] or 1)-1 if len(values)>1 else 0
                    elif action == 'd': self.row = first-1
                    elif action == 'G': self.column = first-1
                    elif action == 'A': self.row -= first
                    elif action == 'B': self.row += first
                    elif action == 'C': self.column += first
                    elif action == 'D': self.column -= first
                    elif action == 'P' and 0 <= self.row < self.rows:
                        row = self.cells[self.row]
                        row[self.column:] = row[self.column+first:]+[' ']*min(first, self.columns-self.column)
                    elif action == '@' and 0 <= self.row < self.rows:
                        row = self.cells[self.row]
                        row[self.column:] = ([' ']*first+row[self.column:])[:self.columns-self.column]
                    elif action == 'J' and values[0] in (2, 3): self.cells = [[' ']*self.columns for _ in range(self.rows)]
                    elif action == 'K' and 0 <= self.row < self.rows:
                        start, end = (0, self.columns) if values[0] == 2 else (0, self.column+1) if values[0] == 1 else (self.column, self.columns)
                        for column in range(max(0, start), min(end, self.columns)): self.cells[self.row][column] = ' '
                    elif action == 'X' and 0 <= self.row < self.rows:
                        for column in range(self.column, min(self.column+first, self.columns)): self.cells[self.row][column] = ' '
                    continue
                if self.buffer.startswith('\x1b['): break
                if self.buffer.startswith(('\x1b(', '\x1b)')):
                    if len(self.buffer) < 3: break
                    self.buffer = self.buffer[3:]; continue
                if len(self.buffer) < 2: break
                self.buffer = self.buffer[2:]; continue
            self.buffer = self.buffer[1:]
            if char == '\r': self.column = 0
            elif char == '\n': self.row += 1
            elif char == '\b': self.column -= 1
            elif char >= ' ':
                if 0 <= self.row < self.rows and 0 <= self.column < self.columns:
                    self.cells[self.row][self.column] = char
                self.column += 1


if __name__ == '__main__': unittest.main()
