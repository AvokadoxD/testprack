// Экспорт презентации в PDF и PNG-превью слайдов.
// Запуск: node tools/export.js [--png]
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const root = path.resolve(__dirname, '..');
  const file = 'file://' + path.join(root, 'prezentaciya', 'index.html') + '?print';
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  await page.goto(file, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
  if (process.argv.includes('--png')) {
    const out = process.env.PNG_DIR || path.join(root, 'prezentaciya', 'preview');
    require('fs').mkdirSync(out, { recursive: true });
    const slides = await page.$$('.slide');
    for (let i = 0; i < slides.length; i++) {
      await slides[i].screenshot({ path: path.join(out, `slide-${String(i + 1).padStart(2, '0')}.png`) });
    }
    console.log('png:', slides.length);
  }
  await page.pdf({ path: path.join(root, 'prezentaciya', 'Яблочный_дозор_ЗОЖ-дозор.pdf'), width: '1920px', height: '1080px', printBackground: true, preferCSSPageSize: true });
  console.log('pdf ok');
  await browser.close();
})();
