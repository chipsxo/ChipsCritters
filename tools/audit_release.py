"""Audit only explicitly allowed release-source files and reachable public Git history.
Print filenames/categories, never matching secret contents. No signing-key folder access.
"""
from pathlib import Path
import sys,re,json,hashlib,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
ROOT_FILES={'.gitignore','README.md','LICENSE','THIRD_PARTY_NOTICES.md','AI_DISCLOSURE.md','SECURITY.md','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','RELEASE-NOTES-0.1.64.md','RELEASE-NOTES-0.1.65.md'}
BAD_PARTS={'.git','.gradle','.gradle-home','work','build','run','logs','config','__pycache__','.chipscritters-signing'}
PATTERNS=[('private key',re.compile(rb'-----BEGIN (?:[A-Z0-9]+ )?PRIVATE KEY-----')),('GitHub credential',re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,})')),('AWS credential',re.compile(rb'AKIA[0-9A-Z]{16}')),('encoded Ed25519 private key',re.compile(rb'MC4CAQAwBQYDK2VwBCIEI[A-Za-z0-9+/=]{39,}'))]
def candidates():
 result=[ROOT/n for n in ROOT_FILES if (ROOT/n).is_file()]
 for directory in ('src','docs','gradle','.github','tools','relay'):
  result.extend(p for p in (ROOT/directory).rglob('*') if p.is_file() and not any(part in BAD_PARTS for part in p.relative_to(ROOT).parts) and p.suffix not in ('.pyc','.class'))
 return sorted(set(result))
def forbidden(name):
 p=Path(name);parts=set(p.parts)
 return bool(parts&BAD_PARTS) or p.name.lower() in {'private.pk8','launcher_accounts.json','launcher_profiles.json','accounts.json','options.txt','servers.dat'} or p.suffix.lower() in {'.pk8','.pem','.key','.p12','.pfx','.log','.zip'} or (p.suffix=='.jar' and p.as_posix()!='gradle/wrapper/gradle-wrapper.jar')
def scan(name,data):
 errors=[]
 if forbidden(name):errors.append((name,'forbidden filename'))
 for category,pattern in PATTERNS:
  if pattern.search(data):errors.append((name,category))
 return errors
def main():
 files=candidates();errors=[];manifest=[]
 for p in files:
  relative=p.relative_to(ROOT).as_posix()
  if p.is_symlink() and not p.resolve().is_relative_to(ROOT):errors.append((relative,'external symlink'));continue
  data=p.read_bytes();errors.extend(scan(relative,data));manifest.append({'path':relative,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
  if p.suffix=='.png':
   import struct
   at=8
   while at<len(data):
    n=struct.unpack('>I',data[at:at+4])[0];kind=data[at+4:at+8]
    if kind not in (b'IHDR',b'IDAT',b'IEND'):errors.append((relative,'image metadata not stripped'))
    at+=12+n
 history=ROOT/'work/release-history-0.1.64';commits=0;blobs=0
 if (history/'.git').exists():
  git=lambda *args:subprocess.check_output(['git','-C',str(history),*args])
  commits=int(git('rev-list','--all','--count'))
  for line in git('rev-list','--all','--objects').decode().splitlines():
   fields=line.split(' ',1);sha=fields[0]
   if git('cat-file','-t',sha).strip()!=b'blob':continue
   name=fields[1] if len(fields)>1 else '<unnamed blob>';blobs+=1;errors.extend(scan(name,git('cat-file','blob',sha)))
 else:errors.append(('history','required public history clone missing'))
 audit={'passed':not errors,'fileCount':len(files),'sourceBytes':sum(f['bytes'] for f in manifest),'historyCommits':commits,'historyBlobs':blobs,'findings':[{'file':n,'category':c} for n,c in errors],'files':manifest}
 (ROOT/'work/release-audit64.json').write_text(json.dumps(audit,indent=2),encoding='utf-8')
 print(json.dumps({k:v for k,v in audit.items() if k!='files'}))
 if errors:sys.exit(2)
if __name__=='__main__':main()
