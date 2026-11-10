// Opt-in: requires the normal backend/Vite servers and a dedicated, owned QA hackathon.
import assert from 'node:assert/strict';
import { randomBytes, randomUUID } from 'node:crypto';
import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { browser, root, waitFor } from './browser.mjs';

const origin = process.env.TEAMFORGE_E2E_ORIGIN ?? 'http://127.0.0.1:5174';
const event = process.env.TEAMFORGE_E2E_EVENT;
if (!event) throw new Error('Set TEAMFORGE_E2E_EVENT to a dedicated QA event; existing events are not mutated by this script.');
const fixtures = { event, users: [], teams: [] }; const tabs = [];
const save = () => writeFile(resolve(root, '.tmp/m6-e2e-fixtures.json'), JSON.stringify(fixtures));
const chrome = await browser();
async function api(page, path) { return page.evaluate(`fetch('/api/v1'+${JSON.stringify(path)}).then(async r=>({status:r.status,body:r.status===204?null:await r.json()}))`); }
async function register(name, width = 1440) {
  const page = await chrome.tab(origin, undefined, true); tabs.push(page); await page.viewport(width, width < 600 ? 844 : 1000); await page.ready('Sign in to TeamForge');
  const email = 'm6-' + randomUUID() + '@example.test', password = randomBytes(24).toString('base64url');
  await page.click('New here? Create an account'); await page.fill('displayName', name); await page.fill('email', email); await page.fill('password', password); await page.click('Create account'); await page.ready('Account created.');
  await page.fill('email', email); await page.fill('password', password); await page.click('Sign in'); await page.ready('Find your team');
  const me = await api(page, '/me'); assert.equal(me.status, 200); fixtures.users.push(me.body.id); await save();
  await waitFor(() => page.evaluate(`document.getElementById('hackathon')?.querySelector('option[value="${event}"]')!==null`)); await page.fill('hackathon', event);
  await waitFor(() => page.evaluate(`document.getElementById('hackathon').value===${JSON.stringify(event)}&&!document.getElementById('hackathon').disabled`));
  return page;
}
async function nav(page, name, content) { await page.click(name); await page.ready(content); await page.evaluate(`new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))`); }
async function select(page, name) { await nav(page, 'Discover teams', 'Search teams'); await page.fill('team-search', name); await page.click('Search'); await page.click(name); await page.ready('Request to join ' + name); }
try {
  const leader = await register('QA Avery');
  await nav(leader, 'Your profile', 'Save profile'); await leader.fill('skills', 'java, design'); await leader.click('Add availability window'); await leader.click('Save profile'); await leader.ready('Profile saved.');
  await nav(leader, 'My team', 'Create team'); const name = 'M6 browser team ' + randomUUID().slice(0, 8);
  await leader.fill('create-team-name', name); await leader.fill('create-team-description', 'A collaboration workspace verified through real browser journeys.'); await leader.fill('create-team-skills', 'java, design'); await leader.click('Create team'); await leader.ready('Team created.');
  const own = await api(leader, '/hackathons/' + event + '/me/team'); assert.equal(own.status, 200); fixtures.teams.push(own.body.id); await save();
  await leader.ready('Suggested teammates'); await leader.screenshot('m6-real-my-team-desktop');
  await nav(leader, 'Invitations & requests', 'Generate / rotate invitation'); await leader.click('Generate / rotate invitation'); await leader.click('Generate invitation'); await leader.ready('Copy this code now');
  const code = await leader.evaluate(`document.getElementById('issued-code').value`); assert.equal(code.length, 43);
  const member = await register('QA Blake'); await select(member, name); await member.click('Request to join ' + name); await member.ready('Your request for this team is pending.');
  await nav(member, 'Invitations & requests', 'Withdraw request'); await member.click('Withdraw request'); await member.ready('Request withdrawn.');
  await select(member, name); await member.click('Request to join ' + name); await member.ready('Your request for this team is pending.');
  await leader.click('Refresh requests'); await leader.ready('Accept QA Blake'); await leader.click('Accept QA Blake'); await leader.ready('Acceptance confirmed.');
  await nav(leader, 'My team', 'Remove QA Blake'); await leader.click('Remove QA Blake'); await leader.click('Confirm removal'); await leader.ready('Member removed.');
  await nav(member, 'Invitations & requests', 'Invitation code'); await member.fill('invite-code', code); await member.click('Redeem invitation'); await member.ready('Admission confirmed.');
  const candidate = await register('QA Casey', 390); await select(candidate, name); await candidate.click('Request to join ' + name); await candidate.ready('Your request for this team is pending.');
  await nav(leader, 'Invitations & requests', 'Leader request inbox'); await leader.click('Refresh requests'); await leader.ready('Decline QA Casey'); await leader.click('Decline QA Casey'); await leader.ready('Request declined.');
  await nav(candidate, 'Invitations & requests', 'Invitation code'); await candidate.fill('invite-code', code); await candidate.click('Redeem invitation'); await candidate.ready('Admission confirmed.');
  await nav(leader, 'My team', 'Leave team'); await leader.click('Leave team'); await leader.ready('Leadership will pass to QA Blake'); await leader.click('Confirm leave'); await leader.ready('You left the team.');
  // Verify the successor receives leader controls through genuine SSE/REST reconciliation.
  await nav(member, 'My team', 'Remove QA Casey'); const state = await api(member, '/teams/' + fixtures.teams[0]); assert.equal(state.body.leaderId, fixtures.users[1]);
  await member.viewport(390, 844); await member.screenshot('m6-real-my-team-mobile');
  assert.equal(await member.evaluate(`document.documentElement.scrollWidth<=innerWidth+1`), true);
  await member.call('Page.reload'); await member.ready('My team'); assert.equal((await api(member, '/me')).status, 200);
  await waitFor(() => member.evaluate(`document.getElementById('hackathon')?.value===${JSON.stringify(event)}`)); await member.ready('Remove QA Casey'); await member.click('Remove QA Casey'); await member.click('Confirm removal'); await member.ready('Member removed.');
  await member.click('Leave team'); await member.click('Confirm leave'); await member.ready('Start something together');
  assert.equal((await api(member, '/teams/' + fixtures.teams[0])).status, 404);
  for (const page of tabs) { assert.equal(await page.evaluate(`localStorage.length+sessionStorage.length`), 0); await page.click('Sign out'); await page.ready('You are signed out.'); assert.equal((await api(page, '/me')).status, 401); }
  console.log('REAL_BROWSER_PASS registration/login/profile/create/invite/request/withdraw/accept/decline/remove/redeem/succession/delete/session-restoration/logout');
  console.log('REAL_BROWSER_PASS desktop1440/mobile390, genuine PostgreSQL and SSE; screenshots contain no invitation codes');
} catch (error) {
  const last = tabs.at(-1);
  if (last) {
    console.log('QA_DIAGNOSTIC', JSON.stringify(await last.evaluate(`({event:document.getElementById('hackathon')?.value,heading:document.querySelector('#dashboard-heading')?.textContent,selected:document.querySelector('#selected-team-heading')?.textContent,buttons:[...document.querySelectorAll('button')].filter(e=>e.checkVisibility()).map(e=>({text:e.textContent.trim(),disabled:e.disabled})),errors:[...document.querySelectorAll('[role="alert"]')].map(e=>e.textContent)})`)));
    await last.screenshot('m6-real-failure');
  }
  throw error;
} finally { await save(); for (const page of tabs) await page.close().catch(() => {}); await chrome.close(); }
