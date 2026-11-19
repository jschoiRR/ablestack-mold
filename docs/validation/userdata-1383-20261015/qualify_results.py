# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
"""Keep all attempts, qualify the last result of each case and measured active colors."""
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parent
def read(name):
    return json.loads((root / name).read_text(encoding='utf-8'))
def save(name, value):
    (root / name).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8', newline='\n')

current = read('ui-results.json')
if 'all_attempts' not in current:
    save('ui-attempts.json', current)
raw = read('ui-attempts.json')
latest = {row['case']: row for row in raw['cases']}
assert all(row['passed'] for row in latest.values()), [row for row in latest.values() if not row['passed']]
save('ui-results.json', dict(uiSource=raw['uiSource'], cases=list(latest.values()), passed=len(latest),
    all_attempts='ui-attempts.json', measurement_notes=[
        'Final scroll comparisons use a settled field layout and native keys without locator refocus.',
        'Early 196-to-174 comparisons included an asynchronous 22px field explanation collapse before submission.',
        'Reopen assertion was narrowed to variable tags: the default domain has an empty selected span.',
        'Role visibility assertions require a visible dialog and name input; empty DOM is not accepted.'
    ]))

def rgba(value):
    values = [float(x) for x in re.findall(r'[\d.]+', value)]
    assert len(values) in (3, 4), value
    return values[:3] + [values[3] if len(values) == 4 else 1]
def blend(foreground, background):
    return [foreground[i]*foreground[3]+background[i]*(1-foreground[3]) for i in range(3)]
def luminance(rgb):
    def component(value):
        value /= 255
        return value/12.92 if value <= 0.04045 else ((value+0.055)/1.055)**2.4
    return sum(component(value)*weight for value, weight in zip(rgb, [0.2126, 0.7152, 0.0722]))
def contrast(color, surface, base):
    bg = blend(rgba(surface), base)
    fg = blend(rgba(color), bg)
    a, b = sorted([luminance(fg), luminance(bg)])
    return (b+0.05)/(a+0.05)

results = []
for path in sorted(root.glob('*-metrics.json')):
    data = read(path.name)
    if not data.get('themeSamples') or not data.get('dialog'):
        continue
    # Disabled/loading controls have a separate readability policy; ancestor spin opacity is not a text pass.
    if any(row['disabled'] for row in data['values']):
        continue
    base = [34, 40, 47] if data['dark'] else [255, 255, 255]
    for role, sample in data['themeSamples'].items():
        if not sample or role == 'checkbox':
            continue
        threshold = 3 if role == 'help' else 4.5
        ratio = contrast(sample['color'], sample['surface'], base)
        results.append(dict(file=path.name, theme='dark' if data['dark'] else 'light', role=role, ratio=round(ratio, 3), threshold=threshold, passed=ratio>=threshold))
    sample = data['themeSamples'].get('input')
    if sample:
        ratio = contrast(sample['border'], sample['surface'], base)
        results.append(dict(file=path.name, theme='dark' if data['dark'] else 'light', role='input-border', ratio=round(ratio, 3), threshold=3, passed=ratio>=3))
assert results and all(row['passed'] for row in results), [row for row in results if not row['passed']]
save('contrast-results.json', dict(samples=results, sample_count=len(results), all_passed=True,
    scope='Measured active text, placeholders, popup/tooltip, help icon and input boundaries. Disabled/loading captured separately; not a whole-menu WCAG audit.'))
print('Final UI cases:', len(latest), 'PASS; active contrast samples:', len(results), 'PASS')
