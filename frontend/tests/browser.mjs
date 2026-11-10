import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as sleep } from 'node:timers/promises';

export const root = fileURLToPath(new URL('../../', import.meta.url));
export async function waitFor(work, message = 'Condition timed out', timeout = 10000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { const result = await work(); if (result) return result; await sleep(40); }
  throw new Error(message);
}
export async function browser() {
  const binary = process.env.BROWSER_BIN ?? ['C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'].find(existsSync);
  if (!binary) throw new Error('No Chrome/Chromium available. Set BROWSER_BIN to run browser interaction tests.');
  const profile = resolve(root, '.tmp', 'm6-browser-' + crypto.randomUUID()); await mkdir(profile, { recursive: true });
  const child = spawn(binary, ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-software-rasterizer', '--no-first-run', '--disable-save-password-bubble', '--password-store=basic', '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank'], { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] });
  let endpoint = ''; child.stderr.on('data', chunk => { const match = String(chunk).match(/DevTools listening on (ws:\/\/[^\s]+)/); if (match) endpoint = match[1]; });
  await waitFor(() => endpoint || (child.exitCode !== null ? Promise.reject(new Error('Chrome exited: ' + child.exitCode)) : false), 'Chrome startup timed out');
  const socket = new WebSocket(endpoint); await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
  let id = 0; const pending = new Map();
  socket.addEventListener('message', message => { const value = JSON.parse(message.data); if (value.id) { const item = pending.get(value.id); if (!item) return; pending.delete(value.id); clearTimeout(item.timer); value.error ? item.reject(new Error(value.error.message)) : item.resolve(value.result); } });
  function send(method, params = {}, sessionId) { return new Promise((resolve, reject) => { const key = ++id; const timer = setTimeout(() => { pending.delete(key); reject(new Error('CDP timeout: ' + method)); }, 15000); pending.set(key, { resolve, reject, timer }); socket.send(JSON.stringify({ id: key, method, params, ...(sessionId ? { sessionId } : {}) })); }); }
  return {
    async tab(url, script, isolated = false) {
      const context = isolated ? await send('Target.createBrowserContext') : {};
      const { targetId } = await send('Target.createTarget', { url: 'about:blank', ...context }); const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
      const call = (method, params) => send(method, params, sessionId);
      await call('Page.enable'); await call('Runtime.enable');
      if (script) await call('Page.addScriptToEvaluateOnNewDocument', { source: script });
      async function evaluate(expression) { const result = await call('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture: true }); if (result.exceptionDetails) throw new Error(result.exceptionDetails.text + ': ' + (result.exceptionDetails.exception?.description ?? '')); return result.result.value; }
      await call('Page.navigate', { url });
      const tab = {
        call, evaluate,
        async ready(text) { return waitFor(() => evaluate(`document.body?.innerText.includes(${JSON.stringify(text)})`), 'UI not ready: ' + text); },
        async click(text) {
          await call('Page.bringToFront');
          const point = await waitFor(() => evaluate(`(async () => {
            const find=()=>[...document.querySelectorAll('button,a,summary')].find(e=>e.checkVisibility()&&!e.disabled&&(e.getAttribute('aria-label')===${JSON.stringify(text)}||e.textContent.trim()===${JSON.stringify(text)}||e.querySelector(':scope > strong')?.textContent.trim()===${JSON.stringify(text)}));
            let e=find();if(!e)return false;e.scrollIntoView({block:'center'});
            await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
            e=find();if(!e)return false;const r=e.getBoundingClientRect(),x=r.x+r.width/2,y=r.y+r.height/2,hit=document.elementFromPoint(x,y);
            return e.contains(hit)?{x,y}:false;
          })()`), 'Enabled stable control missing: ' + text);
          await call('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point });
          await call('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point });
          await call('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point });
          await evaluate(`new Promise(r=>requestAnimationFrame(r))`);
        },
        async fill(id, value) { await evaluate(`(() => { const e = document.getElementById(${JSON.stringify(id)}); if (!e) throw Error('Field missing'); const setter = Object.getOwnPropertyDescriptor(e instanceof HTMLSelectElement ? HTMLSelectElement.prototype : e instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value').set; setter.call(e, ${JSON.stringify(value)}); e.dispatchEvent(new Event('input', {bubbles:true})); e.dispatchEvent(new Event('change', {bubbles:true})); })()`); },
        async key(key, code = key, extra = {}) { const windowsVirtualKeyCode = { Enter: 13, Tab: 9, Escape: 27 }[key]; await call('Input.dispatchKeyEvent', { type: 'keyDown', key, code, windowsVirtualKeyCode, ...extra }); await call('Input.dispatchKeyEvent', { type: 'keyUp', key, code, windowsVirtualKeyCode, ...extra }); },
        async viewport(width, height) { await call('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 600 }); },
        async screenshot(name) { await evaluate(`window.scrollTo(0,0)`); const { data } = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }); const path = resolve(root, '.tmp', name + '.png'); await writeFile(path, Buffer.from(data, 'base64')); return path; },
        async close() { await send('Target.closeTarget', { targetId }); if (context.browserContextId) await send('Target.disposeBrowserContext', context); },
      };
      return tab;
    },
    async close() { try { await send('Browser.close'); } catch {} socket.close(); if (child.exitCode === null) child.kill(); },
  };
}
