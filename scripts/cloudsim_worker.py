"""Main-thread dispatcher for the terminal; stdout is a bounded JSON event channel."""
import json
import os
from pathlib import Path
import signal
import sys

import cloudsim
import cloudsim_runtime as runtime


def emit(event, **values):
    print(json.dumps({'event': event, **values}, ensure_ascii=True), flush=True)


class EventDashboard:
    console = None
    warning = None
    child = None

    def render(self, stage, progress=None, pid=None, force=False):
        emit('progress', stage=stage, progress=progress or {}, pid=pid)

    def set_log(self, path):
        directory = str(Path(self.console.name).parent) if self.console else None
        emit('log', path=str(path), output_directory=directory)
    def close(self): pass

    def poll(self, control):
        pid = getattr(control, 'active_pid', None)
        if pid != self.child:
            self.child = pid
            emit('child', pid=pid, identity=runtime.owned_child_identity(pid, os.getpid()) if pid else None)


def main(argv=None):
    options = cloudsim.parse_args(list(sys.argv[1:] if argv is None else argv))
    completed = []
    def finish(metadata):
        completed.append(metadata); emit('complete', metadata=metadata)
    if options.action == 'setup':
        directory = runtime.ROOT/'.cloudsim'; directory.mkdir(parents=True, exist_ok=True)
        log = directory/'setup.log'; emit('log', path=str(log))
        def interrupted(number, frame): raise KeyboardInterrupt
        previous = signal.signal(signal.SIGTERM, interrupted)
        try:
            with log.open('a', encoding='utf-8') as stream:
                def report(message):
                    stream.write(message+'\n'); stream.flush()
                    emit('progress', stage='setup', progress={'detail': message})
                runtime.install_jdk(report=report)
            finish({'action':'setup', 'status':'complete', 'exit_code':0, 'validation':'NOT_RUN',
                    'output_directory':str(directory), 'log':str(log)})
            return 0
        except KeyboardInterrupt:
            code = 130; error = 'Setup cancelled. Retry when ready.'
        except Exception as exception:
            code = 1; error = cloudsim.shared.sanitize(exception)
        finally: signal.signal(signal.SIGTERM, previous)
        with log.open('a', encoding='utf-8') as stream: stream.write(error+'\n')
        finish({'action':'setup', 'status':'interrupted' if code==130 else 'failed', 'exit_code':code,
                'error':error, 'validation':'NOT_RUN', 'output_directory':str(directory), 'log':str(log)})
        return code
    try:
        code = cloudsim.execute(options, dashboard=EventDashboard(), on_complete=finish,
            invocation={'mode':'interactive', 'arguments':[], 'interactive_choices':list(options.arguments),
                        'equivalent_cli':[('lattora' if cloudsim.lattora_context.get_context(cloudsim.ROOT).bundled else str(cloudsim.ROOT/'cloudsim.sh')), *options.arguments]})
    except Exception as exception:
        code = 1
        finish({'status':'failed', 'exit_code':code, 'validation':'NOT_RUN',
                'error':cloudsim.shared.sanitize(exception)})
    if not completed:
        code = code or 1
        finish({'status':'failed', 'exit_code':code, 'validation':'NOT_RUN',
                'error':'The runner exited without a completion record. Inspect the retained logs and retry.'})
    return code


if __name__ == '__main__': sys.exit(main())
