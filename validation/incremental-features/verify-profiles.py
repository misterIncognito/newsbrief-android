from pathlib import Path
import subprocess,json,os,xml.etree.ElementTree as ET,zipfile,hashlib,time,shutil
r=Path.cwd();out=Path('/private/tmp/newsbrief-runtime-profiles');out.mkdir(exist_ok=True)
env=os.environ.copy();env.update(JAVA_HOME=str(r.parent/'.toolchain/jdk-17.0.20.1+1/Contents/Home'),ANDROID_HOME=str(r.parent/'.toolchain/android-sdk'),GRADLE_USER_HOME=str(r.parent/'.toolchain/gradle-home'))
profiles=[('shell',''),('feed','feed'),('rss-feed','feed,sources-discovery'),('offline-feed','feed,sources-discovery,storage'),('background-feed','feed,sources-discovery,storage,synchronization'),('reader-feed','feed,sources-discovery,storage,reader-browser'),('ai-feed','feed,sources-discovery,storage,ai'),('plugins-feed','feed,sources-discovery,storage,article-plugins'),('full',None)]
result=[]
impls={'feed':'features/FeedFeature','sources-discovery':'features/SourcesFeature','networking':'features/NetworkingFeature','storage':'data/NewsDatabase','synchronization':'worker/RefreshWorker','reader-browser':'network/ArticleReader','ai':'ai/AiClient','article-plugins':'network/ArticleFetchRegistry'}
for name,selection in profiles:
 log=out/(name+'.txt');tasks=[':app:testDebugUnitTest',':app:assembleDebug']
 if name=='full':tasks+=[':app:assembleDebugAndroidTest']
 cmd=['./gradlew',*tasks,'--console=plain']
 if selection is not None:cmd+=['-PnewsbriefFeatures='+selection]
 print('Testing runnable profile '+name,flush=True);start=time.time()
 with log.open('w') as f:run=subprocess.run(cmd,env=env,stdout=f,stderr=subprocess.STDOUT)
 item={'profile':name,'requestedFeatures':selection,'exitCode':run.returncode,'seconds':round(time.time()-start,1),'log':log.name,'tasks':tasks}
 if run.returncode==0:
  files=list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
  item['tests']={k:sum(int(ET.parse(p).getroot().attrib.get(k,0)) for p in files) for k in ['tests','failures','errors','skipped']}
  apk=Path('app/build/outputs/apk/debug/app-debug.apk')
  with zipfile.ZipFile(apk) as z:data=b''.join(z.read(x) for x in z.namelist() if x.endswith('.dex'))
  item['compiledImplementations']={feature:(('Lio/newsbrief/app/'+path+';').encode() in data) for feature,path in impls.items()}
  expected=set() if not selection and selection is not None else set(impls) if selection is None else set(selection.split(','))
  if 'sources-discovery' in expected or 'reader-browser' in expected or 'article-plugins' in expected:expected.add('networking')
  if 'article-plugins' in expected:expected.update(['sources-discovery','reader-browser'])
  assert all(present==(feature in expected) for feature,present in item['compiledImplementations'].items()),item
  copy=Path('app/build/outputs/apk/verified')/('newsbrief-'+name+'-incremental-debug.apk');copy.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(apk,copy)
  item['apk']=str(copy);item['apkSha256']=hashlib.sha256(copy.read_bytes()).hexdigest()
 result.append(item);(out/'results.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(item),flush=True)
 if run.returncode:raise SystemExit(run.returncode)
