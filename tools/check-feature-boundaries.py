#!/usr/bin/env python3
"""Validate physical module ownership and one-way implementation dependencies."""
from pathlib import Path
import json,re,sys
root=Path(__file__).resolve().parents[1]
plan=json.loads((root/'tools/features.json').read_text());specs=plan['features']
available={n for n in specs if (root/f'features/{n}/build.gradle.kts').is_file()}
errors=[];owned=set(plan['foundation']);implementations={}
for name,spec in specs.items():
    owned.update(spec['files'])
    if name not in available:
        if (root/'features'/name).exists() and any(p.is_file() and 'build' not in p.relative_to(root/'features'/name).parts for p in (root/'features'/name).rglob('*')):
            errors.append(f'{name}: implementation exists without its module build file')
        continue
    missing=set(spec['requires'])-available
    if missing:errors.append(f'{name}: missing runtime prerequisites {sorted(missing)}')
    for path in spec['files']:
        if not (root/path).is_file():errors.append(f'{name}: missing owned file {path}')
        if not path.startswith('features/'+name+'/'):errors.append(f'{name}: file outside its module: {path}')
    script=(root/f'features/{name}/build.gradle.kts').read_text()
    for dep in re.findall(r'(?:api|implementation)\(project\("([^"]+)"\)\)',script):
        if dep!=':core:api':errors.append(f'{name}: implementation depends on {dep}; use a core API port')
    if name!='app-integration':
        descriptor=root/f'features/{name}/src/main/resources/META-INF/services/io.newsbrief.app.runtime.AppFeature'
        if not descriptor.is_file() or descriptor.read_text().strip()!=spec['binding']:errors.append(f'{name}: invalid plugin service descriptor')
    for f in (root/'features'/name/'src/main').rglob('*.kt'):
        for symbol in re.findall(r'^(?:data |sealed |abstract |open )?(?:class|object|interface)\s+(\w+)',f.read_text(),re.M):
            implementations[symbol]=name
for base in ['app','core','features']:
    for f in (root/base).rglob('*'):
        if not f.is_file() or 'build' in f.relative_to(root/base).parts:continue
        path=f.relative_to(root).as_posix()
        if f.suffix=='.kt' and path not in owned:errors.append(f'Unowned source: {path}')
for base in ['app/src/main','core/api/src/main','core/runtime/src/main']:
    for f in (root/base).rglob('*.kt'):
        for symbol in implementations:
            if re.search(r'\b'+symbol+r'\b',f.read_text()):errors.append(f'{f.relative_to(root)} references optional implementation {symbol}')
for name in available-{'app-integration'}:
    for f in (root/f'features/{name}/src/main').rglob('*.kt'):
        for symbol,owner in implementations.items():
            if owner!=name and re.search(r'\b'+symbol+r'\b',f.read_text()):errors.append(f'{name}: {f.name} imports {owner} implementation {symbol}')
        for symbol in ['NewsViewModel','NewsRepository','FeatureRuntime','NewsBriefApp','MemoryNewsStore','MemorySettings']:
            if re.search(r'\b'+symbol+r'\b',f.read_text()):errors.append(f'{name}: {f.name} depends on host implementation {symbol}')
# The host may coordinate plugins, but cannot consume or own a feature controller.
for f in (root/'core/runtime/src/main').rglob('*.kt'):
    for symbol in ['AppUi', 'FeedUi', 'FeedFilterUi', 'ReaderUi', 'AiUi', 'SourcesUi', 'DirectoryUi', 'SavedUi', 'BrowserUi', 'TelegramProxyUi', 'WorkerSettingsUi']:
        if re.search(r'\b'+symbol+r'\b', f.read_text()):errors.append(f'Host references feature controller {symbol}: {f.name}')
for f in (root/'core/api/src/main').rglob('*.kt'):
    for symbol in ['NewsViewModel','NewsRepository','FeatureRuntime','NewsBriefApp','MemoryNewsStore','MemorySettings']:
        if re.search(r'\b'+symbol+r'\b',f.read_text()):errors.append(f'API depends on implementation {symbol}: {f.name}')
if errors:
    print('\n'.join(sorted(set(errors))),file=sys.stderr);sys.exit(1)
print('Module boundaries OK: '+(', '.join(sorted(available)) or 'app shell and core only'))
