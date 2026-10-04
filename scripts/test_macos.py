"""macOS probe contracts; native process checks also run on macOS CI."""
import ctypes
import os
import signal
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import cloudsim_runtime as runtime
import research_runner as runner


class MacMemoryTests(unittest.TestCase):
    def probe(self, page_size=16384, free=100000, inactive=150000, speculative=10000):
        return (f'Mach Virtual Memory Statistics: (page size of {page_size} bytes)\n'
                f'Pages free: {free}.\nPages inactive: {inactive}.\nPages speculative: {speculative}.\n'
                'Pages wired down: 400000.\nPages occupied by compressor: 100000.\n')

    def test_real_page_size_and_reclaimable_pages_exclude_wired_and_compressed(self):
        for page_size in (4096, 16384):
            with self.subTest(page_size=page_size), patch.object(runtime.sys,'platform','darwin'), \
                 patch.object(runtime,'mac_command',side_effect=['8589934592', self.probe(page_size)]):
                self.assertEqual(runner.usable_memory(), 260000*page_size)
        with patch.object(runtime.sys,'platform','darwin'), patch.object(runtime,'mac_command',return_value='8589934592'):
            self.assertEqual(runner.physical_memory(), 8*1024**3)

    def test_invalid_or_missing_probes_fail_closed(self):
        for text in ('', self.probe(0), self.probe(free=-1), self.probe().replace('Pages inactive:', 'missing:'), self.probe(free=999999999)):
            with self.subTest(text=text), patch.object(runtime.sys,'platform','darwin'), \
                 patch.object(runtime,'mac_command',side_effect=['8589934592', text]):
                with self.assertRaisesRegex(ValueError, 'memory'): runner.usable_memory()
        with patch.object(runtime.sys,'platform','darwin'), patch.object(runtime,'mac_command',side_effect=OSError('probe failed')):
            with self.assertRaisesRegex(ValueError,'memory'): runner.usable_memory()

    def test_low_available_ram_keeps_existing_heap_guard(self):
        with patch.object(runtime.sys,'platform','darwin'), \
             patch.object(runtime,'mac_command',side_effect=['8589934592', self.probe(free=100,inactive=100,speculative=0)]):
            with self.assertRaisesRegex(ValueError,'Insufficient usable memory'): runner.require_memory(1024)

    def test_macos_monitoring_records_bytes_and_sampled_peak(self):
        with patch.object(runtime.sys,'platform','darwin'), \
             patch.object(runtime,'mac_process_info',return_value={'rss_bytes':125*runner.MIB}):
            self.assertEqual(runner.rss(123), '125 MiB')
            self.assertEqual(runner.sampled_rss(123), 125*runner.MIB)


class MacProcessTests(unittest.TestCase):
    def info(self, pid=100, parent=50, group=100, birth=(10,20)):
        return {'pid':pid, 'parent':parent, 'group':group, 'session':group, 'start_time':list(birth), 'rss_bytes':1024}

    def test_native_layout_and_query_decode_bytes_not_kilobytes(self):
        self.assertEqual(ctypes.sizeof(runtime._MacBsdInfo),136)
        self.assertEqual(ctypes.sizeof(runtime._MacTaskInfo),96)
        self.assertEqual(ctypes.sizeof(runtime._MacTaskAllInfo),232)
        def query(pid, flavor, argument, pointer, size):
            self.assertEqual((pid,flavor,argument,size),(100,2,0,232))
            info = pointer._obj
            info.bsd.pid=100; info.bsd.ppid=50; info.bsd.pgid=100
            info.bsd.start_sec=10; info.bsd.start_usec=20
            info.task.resident_size=12345678
            return size
        library=SimpleNamespace(proc_pidinfo=query)
        with patch.object(runtime,'_mac_libproc',return_value=library), patch.object(runtime.os,'getsid',return_value=100):
            self.assertEqual(runtime.mac_process_info(100),{**self.info(),'rss_bytes':12345678})
        library.proc_pidinfo=lambda *args:0
        with patch.object(runtime,'_mac_libproc',return_value=library):
            self.assertIsNone(runtime.mac_process_info(100))

    def test_group_enumeration_grows_a_full_buffer_and_filters_empty_slots(self):
        sizes=[]
        def query(kind, group, buffer, size):
            self.assertEqual((kind,group),(2,100))
            if buffer is None: return 128
            sizes.append(size)
            if len(sizes)==1: return size
            buffer[0]=100; buffer[1]=101
            return 12
        with patch.object(runtime,'_mac_libproc',return_value=SimpleNamespace(proc_listpids=query)):
            self.assertEqual(runtime.mac_group_members(100),[100,101])
        self.assertEqual(sizes,[256,512])

    def test_owned_child_binds_parent_session_and_precise_birth(self):
        with patch.object(runtime.sys,'platform','darwin'), patch.object(runtime,'mac_process_info',return_value=self.info()):
            self.assertEqual(runtime.owned_child_identity(100,50), {'pid':100,'parent':50,'start_time':[10,20]})
            self.assertIsNone(runtime.owned_child_identity(100,99))
        with patch.object(runtime.sys,'platform','darwin'), patch.object(runtime,'mac_process_info',return_value=self.info(group=99)):
            self.assertIsNone(runtime.owned_child_identity(100,50))

    def test_cleanup_rejects_reused_leader_and_accepts_surviving_owned_group(self):
        identity={'pid':100,'parent':50,'start_time':[10,20]}
        for leader, member, expected in (
            (self.info(birth=(11,20)),self.info(pid=101),False),
            (None,self.info(pid=101,birth=(10,21)),True),
            (None,self.info(pid=101,birth=(9,20)),False),
            (None,self.info(pid=101,group=99),False)):
            with self.subTest(leader=leader,member=member), patch.object(runtime.sys,'platform','darwin'), \
                 patch.object(runtime,'mac_process_info',side_effect=lambda pid: leader if pid==100 else member), \
                 patch.object(runtime,'mac_group_members',return_value=[101]), patch.object(runtime.os,'killpg') as kill:
                runtime.stop_owned_child(identity)
                self.assertEqual(kill.called, expected)
                if expected: kill.assert_called_once_with(100, signal.SIGKILL)

    def test_native_process_metadata_and_rss(self):
        if sys.platform!='darwin': self.skipTest('requires native macOS libproc')
        info=runtime.mac_process_info(os.getpid())
        self.assertEqual(info['pid'],os.getpid())
        self.assertEqual(info['parent'],os.getppid())
        self.assertGreater(info['rss_bytes'],0)
        self.assertIn(os.getpid(),runtime.mac_group_members(os.getpgrp()))


if __name__=='__main__': unittest.main()
