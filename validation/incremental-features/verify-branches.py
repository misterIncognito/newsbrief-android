from pathlib import Path
import subprocess,json,os,xml.etree.ElementTree as ET,zipfile,hashlib,time,shutil
r=Path.cwd();out=Path('/private/tmp/newsbrief-incremental-branches');out.mkdir(exist_ok=True)
env=os.environ.copy();env.update(JAVA_HOME=str(r.parent/'.toolchain/jdk-17.0.20.1+1/Contents/Home'),ANDROID_HOME=str(r.parent/'.toolchain/android-sdk'),GRADLE_USER_HOME=str(r.parent/'.toolchain/gradle-home'))
result=[]
impls={'feed':'features/FeedFeature','sources-discovery':'features/SourcesFeature','networking':'features/NetworkingFeature','storage':'data/NewsDatabase','synchronization':'worker/RefreshWorker','reader-browser':'network/ArticleReader','ai':'ai/AiClient','article-plugins':'network/ArticleFetchRegistry'}
def git(*args):return subprocess.check_output(['git',*args],text=True).strip()
def verify(name):
 subprocess.run(['python3','tools/check-feature-boundaries.py'],check=True,stdout=subprocess.DEVNULL)
 log=out/(name+'.txt');tasks=[':app:testDebugUnitTest',':app:assembleDebug']
 if name=='app-integration':tasks+=[':app:assembleDebugAndroidTest']
 print('Testing actual branch '+git('branch','--show-current')+' ('+name+')',flush=True);start=time.time()
 with log.open('w') as f:run=subprocess.run(['./gradlew',*tasks,'--console=plain'],env=env,stdout=f,stderr=subprocess.STDOUT)
 item={'stage':name,'branch':git('branch','--show-current'),'commit':git('rev-parse','HEAD'),'applicationTree':git('rev-parse','HEAD:app'),'exitCode':run.returncode,'seconds':round(time.time()-start,1),'log':log.name,'tasks':tasks}
 if run.returncode==0:
  files=list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
  item['tests']={k:sum(int(ET.parse(p).getroot().attrib.get(k,0)) for p in files) for k in ['tests','failures','errors','skipped']}
  apk=Path('app/build/outputs/apk/debug/app-debug.apk')
  with zipfile.ZipFile(apk) as z:data=b''.join(z.read(x) for x in z.namelist() if x.endswith('.dex'))
  item['compiledImplementations']={feature:(('Lio/newsbrief/app/'+path+';').encode() in data) for feature,path in impls.items()}
  expected={feature for feature in impls if Path('app/features/'+feature+'.gradle.kts').exists()}
  assert all(present==(feature in expected) for feature,present in item['compiledImplementations'].items()),item
  copy=Path('app/build/outputs/apk/verified')/('newsbrief-branch-'+name+'-debug.apk');copy.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(apk,copy)
  item['apk']=str(copy);item['apkSha256']=hashlib.sha256(copy.read_bytes()).hexdigest()
 result.append(item);(out/'results.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(item),flush=True)
 if run.returncode:raise SystemExit(run.returncode)
for branch in ['dev']+['feature/'+x for x in ['networking','storage','sources-discovery','reader-browser','synchronization','feed','ai','article-plugins','app-integration']]:
 git('switch',branch);verify(branch.split('/')[-1])
git('switch','-c','integration/feed','dev')
for feature,label in [('feed','step1-feed'),('sources-discovery','step2-rss-feed'),('storage','step3-offline-feed')]:
 git('merge','--no-edit','feature/'+feature);verify(label)
assert not git('status','--porcelain')
