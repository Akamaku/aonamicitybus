/**
 * 白急線ポータル 遅延情報API（Google Apps Script）
 *
 * 公開（GET）  ?action=list           … 本日の遅れ設定を返す（誰でも読める）
 * 管理（POST） {action:'auth', id}     … IDの確認
 *              {action:'save', id, data:{date, rules}} … 遅れ設定を保存
 *
 * 管理IDは「プロジェクトの設定 → スクリプト プロパティ」に ADMIN_ID として登録してください。
 * IDはコードにもHTMLにも書かないので、ページのソースを見られても漏れません。
 */
const KEY = 'DELAYS';
const CHUNK = 8000;          // スクリプトプロパティは1件9KBまでなので分割して保存
const MAX_RULES = 500;

function doGet(e) {
  const action = (e && e.parameter && e.parameter.action) || 'list';
  if (action === 'list') return json(load_());
  return json({ error: 'unknown action' });
}

function doPost(e) {
  let body;
  try { body = JSON.parse(e.postData.contents); } catch (err) { return json({ ok: false, error: '不正なリクエストです' }); }
  const adminId = PropertiesService.getScriptProperties().getProperty('ADMIN_ID');
  if (!adminId) return json({ ok: false, error: 'ADMIN_ID が設定されていません' });
  if (!body || typeof body.id !== 'string' || body.id !== adminId) {
    Utilities.sleep(800);    // 総当たり対策で少し待たせる
    return json({ ok: false, error: 'IDが違います' });
  }
  if (body.action === 'auth') return json({ ok: true });
  if (body.action === 'save') {
    const d = body.data || {};
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(d.date || '')) || !Array.isArray(d.rules)) return json({ ok: false, error: 'データの形式が違います' });
    const data = { date: d.date, rules: d.rules.slice(0, MAX_RULES).map(clean_).filter(Boolean), updated: new Date().toISOString() };
    const lock = LockService.getScriptLock();
    lock.waitLock(10000);
    try { save_(data); } finally { lock.releaseLock(); }
    return json({ ok: true, data: data });
  }
  return json({ ok: false, error: 'unknown action' });
}

function clean_(r) {
  if (!r || typeof r.route !== 'string' || !r.route) return null;
  const min = Math.round(Number(r.min));
  if (!(min >= 0 && min <= 999)) return null;
  const o = { route: r.route.slice(0, 40), min: min };
  if (r.dir === 'down' || r.dir === 'up') o.dir = r.dir;
  if (typeof r.type === 'string' && r.type) o.type = r.type.slice(0, 20);
  if (typeof r.train === 'string' && r.train) o.train = r.train.slice(0, 20);
  if (typeof r.note === 'string' && r.note) o.note = r.note.slice(0, 60);
  return o;
}

function save_(data) {
  const p = PropertiesService.getScriptProperties();
  const s = JSON.stringify(data);
  const n = Math.ceil(s.length / CHUNK) || 1;
  const old = Number(p.getProperty(KEY + '_N') || 0);
  const props = {};
  for (let i = 0; i < n; i++) props[KEY + '_' + i] = s.slice(i * CHUNK, (i + 1) * CHUNK);
  props[KEY + '_N'] = String(n);
  p.setProperties(props);
  for (let i = n; i < old; i++) p.deleteProperty(KEY + '_' + i);
}

function load_() {
  const p = PropertiesService.getScriptProperties();
  const n = Number(p.getProperty(KEY + '_N') || 0);
  if (!n) return { date: '', rules: [], updated: '' };
  let s = '';
  for (let i = 0; i < n; i++) s += p.getProperty(KEY + '_' + i) || '';
  try { return JSON.parse(s); } catch (e) { return { date: '', rules: [], updated: '' }; }
}

function json(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
