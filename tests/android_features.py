"""New-feature checks against the isolated QA edition; pass --serial DEVICE --run."""
import android_smoke as q
import time, re, json
if not q.args.run: raise SystemExit('Use --run with an explicit serial.')
def find_scroll(label):
    for _ in range(6):
        doc=q.tree()
        for n in doc.iter('node'):
            if label in (n.get('text'),n.get('content-desc')):
                a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
                if d>b and b>100 and d<2250:return n
        q.adb('shell','input','swipe','550','1800','550','700','350')
    raise AssertionError('Not found after scrolling: '+label)
def tap(label):
    n=find_scroll(label);a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')));q.adb('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(.25)
def fill(label,value):find_scroll(label);q.fill(label,value)
q.adb('shell','am','start','-n',q.PACKAGE+'/pl.digitalbujo.app.MainActivity')
q.tap('Create a profile');q.fill('Full name','QA Alex');q.tap('SAVE')
q.tap('Tasks & plans');q.tap('Add task');q.fill('Task title','Prepare a page');q.fill('Planned date (YYYY-MM-DD, optional)','2027-02-29');fill('Subtasks (one per line)','Find a pen');q.tap('SAVE')
q.find('Text \'2027-02-29\' could not be parsed: Invalid date \'February 29\' as \'2027\' is not a leap year')
q.fill('Planned date (YYYY-MM-DD, optional)','2028-02-29');q.tap('SAVE');q.find('Prepare a page')
tap('Complete Prepare a page');q.find('Reopen Prepare a page');tap('Find a pen');assert q.data()['profiles'][0]['tasks'][0]['subtasks'][0]['done']
q.screenshot('android-tasks.png')
q.tap('Back to journal');q.tap('Tasks & plans');tap('Edit task');q.fill('Task title','Draft survives rotation')
q.adb('shell','wm','user-rotation','lock','1');time.sleep(1);q.find('Draft survives rotation');q.adb('shell','wm','user-rotation','free');time.sleep(1);q.tap('CANCEL')
q.tap('Back to journal');q.tap('QR');q.fill('Website link','https://example.org/journal');q.tap('Generate QR code');q.find('QR code for https://example.org/journal');q.screenshot('android-qr.png')
tap('Scan QR code')
print('CHECKPOINT: tasks, completion, draft rotation and QR generation passed.',flush=True)
print([(n.get('text'),n.get('content-desc')) for n in q.tree().iter('node') if n.get('text')],flush=True)
