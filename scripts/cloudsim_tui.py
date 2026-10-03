"""Task-oriented terminal frontend; scientific execution stays in supervised workers."""
import asyncio
import contextlib
import io
import json
import os
from pathlib import Path
import signal
import sys
import time

from textual import on, work
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Horizontal, Vertical, VerticalScroll
from textual.screen import ModalScreen
from textual.theme import Theme
from textual.widgets import (Button, Checkbox, Collapsible, ContentSwitcher, DataTable,
                             Footer, Header, Input, Label, ProgressBar, RichLog,
                             Select, Static, TabbedContent, TabPane, TextArea)

import cloudsim
import cloudsim_runtime as runtime

ROOT = cloudsim.ROOT
PROFILE_NAMES = {'smoke':'Smoke · 4 cases', 'explore':'Explore · 40 cases',
                 'research':'Research · 450 cases', 'stress':'Static Stress'}
DESCRIPTIONS = {'smoke':'A short check of the frozen protocol. No research claims.',
                'explore':'A broader frozen protocol check. No research claims.',
                'research':'The frozen main and sensitivity matrix, with independent validation.',
                'stress':'Static placement at a chosen size, including calibration. Descriptive results only.'}
FIELDS = ('heap', 'output', 'config-path', 'vms', 'hosts', 'population', 'iterations',
          'replications', 'seed', 'deadline')


def clean(value): return '\n'.join(cloudsim.shared.sanitize(line) for line in str(value).splitlines())


def log_tail(path, max_bytes=65536, max_lines=500):
    if not path: return ''
    try:
        with Path(path).open('rb') as stream:
            stream.seek(0, os.SEEK_END)
            stream.seek(max(0, stream.tell()-max_bytes))
            data = stream.read(max_bytes)
        return '\n'.join(clean(line) for line in data.decode('utf-8', 'replace').splitlines()[-max_lines:])
    except OSError: return ''


class Confirm(ModalScreen[bool]):
    DEFAULT_CSS = '''
    Confirm { align: center middle; background: $background 70%; }
    Confirm > Vertical { width: 80%; max-width: 64; height: auto; padding: 1 2; border: solid $primary; background: $surface; }
    Confirm Static { height: auto; margin-bottom: 1; }
    Confirm Horizontal { height: 3; }
    Confirm Button { width: 1fr; margin-right: 1; }
    '''
    BINDINGS = [('escape', 'back', 'Back')]

    def __init__(self, message, action):
        super().__init__(); self.message, self.action_name = message, action

    def compose(self):
        with Vertical():
            yield Static(self.message, markup=False)
            with Horizontal():
                yield Button('Keep working', id='back')
                yield Button(self.action_name, variant='error', id='confirm')

    def on_mount(self): self.query_one('#back').focus()
    def action_back(self): self.dismiss(False)
    def on_button_pressed(self, event): self.dismiss(event.button.id == 'confirm')


class LogView(ModalScreen):
    DEFAULT_CSS = '''
    LogView { align: center middle; background: $background 70%; }
    LogView > Vertical { width: 94%; height: 90%; padding: 1; border: solid $primary; background: $surface; }
    LogView Static { height: auto; max-height: 3; }
    LogView TextArea { height: 1fr; }
    LogView Button { height: 3; }
    '''
    BINDINGS = [('escape', 'close', 'Close')]

    def __init__(self, title, text): super().__init__(); self.title_text, self.text = title, text
    def compose(self):
        with Vertical():
            yield Static(clean(self.title_text), markup=False)
            yield TextArea(self.text, read_only=True, show_line_numbers=False)
            yield Button('Close', id='close')
    def action_close(self): self.dismiss()
    def on_button_pressed(self, event): self.dismiss()


class CloudSimApp(App):
    TITLE = 'CloudSim'
    SUB_TITLE = 'Experiment console'
    ENABLE_COMMAND_PALETTE = False
    CSS = '''
    Screen { background: $background; }
    Header { height: 1; background: $surface; }
    Footer { background: $surface; }
    #readiness { height: auto; max-height: 3; padding: 0 2; color: $text-muted; background: $surface; }
    #readiness.ready { color: $success; }
    #readiness.blocked { color: $warning; }
    TabbedContent { height: 1fr; }
    TabPane { height: 1fr; padding: 0 1; }
    ContentSwitcher, #configure, #progress, #result { height: 1fr; }
    ContentSwitcher { align-horizontal: center; }
    #configure, #progress, #result { width: 100%; max-width: 100; }
    VerticalScroll { height: 1fr; }
    .heading { height: auto; margin: 1 0 0 0; text-style: bold; color: $text; }
    Label { height: 1; margin: 1 0 0 0; color: $text-muted; }
    Input, Select { height: 3; margin: 0; }
    .pair { height: auto; }
    .pair > Vertical { height: auto; width: 1fr; padding-right: 1; }
    .actions { height: 3; margin-top: 1; }
    .actions Button { width: 1fr; min-width: 10; margin-right: 1; }
    #start { width: 28; }
    #fix-setup { width: 16; }
    #run-description, #summary, #setup-status, #setup-copy, #result-details, #stage-detail, #measurements { height: auto; }
    #run-description { color: $text-muted; margin-bottom: 1; }
    #summary { color: $text-muted; margin: 1 0; }
    #form-error { height: auto; max-height: 4; color: $error; display: none; }
    #outcome, #stage-title { height: auto; text-style: bold; margin-top: 1; }
    #outcome.success { color: $success; }
    #outcome.failure { color: $error; }
    #result-details, #stage-detail { margin: 1 0; }
    #stage-detail { max-height: 4; }
    #measurements { color: $text-muted; }
    ProgressBar { height: 1; margin: 1 0; }
    #live-log { height: 1fr; min-height: 4; min-width: 0; border: solid $panel; background: $surface; }
    #recent { height: 1fr; min-height: 5; margin-top: 1; }
    #results-detail { height: auto; max-height: 4; color: $text-muted; }
    #setup-copy { margin: 1 0; color: $text-muted; }
    Collapsible { padding: 0; margin-top: 1; }
    #stress-settings, #custom-search, #stress-advanced, #frozen-config { height: auto; }
    '''
    BINDINGS = [('ctrl+n', 'edit', 'New run'), ('ctrl+c', 'cancel', 'Cancel'),
                ('ctrl+q', 'quit_requested', 'Quit'), ('f1', 'help', 'Help'),
                Binding('f2', "tab('run')", 'Run', show=False), Binding('f3', "tab('results')", 'Results', show=False),
                Binding('f4', "tab('tools')", 'Tools', show=False), Binding('f5', "tab('setup')", 'Setup', show=False),
                Binding('ctrl+r', 'start', 'Start', show=False), Binding('q', 'quit_requested', 'Quit', show=False),
                Binding('ctrl+1', "tab('run')", 'Run', show=False), Binding('ctrl+2', "tab('results')", 'Results', show=False),
                Binding('ctrl+3', "tab('tools')", 'Tools', show=False), Binding('ctrl+4', "tab('setup')", 'Setup', show=False)]

    def __init__(self, output_parent=None):
        super().__init__()
        self.output_parent = Path(output_parent or ROOT/'results').resolve()
        self.ready = False
        self.busy = True
        self.process = None
        self.child_pid = None
        self.child_identity = None
        self.pending_java = None
        self.exit_after_job = False
        self.exit_code = 0
        self.java_override = None
        self.current_profile = 'smoke'
        self.saved = {}
        self.preset_previous = 'small'
        self.last_args = []
        self.last_action = 'profile'
        self.outcome = {}
        self.outcome_log = None
        self.active_directory = None
        self.recent = {}
        self.log_path = None
        self.log_text = ''
        self.follow = True
        self.started = None
        self.progress = {}
        self.stage = ''
        self.signal_handlers = {}
        self.register_theme(Theme(name='cloudsim', primary='#639ed6', secondary='#7b96ae',
            accent='#639ed6', foreground='#e2e8ef', background='#10161e', surface='#18212c',
            panel='#243142', success='#9acb98', warning='#e9c179', error='#ee9999', dark=True))
        self.theme = 'cloudsim'

    def field(self, title, name, value=''):
        yield Label(title)
        yield Input(value, id=name)

    def compose(self) -> ComposeResult:
        yield Header()
        yield Static('Checking setup…', id='readiness', markup=False)
        with TabbedContent(initial='run'):
            with TabPane('Run', id='run'):
                with ContentSwitcher(initial='configure', id='flow'):
                    with Vertical(id='configure'):
                        with VerticalScroll(id='form'):
                            yield Static('Run an experiment', classes='heading')
                            yield Label('Profile')
                            yield Select([(v,k) for k,v in PROFILE_NAMES.items()], value='smoke', allow_blank=False, id='profile')
                            yield Static(DESCRIPTIONS['smoke'], id='run-description', markup=False)
                            with Vertical(id='stress-settings'):
                                yield Label('Size preset')
                                yield Select([(f'{k.title()} · {v:,} VMs / {h:,} hosts',k) for k,(v,h) in cloudsim.stress.PRESETS.items()],
                                             value='small', allow_blank=False, id='preset')
                                with Horizontal(classes='pair'):
                                    with Vertical(): yield from self.field('Virtual machines', 'vms', '500')
                                    with Vertical(): yield from self.field('Hosts', 'hosts', '100')
                            yield Static('', id='summary', markup=False)
                            with Collapsible(title='Advanced settings', id='advanced'):
                                yield from self.field('Maximum Java heap (MiB)', 'heap', '1024')
                                yield from self.field('Save results under', 'output', str(self.output_parent))
                                yield Checkbox('Use existing build (diagnostic; skip build/tests)', id='skip-build')
                                with Vertical(id='frozen-config'):
                                    yield from self.field('Optional properties file (seed / log level only)', 'config-path')
                                with Vertical(id='stress-advanced'):
                                    yield Label('Search settings')
                                    yield Select([('Default · N30 / T40 / R5','default'),('Custom','custom')],
                                                 value='default', allow_blank=False, id='search')
                                    with Vertical(id='custom-search'):
                                        with Horizontal(classes='pair'):
                                            with Vertical(): yield from self.field('Population (N)', 'population', '30')
                                            with Vertical(): yield from self.field('Iterations (T)', 'iterations', '40')
                                            with Vertical(): yield from self.field('Replications (R)', 'replications', '5')
                                    yield from self.field('Master seed', 'seed', '123456')
                                    yield from self.field('Experiment deadline (e.g. 2h, 4h, none)', 'deadline', '2h')
                        yield Static('', id='form-error', markup=False)
                        with Horizontal(classes='actions'):
                            yield Button('Start experiment', variant='primary', id='start', disabled=True)
                            yield Button('Fix setup', id='fix-setup')
                    with Vertical(id='progress'):
                        yield Static('', id='stage-title', markup=False)
                        yield Static('', id='measurements', markup=False)
                        yield ProgressBar(total=None, show_eta=False, id='bar')
                        yield Static('', id='stage-detail', markup=False)
                        yield RichLog(max_lines=500, min_width=0, wrap=True, id='live-log')
                        with Horizontal(classes='actions'):
                            yield Button('Pause log', id='follow')
                            yield Button('Cancel job', variant='error', id='cancel-job')
                    with Vertical(id='result'):
                        with VerticalScroll():
                            yield Static('', id='outcome', markup=False)
                            yield Static('', id='result-details', markup=False)
                            with Collapsible(title='Commands and full metadata'):
                                yield Static('', id='metadata', markup=False)
                        with Horizontal(classes='actions'):
                            yield Button('Edit settings', id='edit')
                            yield Button('Retry', id='retry')
                            yield Button('Fix setup', id='result-setup')
                            yield Button('View log', id='view-log')
            with TabPane('Results', id='results'):
                yield Static('Retained runs', classes='heading')
                yield Static('Refresh reads only known results folders. Select a run to inspect or validate it.', markup=False)
                yield DataTable(id='recent', cursor_type='row', zebra_stripes=True)
                yield Static('', id='results-detail', markup=False)
                with Horizontal(classes='actions'):
                    yield Button('Refresh', id='refresh-results')
                    yield Button('Details', id='result-details-button')
                    yield Button('Validate selected', id='validate-selected')
            with TabPane('Tools', id='tools'):
                with VerticalScroll():
                    yield Static('Project tools', classes='heading')
                    yield Static('Checks and build logs are retained. Validation preserves the selected evidence.', markup=False)
                    with Horizontal(classes='actions'):
                        yield Button('Check setup', id='check')
                        yield Button('Build', id='build')
                        yield Button('Run tests', id='test')
                    yield from self.field('Existing run or campaign directory', 'validate-path')
                    yield Button('Validate directory', variant='primary', id='validate')
            with TabPane('Setup', id='setup'):
                with VerticalScroll():
                    yield Static('Environment setup', classes='heading')
                    yield Static('Checking Java 21 and usable memory…', id='setup-status', markup=False)
                    yield Static('Install a full JDK 21 inside this project, or select one you already have. '
                                 'The download is checksum-verified. System Java and shell settings are preserved.',
                                 id='setup-copy', markup=False)
                    yield Button('Install local JDK 21 (198 MiB)', variant='primary', id='install-jdk')
                    yield from self.field('Installed JDK 21 directory', 'jdk-path')
                    with Horizontal(classes='actions'):
                        yield Button('Use this JDK', id='select-jdk')
                        yield Button('Check again', id='check-again')
        yield Footer()

    def on_mount(self):
        self.query_one('#recent', DataTable).add_columns('Run / action', 'Outcome', 'Validation')
        self.profile_visibility()
        self.update_summary()
        self.set_interval(1, self.tick)
        loop = asyncio.get_running_loop()
        for number in (signal.SIGTERM, signal.SIGINT):
            self.signal_handlers[number] = signal.getsignal(number)
            loop.add_signal_handler(number, self.signal_stop, number)
        self.check_readiness()

    def set_busy(self, busy):
        self.busy = busy
        for name in ('check','build','test','validate','check-again','install-jdk','select-jdk',
                     'validate-selected','result-details-button','retry','edit'):
            self.query_one('#'+name, Button).disabled = busy
        self.query_one('#start', Button).disabled = busy or not self.ready

    def signal_stop(self, number):
        if number == signal.SIGTERM: self.exit_after_job = True; self.exit_code = 143
        if self.busy and self.process and self.process.returncode is None: self.process.send_signal(number)
        elif number == signal.SIGTERM: self.exit(143)

    async def on_unmount(self):
        await self.stop_worker()
        loop = asyncio.get_running_loop()
        for number, handler in self.signal_handlers.items():
            loop.remove_signal_handler(number); signal.signal(number, handler)

    async def stop_worker(self):
        process = self.process
        if not process: return
        if process.returncode is None:
            try: process.send_signal(signal.SIGTERM)
            except ProcessLookupError: pass
            try: await asyncio.wait_for(process.wait(), 8)
            except asyncio.TimeoutError:
                self.stop_owned_child()
                process.kill(); await process.wait()
        self.stop_owned_child()

    def track_child(self, event):
        self.child_pid = event.get('pid')
        self.child_identity = None
        if not self.child_pid or not self.process: return
        identity = event.get('identity') or runtime.owned_child_identity(self.child_pid, self.process.pid)
        if identity and identity.get('pid')==self.child_pid and identity.get('parent')==self.process.pid:
            self.child_identity = identity

    def stop_owned_child(self):
        identity = self.child_identity
        if not identity: return
        try:
            leader = Path(f'/proc/{identity["pid"]}/stat')
            if leader.exists():
                fields = leader.read_text().rsplit(')',1)[1].split()
                if fields[19] != identity['start_time']: return  # A reused PID is never owned.
            # A Linux process group/session keeps its ID while descendants remain,
            # even after its leader is reaped. Check surviving membership too.
            for path in Path('/proc').glob('[0-9]*/stat'):
                try: fields = path.read_text().rsplit(')',1)[1].split()
                except OSError: continue
                if int(fields[2]) == identity['pid'] and int(fields[3]) == identity['pid'] and int(fields[19]) >= int(identity['start_time']):
                    os.killpg(identity['pid'], signal.SIGKILL); break
        except (OSError, ValueError, TypeError, IndexError): pass
        self.child_identity = None

    def action_tab(self, tab): self.query_one(TabbedContent).active = tab
    def action_setup(self): self.action_tab('setup')
    def action_edit(self):
        if self.busy: return
        self.action_tab('run')
        self.query_one('#flow', ContentSwitcher).current = 'configure'
        self.query_one('#profile').focus()

    def action_help(self):
        self.push_screen(LogView('CloudSim help',
            'Run: choose a profile and start. Tab moves between controls; Enter activates; arrow keys select.\n'
            'Advanced settings hold heap, output folder and diagnostic build options.\n'
            'F2 / F3 / F4 / F5: Run / Results / Tools / Setup. Ctrl+N: edit or start another run.\n'
            'Ctrl+C: confirm cancellation. Ctrl+Q: quit. Esc closes a dialog.\n'
            'Results are validated independently. Build/test success is a separate status.\n'
            'CLI automation: ./cloudsim.sh --help'))

    def action_quit_requested(self):
        if not self.busy: self.exit(0); return
        def decision(confirmed):
            if confirmed:
                self.exit_after_job = True
                if self.process and self.process.returncode is None: self.process.send_signal(signal.SIGTERM)
        self.push_screen(Confirm('A job is running. Cancel it and close CloudSim?', 'Cancel & quit'), decision)

    def action_cancel(self):
        if not self.busy or not self.process: return
        self.push_screen(Confirm('Cancel this job? Partial results and logs will be kept.', 'Cancel job'),
                         lambda confirmed: self.process.send_signal(signal.SIGINT) if confirmed and self.process and self.process.returncode is None else None)

    @on(Select.Changed, '#profile')
    def change_profile(self, event):
        if event.value == self.current_profile: return
        self.saved[self.current_profile] = {name:self.query_one('#'+name, Input).value for name in FIELDS}
        self.saved[self.current_profile].update(preset=str(self.query_one('#preset', Select).value),
            search=str(self.query_one('#search', Select).value), skip=self.query_one('#skip-build', Checkbox).value)
        self.current_profile = str(event.value)
        values = self.saved.get(self.current_profile)
        if values:
            self.preset_previous = values['preset']
            self.query_one('#preset', Select).value = values['preset']
            self.query_one('#search', Select).value = values['search']
            self.query_one('#skip-build', Checkbox).value = values['skip']
            for name in FIELDS: self.query_one('#'+name, Input).value = values[name]
        self.profile_visibility(); self.update_summary()

    @on(Select.Changed, '#preset')
    def change_preset(self, event):
        if event.value == self.preset_previous: return
        self.preset_previous = str(event.value)
        vms, hosts = cloudsim.stress.PRESETS[self.preset_previous]
        self.query_one('#vms', Input).value = str(vms)
        self.query_one('#hosts', Input).value = str(hosts)
        self.update_summary()

    @on(Select.Changed, '#search')
    def change_search(self, event): self.profile_visibility(); self.update_summary()
    def on_input_changed(self, event):
        if event.input.id in FIELDS: self.update_summary()
    def on_checkbox_changed(self, event): self.update_summary()

    def profile_visibility(self):
        stress = self.current_profile == 'stress'
        self.query_one('#stress-settings').display = stress
        self.query_one('#stress-advanced').display = stress
        self.query_one('#frozen-config').display = not stress
        self.query_one('#custom-search').display = self.query_one('#search', Select).value == 'custom'
        self.query_one('#run-description', Static).update(DESCRIPTIONS[self.current_profile])

    def arguments(self):
        get = lambda name: self.query_one('#'+name, Input).value
        arguments = ['--profile', self.current_profile, '--heap-mib', get('heap'), '--output-dir', get('output'), '--plain']
        if self.query_one('#skip-build', Checkbox).value: arguments.append('--skip-build')
        if self.current_profile == 'stress':
            arguments += ['--preset', str(self.query_one('#preset', Select).value), '--vms', get('vms'),
                          '--hosts', get('hosts'), '--seed', get('seed'), '--time-limit', get('deadline')]
            if self.query_one('#search', Select).value == 'custom':
                for name in ('population','iterations','replications'): arguments += ['--'+name, get(name)]
        elif get('config-path'): arguments += ['--config', get('config-path')]
        return arguments

    def parsed(self, arguments):
        errors = io.StringIO()
        with contextlib.redirect_stderr(errors):
            try: return cloudsim.parse_args(arguments)
            except SystemExit:
                raise ValueError(errors.getvalue().split('error:')[-1].strip()) from None

    def update_summary(self):
        if not self.is_mounted: return
        try:
            options = self.parsed(self.arguments())
            preview = cloudsim.preview(options)
            if self.current_profile == 'stress':
                work = preview['planned_work']['combined_totals']['expected_cases']
                text = f'{options.vms:,} VMs / {options.hosts:,} hosts · N{options.population}/T{options.iterations}/R{options.replications}\n'
                text += f'{work} cases including calibration · deadline {self.query_one("#deadline", Input).value}'
            else:
                n,t,r,_ = cloudsim.FROZEN_SETTINGS[self.current_profile]
                text = f'{cloudsim.shared.profile_total(self.current_profile)} cases · frozen N{n}/T{t}/R{r}'
            text += '\nBuild and tests, then simulation and independent validation.' if not options.skip_build else '\nDiagnostic run: build and tests skipped.'
            if preview['warnings']: text += '\n'+ '\n'.join(preview['warnings'])
        except (ValueError, OSError, KeyError): text = 'Complete the settings to preview the work. Start will check every value.'
        self.query_one('#summary', Static).update(clean(text))

    async def action_start(self): await self.start_action()
    async def start_action(self):
        if self.busy: return
        arguments = self.arguments()
        try: self.parsed(arguments)
        except (ValueError, OSError) as error:
            box = self.query_one('#form-error', Static); box.update(clean(error)); box.display = True
            flags = {'heap-mib':'heap', 'output-dir':'output', 'time-limit':'deadline', 'config':'config-path',
                     **{name:name for name in ('vms','hosts','seed','population','iterations','replications')}}
            field = next((name for flag,name in flags.items() if flag in str(error)), 'heap')
            self.query_one('#advanced', Collapsible).collapsed = False
            self.query_one('#'+field, Input).focus(); return
        if not self.ready: self.action_setup(); return
        self.query_one('#form-error').display = False
        self.last_args = arguments; self.last_action = 'profile'
        self.begin(arguments)

    def begin(self, arguments):
        if self.busy: return
        self.set_busy(True)
        self.action_tab('run')
        self.query_one('#flow', ContentSwitcher).current = 'progress'
        self.started = time.monotonic(); self.stage = 'preflight'; self.progress = {}
        self.log_path = None; self.log_text = ''; self.follow = True
        self.query_one('#live-log', RichLog).clear()
        self.query_one('#follow', Button).label = 'Pause log'
        self.run_job(arguments)

    def check_readiness(self):
        if self.process and self.process.returncode is None: return
        self.set_busy(True)
        self.ready = False
        self.query_one('#start', Button).disabled = True
        self.query_one('#readiness', Static).update('Checking Java 21 and usable memory…')
        self.run_job(['--check','--plain','--output-dir',str(self.output_parent)], readiness=True)

    @work(exclusive=True, group='execution', exit_on_error=False)
    async def run_job(self, arguments, readiness=False):
        self.set_busy(True)
        self.active_directory = None
        stderr = bytearray(); result = None
        env = dict(os.environ)
        if self.java_override: env['JAVA_HOME'] = str(self.java_override)
        try:
            self.process = await asyncio.create_subprocess_exec(sys.executable, '-B', str(ROOT/'scripts/cloudsim_worker.py'),
                *arguments, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
                stdin=asyncio.subprocess.DEVNULL, env=env, limit=1024*1024)
            async def errors():
                while chunk := await self.process.stderr.read(8192):
                    stderr.extend(chunk); del stderr[:-65536]
            error_task = asyncio.create_task(errors())
            try:
                async for line in self.process.stdout:
                    event = json.loads(line)
                    kind = event.get('event')
                    if kind == 'progress' and not readiness:
                        self.stage = event['stage']; self.progress = event.get('progress', {}); self.tick()
                    elif kind == 'child': self.track_child(event)
                    elif kind == 'log':
                        self.active_directory = Path(event['output_directory']) if event.get('output_directory') else self.active_directory
                        if not readiness: self.log_path = Path(event['path'])
                    elif kind == 'complete': result = event['metadata']
                code = await self.process.wait()
                if code < 0: code = 128-code
                await self.stop_worker()
            finally:
                if self.process.returncode is None: error_task.cancel()
                await asyncio.gather(error_task, return_exceptions=True)
            if not result:
                result = self.failed_worker(code or 1, 'Runner completion is missing. '+clean(stderr.decode('utf-8','replace')))
            if code != result.get('exit_code'):
                result.update(status='failed', exit_code=code or 1, validation='NOT_RUN',
                              error='Runner exit and completion record disagree. Inspect the retained logs.')
        except asyncio.CancelledError:
            await self.stop_worker(); raise
        except Exception as error:
            await self.stop_worker()
            result = self.failed_worker(1, clean(error))
        finally:
            self.set_busy(False); self.child_pid = None; self.child_identity = None
        self.record_result(result)
        if readiness:
            self.ready = result.get('status')=='checked' and result.get('exit_code')==0
            if self.pending_java:
                if self.ready: runtime.save_java_home(self.java_override)
                else: self.java_override = self.pending_java[0]
                self.pending_java = None
            message = ('Ready · Java 21 · '+str(result.get('usable_memory_bytes',0)//cloudsim.shared.MIB)+' MiB usable memory') if self.ready else 'Setup needs attention · open Setup to resolve it'
            banner = self.query_one('#readiness', Static); banner.update(message)
            banner.set_class(self.ready, 'ready'); banner.set_class(not self.ready, 'blocked')
            self.query_one('#setup-status', Static).update(clean(result.get('error') or
                'Java 21 is ready.\nSelected Java: '+str(result.get('java'))+'\nUsable memory: '+str(result.get('usable_memory_bytes',0)//cloudsim.shared.MIB)+' MiB'))
            self.query_one('#start', Button).disabled = not self.ready
            self.query_one('#fix-setup').display = not self.ready
        else:
            self.show_result(result)
            self.query_one('#start', Button).disabled = not self.ready
            if result.get('action') == 'setup' and result.get('exit_code')==0:
                self.pending_java = (self.java_override,)
                self.java_override = ROOT/'.cloudsim/jdk'
                self.call_later(self.check_readiness)
        if self.exit_after_job: self.exit(self.exit_code)

    def failed_worker(self, code, error):
        result = {}
        directory = self.active_directory
        if directory:
            path = directory/'runner.json'
            try:
                if path.stat().st_size <= 1024*1024:
                    saved = json.loads(path.read_text())
                    if isinstance(saved,dict): result = saved
            except (OSError,ValueError): pass
            result['output_directory'] = str(directory)
        result.update(status='failed',exit_code=code,validation='NOT_RUN',error=error,finished_at=cloudsim.shared.stamp())
        if directory:
            try: cloudsim.shared.save_metadata(directory,result)
            except OSError as failure: result['error'] += '\nCould not save the outcome: '+clean(failure)
        return result

    def tick(self):
        if not self.is_mounted or not self.started: return
        elapsed = int(time.monotonic()-self.started)
        self.query_one('#stage-title', Static).update(f'{self.stage.replace("-", " ").title()} · {elapsed//60:02}:{elapsed%60:02} elapsed')
        p = self.progress
        values = []
        if p.get('done') is not None and p.get('total'):
            values.append(f'{p["done"]}/{p["total"]} cases')
            self.query_one('#bar', ProgressBar).update(total=p['total'], progress=p['done'])
        else: self.query_one('#bar', ProgressBar).update(total=None)
        if p.get('evaluations') is not None: values.append(f'{p["evaluations"]} evaluations')
        for name in ('algorithm','scenario','replication'):
            if p.get(name) is not None: values.append(f'{name}: {p[name]}')
        if p.get('deadline_remaining') is not None: values.append(f'{int(p["deadline_remaining"])}s left')
        self.query_one('#measurements', Static).update(clean(' · '.join(values)))
        self.query_one('#stage-detail', Static).update(clean(p.get('detail', 'Checking prerequisites…')))
        if self.follow and self.log_path:
            text = log_tail(self.log_path)
            if text != self.log_text:
                self.log_text = text
                log = self.query_one('#live-log', RichLog); log.clear(); log.write(text)

    def show_result(self, metadata, recorded=False):
        self.outcome = metadata
        directory = Path(metadata['output_directory']) if metadata.get('output_directory') else None
        candidate = metadata.get('log') or (None if recorded else self.log_path)
        candidate = Path(candidate) if candidate else None
        self.outcome_log = candidate if candidate and directory and candidate.resolve().is_relative_to(directory.resolve()) and candidate.is_file() else directory/'console.log' if directory else None
        self.query_one('#flow', ContentSwitcher).current = 'result'
        okay = metadata.get('exit_code')==0 and metadata.get('status') in ('complete','checked')
        profile = metadata.get('profile')
        if profile: okay = okay and metadata.get('validation')=='PASS'
        title = ('Validated' if profile else 'Setup ready' if metadata.get('status')=='checked' else 'Action complete') if okay else 'Cancelled' if metadata.get('status')=='interrupted' else 'Could not complete the job'
        heading = self.query_one('#outcome', Static); heading.update(title)
        heading.set_class(okay,'success'); heading.set_class(not okay,'failure')
        detail = [metadata.get('error') or (None if recorded else self.progress.get('detail')) or title]
        if metadata.get('output_directory'): detail += ['', 'Saved output', metadata['output_directory']]
        if profile: detail += ['', 'Independent validation: '+str(metadata.get('validation','NOT_RUN'))]
        detail += ['Build/tests: '+str(metadata.get('tests','NOT_RUN'))]
        if metadata.get('diagnostic'): detail += ['Diagnostic result: source, retained artifact or skipped build differs from a clean-source run.']
        if profile == 'stress': detail += ['Static stress results are descriptive; no frozen research claims.']
        if profile in ('smoke','explore'): detail += ['Frozen protocol check; no research claims.']
        if metadata.get('claims') is not None: detail += [f'Research decisions: {metadata["claims"]} CLAIM / {metadata.get("no_claim",0)} NO_CLAIM']
        validation = metadata.get('validation_result')
        if isinstance(validation, dict):
            detail += [validation.get('scope',''), validation.get('artifact_binding',''), validation.get('limitations','')]
        self.query_one('#result-details', Static).update('\n'.join(clean(line) for line in detail))
        self.query_one('#metadata', Static).update(clean(json.dumps(metadata, indent=2)))
        self.query_one('#retry', Button).disabled = recorded or not self.last_args
        if not okay and 'Java' in str(metadata.get('error')):
            self.ready = False
            self.query_one('#readiness', Static).update('Setup needs attention · open Setup to resolve it')
        self.tick()

    def record_result(self, metadata):
        directory = metadata.get('output_directory')
        if directory: self.recent[str(directory)] = metadata

    def refresh_results(self):
        parents = {self.output_parent, ROOT/'results', ROOT/'results/stress'}
        value = self.query_one('#output', Input).value
        if value: parents.add(Path(value).expanduser().resolve())
        records = []
        for parent in parents:
            try:
                paths = sorted(parent.glob('*/runner.json'), key=lambda p:p.stat().st_mtime, reverse=True)[:100]
                for path in paths:
                    if path.is_symlink() or path.stat().st_size>1024*1024: continue
                    try:
                        data = json.loads(path.read_text())
                        if isinstance(data,dict):
                            data['output_directory'] = str(path.parent.resolve()); records.append(data)
                    except (OSError, ValueError): continue
            except OSError: continue
        for data in records: self.record_result(data)
        table = self.query_one('#recent', DataTable); table.clear()
        for path, data in list(self.recent.items())[-100:][::-1]:
            label = data.get('profile') or data.get('action') or Path(path).name
            table.add_row(clean(label), clean(data.get('status','unknown')), clean(data.get('validation','NOT_RUN')), key=path)
        self.query_one('#results-detail', Static).update(f'{table.row_count} retained actions. Select a row; Details includes the saved path.')

    def selected_result(self):
        table = self.query_one('#recent', DataTable)
        if not table.row_count: return None
        key = table.coordinate_to_cell_key(table.cursor_coordinate).row_key.value
        return self.recent.get(key)

    @on(TabbedContent.TabActivated)
    def activated(self, event):
        if event.pane.id == 'results': self.refresh_results()

    def tool(self, action, directory=None):
        if self.busy: self.notify('Wait for the current job, or cancel it first.'); return
        arguments = ['--'+action]
        if directory: arguments.append(str(directory))
        arguments += ['--plain','--output-dir',str(self.output_parent)]
        if action not in ('validate','setup'): arguments += ['--heap-mib',self.query_one('#heap', Input).value]
        try: self.parsed(arguments)
        except (ValueError, OSError) as error: self.notify(clean(error), severity='error'); return
        self.last_args = arguments; self.last_action = action; self.begin(arguments)

    async def on_button_pressed(self, event):
        button = event.button.id
        if button == 'start': await self.start_action()
        elif button in ('fix-setup','result-setup'): self.action_setup()
        elif button == 'edit': self.action_edit()
        elif button == 'retry' and not self.busy and self.last_args: self.begin(self.last_args)
        elif button == 'follow':
            self.follow = not self.follow
            event.button.label = 'Pause log' if self.follow else 'Follow log'; self.tick()
        elif button == 'cancel-job': self.action_cancel()
        elif button in ('check','build','test'): self.tool(button)
        elif button == 'validate':
            value = self.query_one('#validate-path', Input).value
            if value: self.tool('validate', Path(value).expanduser())
            else: self.notify('Enter an existing run or campaign directory.', severity='warning')
        elif button == 'check-again' and not self.busy: self.check_readiness()
        elif button == 'install-jdk' and not self.busy:
            self.push_screen(Confirm('Download and install a verified JDK 21 (198 MiB) inside .cloudsim? System Java settings will be preserved.', 'Install JDK 21'),
                lambda confirmed: self.tool('setup') if confirmed else None)
        elif button == 'select-jdk' and not self.busy:
            value = self.query_one('#jdk-path', Input).value
            if not value:
                self.notify('Enter the JDK directory containing bin/java and bin/javac.', severity='warning'); return
            self.pending_java = (self.java_override,)
            self.java_override = Path(value).expanduser().resolve()
            self.check_readiness()
        elif button == 'refresh-results': self.refresh_results()
        elif button in ('result-details-button','validate-selected'):
            selected = self.selected_result()
            if not selected: self.notify('Refresh and select a retained run first.'); return
            if button == 'validate-selected': self.tool('validate', Path(selected['output_directory']))
            else:
                self.action_tab('run'); self.show_result(selected, recorded=True)
        elif button == 'view-log':
            path = self.outcome_log
            self.push_screen(LogView(str(path or 'No retained log available'), log_tail(path)))


def run():
    if os.environ.get('TERM', 'dumb') in ('', 'dumb'):
        print('A capable terminal is required. Use ./cloudsim.sh --plain --check or --profile smoke|explore|research|stress.', file=sys.stderr)
        return 2
    return CloudSimApp().run() or 0
