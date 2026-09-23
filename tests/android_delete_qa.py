"""Explicit QA cleanup via the actual Delete profile UI; never targets non-QA names."""
import re
import android_smoke as q
before = q.data()
qa = [p for p in before['profiles'] if re.fullmatch(r'QA (Alex|Sam)( \(import \d+\))*', p['name'])]
others = [p for p in before['profiles'] if p not in qa]
if not qa:
    print('No QA profiles to delete.')
    raise SystemExit()
q.tap('Delete profile ' + qa[0]['name'], attr='content-desc')
q.fill('Type profile name to confirm', 'Wrong name')
q.tap('DELETE PROFILE')
q.find('Type the profile name exactly to confirm deletion.')
q.tap('CANCEL')
assert q.data() == before
for p in qa:
    q.tap('Delete profile ' + p['name'], attr='content-desc')
    q.fill('Type profile name to confirm', p['name'])
    q.tap('DELETE PROFILE')
    current = q.data()
    assert all(row['id'] != p['id'] for row in current['profiles'])
    print('Deleted:', p['name'], flush=True)
assert q.data()['profiles'] == others
q.screenshot('android-clean.png')
print('PASS: exact-name confirmation, cancellation, permanent deletion and non-QA profile preservation.', flush=True)
