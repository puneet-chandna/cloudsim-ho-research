"""Interactive contracts for the replacement terminal; headless checks are not native run evidence."""
import importlib.util
import asyncio
import json
import os
from pathlib import Path
import sys
import tempfile
import signal
import unittest
from unittest.mock import patch

HAS_TEXTUAL = importlib.util.find_spec('textual') is not None
if HAS_TEXTUAL:
    from textual.widgets import Input, Select, Button, TabbedContent, Collapsible, DataTable, Checkbox, Static
    from cloudsim_tui import CloudSimApp, LogView, Confirm


@unittest.skipUnless(HAS_TEXTUAL, 'Install pinned UI dependencies with ./cloudsim.sh --setup')
class InterfaceTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        jdk = self.root/'jdk/bin'; jdk.mkdir(parents=True)
        for name, version in (('java', 'openjdk version "21.0.1"'), ('javac', 'javac 21.0.1')):
            path = jdk/name
            path.write_text('#!'+sys.executable+'\nprint('+repr(version)+')\n')
            path.chmod(0o755)
        self.env = patch.dict(os.environ, {'JAVA_HOME':str(jdk.parent), 'JAVA_TOOL_OPTIONS':'',
                                          'JDK_JAVA_OPTIONS':'', '_JAVA_OPTIONS':''})
        self.env.start(); self.addCleanup(self.env.stop)
        # Capacity is a controlled fixture here; real cgroup guards have their own tests.
        worker = self.root/'widget-worker.py'
        worker.write_text('import sys\nsys.path.insert(0,'+repr(str(Path(__file__).resolve().parent))+')\n'
            'import cloudsim_worker\ncloudsim_worker.cloudsim.shared.require_memory=lambda heap:4*1024**3\n'
            'sys.exit(cloudsim_worker.main())\n')
        spawn = asyncio.create_subprocess_exec
        async def launch(*arguments, **kwargs):
            if len(arguments)>2 and str(arguments[2]).endswith('/scripts/cloudsim_worker.py'):
                arguments = (*arguments[:2],str(worker),*arguments[3:])
            return await spawn(*arguments, **kwargs)
        launcher = patch('cloudsim_tui.asyncio.create_subprocess_exec', new=launch)
        launcher.start(); self.addCleanup(launcher.stop)

    async def settled(self, app, pilot):
        for _ in range(100):
            await pilot.pause(.05)
            if not app.busy: return
        self.fail('Readiness worker did not complete')

    async def test_missing_jdk_blocks_start_and_exposes_setup_at_small_size(self):
        with patch.dict(os.environ, {'JAVA_HOME':str(self.root/'missing')}):
            app = CloudSimApp(output_parent=self.root/'results')
            async with app.run_test(size=(60,18)) as pilot:
                await self.settled(app, pilot)
                self.assertFalse(app.ready)
                self.assertTrue(app.query_one('#start', Button).disabled)
                app.action_setup()
                await pilot.pause()
                self.assertEqual(app.query_one(TabbedContent).active, 'setup')
                self.assertTrue(app.query_one('#install-jdk', Button).display)
                self.assertTrue(list((self.root/'results').glob('check-*/runner.json')))

    async def test_stress_preset_updates_counts_and_retains_edit_on_navigation(self):
        app = CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(80,24)) as pilot:
            await self.settled(app, pilot)
            app.query_one('#profile', Select).value = 'stress'
            await pilot.pause()
            app.query_one('#preset', Select).value = 'micro'
            await pilot.pause()
            self.assertEqual(app.query_one('#vms', Input).value, '50')
            self.assertEqual(app.query_one('#hosts', Input).value, '10')
            app.query_one('#vms', Input).value = '73'
            app.query_one(TabbedContent).active = 'tools'
            await pilot.pause()
            app.query_one(TabbedContent).active = 'run'
            await pilot.pause()
            self.assertEqual(app.query_one('#vms', Input).value, '73')
            app.query_one('#profile', Select).value = 'smoke'
            await pilot.pause()
            self.assertFalse(app.query_one('#stress-settings').display)
            app.query_one('#profile', Select).value = 'stress'
            await pilot.pause()
            self.assertEqual(app.query_one('#vms', Input).value, '73')

    async def test_invalid_input_preserves_unicode_and_does_not_start_a_run(self):
        app = CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(80,24)) as pilot:
            await self.settled(app, pilot)
            self.assertTrue(app.ready)
            app.query_one('#advanced', Collapsible).collapsed = False
            await pilot.pause()
            output = app.query_one('#output', Input)
            output.focus()
            await pilot.press('home', 'shift+end', 'backspace', *str(self.root/'試験 東京'))
            app.query_one('#heap', Input).value = 'oops'
            await app.start_action()
            await pilot.pause()
            self.assertFalse(app.busy)
            self.assertEqual(output.value, str(self.root/'試験 東京'))
            self.assertFalse(list(self.root.glob('試験 東京/*/runner.json')))
            self.assertIn('heap', str(app.query_one('#form-error').render()).lower())

    async def test_edit_after_failure_keeps_settings_and_requires_new_validation(self):
        app = CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(120,34)) as pilot:
            await self.settled(app, pilot)
            app.query_one('#heap', Input).value = '2048'
            app.show_result({'status':'failed','exit_code':1,'error':'fixture failure','validation':'NOT_RUN'})
            await pilot.pause()
            app.action_edit()
            await pilot.pause()
            self.assertEqual(app.query_one('#heap', Input).value, '2048')
            self.assertEqual(app.query_one('#flow').current, 'configure')
            self.assertNotIn('VALIDATED', str(app.query_one('#outcome').render()))

    async def test_archive_details_and_log_belong_to_selected_run(self):
        directory = self.root/'archive'; directory.mkdir()
        (directory/'console.log').write_text('ARCHIVED RUN LOG')
        current = self.root/'current.log'; current.write_text('UNRELATED CURRENT RUN LOG')
        app = CloudSimApp(output_parent=self.root/'results')
        async with app.run_test() as pilot:
            await self.settled(app, pilot)
            app.log_path = current; app.progress = {'detail':'UNRELATED CURRENT DETAIL'}
            metadata = {'profile':'smoke','status':'complete','exit_code':0,'validation':'PASS',
                        'output_directory':str(directory)}
            app.recent[str(directory)] = metadata
            app.action_tab('results'); await pilot.pause()
            app.refresh_results()
            table = app.query_one('#recent', DataTable)
            table.move_cursor(row=table.get_row_index(str(directory)))
            app.query_one('#result-details-button', Button).press(); await pilot.pause()
            self.assertEqual(app.outcome['output_directory'], str(directory))
            self.assertNotIn('UNRELATED', str(app.query_one('#result-details').render()))
            app.query_one('#view-log', Button).press(); await pilot.pause()
            self.assertIsInstance(app.screen, LogView)
            self.assertEqual(app.screen.text, 'ARCHIVED RUN LOG')

    async def test_invalid_main_stress_fields_focus_the_visible_input(self):
        app = CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(80,24)) as pilot:
            await self.settled(app, pilot)
            app.query_one('#profile', Select).value = 'stress'
            await pilot.pause()
            for name in ('population','iterations','replications','seed','deadline'):
                with self.subTest(name=name):
                    field = app.query_one('#'+name, Input); previous = field.value
                    field.value = 'oops'; await app.start_action(); await pilot.pause()
                    self.assertIs(app.focused, field)
                    self.assertFalse(app.busy)
                    self.assertTrue(app.query_one('#advanced', Collapsible).collapsed)
                    field.value = previous

    async def test_every_stress_preset_uses_editable_main_search_settings(self):
        import cloudsim
        app = CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(120,34)) as pilot:
            await self.settled(app,pilot)
            app.query_one('#profile',Select).value='stress'; await pilot.pause()
            for name,value in (('population','12'),('iterations','7'),('replications','3'),('seed','8765')):
                app.query_one('#'+name,Input).value=value
            for preset,(vms,hosts) in cloudsim.stress.PRESETS.items():
                with self.subTest(preset=preset):
                    app.query_one('#preset',Select).value=preset;await pilot.pause()
                    options=app.parsed(app.arguments())
                    self.assertEqual((options.vms,options.hosts),(vms,hosts))
                    self.assertEqual((options.population,options.iterations,options.replications,options.seed),(12,7,3,8765))
                    for name in ('population','iterations','replications','seed'):
                        field=app.query_one('#'+name,Input)
                        self.assertTrue(field.display and all(parent.display for parent in field.ancestors))
                        self.assertFalse(field.disabled)
                        self.assertIn(app.query_one('#stress-settings'),field.ancestors)
                        self.assertNotIn(app.query_one('#advanced'),field.ancestors)
                    self.assertTrue(app.query_one('#advanced',Collapsible).collapsed)

    async def test_frozen_values_are_read_only_and_workers_are_runtime_only(self):
        import cloudsim
        app=CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(120,34)) as pilot:
            await self.settled(app,pilot)
            for profile,(n,t,r,_) in cloudsim.FROZEN_SETTINGS.items():
                app.query_one('#profile',Select).value=profile;await pilot.pause()
                for name,value in (('population',n),('iterations',t),('replications',r)):
                    self.assertEqual(str(app.query_one('#locked-'+name,Static).render()),str(value))
                    self.assertFalse(all(parent.display for parent in app.query_one('#'+name,Input).ancestors))
                    self.assertNotIn('--'+name,app.arguments())
                before=cloudsim.preview(app.parsed(app.arguments()))['effective_config']
                app.query_one('#workers',Select).value='32';await pilot.pause()
                options=app.parsed(app.arguments())
                self.assertEqual(options.workers,32)
                after=cloudsim.preview(options)['effective_config']
                self.assertEqual(before,after)

    async def test_build_options_are_explicit_and_mutually_exclusive(self):
        app=CloudSimApp(output_parent=self.root/'results')
        async with app.run_test() as pilot:
            await self.settled(app,pilot)
            self.assertEqual(app.query_one('#workers',Select).value,'auto')
            force=app.query_one('#force-build',Checkbox); skip=app.query_one('#skip-build',Checkbox)
            force.value=True;await pilot.pause()
            self.assertIn('--force-build',app.arguments())
            self.assertNotIn('--skip-build',app.arguments())
            skip.value=True;await pilot.pause()
            self.assertFalse(force.value)
            self.assertIn('--skip-build',app.arguments())
            self.assertNotIn('--force-build',app.arguments())
            force.value=True;await pilot.pause()
            self.assertFalse(skip.value)
            self.assertTrue(app.parsed(app.arguments()).force_build)

    async def test_main_form_remains_keyboard_accessible_after_resize(self):
        app=CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(120,34)) as pilot:
            await self.settled(app,pilot)
            app.query_one('#profile',Select).value='stress';await pilot.pause()
            for width,height in ((120,34),(80,24),(60,18),(120,34)):
                await pilot.resize_terminal(width,height);await pilot.pause()
                self.assertEqual(app.screen.has_class('narrow'),width<100)
                self.assertEqual(app.screen.has_class('compact'),width<74 or height<22)
                if width==80: self.assertGreater(app.query_one('#form').region.width,70)
                for name in ('population','iterations','replications','seed','workers','heap'):
                    field=app.query_one('#'+name)
                    field.focus();await pilot.pause()
                    self.assertIs(app.focused,field)
                    self.assertTrue(app.query_one('#form').content_region.overlaps(field.region))
                button=app.query_one('#start',Button)
                self.assertGreater(button.region.width,0)
                self.assertLessEqual(button.region.bottom,height-1)
                self.assertTrue(app.query_one('#advanced',Collapsible).collapsed)

    async def test_large_plan_explains_one_full_campaign_and_exact_calibrations(self):
        app=CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(120,34)) as pilot:
            await self.settled(app,pilot)
            app.query_one('#profile',Select).value='stress';await pilot.pause()
            app.query_one('#preset',Select).value='large';await pilot.pause()
            options=app.parsed(app.arguments())
            self.assertEqual((options.vms,options.hosts,options.population,options.iterations,options.replications),(10000,2000,30,40,5))
            summary=str(app.query_one('#summary',Static).render())
            for value in ('At this size only','100 VMs / 20 hosts','500 VMs / 100 hosts','10,000 VMs / 2,000 hosts','N10 / T10 / R1','shared heap','verified build'):
                self.assertIn(value,summary)

    async def test_stress_parameters_fit_the_initial_standard_viewports(self):
        app=CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(120,34)) as pilot:
            await self.settled(app,pilot)
            app.query_one('#profile',Select).value='stress';await pilot.pause()
            for width,height in ((120,34),(80,24)):
                await pilot.resize_terminal(width,height);await pilot.pause()
                viewport=app.query_one('#form').content_region
                for name in ('vms','hosts','population','iterations','replications','seed','workers','heap','deadline'):
                    with self.subTest(size=(width,height),field=name):
                        self.assertTrue(viewport.contains_region(app.query_one('#'+name).region))

    async def test_progress_cancel_stays_visible_and_confirmable_after_resize(self):
        from types import SimpleNamespace
        signals=[]
        app=CloudSimApp(output_parent=self.root/'results')
        async with app.run_test(size=(120,34)) as pilot:
            await self.settled(app,pilot)
            app.query_one('#flow').current='progress'
            app.busy=True
            app.started=__import__('time').monotonic()
            app.stage='production'
            app.progress={'done':0,'total':20,'evaluations':0,
                          'detail':'2 workers active. Native evaluations are computing; completed cases publish after each case finishes. '*3}
            app.process=SimpleNamespace(returncode=None,send_signal=signals.append)
            try:
                app.tick();await pilot.pause()
                for width,height in ((120,34),(80,24),(60,18)):
                    await pilot.resize_terminal(width,height);await pilot.pause()
                    button=app.query_one('#cancel-job',Button)
                    self.assertGreater(button.region.width,0)
                    self.assertLessEqual(button.region.bottom,height-1)
                    await pilot.click('#cancel-job');await pilot.pause()
                    self.assertIsInstance(app.screen,Confirm)
                    self.assertEqual(signals,[])
                    await pilot.press('escape');await pilot.pause()
                await pilot.click('#cancel-job');await pilot.pause()
                app.screen.query_one('#confirm',Button).press();await pilot.pause()
                self.assertEqual(signals,[signal.SIGINT])
            finally:
                app.process=None;app.busy=False

    async def test_legacy_setup_check_with_profile_is_not_an_experiment(self):
        app=CloudSimApp(output_parent=self.root/'results')
        async with app.run_test() as pilot:
            await self.settled(app,pilot)
            app.show_result(dict(profile='research',status='checked',exit_code=0,validation='NOT_RUN'))
            await pilot.pause()
            self.assertEqual(str(app.query_one('#outcome').render()),'Setup ready')
            self.assertNotIn('Independent validation:',str(app.query_one('#result-details').render()))

    async def test_invalid_frozen_properties_reveal_and_focus_the_config_entry(self):
        app=CloudSimApp(output_parent=self.root/'results')
        config=self.root/'frozen.properties'
        cases=((b'master.seed=oops\n','master.seed must be signed decimal'),
               (b'log.level=TRACE\n','log.level must be INFO or DEBUG'),
               (b'population=999\n','Unknown property: population'),
               (b'heap=999\n','Unknown property: heap'),
               (b'master.seed=\\u00XX\n','Malformed Unicode escape in config'),
               (b'log.level=\xff\n','Config must contain valid UTF-8'))
        async with app.run_test(size=(80,24)) as pilot:
            await self.settled(app,pilot)
            entry=app.query_one('#config-path',Input)
            advanced=app.query_one('#advanced',Collapsible)
            for contents,message in cases:
                with self.subTest(contents=contents):
                    config.write_bytes(contents)
                    entry.value=str(config);advanced.collapsed=True;await pilot.pause()
                    with patch.object(app,'begin') as begin:
                        await app.start_action();await pilot.pause()
                        begin.assert_not_called()
                    self.assertFalse(advanced.collapsed)
                    self.assertIs(app.focused,entry)
                    self.assertTrue(entry.display and all(parent.display for parent in entry.ancestors))
                    self.assertEqual(entry.value,str(config))
                    self.assertIn(message,str(app.query_one('#form-error',Static).render()))
                    self.assertFalse(app.busy)
            # CLI value errors take precedence even with a bad properties file.
            app.query_one('#heap',Input).value='oops';advanced.collapsed=True
            await app.start_action();await pilot.pause()
            self.assertIs(app.focused,app.query_one('#heap',Input))
            self.assertTrue(advanced.collapsed)

    async def test_theme_change_preserves_the_run_and_reduced_motion_stays_static(self):
        app=CloudSimApp(output_parent=self.root/'results')
        app.animation_level='none'
        async with app.run_test(size=(80,24)) as pilot:
            await self.settled(app,pilot)
            arguments=app.arguments()
            for theme in ('cloudsim-ember','cloudsim-paper','cloudsim'):
                app.query_one('#theme',Select).value=theme;await pilot.pause()
                self.assertEqual(app.theme,theme)
                self.assertEqual(app.arguments(),arguments)
            self.assertEqual(app.query_one('#configure-body').styles.opacity,1)

    async def test_dead_worker_still_cleans_verified_owned_child(self):
        app = CloudSimApp(output_parent=self.root/'results')
        child = None
        async with app.run_test() as pilot:
            await self.settled(app, pilot)
            worker = await asyncio.create_subprocess_exec(sys.executable, '-c',
                'import subprocess,sys,time; p=subprocess.Popen([sys.executable,"-c","import time;time.sleep(60)"],'
                'start_new_session=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL);print(p.pid,flush=True);time.sleep(60)',
                stdout=asyncio.subprocess.PIPE)
            child = int(await worker.stdout.readline())
            try:
                app.process = worker
                app.track_child({'pid':child})
                worker.kill(); await worker.wait()
                await app.stop_worker()
                for _ in range(100):
                    stat = Path(f'/proc/{child}/stat')
                    if not stat.exists() or stat.read_text().rsplit(')',1)[1].split()[0]=='Z': break
                    await asyncio.sleep(.02)
                else: self.fail('Owned child survived worker exit')
            finally:
                if worker.returncode is None: worker.kill(); await worker.wait()
                try: os.killpg(child, signal.SIGKILL)
                except ProcessLookupError: pass

    async def test_select_existing_jdk_recovers_with_supervised_checks(self):
        with patch.dict(os.environ, {'JAVA_HOME':str(self.root/'missing')}):
            app = CloudSimApp(output_parent=self.root/'results')
            async with app.run_test(size=(80,24)) as pilot:
                await self.settled(app, pilot)
                app.action_setup(); await pilot.pause()
                app.query_one('#jdk-path', Input).value = str(self.root/'jdk')
                with patch('cloudsim_runtime.save_java_home') as save:
                    app.query_one('#select-jdk', Button).press()
                    await pilot.pause(.1); await self.settled(app, pilot)
                    self.assertTrue(app.ready)
                    self.assertFalse(app.query_one('#start', Button).disabled)
                    save.assert_called_once_with(self.root/'jdk')

    async def test_dead_group_leader_does_not_hide_owned_descendant(self):
        app = CloudSimApp(output_parent=self.root/'results')
        marker = self.root/'descendant.pid'
        leader = ('import subprocess,sys,time,pathlib; p=subprocess.Popen([sys.executable,"-c","import time;time.sleep(60)"]);'
                  f'pathlib.Path({str(marker)!r}).write_text(str(p.pid));time.sleep(60)')
        code = ('import subprocess,sys,time; p=subprocess.Popen([sys.executable,"-c",sys.argv[1]],start_new_session=True,'
                'stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL);print(p.pid,flush=True);p.wait();print("reaped",flush=True);time.sleep(60)')
        async with app.run_test() as pilot:
            await self.settled(app, pilot)
            worker = await asyncio.create_subprocess_exec(sys.executable,'-c',code,leader,stdout=asyncio.subprocess.PIPE)
            child = int(await worker.stdout.readline())
            try:
                while not marker.exists(): await asyncio.sleep(.02)
                descendant = int(marker.read_text())
                app.process=worker; app.track_child({'pid':child})
                os.kill(child,signal.SIGTERM)
                self.assertEqual((await worker.stdout.readline()).strip(),b'reaped')
                self.assertFalse(Path(f'/proc/{child}').exists())
                worker.kill(); await worker.wait(); await app.stop_worker()
                for _ in range(100):
                    stat=Path(f'/proc/{descendant}/stat')
                    if not stat.exists() or stat.read_text().rsplit(')',1)[1].split()[0]=='Z': break
                    await asyncio.sleep(.02)
                else: self.fail('Owned descendant survived after its leader was reaped')
            finally:
                if worker.returncode is None: worker.kill(); await worker.wait()
                try: os.killpg(child,signal.SIGKILL)
                except ProcessLookupError: pass

    async def test_worker_failure_preserves_its_output_and_disclaims_validation(self):
        directory=self.root/'failed-run';directory.mkdir()
        (directory/'runner.json').write_text(json.dumps({'profile':'smoke','status':'build','tests':'NOT_RUN'}))
        app=CloudSimApp(output_parent=self.root/'results')
        async with app.run_test() as pilot:
            await self.settled(app,pilot)
            app.active_directory=directory
            result=app.failed_worker(137,'Runner completion is missing.')
            self.assertEqual(result['output_directory'],str(directory))
            self.assertEqual(result['validation'],'NOT_RUN')
            saved=json.loads((directory/'runner.json').read_text())
            self.assertEqual(saved['status'],'failed')
            self.assertEqual(saved['exit_code'],137)
            app.show_result(result);await pilot.pause()
            self.assertIn(str(directory),str(app.query_one('#result-details').render()))

    async def test_results_selects_experiment_and_explains_ineligible_actions(self):
        app=CloudSimApp(output_parent=self.root/'results')
        entries={
            'check':dict(profile='research',status='checked',exit_code=0,validation='NOT_RUN',started_at='2099-01-03T00:00:00Z'),
            'report':dict(action='validate',status='complete',exit_code=0,validation='PASS',started_at='2099-01-02T00:00:00Z'),
            'experiment':dict(profile='smoke',status='complete',exit_code=0,validation='PASS',started_at='2099-01-01T00:00:00Z')}
        for name,data in entries.items():
            folder=self.root/'results'/name;folder.mkdir(parents=True)
            (folder/'runner.json').write_text(json.dumps(data))
        async with app.run_test(size=(120,34)) as pilot:
            await self.settled(app,pilot)
            app.action_tab('results');await pilot.pause()
            self.assertEqual(app.selected_result()['output_directory'],str(self.root/'results/experiment'))
            table=app.query_one('#recent',DataTable)
            for name,reason in (('check','setup check'),('report','validation report')):
                table.move_cursor(row=table.get_row_index(str(self.root/'results'/name)));await pilot.pause()
                self.assertTrue(app.query_one('#validate-selected',Button).disabled)
                self.assertIn(reason,str(app.query_one('#results-detail').render()).lower())
            table.move_cursor(row=table.get_row_index(str(self.root/'results/experiment')));await pilot.pause()
            self.assertFalse(app.query_one('#validate-selected',Button).disabled)
            app.set_busy(True);await pilot.pause()
            self.assertTrue(app.query_one('#validate-selected',Button).disabled)
            self.assertIn('running',str(app.query_one('#results-detail').render()).lower())
            app.set_busy(False)

    async def test_validation_report_displays_explicit_pass(self):
        app=CloudSimApp(output_parent=self.root/'results')
        async with app.run_test() as pilot:
            await self.settled(app,pilot)
            app.show_result(dict(action='validate',status='complete',exit_code=0,validation='PASS',
                                 validation_result={'scope':'campaign','artifact_binding':'verified','limitations':'recorded evidence'}))
            await pilot.pause()
            self.assertIn('Validated',str(app.query_one('#outcome').render()))
            self.assertIn('Independent validation: PASS',str(app.query_one('#result-details').render()))
