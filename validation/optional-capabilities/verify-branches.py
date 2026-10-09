from pathlib import Path
import subprocess,json,os,xml.etree.ElementTree as ET,zipfile,hashlib,time,shutil
r=Path.cwd();out=Path('/private/tmp/newsbrief-capability-branches');out.mkdir(exist_ok=True)
env=os.environ.copy();env.update(JAVA_HOME=str(r.parent/'.toolchain/jdk-17.0.20.1+1/Contents/Home'),ANDROID_HOME=str(r.parent/'.toolchain/android-sdk'),GRADLE_USER_HOME=str(r.parent/'.toolchain/gradle-home'))
plan=json.loads(Path('tools/features.json').read_text())['features'];result=[]
impls={'save-articles':'ui/SaveArticleAction','share-articles':'ui/ShareArticleAction','source-discovery':'ui/SourceDirectoryPageKt','source-management':'ui/SourcesPageKt','news-proxy':'network/CloudflarePageProxy','page-proxy':'network/PageProxyRegistry','rss-source':'network/RssSourcePlugin','telegram-source':'network/TelegramPreviewSourcePlugin','telegram-proxy':'network/TelegramProxyClient','native-reader':'network/ArticleReader','embedded-browser':'ui/EmbeddedBrowserPageKt','feed-filters':'data/FeedFilters','synchronization':'worker/RefreshWorker','ai':'ai/AiClient','storage':'data/NewsDatabase','article-plugins':'network/ArticleFetchRegistry','source-providers':'network/SourcePluginRegistry','networking':'network/NewsHttp','feed':'features/FeedFeature'}
def git(*args):return subprocess.check_output(['git',*args],text=True).strip()
def verify(name):
 subprocess.run(['python3','tools/check-feature-boundaries.py'],check=True,stdout=subprocess.DEVNULL)
 log=out/(name+'.txt');tasks=[':app:testDebugUnitTest',':app:assembleDebug']
 if name=='app-integration':tasks+=[':app:assembleDebugAndroidTest']
 print('Testing actual checkout '+git('branch','--show-current')+' ('+name+')',flush=True);start=time.time()
 with log.open('w') as f:run=subprocess.run(['./gradlew',*tasks,'--console=plain'],env=env,stdout=f,stderr=subprocess.STDOUT)
 item={'stage':name,'branch':git('branch','--show-current'),'commit':git('rev-parse','HEAD'),'applicationTree':git('rev-parse','HEAD:app'),'exitCode':run.returncode,'seconds':round(time.time()-start,1),'log':log.name,'tasks':tasks}
 if run.returncode==0:
  files=list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
  item['tests']={k:sum(int(ET.parse(p).getroot().attrib.get(k,0)) for p in files) for k in ['tests','failures','errors','skipped']}
  expected={f for f in impls if Path('app/features/'+f+'.gradle.kts').exists()};item['enabledFeatures']=sorted(expected)
  apk=Path('app/build/outputs/apk/debug/app-debug.apk')
  with zipfile.ZipFile(apk) as z:data=b''.join(z.read(x) for x in z.namelist() if x.endswith('.dex'))
  item['compiledImplementations']={feature:(('Lio/newsbrief/app/'+path+';').encode() in data) for feature,path in impls.items()}
  assert all(present==(feature in expected) for feature,present in item['compiledImplementations'].items()),item
  copy=Path('app/build/outputs/apk/verified')/('newsbrief-capability-'+name+'-debug.apk');copy.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(apk,copy)
  item['apk']=str(copy);item['apkSha256']=hashlib.sha256(copy.read_bytes()).hexdigest()
 result.append(item);(out/'results.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(item),flush=True)
 if run.returncode:raise SystemExit(run.returncode)
branches=['integration/feed','feature/app-integration','dev']+['feature/'+x for x in plan if x!='app-integration']
for branch in branches:
 git('switch',branch);verify('mvp' if branch=='integration/feed' else branch.split('/')[-1])
git('switch','-c','verify/optional-feature-merges','integration/feed')
for label,features in [('add-save',['save-articles']),('add-share',['share-articles']),('add-catalog',['source-management','source-discovery']),('add-proxy-plugins',['news-proxy','telegram-proxy'])]:
 for feature in features:git('merge','--no-edit','feature/'+feature)
 verify(label)
git('switch','integration/feed')
assert not git('status','--porcelain')
