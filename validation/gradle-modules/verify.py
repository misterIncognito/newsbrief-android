from pathlib import Path
import subprocess,json,os,xml.etree.ElementTree as ET,zipfile,hashlib,time,shutil,sys
root=Path.cwd();out=Path('/private/tmp/newsbrief-gradle-module-checks');out.mkdir(exist_ok=True)
env=os.environ.copy();env.update(JAVA_HOME=str(root.parent/'.toolchain/jdk-17.0.20.1+1/Contents/Home'),ANDROID_HOME=str(root.parent/'.toolchain/android-sdk'),GRADLE_USER_HOME=str(root.parent/'.toolchain/gradle-home'))
plan=json.loads(Path('tools/features.json').read_text())['features'];results=[]
impls={'save-articles':'ui/SaveArticleAction','share-articles':'ui/ShareArticleAction','source-discovery':'ui/SourceDirectoryPageKt','source-management':'ui/SourcesPageKt','news-proxy':'network/CloudflarePageProxy','page-proxy':'network/PageProxyRegistry','rss-source':'network/RssSourcePlugin','telegram-source':'network/TelegramPreviewSourcePlugin','telegram-proxy':'network/TelegramProxyClient','native-reader':'network/ArticleReader','embedded-browser':'ui/EmbeddedBrowserPageKt','feed-filters':'data/FeedFilters','synchronization':'worker/RefreshWorker','ai':'ai/AiClient','storage':'data/NewsDatabase','article-plugins':'network/ArticleFetchRegistry','source-providers':'network/SourcePluginRegistry','networking':'network/NewsHttp','feed':'features/FeedFeature'}
def git(*args):return subprocess.check_output(['git',*args],text=True).strip()
def source_hash():return hashlib.sha256(git('ls-tree','-r','HEAD','app','core','features','build.gradle.kts','settings.gradle.kts','tools/features.json').encode()).hexdigest()
def verify(name,request=None,existing_log=None):
 subprocess.run(['python3','tools/check-feature-boundaries.py'],check=True,stdout=subprocess.DEVNULL)
 log=out/(name+'.txt');tasks=['verifyModuleInterfaces',':app:testDebugUnitTest',':app:assembleDebug']
 if name in ['full','app-integration']:tasks+=[':app:assembleDebugAndroidTest']
 print('Testing '+git('branch','--show-current')+' ('+name+')',flush=True);start=time.time()
 if existing_log:
  shutil.copy2(existing_log,log);code=0
  assert 'BUILD SUCCESSFUL' in log.read_text()
 else:
  with log.open('w') as f:code=subprocess.run(['./gradlew',*tasks,*(['-PnewsbriefFeatures='+request] if request is not None else []),'--console=plain'],env=env,stdout=f,stderr=subprocess.STDOUT).returncode
 available={n for n in plan if Path('features/'+n+'/build.gradle.kts').exists()}
 enabled=set(filter(None,request.split(','))) if request is not None else available-{'app-integration'}
 while True:
  expanded=enabled|{d for n in enabled for d in plan[n]['requires']}
  if expanded==enabled:break
  enabled=expanded
 integration='app-integration' in available and set(plan['app-integration']['testRequires'])<=enabled
 item={'stage':name,'branch':git('branch','--show-current'),'commit':git('rev-parse','HEAD'),'compilationTree':source_hash(),'requestedFeatures':request,'enabledFeatures':sorted(enabled),'exitCode':code,'seconds':round(time.time()-start,1),'log':log.name,'tasks':tasks}
 if code==0:
  projects=['app','core/api','core/runtime']+['features/'+n for n in sorted(enabled|({'app-integration'} if integration else set()))]
  perproject={}
  for project in projects:
   files=list(Path(project+'/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
   perproject[project]={k:sum(int(ET.parse(p).getroot().attrib.get(k,0)) for p in files) for k in ['tests','failures','errors','skipped']}
  item['testsByModule']=perproject;item['tests']={k:sum(m[k] for m in perproject.values()) for k in ['tests','failures','errors','skipped']}
  assert item['tests']['tests']>0 and not any(item['tests'][k] for k in ['failures','errors','skipped']),item
  apk=Path('app/build/outputs/apk/debug/app-debug.apk')
  with zipfile.ZipFile(apk) as z:
   data=b''.join(z.read(x) for x in z.namelist() if x.endswith('.dex'))
   descriptor='META-INF/services/io.newsbrief.app.runtime.AppFeature'
   services=set(z.read(descriptor).decode().split()) if descriptor in z.namelist() else set()
  item['pluginServices']=sorted(services)
  assert services=={plan[n]['binding'] for n in enabled},services
  item['compiledImplementations']={feature:(('Lio/newsbrief/app/'+path+';').encode() in data) for feature,path in impls.items()}
  assert all(present==(feature in enabled) for feature,present in item['compiledImplementations'].items()),item
  assert b'Lio/newsbrief/app/FixtureModulePlugin;' not in data and b'Lio/newsbrief/app/TestSettings;' not in data
  item['compilerProjectGraph']=json.loads(Path('build/reports/module-interfaces.json').read_text())
  assert set(item['compilerProjectGraph'])=={':features:'+n for n in enabled|({'app-integration'} if integration else set())}
  assert all(refs==[':core:api'] for refs in item['compilerProjectGraph'].values())
  copy=Path('app/build/outputs/apk/verified')/('newsbrief-modules-'+name+'-debug.apk');copy.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(apk,copy)
  item['apk']=str(copy);item['apkSha256']=hashlib.sha256(copy.read_bytes()).hexdigest()
 results.append(item);(out/'results.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps({'stage':name,'exitCode':code,'tests':item.get('tests'),'apk':item.get('apk')}),flush=True)
 if code:raise SystemExit(code)
mode=sys.argv[1]
if mode=='profiles':
 verify('full',existing_log='/private/tmp/newsbrief-real-modules-build11.txt')
 verify('selected-mvp','feed,rss-source,storage')
 verify('selected-share-browser','feed,rss-source,storage,share-articles,embedded-browser')
 (out/'profiles.json').write_text(json.dumps(results,indent=2)+'\n')
else:
 branches=['integration/feed','feature/app-integration','dev']+['feature/'+n for n in plan if n!='app-integration']
 for branch in branches:
  git('switch',branch);verify('mvp' if branch=='integration/feed' else branch.split('/')[-1])
 git('switch','-c','verify/gradle-module-merges','integration/feed')
 for label,features in [('add-save',['save-articles']),('add-share',['share-articles']),('add-catalog',['source-management','source-discovery']),('add-proxy-plugins',['news-proxy','telegram-proxy'])]:
  for feature in features:git('merge','--no-edit','feature/'+feature)
  verify(label)
 (out/'branches.json').write_text(json.dumps(results,indent=2)+'\n')
 git('switch','integration/feed');assert not git('status','--porcelain')
