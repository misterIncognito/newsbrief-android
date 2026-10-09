from pathlib import Path
import subprocess,json,os,xml.etree.ElementTree as ET,zipfile,hashlib,time,shutil
r=Path.cwd();out=Path('/private/tmp/newsbrief-capability-profiles');out.mkdir(exist_ok=True)
env=os.environ.copy();env.update(JAVA_HOME=str(r.parent/'.toolchain/jdk-17.0.20.1+1/Contents/Home'),ANDROID_HOME=str(r.parent/'.toolchain/android-sdk'),GRADLE_USER_HOME=str(r.parent/'.toolchain/gradle-home'))
plan=json.loads(Path('tools/features.json').read_text())['features'];mvp='feed,rss-source,storage'
profiles=[('full',None),('mvp',mvp),('mvp-session','feed,rss-source'),('feed','feed'),('shell','')]+[('mvp-'+f,mvp+','+f) for f in ['save-articles','share-articles','source-management','source-discovery','feed-filters','news-proxy','telegram-source','telegram-proxy','native-reader','embedded-browser']]
result=[]
impls={'save-articles':'ui/SaveArticleAction','share-articles':'ui/ShareArticleAction','source-discovery':'ui/SourceDirectoryPageKt','source-management':'ui/SourcesPageKt','news-proxy':'network/CloudflarePageProxy','page-proxy':'network/PageProxyRegistry','rss-source':'network/RssSourcePlugin','telegram-source':'network/TelegramPreviewSourcePlugin','telegram-proxy':'network/TelegramProxyClient','native-reader':'network/ArticleReader','embedded-browser':'ui/EmbeddedBrowserPageKt','feed-filters':'data/FeedFilters','synchronization':'worker/RefreshWorker','ai':'ai/AiClient','storage':'data/NewsDatabase','article-plugins':'network/ArticleFetchRegistry','source-providers':'network/SourcePluginRegistry','networking':'network/NewsHttp','feed':'features/FeedFeature'}
for name,selection in profiles:
 log=out/(name+'.txt');tasks=[':app:testDebugUnitTest',':app:assembleDebug']
 if name=='full':tasks+=[':app:assembleDebugAndroidTest']
 cmd=['./gradlew',*tasks,'--console=plain']
 if selection is not None:cmd+=['-PnewsbriefFeatures='+selection]
 print('Testing optional composition '+name,flush=True);start=time.time()
 with log.open('w') as f:run=subprocess.run(cmd,env=env,stdout=f,stderr=subprocess.STDOUT)
 item={'profile':name,'requestedFeatures':selection,'exitCode':run.returncode,'seconds':round(time.time()-start,1),'log':log.name,'tasks':tasks}
 if run.returncode==0:
  files=list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
  item['tests']={k:sum(int(ET.parse(p).getroot().attrib.get(k,0)) for p in files) for k in ['tests','failures','errors','skipped']}
  expected=set(impls) if selection is None else set(filter(None,selection.split(',')))
  changed=True
  while changed:
   before=len(expected)
   for feature in list(expected):expected.update(plan[feature]['requires'])
   changed=len(expected)!=before
  item['enabledFeatures']=sorted(expected)
  apk=Path('app/build/outputs/apk/debug/app-debug.apk')
  with zipfile.ZipFile(apk) as z:data=b''.join(z.read(x) for x in z.namelist() if x.endswith('.dex'))
  item['compiledImplementations']={feature:(('Lio/newsbrief/app/'+path+';').encode() in data) for feature,path in impls.items()}
  assert all(present==(feature in expected) for feature,present in item['compiledImplementations'].items()),item
  copy=Path('app/build/outputs/apk/verified')/('newsbrief-optional-'+name+'-debug.apk');copy.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(apk,copy)
  item['apk']=str(copy);item['apkSha256']=hashlib.sha256(copy.read_bytes()).hexdigest()
 result.append(item);(out/'results.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(item),flush=True)
 if run.returncode:raise SystemExit(run.returncode)
