"""Physical QA-edition workflow. Never clears app data. Requires --serial and --run.
Create and install app-qa.apk before running; use only a fresh QA edition.
"""
import argparse, json, re, shlex, subprocess, time, os
from pathlib import Path
from xml.etree import ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--run', action='store_true')
args = parser.parse_args()
ADB = os.environ.get('ADB', str(Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk'))) / 'platform-tools/adb'))
PACKAGE = 'pl.digitalbujo.app.qa'
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '.runtime' / 'qa'
OUT.mkdir(parents=True, exist_ok=True)

def adb(*parts, binary=False):
    return subprocess.check_output([ADB, '-s', args.serial, *parts], text=not binary, timeout=35)

def tree():
    adb('shell', 'uiautomator', 'dump', '/data/local/tmp/digital-bujo-qa.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/data/local/tmp/digital-bujo-qa.xml'))

def find(label, attr=None, contains=False):
    for attempt in range(4):
        doc = tree()
        for node in doc.iter('node'):
            keys = [attr] if attr else ['text', 'content-desc']
            if any((label in node.get(key, '') if contains else node.get(key) == label) for key in keys):
                return node
        time.sleep(.3)
    visible = [(n.get('class','').split('.')[-1], n.get('text'), n.get('content-desc')) for n in doc.iter('node') if n.get('package') == PACKAGE and (n.get('text') or n.get('content-desc'))]
    raise AssertionError(f'Not found: {label}. App UI: {visible}')

def tap(label, **kwargs):
    node = find(label, **kwargs)
    a,b,c,d = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((a+c)//2), str((b+d)//2))
    time.sleep(.2)

def fill(label, value):
    node = find(label, attr='content-desc')
    a,b,c,d = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((a+c)//2), str((b+d)//2))
    adb('shell', 'input', 'keyevent', '123')
    count = len(node.get('text','')) + 3
    adb('shell', 'input', 'keyevent', *(['67'] * count))
    adb('shell', 'input', 'text', shlex.quote(value.replace(' ', '%s')))
    adb('shell', 'input', 'keyevent', '111')  # dismiss keyboard without navigation

def data():
    return json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/journals.json'))

def screenshot(name):
    (OUT / name).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))

def run():
    # Refuse to mix automated fixtures into a previously used test edition.
    result = subprocess.run([ADB, '-s', args.serial, 'shell', 'run-as', PACKAGE, 'test', '-e', 'files/journals.json'], capture_output=True)
    if result.returncode == 0:
        raise RuntimeError('QA edition already has data. Review it before running; this script never resets it.')
    adb('shell', 'am', 'start', '-n', PACKAGE + '/pl.digitalbujo.app.MainActivity')
    find('Create a profile')
    screenshot('android-welcome.png')
    tap('Create a profile')
    fill('Full name', 'QA Alex')
    tap('SAVE')
    tap('Add journal')
    fill('Journal title', 'Phone notebook 2029')
    fill('Page count', '96')
    tap('SAVE')
    find('Phone notebook 2029')
    tap('Add spread')
    fill('Spread title', 'Quiet plans')
    fill('First page', '2')
    fill('Last page', '3')
    tap('SAVE')
    find('Quiet plans')
    tap('Edit Quiet plans')
    fill('Spread title', 'Quiet plans revised')
    tap('SAVE')
    find('Quiet plans revised')
    tap('Edit journal')
    fill('Page count', '2')
    tap('SAVE')
    find('Page count cannot exclude an existing spread.')
    fill('Page count', '96')
    tap('SAVE')
    tap('Add spread')
    fill('Spread title', 'Overlap')
    fill('First page', '3')
    fill('Last page', '4')
    tap('SAVE')
    find('These pages already belong to another spread.')
    tap('CANCEL')
    screenshot('android-journal.png')
    assert data()['profiles'][0]['journals'][0]['spreads'][0]['title'] == 'Quiet plans revised'
    adb('shell', 'input', 'keyevent', '3')
    adb('shell', 'am', 'start', '-n', PACKAGE + '/pl.digitalbujo.app.MainActivity')
    find('Quiet plans revised')
    # Actual process restart must read the saved journal and reopen its sole profile.
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-n', PACKAGE + '/pl.digitalbujo.app.MainActivity')
    tap('Open Phone notebook 2029')
    find('Quiet plans revised')
    adb('shell', 'input', 'keyevent', '4')
    find('Add journal')
    tap('Switch or add profile')
    tap('Create a profile')
    fill('Full name', 'QA Sam')
    tap('SAVE')
    assert len(data()['profiles'][1]['journals']) == 0
    find('Start with the notebook beside you.', contains=True)
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-n', PACKAGE + '/pl.digitalbujo.app.MainActivity')
    find('QA Alex', contains=True); find('QA Sam', contains=True)
    screenshot('android-profiles.png')
    print('PASS: physical-phone onboarding, journal/spread creation, editing, range errors, background/resume, process restart, Back navigation and profile isolation.')

if __name__ == '__main__':
    if args.run:
        run()
    else:
        print('Supply --run to test the fresh QA edition; no device changes made.')
