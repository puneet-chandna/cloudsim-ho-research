"""Persistent stdlib terminal console; the shared dispatcher owns every action."""
import contextlib
import io
import os
from pathlib import Path
import shlex
import signal
import sys
import time
import unicodedata

import cloudsim

# Curses stays behind the interactive entry point: CLI actions need no curses.
try:
    import curses
except ImportError:
    curses = None


def cell_width(text):
    return sum(0 if unicodedata.category(c) in ('Mn', 'Me') else
               2 if unicodedata.east_asian_width(c) in ('W', 'F') else 1 for c in text)


def clip(text, width, *, ascii_only=False):
    text = cloudsim.shared.sanitize(text)
    if ascii_only: text = text.encode('ascii', 'replace').decode('ascii')
    return cloudsim.shared.clip_cells(text, max(0, width))


def read_key(screen):
    """Wide curses input returns decoded text or an integer special key."""
    try: return screen.get_wch()
    except curses.error: return None  # Nonblocking input queue is empty.


class LogTail:
    @staticmethod
    def read(path: Path, *, max_bytes=65536, max_lines=500):
        if not path or max_bytes <= 0 or max_lines <= 0: return []
        try:
            with Path(path).open('rb') as stream:
                stream.seek(0, os.SEEK_END)
                length = stream.tell()
                stream.seek(max(0, length-max_bytes))
                data = stream.read(max_bytes)
        except OSError: return []
        # A clipped UTF-8 prefix is replacement-decoded; no whole-line read.
        return [cloudsim.shared.sanitize(line) for line in
                data.decode('utf-8', errors='replace').splitlines()[-max_lines:]]


class TerminalDashboard:
    def __init__(self, screen, app=None):
        self.screen, self.app = screen, app
        self.console = None
        self.warning = None
        self.reset()

    def reset(self):
        self.started = time.monotonic()
        self.updated = 0
        self.stage = 'preflight'
        self.progress = {}
        self.pid = None
        self.log = None
        self.lines = []
        self.follow = True
        self.offset = 0

    def set_log(self, path):
        self.log = path
        self.updated = 0

    def refresh_log(self):
        now = time.monotonic()
        if now-self.updated >= 1:
            self.lines = LogTail.read(self.log)
            self.updated = now

    def render(self, stage, progress=None, pid=None, force=False):
        self.stage, self.progress, self.pid = stage, progress or {}, pid
        self.refresh_log()
        if self.app: self.app.draw()

    def poll(self, control):
        if self.app:
            if control.signum == signal.SIGTERM: self.app.exit_after_job = True
            # A bounded queue prevents a paste flooding supervision.
            for _ in range(32):
                key = read_key(self.screen)
                if key is None: break
                self.app.handle_key(key, control)
            self.refresh_log()
            if time.monotonic()-self.app.drawn >= 1 or self.app.dirty:
                self.app.draw()

    def close(self):
        # Job finish must not end the application or restore its screen.
        self.refresh_log()


HOME = ('Run experiment', 'Check environment', 'Build', 'Test', 'Validate output', 'Quit')
DESCRIPTIONS = ('Configure Smoke, Explore, Research or Static Stress.',
                'Check full JDK 21 and independently verified memory headroom.',
                'Package with Maven. Tests are explicitly NOT RUN.',
                'Run Maven clean verify and Python regression discovery.',
                'Independently validate retained run or campaign evidence.',
                'Leave this console. Retained action results remain on disk.')
PROFILES = ('smoke', 'explore', 'research', 'stress')
PROFILE_TEXT = ('Smoke / 4 cases: short frozen protocol check',
                'Explore / 40 cases: broader frozen protocol check',
                'Research / 450 cases: frozen research and sensitivity matrix',
                'Static Stress: calibrated, bounded static placement workload')
LABELS = {'heap_mib': 'Heap (MiB)', 'output_dir': 'Output parent', 'config': 'Config file',
          'skip_build': 'Build policy', 'preset': 'Size preset', 'search': 'Search settings',
          'vms': 'VM count', 'hosts': 'Host count', 'population': 'Population (even)',
          'iterations': 'Iterations', 'replications': 'Replications', 'seed': 'Master seed',
          'time_limit': 'Shared deadline', 'validate': 'Run / campaign directory',
          'advanced': 'Advanced fields', 'review': 'Review and confirm'}


class Application:
    def __init__(self, screen):
        self.screen = screen
        screen.keypad(True)
        screen.nodelay(True)
        self.state = 'home'
        self.home_index = self.profile_index = self.focus = 0
        self.action = self.profile = None
        self.values = {}
        self.saved = {}
        self.advanced = False
        self.editing = False
        self.buffer = ''
        self.error = ''
        self.options = None
        self.preview = None
        self.confirm_index = 0
        self.cancel_index = 0
        self.cancel_dialog = False
        self.history = []
        self.outcome = None
        self.detail_scroll = 0
        self.exit_requested = False
        self.exit_after_job = False
        self.pending_signal = None
        self.dirty = True
        self.drawn = 0
        encoding = getattr(sys.stdout, 'encoding', '') or ''
        self.ascii_only = 'utf' not in encoding.lower() or os.environ.get('LC_ALL') in ('C', 'POSIX')
        self.color = False
        if curses and 'NO_COLOR' not in os.environ:
            try:
                if curses.has_colors():
                    curses.start_color(); curses.use_default_colors()
                    curses.init_pair(1, curses.COLOR_CYAN, -1)
                    self.color = True
            except curses.error: pass
        self.dashboard = TerminalDashboard(screen, self)

    def configure(self, action, profile=None):
        self.action, self.profile = action, profile
        identity = profile or action
        if identity not in self.saved:
            self.saved[identity] = ({'heap_mib': '1024', 'output_dir': str(cloudsim.ROOT/
                ('results/stress' if profile == 'stress' else 'results')), 'config': '',
                'skip_build': False, 'preset': 'small', 'search': 'Default',
                'vms': '500', 'hosts': '100', 'population': '30', 'iterations': '40',
                'replications': '5', 'seed': '123456', 'time_limit': '2h', 'validate': ''}, 0, False)
        self.values, self.focus, self.advanced = self.saved[identity]
        self.state = 'config'
        self.error = ''

    def save_focus(self):
        self.saved[self.profile or self.action] = (self.values, self.focus, self.advanced)

    def fields(self):
        fields = []
        if self.profile == 'stress':
            fields += ['preset', 'search', 'vms', 'hosts']
            if self.values['search'] == 'Custom': fields += ['population', 'iterations', 'replications']
        if self.action == 'validate': fields += ['validate']
        else: fields += ['heap_mib']
        fields += ['output_dir']
        if self.action == 'profile': fields += ['skip_build']
        if self.action == 'profile':
            fields += ['advanced']
            if self.advanced:
                fields += ['seed', 'time_limit'] if self.profile == 'stress' else ['config']
        return fields + ['review']

    def arguments(self):
        args = ['--profile', self.profile] if self.action == 'profile' else ['--'+self.action]
        if self.action == 'validate': args.append(self.values['validate'])
        else: args += ['--heap-mib', self.values['heap_mib']]
        args += ['--output-dir', self.values['output_dir']]
        if self.action == 'profile':
            if self.values['skip_build']: args.append('--skip-build')
            if self.profile == 'stress':
                args += ['--preset', self.values['preset'], '--vms', self.values['vms'],
                         '--hosts', self.values['hosts'], '--seed', self.values['seed'],
                         '--time-limit', self.values['time_limit']]
                if self.values['search'] == 'Custom':
                    for field in ('population', 'iterations', 'replications'):
                        args += ['--'+field, self.values[field]]
            elif self.values['config']: args += ['--config', self.values['config']]
        return args

    def review(self):
        errors = io.StringIO()
        try:
            with contextlib.redirect_stderr(errors):
                self.options = cloudsim.parse_args(self.arguments())
            self.preview = cloudsim.preview(self.options) if self.action == 'profile' else None
        except (SystemExit, ValueError, OSError) as error:
            self.error = cloudsim.shared.sanitize(errors.getvalue().split('error:')[-1].strip() or error)
            self.state = 'config'
            return False
        self.error = ''
        self.state = 'confirm'
        self.confirm_index = 0
        self.detail_scroll = 0
        self.save_focus()
        return True

    def start(self):
        self.state = 'running'
        self.dashboard.reset()
        self.cancel_dialog = False
        self.outcome = None
        self.draw()
        def completed(metadata): self.outcome = metadata
        try:
            code = cloudsim.execute(self.options, dashboard=self.dashboard, on_complete=completed,
                invocation={'mode': 'interactive', 'arguments': [],
                            'interactive_choices': list(self.options.arguments),
                            'equivalent_cli': [str(cloudsim.ROOT/'cloudsim.sh'), *self.options.arguments]})
        except Exception as error:
            code = 1
            self.outcome = {'status': 'failed', 'exit_code': code, 'error': cloudsim.shared.sanitize(error)}
        if self.outcome is None:
            self.outcome = {'status': 'failed', 'exit_code': code or 1,
                            'error': 'Dispatcher returned without a retained completion outcome.'}
        self.history.append(self.outcome)
        self.state = 'complete'
        self.detail_scroll = 0
        self.cancel_dialog = False
        if code == 128+signal.SIGTERM: self.exit_after_job = True
        if self.exit_after_job: self.exit_requested = True
        self.draw()

    def handle_key(self, key, control=None):
        self.dirty = True
        character = key if isinstance(key, str) else None
        # Only ASCII controls/hotkeys share our integer bindings. Unicode text
        # must stay distinct from curses' integer special-key namespace.
        if character is not None and ord(character) < 128: key = ord(character)
        down = key in (curses.KEY_DOWN, 9)
        up = key in (curses.KEY_UP, curses.KEY_BTAB)
        enter = key in (10, 13, curses.KEY_ENTER)
        if key == curses.KEY_RESIZE: return
        if self.state == 'running':
            if key == 3 and control: control.signum = signal.SIGINT
            elif self.cancel_dialog:
                if down or up or key in (curses.KEY_LEFT, curses.KEY_RIGHT): self.cancel_index = 1-self.cancel_index
                elif key == 27: self.cancel_dialog = False
                elif enter:
                    if self.cancel_index and control: control.signum = signal.SIGINT
                    self.cancel_dialog = False
            elif key in (ord('c'), ord('q'), 27):
                self.cancel_dialog = True; self.cancel_index = 0
            elif key in (curses.KEY_UP, curses.KEY_PPAGE):
                self.dashboard.follow = False
                self.dashboard.offset = min(499, self.dashboard.offset + (1 if key == curses.KEY_UP else 10))
            elif key in (curses.KEY_DOWN, curses.KEY_NPAGE):
                self.dashboard.follow = False
                self.dashboard.offset = max(0, self.dashboard.offset-(1 if key == curses.KEY_DOWN else 10))
            elif key == curses.KEY_END: self.dashboard.follow = True; self.dashboard.offset = 0
            elif key == ord(' '): self.dashboard.follow = not self.dashboard.follow
            return
        if self.editing:
            if key == 27: self.editing = False; self.error = ''; return
            if enter:
                field = self.fields()[self.focus]
                self.values[field] = self.buffer
                self.editing = False
                # Shared parser is the authority; preserve entered values on error.
                errors = io.StringIO()
                try:
                    with contextlib.redirect_stderr(errors): cloudsim.parse_args(self.arguments())
                    self.error = ''
                except (SystemExit, ValueError, OSError) as error:
                    self.error = cloudsim.shared.sanitize(errors.getvalue().split('error:')[-1].strip() or error)
                return
            if key in (curses.KEY_BACKSPACE, 127, 8): self.buffer = self.buffer[:-1]
            elif key == 21: self.buffer = ''
            elif character is not None and character.isprintable(): self.buffer += character
            elif isinstance(key, int) and 32 <= key < 256: self.buffer += chr(key)
            return
        if self.state == 'home':
            if down: self.home_index = (self.home_index+1)%len(HOME)
            elif up: self.home_index = (self.home_index-1)%len(HOME)
            elif key == curses.KEY_END: self.home_index = len(HOME)-1
            elif key in (ord('q'), 27): self.exit_requested = True
            elif enter:
                if self.home_index == 0: self.state = 'profiles'
                elif self.home_index == 5: self.exit_requested = True
                else: self.configure(('check', 'build', 'test', 'validate')[self.home_index-1])
        elif self.state == 'profiles':
            if down: self.profile_index = (self.profile_index+1)%4
            elif up: self.profile_index = (self.profile_index-1)%4
            elif key == curses.KEY_END: self.profile_index = 3
            elif key == 27: self.state = 'home'
            elif enter: self.configure('profile', PROFILES[self.profile_index])
        elif self.state == 'config':
            fields = self.fields()
            self.focus = min(self.focus, len(fields)-1)
            if down: self.focus = (self.focus+1)%len(fields)
            elif up: self.focus = (self.focus-1)%len(fields)
            elif key == curses.KEY_END: self.focus = len(fields)-1
            elif key == 27:
                self.save_focus(); self.state = 'profiles' if self.action == 'profile' else 'home'
            elif enter:
                field = fields[self.focus]
                if field == 'review': self.review()
                elif field == 'advanced': self.advanced = not self.advanced
                elif field == 'skip_build': self.values[field] = not self.values[field]
                elif field == 'search': self.values[field] = 'Custom' if self.values[field] == 'Default' else 'Default'
                elif field == 'preset':
                    presets = list(cloudsim.stress.PRESETS)
                    self.values[field] = presets[(presets.index(self.values[field])+1)%len(presets)]
                    self.values['vms'], self.values['hosts'] = map(str, cloudsim.stress.PRESETS[self.values[field]])
                else: self.editing = True; self.buffer = str(self.values[field])
        elif self.state == 'confirm':
            if down or up or key in (curses.KEY_LEFT, curses.KEY_RIGHT): self.confirm_index = 1-self.confirm_index
            elif key in (curses.KEY_PPAGE, curses.KEY_NPAGE):
                self.detail_scroll = max(0, self.detail_scroll + (8 if key == curses.KEY_NPAGE else -8))
            elif key == 27: self.state = 'config'
            elif enter:
                if self.confirm_index: self.start()
                else: self.state = 'config'
        elif self.state == 'complete':
            if key in (curses.KEY_DOWN, curses.KEY_NPAGE): self.detail_scroll += 5
            elif key in (curses.KEY_UP, curses.KEY_PPAGE): self.detail_scroll = max(0, self.detail_scroll-5)
            elif enter or key == 27: self.state = 'home'
            elif key == ord('q'): self.exit_requested = True

    def write(self, row, column, text, *, selected=False):
        rows, columns = self.screen.getmaxyx()
        if row < 0 or row >= rows or column >= columns-1: return
        attr = (curses.A_REVERSE | curses.A_BOLD) if selected else 0
        if selected and self.color: attr |= curses.color_pair(1)
        text = clip(text, columns-column-1, ascii_only=self.ascii_only)
        try: self.screen.addstr(row, column, text, attr)
        except curses.error: pass  # Resize can invalidate one write between getmaxyx/addstr.

    def lines(self, texts, start=4, *, end=None):
        rows, _ = self.screen.getmaxyx()
        for row, text in enumerate(texts, start):
            if row >= (end if end is not None else rows-3): break
            self.write(row, 2, text)

    def wrapped(self, texts):
        """Keep complete review/path values reachable at either supported width."""
        _, columns = self.screen.getmaxyx()
        width = max(1, columns-5)
        result = []
        for value in texts:
            text = cloudsim.shared.sanitize(value)
            if self.ascii_only: text = text.encode('ascii', 'replace').decode('ascii')
            if not text: result.append('')
            while text:
                part = clip(text, width)
                result.append(part)
                text = text[len(part):]
        return result

    def preview_lines(self):
        if not self.preview: return [DESCRIPTIONS[self.home_index], '', self.command()]
        p = self.preview
        memory = p['memory_evidence']
        def gib(value): return f'{value/1024**3:.2f} GiB' if value is not None else 'unknown'
        lines = [f"Profile: {self.profile}",
                 f"Physical RAM: {gib(memory['physical_memory_bytes'])}",
                 f"Usable RAM:   {gib(memory['usable_memory_bytes'])}",
                 f"Heap: {self.options.heap_mib} MiB (independent launch guard)"]
        plan = p['planned_work']
        if self.profile == 'stress':
            cfg = self.options.stress_options
            lines += [f'VMs / hosts: {cfg.vms} / {cfg.hosts}',
                      f'Search: N{cfg.population} / T{cfg.iterations} / R{cfg.replications}',
                      f"Production: {plan['production']['expected_cases']} cases; {plan['production']['expected_evaluations']} evaluations",
                      f"Calibration: {len(plan['calibration'])} mandatory pilot(s)"]
            for block in plan['calibration']:
                lines += [f"  {block['effective_config']['vms']} VMs: {block['expected_cases']} cases / {block['expected_evaluations']} evaluations"]
            totals = plan['combined_totals']
            lines += [f"Combined: {totals['expected_cases']} cases; {totals['expected_evaluations']} evaluations",
                      f'Deadline: {cfg.time_limit if cfg.time_limit is not None else "unlimited"} seconds',
                      p['time_limit_scope'], 'Static placement workload; no research claims.']
        else:
            n, t, r, scenarios = cloudsim.FROZEN_SETTINGS[self.profile]
            lines += [f"Expected work: {plan['expected_cases']} cases", f'Frozen search: N{n} / T{t} / R{r}; {scenarios}']
        lines += [f'Output parent: {self.options.output_dir}',
                  'Build/tests: SKIPPED (diagnostic)' if self.options.skip_build else 'Build/tests: required before run']
        lines += [warning if warning.startswith('WARNING:') else 'WARNING: '+warning for warning in p['warnings']]
        lines += ['', 'Equivalent CLI:', p['commands']['launcher_shell'], '', 'JVM commands / exact flags:']
        lines += p['commands']['java_shell']
        return lines

    def command(self):
        return shlex.join([str(cloudsim.ROOT/'cloudsim.sh'), *(self.options.arguments if self.options else self.arguments())])

    def badge(self):
        result = self.outcome or {}
        code = result.get('exit_code', 1)
        if result.get('status') == 'timed_out' or 'deadline' in result.get('error', '').lower(): return 'TIMED OUT'
        if result.get('status') == 'interrupted' or code in (130, 143): return 'CANCELLED'
        if code or result.get('status') == 'failed': return 'FAILED'
        if result.get('validation') == 'PASS': return 'VALIDATED'
        if self.action == 'build' and result.get('status') == 'complete': return 'BUILD COMPLETE'
        if self.action == 'test' and result.get('tests') == 'PASS': return 'TESTS PASSED'
        if self.action == 'check' and result.get('status') == 'checked': return 'CHECK PASSED'
        return 'FAILED'

    def draw(self):
        rows, columns = self.screen.getmaxyx()
        self.screen.erase()
        self.write(0, 2, 'CLOUDSIM  /  '+self.state.upper())
        self.write(1, 2, '-'*(columns-5))
        footer = 'Arrows/Tab select   Enter activate   Esc back   q quit'
        if self.state == 'running': footer = 'c cancel job   PgUp/PgDn scroll   End follow   Space pause log'
        elif self.state == 'config': footer = 'Arrows/Tab fields   Enter edit   Ctrl-U clear edit   Esc back'
        elif self.state == 'confirm': footer = 'Tab Back/Start   Enter choose   PgUp/PgDn details   Esc back'
        elif self.state == 'complete': footer = 'Enter Home   PgUp/PgDn details   q quit'
        if rows < 24 or columns < 80:
            self.write(3, 2, 'Resize terminal to at least 80 x 24')
            if self.state == 'running':
                self.write(5, 2, 'Job continues: '+self.dashboard.stage)
                self.write(6, 2, 'c cancel job; Enter Continue / Tab Cancel')
        elif self.state == 'home':
            self.write(3, 2, 'Choose an action')
            for index, item in enumerate(HOME):
                self.write(5+index*2, 2, ('> ' if index == self.home_index else '  ')+item, selected=index == self.home_index)
            if columns >= 100:
                self.write(5, 35, DESCRIPTIONS[self.home_index])
                self.write(7, 35, 'Actions return here when complete.')
                self.write(9, 35, 'Results and full logs are retained on disk.')
            else: self.write(18, 2, DESCRIPTIONS[self.home_index])
            if self.history: self.write(rows-4, 2, f'Last action: {self.badge()} / exit {self.history[-1].get("exit_code")}')
        elif self.state == 'profiles':
            self.write(3, 2, 'Choose a profile to configure')
            for index, item in enumerate(PROFILE_TEXT):
                self.write(5+index*3, 2, ('> ' if index == self.profile_index else '  ')+item, selected=index == self.profile_index)
        elif self.state == 'config':
            self.write(3, 2, f'Configure {self.profile or self.action}')
            fields = self.fields()
            self.focus = min(self.focus, len(fields)-1)
            capacity = rows-10
            offset = max(0, self.focus-capacity+1)
            for index, field in enumerate(fields[offset:offset+capacity], offset):
                value = self.values.get(field, '')
                if field == 'skip_build': value = 'Skip (diagnostic)' if value else 'Build and test'
                elif field == 'advanced': value = 'expanded' if self.advanced else 'collapsed'
                if self.editing and index == self.focus: value = self.buffer+'|'
                if self.editing and index == self.focus:
                    available = (max(60, columns//2)-30) if columns >= 100 else columns-31
                    while cell_width(value) > available: value = value[1:]
                text = ('> ' if index == self.focus else '  ')+f'{LABELS[field]:22} {value}'
                if columns >= 100: text = clip(text, max(60, columns//2)-3)
                self.write(5+index-offset, 2, text, selected=index == self.focus)
            if self.profile and self.profile != 'stress':
                n, t, r, _ = cloudsim.FROZEN_SETTINGS[self.profile]
                self.write(rows-6, 2, f'Frozen read-only: N{n}/T{t}/R{r}; {cloudsim.shared.profile_total(self.profile)} cases')
            elif self.profile == 'stress':
                self.write(rows-7, 2, 'Default: N30/T40/R5; Custom edits search. Both include pilots.')
                caution = (self.values['preset'] == 'xlarge' or
                           self.values['vms'].isdigit() and int(self.values['vms']) >= 20000 or
                           self.values['hosts'].isdigit() and int(self.values['hosts']) >= 4000)
                self.write(rows-6, 2, 'WARNING: XLarge unverified; review RAM caution before Start.' if caution
                           else 'Static placement workload; no research claims.')
            if columns >= 100:
                side = max(62, columns//2)
                for row, text in enumerate(('Resolved choices', f'Heap: {self.values["heap_mib"]} MiB',
                    f'Profile: {self.profile or self.action}',
                    f'VMs / hosts: {self.values["vms"]} / {self.values["hosts"]}' if self.profile == 'stress' else
                    'Frozen protocol settings are read-only.' if self.profile else 'No simulation executes for this action.',
                    'Start requires Review and confirmation.'), 5):
                    self.write(row, side, text)
        elif self.state == 'confirm':
            self.write(3, 2, 'Review settings before starting')
            texts = self.wrapped(self.preview_lines())
            max_scroll = max(0, len(texts)-(rows-9))
            self.detail_scroll = min(self.detail_scroll, max_scroll)
            self.lines(texts[self.detail_scroll:], 5, end=rows-4)
            self.write(rows-4, 2, '[ Back ]', selected=self.confirm_index == 0)
            self.write(rows-4, 16, '[ Start ]', selected=self.confirm_index == 1)
        elif self.state == 'running':
            d = self.dashboard
            p = d.progress
            def measurement(name): return p.get(name) if p.get(name) is not None else 'unavailable'
            elapsed = int(time.monotonic()-d.started)
            self.write(3, 2, f'Stage: {d.stage:<15} Elapsed: {elapsed//60:02}:{elapsed%60:02}')
            self.write(4, 2, f'Cases: {measurement("done")} / {measurement("total")}    Evaluations: {measurement("evaluations")}')
            self.write(5, 2, f'Java RSS: {cloudsim.shared.rss(d.pid) if d.pid else "unavailable"}    Heap: {self.values.get("heap_mib", "unavailable")} MiB')
            self.write(6, 2, f'Algorithm: {measurement("algorithm")}  Scenario: {measurement("scenario")}  Replication: {measurement("replication")}')
            self.write(7, 2, 'Detail: '+str(p.get('detail', 'Awaiting supervisor update')))
            if self.profile == 'stress':
                remaining = p.get('deadline_remaining')
                countdown = f'{remaining:.0f}s' if isinstance(remaining, (int, float)) else 'unlimited' if p.get('deadline_unlimited') else 'unavailable'
                self.write(8, 2, f'Deadline: {self.values.get("time_limit", "unavailable")}; remaining: {countdown}')
            else: self.write(8, 2, 'Validation is a separate stage; child exit zero is not success.')
            warning = '; '.join((self.preview or {}).get('warnings', [])) or d.warning or ''
            self.write(9, 2, 'WARNING: '+warning if warning else '')
            self.write(11, 2, f'Log [{"Follow" if d.follow else "Pause"}]: {d.log or "awaiting log"}')
            size = max(1, rows-16)
            end = max(0, len(d.lines)-(0 if d.follow else d.offset))
            self.lines(d.lines[max(0, end-size):end], 12, end=rows-4)
        elif self.state == 'complete':
            result = self.outcome or {}
            self.write(3, 2, f'{self.badge()}  /  exit {result.get("exit_code", "unavailable")}')
            retained_log = self.dashboard.log
            if not retained_log or not Path(retained_log).exists():
                retained_log = str(Path(result['output_directory'])/'console.log') if result.get('output_directory') else 'unavailable'
            texts = [result.get('error', self.dashboard.progress.get('detail', 'Action complete')),
                     'Retained output: '+str(result.get('output_directory', 'unavailable')),
                     'Run directory: '+str(result.get('run_directory', 'unavailable')),
                     'Log: '+str(retained_log),
                     'Validation: '+str(result.get('validation', 'NOT_RUN')),
                     'Tests: '+str(result.get('tests', 'NOT_RUN'))]
            validation = result.get('validation_result')
            if isinstance(validation, dict):
                binding = str(validation.get('artifact_binding', 'unavailable'))
                texts += ['Validation scope: '+str(validation.get('scope', 'unavailable')),
                          ('WARNING: ' if binding.startswith('unavailable') else '')+'Artifact binding: '+binding,
                          'Validation limitations: '+str(validation.get('limitations', 'unavailable')),
                          'Validated runs:']
                texts += [str(path) for path in validation.get('validated_runs', [])]
            texts += [str(result.get('ui_warning') or ''), str(result.get('diagnostic') and 'WARNING: source/artifact diagnostics' or '')]
            texts += ['WARNING: '+warning for warning in (self.preview or {}).get('warnings', [])]
            if result.get('claims') is not None and result.get('validation') == 'PASS':
                texts += [f'Validated claims: {result["claims"]}; NO_CLAIM: {result.get("no_claim", "unavailable")}']
            texts += ['', 'Log tail:', *self.dashboard.lines]
            texts = self.wrapped(texts)
            self.detail_scroll = min(self.detail_scroll, max(0, len(texts)-(rows-9)))
            self.lines(texts[self.detail_scroll:], 5)
        if self.error and rows >= 24: self.write(rows-4, 2, 'ERROR: '+self.error)
        if self.cancel_dialog:
            self.write(max(2, rows-4), 2, 'Cancel active job?', selected=True)
            self.write(max(3, rows-3), 2, '[ Continue ]', selected=self.cancel_index == 0)
            self.write(max(3, rows-3), 20, '[ Cancel job ]', selected=self.cancel_index == 1)
        self.write(rows-2, 2, footer)
        self.screen.refresh()
        self.drawn = time.monotonic()
        self.dirty = False

    def loop(self):
        handlers = {}
        def on_signal(number, frame): self.pending_signal = number
        try:
            for number in (signal.SIGINT, signal.SIGTERM):
                handlers[number] = signal.signal(number, on_signal)
            while not self.exit_requested:
                if self.pending_signal: return 128+self.pending_signal if self.pending_signal == signal.SIGTERM else 0
                key = read_key(self.screen)
                if key is not None: self.handle_key(key)
                if self.dirty or time.monotonic()-self.drawn >= 1: self.draw()
                time.sleep(.025)
            return 143 if self.exit_after_job else 0
        finally:
            for number, handler in handlers.items(): signal.signal(number, handler)


def run():
    alternative = 'Use ./cloudsim.sh --plain --check or --profile smoke|explore|research|stress.'
    if curses is None or os.environ.get('TERM', 'dumb') in ('', 'dumb'):
        print('ERROR: interactive terminal requires curses and a capable TERM. '+alternative, file=sys.stderr)
        return 2
    def application(screen):
        try: curses.curs_set(0)
        except curses.error: pass
        return Application(screen).loop()
    try: return curses.wrapper(application)
    except (curses.error, OSError, ValueError) as error:
        print('ERROR: interactive terminal unavailable: '+cloudsim.shared.sanitize(error)+'. '+alternative, file=sys.stderr)
        return 2
