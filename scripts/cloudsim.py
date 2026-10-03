#!/usr/bin/env python3
"""One entry point for frozen CloudSim experiments and static stress."""
import argparse
from copy import deepcopy
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile

import research_runner as shared
import stress_runner as stress

ROOT = Path(__file__).resolve().parents[1]
FROZEN_SETTINGS = {'smoke': (10, 4, 1, 'Micro'),
                   'explore': (20, 20, 5, 'Micro,Small'),
                   'research': (30, 40, 30, 'Micro,Small,Medium')}
STRESS_FLAGS = ('preset', *stress.FIELDS, 'seed', 'time_limit')
SPACE = ' \t\f'


def _decode_property(text):
    result = []
    i = 0
    while i < len(text):
        char = text[i]; i += 1
        if char == '\\':
            if i == len(text): break
            char = text[i]; i += 1
            if char == 'u':
                digits = text[i:i+4]
                if len(digits) != 4 or not re.fullmatch('[0-9a-fA-F]{4}', digits):
                    raise ValueError('Malformed Unicode escape in config')
                char = chr(int(digits, 16)); i += 4
            else: char = {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}.get(char, char)
        result.append(char)
    return ''.join(result)


def _properties(text):
    """Properties.load(Reader) grammar; no INI interpolation or whitespace trimming."""
    logical = ''
    continuing = False
    lines = []
    natural_lines = re.split(r'\r\n|\r|\n', text)
    for index, natural in enumerate(natural_lines):
        part = natural.lstrip(SPACE)
        if not continuing and (not part or part[0] in '#!'): continue
        # Java restarts new-line/comment detection after a continuation of an
        # otherwise empty logical line. A dangling EOF backslash still forms
        # an empty property and is rejected by the frozen key allowlist.
        if continuing and not logical and (not part or part[0] in '#!'):
            if natural or index < len(natural_lines)-1:
                continuing = False
                continue
        logical += part
        slashes = len(logical) - len(logical.rstrip('\\'))
        if slashes % 2:
            logical = logical[:-1]; continuing = True
            continue
        lines.append(logical); logical = ''; continuing = False
    if continuing: lines.append(logical)
    result = {}
    for line in lines:
        escaped = False
        end = len(line)
        separator = False
        for i, char in enumerate(line):
            if not escaped and (char in SPACE or char in '=:'):
                end = i; separator = char in '=:'; break
            escaped = not escaped if char == '\\' else False
        start = end
        if separator: start += 1
        while start < len(line):
            if line[start] in SPACE: start += 1
            elif not separator and line[start] in '=:': separator = True; start += 1
            else: break
        key, value = _decode_property(line[:end]), _decode_property(line[start:])
        if key in result: raise ValueError(f'Duplicate property: {key}')
        result[key] = value
    return result


def read_frozen_config(path: Path | None) -> dict:
    properties = {}
    if path is not None:
        if not path.is_file(): raise ValueError('Config must be an existing UTF-8 properties file')
        try: properties = _properties(path.read_text(encoding='utf-8'))
        except UnicodeError as error: raise ValueError('Config must contain valid UTF-8') from error
    unknown = properties.keys() - {'master.seed', 'log.level'}
    if unknown: raise ValueError('Unknown property: ' + sorted(unknown)[0])
    value = properties.get('master.seed', '123456')
    if not re.fullmatch('[+-]?[0-9]+', value): raise ValueError('master.seed must be signed decimal')
    digits = value.lstrip('+-').lstrip('0') or '0'
    if len(digits) > 19: raise ValueError('master.seed must be a signed 64-bit integer')
    seed = int(('-' if value.startswith('-') else '') + digits)
    if not -(1 << 63) <= seed < (1 << 63): raise ValueError('master.seed must be a signed 64-bit integer')
    level = properties.get('log.level', 'INFO')
    if level not in ('INFO', 'DEBUG'): raise ValueError('log.level must be INFO or DEBUG')
    return {'master.seed': str(seed), 'log.level': level}


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False,
        epilog='No arguments opens the terminal app. Linux, Python 3.11+ and full JDK 21 are required to run. Help and dry-run require no JDK.')
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument('--profile', choices=(*shared.FROZEN_PROFILES, 'stress'))
    for action in ('check', 'build', 'test'): actions.add_argument('--'+action, action='store_true')
    actions.add_argument('--validate', type=stress.output_path, metavar='DIRECTORY')
    parser.add_argument('--plain', action='store_true', help='plain progress; automatic for nonterminal output')
    parser.add_argument('--dry-run', action='store_true', help='preview a profile without children or output creation')
    parser.add_argument('--heap-mib', type=shared.heap_value, default=1024)
    parser.add_argument('--output-dir', type=stress.output_path, help='parent for unique retained outputs')
    parser.add_argument('--config', type=stress.output_path, help='frozen profiles only: master.seed and log.level')
    parser.add_argument('--skip-build', action='store_true', help='diagnostic profile run using the existing JAR')
    parser.add_argument('--preset', choices=stress.PRESETS)
    for field in stress.FIELDS: parser.add_argument('--'+field, type=stress.positive)
    parser.add_argument('--seed', type=stress.seed_value)
    parser.add_argument('--time-limit', type=stress.parse_duration)
    arguments = list(argv)
    flags = [arg.split('=', 1)[0] for arg in arguments if arg.startswith('--')]
    if len(flags) != len(set(flags)): parser.error('duplicate options are not supported')
    options = parser.parse_args(arguments)
    options.arguments = arguments
    options.action = 'profile' if options.profile else next((a for a in ('check', 'build', 'test', 'validate') if getattr(options, a)), None)
    if arguments and options.action is None: parser.error('select an action: --profile, --check, --build, --test or --validate')
    if options.action != 'profile':
        incompatible = {'--config', '--skip-build', '--dry-run', *('--'+field.replace('_', '-') for field in STRESS_FLAGS)}
        if options.action == 'validate': incompatible.add('--heap-mib')
        if set(flags) & incompatible: parser.error('profile settings require an applicable --profile action')
    elif options.profile != 'stress':
        if any('--'+field.replace('_', '-') in flags for field in STRESS_FLAGS):
            parser.error('stress settings require --profile stress; frozen search settings are read-only')
        if options.config:
            options.config = options.config.resolve()
        try: read_frozen_config(options.config)
        except (OSError, ValueError) as error: parser.error(str(error))
    elif options.config is not None: parser.error('--config is incompatible with --profile stress')
    options.output_dir = (options.output_dir or ROOT/('results/stress' if options.profile == 'stress' else 'results')).resolve()
    if options.validate is not None: options.validate = options.validate.resolve()
    if options.profile == 'stress':
        # The existing parser owns defaults, integer bounds, matrix and heap guards.
        options.stress_options = stress.parse_args(runner_args(options))
        for field in (*STRESS_FLAGS, 'heap_mib'): setattr(options, field, getattr(options.stress_options, field))
    return options


def runner_args(options: argparse.Namespace) -> list[str]:
    arguments = ['--heap-mib', str(options.heap_mib), '--output-dir', str(options.output_dir)]
    if options.plain or not sys.stdout.isatty(): arguments.append('--plain')
    if options.skip_build: arguments.append('--skip-build')
    if options.config is not None: arguments += ['--config', str(options.config)]
    if options.profile == 'stress':
        for field in STRESS_FLAGS:
            value = getattr(options, field)
            # None represents an explicit unlimited deadline, or an unset default.
            supplied = '--'+field.replace('_', '-') in [a.split('=', 1)[0] for a in options.arguments]
            if value is not None or (field == 'time_limit' and (supplied or hasattr(options, 'stress_options'))):
                value = 'none' if value is None else str(int(value)) if field == 'time_limit' else str(value)
                arguments += ['--'+field.replace('_', '-'), value]
    return arguments


def _effective(options):
    if options.profile == 'stress': result = stress.effective(options.stress_options)
    elif options.profile:
        result = _properties((ROOT/'src/main/resources/protocol.properties').read_text(encoding='utf-8'))
        population, iterations, replications, scenarios = FROZEN_SETTINGS[options.profile]
        result.update(read_frozen_config(options.config), profile=options.profile,
                      population=str(population), iterations=str(iterations), replications=str(replications),
                      scenarios=scenarios, **{'evaluation.budget': str(population+3*population*iterations),
                                             'expected.cases': str(shared.profile_total(options.profile))})
    else: result = {}
    result.update(heap_mib=options.heap_mib, output_dir=str(options.output_dir), skip_build=options.skip_build,
                  config=str(options.config) if options.config else None)
    return result


def preview(options: argparse.Namespace) -> dict:
    if options.action != 'profile': raise ValueError('Preview requires --profile')
    effective = _effective(options)
    memory = stress.memory_preview(options.stress_options if options.profile == 'stress' else argparse.Namespace(preset='small', vms=100, hosts=20))
    java = Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else Path('java')
    retained = Path('<RETAINED>')
    if options.profile == 'stress':
        plan = stress.planned_work(options.stress_options)
        blocks = [(p, 'stress_calibration', f'calibration-{p.vms}') for p in stress.calibrations(options.stress_options)]
        blocks.append((options.stress_options, 'stress', 'production'))
        commands = [stress.java_command(java, config, retained/'stress.jar', retained/name/'artifacts', phase)
                    for config, phase, name in blocks]
    else:
        plan = {'expected_cases': shared.profile_total(options.profile),
                'main_cases': 360 if options.profile == 'research' else shared.profile_total(options.profile),
                'sensitivity_cases': 90 if options.profile == 'research' else 0}
        commands = [shared.java_command(java, options.heap_mib, retained/(options.profile+'.jar'),
                                        retained, options.config, profile=options.profile)]
    launch = [str(ROOT/'cloudsim.sh'), '--profile', options.profile, *runner_args(options)]
    warnings = [value for value in (memory.get('warning'), memory.get('policy_note')) if value]
    if memory['usable_memory_bytes'] is None: warnings.append('Usable memory is unknown; launch requires independent verified headroom.')
    if options.skip_build: warnings.append('Diagnostic --skip-build: existing packaged JAR may differ from current source protocol values.')
    return {'action': options.action, 'profile': options.profile, 'effective_config': effective,
            'planned_work': plan, 'memory_evidence': memory, 'warnings': warnings,
            'commands': {'launcher': launch, 'launcher_shell': shlex.join(launch), 'java': commands,
                         'java_shell': [shlex.join(command) for command in commands],
                         'java_scope': 'Templates: unique retained output directory is assigned at execution.'},
            'time_limit_scope': 'calibration + production + all validation; excludes build/tests' if options.profile == 'stress' else None}


def execute(options: argparse.Namespace, *, dashboard=None, on_complete=None, invocation=None) -> int:
    original = deepcopy(invocation) if invocation is not None else {'arguments': list(options.arguments)}
    if isinstance(original, dict): original['effective_config'] = _effective(options)
    if options.action == 'profile':
        if options.dry_run:
            print(json.dumps(preview(options), indent=2)); return 0
        runner = stress if options.profile == 'stress' else shared
        keywords = {'dashboard': dashboard, 'on_complete': on_complete, 'invocation': original}
        if options.profile != 'stress': keywords['profile'] = options.profile
        return runner.main(runner_args(options), **keywords)
    return _maintenance(options, dashboard, on_complete, original)


def _maintenance(options, dashboard, on_complete, invocation):
    supplied = dashboard is not None
    dashboard = dashboard if supplied else shared.Dashboard(plain=options.plain, heap_mib=options.heap_mib, title=options.action.upper(), total=1)
    control = shared.ProcessControl(dashboard, ROOT)
    metadata = {'started_at': shared.stamp(), 'status': 'preflight', 'action': options.action,
                'arguments': list(options.arguments), 'invocation': invocation, 'validation': 'NOT_RUN',
                'tests': 'NOT_RUN', 'commands': [], 'exit_code': None}
    outer = console = None
    code = 1
    control.install()
    try:
        output_parent = options.output_dir
        if options.action == 'validate':
            selected = options.validate.resolve()
            if output_parent.resolve().is_relative_to(selected):
                if any(arg.split('=',1)[0]=='--output-dir' for arg in options.arguments):
                    raise ValueError('Validation output directory must be outside selected evidence')
                output_parent = selected.parent
        output_parent.mkdir(parents=True, exist_ok=True)
        outer = Path(tempfile.mkdtemp(prefix=options.action+'-', dir=output_parent))
        control.root = outer
        console = (outer/'console.log').open('a'); dashboard.console = console
        metadata['output_directory'] = str(outer)
        shared.save_metadata(outer, metadata)
        if options.action == 'validate':
            try: from run_validation import validate_existing
            except ImportError as error: raise ValueError('Standalone validation backend is not yet available') from error
            metadata['status'] = 'validation'; shared.save_metadata(outer, metadata)
            result = validate_existing(options.validate, control, outer)
            metadata['validation_result'] = result
            metadata.update(status='complete', validation='PASS'); code = 0
            control.render('complete', {'detail': f'VALIDATED: {options.validate}; logs: {outer}',
                                       'extra_lines': [result['scope'],result['artifact_binding'],result['limitations']]}, force=True)
        else:
            java, available = shared.check_environment(control, options.heap_mib)
            metadata.update(java=str(java), usable_memory_bytes=available)
            if options.action == 'check':
                metadata['status'] = 'checked'; code = 0
                control.render('checked', {'detail': f'PASS: JDK 21; {available//shared.MIB} MiB usable memory. No experiment executed.'}, force=True)
            else:
                commands = [([str(ROOT/'mvnw'), '-B', '-Dmaven.test.skip=true', 'clean', 'package'], 'build.log')] if options.action == 'build' else [
                    ([str(ROOT/'mvnw'), '-B', 'clean', 'verify'], 'build.log'),
                    ([sys.executable, '-B', '-m', 'unittest', 'discover', '-s', 'scripts', '-p', 'test_*.py'], 'python-tests.log')]
                for command, name in commands:
                    metadata.update(status=options.action)
                    metadata['commands'].append(command); shared.save_metadata(outer, metadata)
                    code = control.run(command, outer/name, options.action, cwd=ROOT)
                    if code: raise ValueError(f'{options.action.capitalize()} failed with exit {code}; log: {outer/name}')
                metadata.update(status='complete', tests='PASS' if options.action == 'test' else 'NOT_RUN')
                detail = 'BUILD COMPLETE; tests NOT RUN' if options.action == 'build' else 'TESTS PASSED'
                control.render('complete', {'detail': detail+f'; logs: {outer}'}, force=True)
    except shared.Interrupted as error:
        code = 128+error.signum; metadata.update(status='interrupted', error=f'Interrupted by signal {error.signum}')
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        code = getattr(control, 'last_exit_code', 0) or code or 1
        metadata.update(status='failed', error=shared.sanitize(error))
    finally:
        if metadata['status'] in ('failed', 'interrupted'):
            try: control.render(metadata['status'], {'detail': metadata['error']}, force=True)
            except shared.PresentationError: pass
        code = shared.finish(control, metadata, code, outer, console, on_complete)
        if outer and not supplied: print(f'Retained logs: {shared.sanitize(outer)}')
    return code


def main(argv=None) -> int:
    options = parse_args(list(argv if argv is not None else sys.argv[1:]))
    if options.action is None:
        if not sys.stdin.isatty() or not sys.stdout.isatty():
            print('ERROR: terminal stdin/stdout required; select --profile smoke|explore|research|stress, --check, --build, --test or --validate DIRECTORY.', file=sys.stderr)
            return 2
        try: from cloudsim_tui import run
        except ImportError as error:
            print('ERROR: interactive terminal application unavailable: '+shared.sanitize(error), file=sys.stderr)
            return 2
        return run()
    return execute(options)


if __name__ == '__main__': sys.exit(main())
