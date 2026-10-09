#!/usr/bin/env python3
"""Check optional feature ownership and dependencies in the current checkout."""
from pathlib import Path
import json
import re
import sys

root = Path(__file__).resolve().parents[1]
plan = json.loads((root / 'tools/features.json').read_text())
features = plan['features']
enabled = {name for name in features if (root / f'app/features/{name}.gradle.kts').is_file()}
errors = []
owners = {path: name for name, feature in features.items() for path in feature['files']}
for name in sorted(enabled):
    feature = features[name]
    missing = set(feature['requires']) - enabled
    if missing:
        errors.append(f'{name} requires {sorted(missing)}')
    for path in feature['files']:
        if not (root / path).is_file():
            errors.append(f'{name} is missing {path}')
    if name != 'app-integration':
        for path in feature['files']:
            if '/src/main/java/' not in path:
                continue
            content = (root / path).read_text() if (root / path).is_file() else ''
            for forbidden in ['NewsBriefApp']:
                if re.search(r'\b' + forbidden + r'\b', content):
                    errors.append(f'{name}: {path} references integration class {forbidden}')
            if name != 'storage':
                for forbidden in ['SettingsStore', 'NewsDatabase', 'androidx.room.Room', 'androidx.room.withTransaction']:
                    if re.search(r'\b' + re.escape(forbidden) + r'\b', content):
                        errors.append(f'{name}: {path} references storage implementation {forbidden}')
for path, owner in owners.items():
    if owner not in enabled and (root / path).is_file():
        # The application shell intentionally keeps its activity/application/manifest.
        if Path(path).name not in ['MainActivity.kt', 'NewsBriefApp.kt', 'AndroidManifest.xml']:
            errors.append(f'{path} exists but its feature {owner} is disabled')
known = set(plan['foundation']) | set(owners)
known.update({
    'app/src/main/java/io/newsbrief/app/ui/NewsBriefShell.kt',
    'app/src/test/java/io/newsbrief/app/ShellAppTest.kt',
})
for folder in ['app/src/main/java', 'app/src/test/java', 'app/src/androidTest']:
    for file in (root / folder).rglob('*.kt'):
        path = file.relative_to(root).as_posix()
        if path not in known:
            errors.append(f'{path} has no declared owner in tools/features.json')
# The runnable foundation may reference ports, never optional implementations.
implementation_names = [
    'NewsHttp', 'NewsDatabase', 'SettingsStore', 'WebCollectors', 'SourcePluginRegistry',
    'PublicDirectoryNetwork', 'SourceCatalog', 'FeedFilters', 'ArticleReader',
    'ArticleImageCache', 'FeedSynchronizer', 'ArticlePrefetcher', 'RefreshWorker',
    'PrefetchWorker', 'FeedPage', 'FeedAutoRefresh', 'AiClient', 'DigestPage',
    'ArticleFetchRegistry', 'ReaderPage', 'EmbeddedBrowserPage', 'NewsCard',
    'SaveArticleService', 'SaveArticleAction', 'ShareArticleAction', 'CloudflarePageProxy',
    'TelegramProxyClient', 'RssSourcePlugin', 'TelegramPreviewSourcePlugin', 'SourcesPage',
    'SourceDirectoryPage', 'FeedFilterControls', 'PageProxyRegistry',
]
for path in plan['foundation']:
    if not (root / path).is_file():
        errors.append(f'foundation is missing {path}')
    elif '/src/main/java/' in path:
        content = (root / path).read_text()
        for name in implementation_names:
            if re.search(r'\b' + name + r'\b', content):
                errors.append(f'foundation: {path} references optional implementation {name}')

if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print('Feature boundaries OK: ' + (', '.join(sorted(enabled)) or 'foundation and shell only'))
