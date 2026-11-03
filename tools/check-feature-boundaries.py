#!/usr/bin/env python3
"""Validate physical module ownership and one-way implementation dependencies."""
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[1]
features=root/'features'
modules={p.name:p for p in features.iterdir() if p.is_dir() and (p/'build.gradle.kts').is_file()} if features.exists() else {}
errors=[];implementations={}
for name,module in modules.items():
    if not (module/'feature.properties').is_file():errors.append(f'{name}: missing feature.properties')
    script=(module/'build.gradle.kts').read_text()
    for dep in re.findall(r'(?:api|implementation)\(project\("([^\"]+)"\)\)',script):
        if dep!=':core:api':errors.append(f'{name}: implementation depends on {dep}; use a core API port')
    if name!='app-integration':
        descriptor=module/'src/main/resources/META-INF/services/io.newsbrief.app.runtime.AppFeature'
        if not descriptor.is_file() or not descriptor.read_text().strip():errors.append(f'{name}: missing plugin service descriptor')
    for f in (module/'src/main').rglob('*.kt'):
        for symbol in re.findall(r'^(?:data |sealed |abstract |open )?(?:class|object|interface)\s+(\w+)',f.read_text(),re.M):
            implementations[symbol]=name
for base in ['app/src/main','core/api/src/main','core/runtime/src/main']:
    for f in (root/base).rglob('*.kt'):
        for symbol in implementations:
            if re.search(r'\b'+symbol+r'\b',f.read_text()):errors.append(f'{f.relative_to(root)} references optional implementation {symbol}')
for name in set(modules)-{'app-integration'}:
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
print('Module boundaries OK: '+(', '.join(sorted(modules)) or 'app shell and core only'))
