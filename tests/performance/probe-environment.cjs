const { chromium } = require('../../frontend/node_modules/playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: false });
  try {
    const page = await browser.newPage({ viewport: { width: 1366, height: 768 } });
    await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'domcontentloaded' });
    const details = await page.evaluate(() => {
      const gl = document.createElement('canvas').getContext('webgl2');
      const ext = gl?.getExtension('WEBGL_debug_renderer_info');
      return { userAgent: navigator.userAgent, viewport: { width: innerWidth, height: innerHeight, devicePixelRatio }, webgl2: !!gl,
        renderer: ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : null,
        vendor: ext ? gl.getParameter(ext.UNMASKED_VENDOR_WEBGL) : null };
    });
    details.browserVersion = browser.version();
    details.capturedAt = new Date().toISOString();
    details.mode = 'Playwright headed Chrome; probe only, not application performance measurement';
    fs.writeFileSync(path.join(__dirname, '../evidence/T065/environment-browser.json'), JSON.stringify(details, null, 2));
    console.log(JSON.stringify(details));
  } finally { await browser.close(); }
})().catch(err => { console.error(err); process.exitCode = 1; });
