import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';
import { browser, root, waitFor } from './browser.mjs';
import { fixture } from './fixture.mjs';

let chrome, vite;
const origin = 'http://127.0.0.1:5175';
before(async () => {
  try { await fetch(origin); throw new Error('Test port 5175 is already occupied; no existing server touched.'); } catch (error) { if (!String(error).includes('fetch failed')) throw error; }
  vite = spawn(process.execPath, [resolve(root, 'frontend/node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5175'], { cwd: resolve(root, 'frontend'), windowsHide: true, stdio: 'ignore' });
  await waitFor(async () => { try { return (await fetch(origin)).ok; } catch { return false; } }, 'Vite startup failed');
  chrome = await browser();
});
after(async () => { if (chrome) await chrome.close(); if (vite) vite.kill(); });
async function tab(signedIn = true, own = false, setup = '') {
  const page = await chrome.tab(origin, fixture + `window.fixture.signedIn=${signedIn};window.fixture.own=${own ? 'window.fixture.team' : 'null'};` + setup);
  await page.viewport(1440, 1000); await page.ready(signedIn ? 'Find your team' : 'Sign in to TeamForge'); return page;
}
async function route(page, name, heading) { await page.click(name); await waitFor(() => page.evaluate(`document.querySelector('nav a[aria-current="page"]')?.textContent.includes(${JSON.stringify(name)})`)); await page.ready(heading); }
test('registration validates fields and sends CSRF without persistent credentials', async () => {
  const page = await tab(false); try {
    await page.viewport(390, 844);
    await page.click('New here? Create an account'); await page.fill('displayName', 'New participant'); await page.fill('email', 'new@example.test'); await page.fill('password', 'Synthetic-test-password-42');
    await page.click('Create account'); await page.ready('Account created. Sign in to continue.');
    const data = await page.evaluate(`({calls:fixture.calls.filter(x=>x.p==='/auth/register'),stored:localStorage.length,password:document.getElementById('password').value})`);
    assert.equal(data.calls.length, 1); assert.equal(data.calls[0].headers['X-CSRF-TOKEN'], 'fixture-csrf'); assert.equal(data.stored, 0); assert.equal(data.password, '');
  } finally { await page.close(); }
});
test('login errors are associated and focused; successful login restores workspace', async () => {
  const page = await tab(false); try {
    await page.fill('email', 'fixture@example.test'); await page.fill('password', 'Synthetic-test-password-42');
    await page.evaluate(`fixture.fail={status:400,message:'Check your email.',fields:{email:'Use a valid email.'}}`);
    await page.click('Sign in'); await page.ready('Check your email.');
    assert.equal(await page.evaluate(`document.activeElement.getAttribute('role')`), 'alert');
    assert.equal(await page.evaluate(`document.getElementById(document.getElementById('email').getAttribute('aria-describedby')).textContent.includes('valid email')`), true);
    await page.click('Sign in'); await page.ready('Find your team'); assert.equal(await page.evaluate(`document.querySelectorAll('nav a').length`), 4);
  } finally { await page.close(); }
});
test('navigation supports keyboard, main focus and one visible page heading', async () => {
  const page = await tab(); try {
    await page.evaluate(`document.querySelector('a[href="#profile"]').focus()`); await page.key('Enter', 'Enter', { windowsVirtualKeyCode: 13 }); await page.ready('Your profile');
    await waitFor(() => page.evaluate(`document.activeElement.id==='main'`));
    assert.equal(await page.evaluate(`[...document.querySelectorAll('h1')].filter(x=>x.checkVisibility()).length`), 1);
    await page.evaluate(`document.querySelector('.skip-link').focus()`); await page.key('Enter', 'Enter', { windowsVirtualKeyCode: 13 });
    assert.equal(await page.evaluate(`document.activeElement.id`), 'main');
    assert.equal(await page.evaluate(`document.querySelector('nav [aria-current="page"]').getAttribute('href')`), '#profile');
  } finally { await page.close(); }
});
test('profile editing, availability and save use the typed CSRF client', async () => {
  const page = await tab(); try {
    await route(page, 'Your profile', 'Your profile'); await page.fill('profile-name', 'Alex Updated'); await page.fill('skills', 'java, design'); await page.click('Add availability window'); await page.click('Save profile'); await page.ready('Profile saved.');
    const body = await page.evaluate(`JSON.parse(fixture.calls.find(x=>x.p==='/me/profile'&&x.method==='PATCH').body)`);
    assert.equal(body.displayName, 'Alex Updated'); assert.deepEqual(body.skills, ['java', 'design']); assert.equal(body.collaborationWindows.length, 1);
  } finally { await page.close(); }
});
test('team creation prevents duplicate submission and preserves idempotency header', async () => {
  const page = await tab(); try {
    await route(page, 'My team', 'Start something together'); await page.fill('create-team-name', 'New team');
    await page.evaluate(`fixture.delay=120;const form=document.getElementById('create-team-name').form;form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));`);
    await page.ready('Team created.');
    const calls = await page.evaluate(`fixture.calls.filter(x=>x.p.endsWith('/teams')&&x.method==='POST')`);
    assert.equal(calls.length, 1); assert.ok(calls[0].headers['Idempotency-Key']); assert.equal(calls[0].headers['X-CSRF-TOKEN'], 'fixture-csrf');
  } finally { await page.close(); }
});
test('board search and filters reach the server before pagination', async () => {
  const page = await tab(); try {
    await page.ready('Open Circuit'); await page.fill('team-search', 'no matching name'); await page.click('Search'); await page.ready('No matching teams'); await page.fill('team-search', 'Open Circuit'); await page.click('More filters & sorting');
    await page.fill('board-skill', 'design'); await page.click('Apply filters');
    await waitFor(() => page.evaluate(`fixture.calls.some(x=>x.query?.includes('q=Open+Circuit')&&x.query?.includes('skill=design'))`));
    assert.equal(await page.evaluate(`document.getElementById('team-search').getAttribute('aria-describedby')`), 'search-hint');
    await page.fill('board-sort', 'name'); assert.equal(await page.evaluate(`document.getElementById('board-sort').value`), 'name');
  } finally { await page.close(); }
});
test('invitation rotation requires confirmation and copy stays out of storage', async () => {
  const page = await tab(true, true); try {
    await route(page, 'Invitations & requests', 'Generate / rotate invitation'); await page.click('Generate / rotate invitation'); await page.ready('Generate a new invitation?');
    assert.equal(await page.evaluate(`document.activeElement.textContent`), 'Cancel'); await page.key('Escape'); await waitFor(() => page.evaluate(`!document.querySelector('dialog')`));
    assert.equal(await page.evaluate(`document.activeElement.textContent`), 'Generate / rotate invitation');
    await page.click('Generate / rotate invitation'); await page.click('Generate invitation'); await page.ready('Copy this code now'); await page.click('Copy invitation'); await page.ready('Invitation copied.');
    assert.equal(await page.evaluate(`fixture.clipboard.length`), 43); assert.equal(await page.evaluate(`localStorage.length+sessionStorage.length`), 0);
  } finally { await page.close(); }
});
test('request creation and withdrawal update visible pending state', async () => {
  const page = await tab(); try {
    await page.ready('Open Circuit'); await page.click('Open Circuit');
  } catch {
    // Team buttons include their full roster summary as accessible content.
    await page.evaluate(`document.querySelector('.team-select').click()`);
  }
  try {
    await page.ready('Request to join Open Circuit'); await page.click('Request to join Open Circuit');
    try { await page.ready('Your request for this team is pending.'); } catch { throw new Error(await page.evaluate(`document.body.innerText`)); }
    await route(page, 'Invitations & requests', 'Your requests'); await page.click('Withdraw request'); await page.ready('Request withdrawn.'); assert.equal(await page.evaluate(`fixture.requests.length`), 0);
  } finally { await page.close(); }
});
test('leave dialog traps focus, Escape restores it and confirmation updates membership', async () => {
  const page = await tab(true, true); try {
    await route(page, 'My team', 'Leave team'); await page.click('Leave team'); await page.ready('Leave this team?');
    assert.equal(await page.evaluate(`document.activeElement.textContent`), 'Cancel'); await page.key('Tab', 'Tab', { modifiers: 8, windowsVirtualKeyCode: 9 });
    assert.equal(await page.evaluate(`document.activeElement.closest('dialog')!==null`), true);
    await page.key('Escape'); await waitFor(() => page.evaluate(`!document.querySelector('dialog')`)); assert.equal(await page.evaluate(`document.activeElement.textContent`), 'Leave team');
    await page.click('Leave team'); await page.click('Confirm leave'); await page.ready('Start something together'); assert.equal(await page.evaluate(`fixture.own`), null);
  } finally { await page.close(); }
});
test('SSE reconciliation survives navigation without duplicate subscriptions and logout cleans up', async () => {
  const page = await tab(); try {
    await page.ready('Live updates connected'); await route(page, 'Your profile', 'Your profile'); await route(page, 'Discover teams', 'Find your team');
    const before = await page.evaluate(`fixture.calls.filter(x=>x.p.endsWith('/me/team')).length`);
    await page.evaluate(`fixture.streams.find(x=>!x.closed).dispatchEvent(new Event('invalidate'))`);
    await waitFor(async () => (await page.evaluate(`fixture.calls.filter(x=>x.p.endsWith('/me/team')).length`)) > before);
    assert.equal(await page.evaluate(`fixture.streams.filter(x=>!x.closed).length`), 1);
    await page.click('Sign out'); await page.ready('You are signed out.'); assert.equal(await page.evaluate(`fixture.streams.filter(x=>!x.closed).length`), 0);
  } finally { await page.close(); }
});
test('responsive pages have labels, valid descriptions, touch targets and no horizontal overflow', async () => {
  const page = await tab(true, true); try {
    for (const [width, height] of [[1440, 1000], [768, 1024], [390, 844]]) {
      await page.viewport(width, height);
      for (const [name, heading] of [['Discover teams', 'Find your team'], ['My team', 'My team'], ['Invitations & requests', 'Your next step'], ['Your profile', 'Your profile']]) {
        await route(page, name, heading);
        const checks = await page.evaluate(`(() => {const visible=e=>e.checkVisibility();return {overflow:document.documentElement.scrollWidth>innerWidth+1,headings:[...document.querySelectorAll('h1')].filter(visible).length,unlabelled:[...document.querySelectorAll('input,select,textarea')].filter(visible).filter(e=>!e.labels.length&&!e.getAttribute('aria-label')).map(e=>e.id),broken:[...document.querySelectorAll('[aria-describedby]')].filter(visible).flatMap(e=>e.getAttribute('aria-describedby').split(' ').filter(id=>id&&!document.getElementById(id))),small:[...document.querySelectorAll('button,nav a')].filter(visible).filter(e=>e.getBoundingClientRect().height<43).map(e=>e.textContent)}})()`);
        assert.equal(checks.overflow, false, width + ' ' + name); assert.equal(checks.headings, 1); assert.deepEqual(checks.unlabelled, []); assert.deepEqual(checks.broken, []); assert.deepEqual(checks.small, []);
        if (name === 'Discover teams') await page.screenshot('m6-discover-' + width);
      }
      await page.screenshot('m6-profile-' + width);
    }
    const ax = await page.call('Accessibility.getFullAXTree'); assert.ok(ax.nodes.some(node => node.role?.value === 'navigation' && node.name?.value === 'Main navigation'));
  } finally { await page.close(); }
});
test('403, 409 and 429 errors provide recovery guidance without losing profile input', async () => {
  const page = await tab(); try {
    await route(page, 'Your profile', 'Save profile'); await page.fill('profile-name', 'Keep my draft');
    for (const [status, hint] of [[403, 'current permissions'], [409, 'current state'], [429, 'Wait 2 seconds']]) {
      await page.evaluate(`fixture.fail={status:${status},message:'Try this action again.'}`); await page.click('Save profile'); await page.ready(hint);
      assert.equal(await page.evaluate(`document.getElementById('profile-name').value`), 'Keep my draft');
    }
  } finally { await page.close(); }
});
test('rendered text contrast, keyboard focus and reduced-motion styles meet the checked thresholds', async () => {
  const page = await tab(true, true); try {
    for (const [name, content] of [['Discover teams', 'Find your team'], ['My team', 'My team'], ['Invitations & requests', 'Your next step'], ['Your profile', 'Save profile']]) {
      await route(page, name, content);
      const failures = await page.evaluate(`(() => {
      const rgb=s=>{const n=s.match(/[\\d.]+/g)?.map(Number);return n?.length>=3?n:null;};
      const luminance=c=>c.slice(0,3).map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0);
      const result=[];
      for(const e of document.querySelectorAll('h1,h2,h3,p,span,strong,label,legend,button,nav a,input,select,summary,li')){
        if(!e.checkVisibility()||e.disabled)continue;
        if(!['INPUT','SELECT'].includes(e.tagName)&&![...e.childNodes].some(n=>n.nodeType===3&&n.textContent.trim()))continue;
        const s=getComputedStyle(e),fg=rgb(s.color);let parent=e,bg;
        while(parent){const c=rgb(getComputedStyle(parent).backgroundColor);if(c&&(c[3]===undefined||c[3]===1)){bg=c;break;}parent=parent.parentElement;}
        bg??=[255,255,255];if(!fg)continue;const a=luminance(fg),b=luminance(bg),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05),large=parseFloat(s.fontSize)>=24||(parseFloat(s.fontSize)>=18.66&&Number(s.fontWeight)>=700);
        if(ratio<(large?3:4.5))result.push({text:e.textContent.trim().slice(0,30),ratio});
      }return result;
    })()`);
      assert.deepEqual(failures, [], name);
    }
    await page.evaluate(`document.querySelector('nav a').focus()`); await page.key('Tab'); assert.ok(await page.evaluate(`parseFloat(getComputedStyle(document.activeElement).outlineWidth)>=2`));
    await page.call('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
    assert.equal(await page.evaluate(`getComputedStyle(document.querySelector('button')).transitionDuration`), '0s');
  } finally { await page.close(); }
});
test('selected hackathon survives reload in the URL without persistent credentials', async () => {
  const id = '10000000-0000-4000-8000-000000000002';
  const page = await tab(true, false, `fixture.events=[fixture.event,{...fixture.event,id:'${id}',name:'Second hackathon'}];`);
  try {
    await waitFor(() => page.evaluate(`!document.getElementById('hackathon').disabled`)); await page.fill('hackathon', id);
    await waitFor(() => page.evaluate(`new URLSearchParams(location.search).get('hackathon')==='${id}'`));
    await page.call('Page.reload'); await page.ready('Find your team'); await waitFor(() => page.evaluate(`document.getElementById('hackathon')?.value==='${id}'`));
    await page.evaluate(`history.pushState(null,'','?hackathon=10000000-0000-4000-8000-000000000001#discover');window.dispatchEvent(new PopStateEvent('popstate'));`);
    await waitFor(() => page.evaluate(`document.getElementById('hackathon')?.value==='10000000-0000-4000-8000-000000000001'`));
    await page.evaluate(`history.back()`); await waitFor(() => page.evaluate(`document.getElementById('hackathon')?.value==='${id}'`));
    assert.equal(await page.evaluate(`localStorage.length+sessionStorage.length`), 0);
  } finally { await page.close(); }
});
test('background request reconciliation does not disable already loaded actions', async () => {
  const page = await tab(true, true); try {
    await route(page, 'Invitations & requests', 'Generate / rotate invitation');
    await waitFor(() => page.evaluate(`[...document.querySelectorAll('button')].some(e=>e.textContent==='Generate / rotate invitation'&&!e.disabled)`));
    const before = await page.evaluate(`fixture.calls.filter(x=>x.p.endsWith('/me/requests')).length`);
    await page.evaluate(`fixture.readDelay=800;fixture.streams.find(x=>!x.closed).dispatchEvent(new Event('invalidate'));`);
    await waitFor(async () => (await page.evaluate(`fixture.calls.filter(x=>x.p.endsWith('/me/requests')).length`)) > before);
    assert.equal(await page.evaluate(`[...document.querySelectorAll('button')].find(e=>e.textContent==='Generate / rotate invitation').disabled`), false);
  } finally { await page.close(); }
});
