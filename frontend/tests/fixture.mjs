export const fixture = `(() => {
  const user = {id:'00000000-0000-4000-8000-000000000001',email:'fixture@example.test',profile:{displayName:'Alex Morgan',timeZone:'UTC',skills:['java'],collaborationWindows:[]}};
  const event = {id:'10000000-0000-4000-8000-000000000001',name:'TeamForge Demo',minSize:2,maxSize:5,active:true};
  const member = {userId:user.id,displayName:user.profile.displayName,joinedAt:'2026-11-01T00:00:00Z',leader:true};
  const team = {id:'20000000-0000-4000-8000-000000000001',hackathonId:event.id,name:'Open Circuit',description:'Build tools that make collaboration easier.',desiredSkills:['java','design'],leaderId:user.id,status:'INCOMPLETE',minSize:2,maxSize:5,members:[member]};
  window.fixture={user,event,team,own:null,signedIn:false,calls:[],streams:[],requests:[],inbox:[],fail:null,delay:0,clipboard:''};
  Object.defineProperty(navigator,'clipboard',{value:{writeText:async text=>{window.fixture.clipboard=text;}}});
  window.EventSource=class extends EventTarget { constructor(url){super();this.url=url;this.closed=false;window.fixture.streams.push(this);setTimeout(()=>this.dispatchEvent(new Event('ready')),10);} close(){this.closed=true;} };
  window.fetch=async (url,init={})=>{
    const s=window.fixture,requestUrl=new URL(url,location.href),p=requestUrl.pathname.replace('/api/v1',''),method=init.method??'GET'; s.calls.push({p,method,query:requestUrl.search,headers:init.headers,body:init.body});
    if(s.readDelay && method==='GET' && p.endsWith('/requests'))await new Promise(resolve=>setTimeout(resolve,s.readDelay));
    if(s.delay && method!=='GET')await new Promise(resolve=>setTimeout(resolve,s.delay));
    let status=200,body={}; const respond=()=>new Response(status===204?null:JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Retry-After':'2'}});
    if(s.fail && method!=='GET'){status=s.fail.status;body={message:s.fail.message,fieldErrors:s.fail.fields??{}};s.fail=null;return respond();}
    if(p==='/health')body={status:'UP'};
    else if(p==='/auth/csrf')body={headerName:'X-CSRF-TOKEN',token:'fixture-csrf'};
    else if(p==='/auth/register'){status=201;s.user.profile.displayName=JSON.parse(init.body).displayName;}
    else if(p==='/auth/login'){s.signedIn=true;status=204;}
    else if(p==='/auth/logout'){s.signedIn=false;status=204;}
    else if(!s.signedIn){status=401;body={message:'Sign in to continue.'};}
    else if(p==='/me')body=s.user;
    else if(p==='/me/profile'){if(method==='PATCH')s.user.profile={...s.user.profile,...JSON.parse(init.body)};body=s.user.profile;}
    else if(p==='/hackathons')body={items:s.events??[event]};
    else if(p.endsWith('/me/team')){if(s.own)body=s.own;else status=204;}
    else if(p.endsWith('/me/requests'))body={items:s.requests,nextCursor:null};
    else if(p.endsWith('/invite-joins')){s.own=s.team;status=201;body={teamId:team.id};}
    else if(p.endsWith('/teams') && p.startsWith('/hackathons')){if(method==='POST'){s.own={...s.team,...JSON.parse(init.body)};s.team=s.own;status=201;body=s.own;}else body={items:[{...s.team,memberCount:s.team.members.length,openSeats:5-s.team.members.length,leaderDisplayName:'Alex Morgan'}].filter(t=>(t.name+' '+t.description).toLowerCase().includes((requestUrl.searchParams.get('q')??'').toLowerCase())),nextCursor:null};}
    else if(p.endsWith('/audit-events'))body={items:[]};
    else if(p.endsWith('/suggestions'))body={items:[{userId:'candidate',displayName:'Jordan Lee',score:65,skillScore:50,timeScore:100,reasons:['Covers 1 of 2 uncovered desired skills.','Shares collaboration time.']}],windowStart:'2026-11-10',evaluatedCandidates:1,truncated:false};
    else if(p.endsWith('/invite')){status=201;body={code:'X'.repeat(43),expiresAt:'2026-11-11T12:00:00Z'};}
    else if(p.endsWith('/members/me')){s.own=null;status=200;body={teamId:team.id,deleted:true,leaderId:null};}
    else if(p.includes('/members/')){s.team.members=s.team.members.filter(x=>!p.endsWith(x.userId));status=204;}
    else if(p.endsWith('/accept')){s.inbox=[];body={id:'request',state:'ACCEPTED'};}
    else if(p.endsWith('/decline')){s.inbox=[];body={state:'DECLINED'};}
    else if(p.includes('/requests/') && method==='DELETE'){s.requests=[];status=204;}
    else if(p.endsWith('/requests')){if(method==='POST'){const r={id:'30000000-0000-4000-8000-000000000001',teamId:team.id,applicantId:user.id,displayName:'Alex Morgan',state:'PENDING',createdAt:'2026-11-10T00:00:00Z'};s.requests=[r];status=201;body=r;}else body={items:s.inbox,nextCursor:null};}
    else if(p.startsWith('/teams/')){if(method==='PATCH'){s.team={...s.team,...JSON.parse(init.body)};if(s.own)s.own=s.team;}body=s.team;}
    else {status=404;body={message:'Fixture route not found'};}
    return respond();
  };
})();`;
