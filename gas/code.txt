/**
 * 白急線ポータル 遅延情報API（Google Apps Script）
 *
 * 公開（GET）  ?action=list           … 本日の遅れ設定を返す（誰でも読める）
 * 管理（POST） {action:'auth', id}     … IDの確認（遅れ・運転見合わせの設定に使う）
 *              {action:'save', id, data:{date, rules, auto, edits}} … 遅れ設定・運輸司令の変更を保存
 *
 * 管理IDは「プロジェクトの設定 → スクリプト プロパティ」に ADMIN_ID として登録してください。
 * IDはコードにもHTMLにも書かないので、ページのソースを見られても漏れません。
 */
const KEY = 'DELAYS';
const CHUNK = 8000;          // スクリプトプロパティは1件9KBまでなので分割して保存
const MAX_RULES = 500;
const MAX_EDITS = 500;

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
    const data = { date: d.date, rules: d.rules.slice(0, MAX_RULES).map(clean_).filter(Boolean), auto: !!d.auto,   // auto = 自動遅延のON/OFF
                   edits: Array.isArray(d.edits) ? d.edits.slice(0, MAX_EDITS).map(cleanEdit_).filter(Boolean) : [],   // 運輸司令の変更
                   updated: new Date().toISOString() };
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
  // 区間（○○駅〜○◇駅）と事象（「人身事故」など。表示は「○○駅〜○◇駅 人身事故が発生」）
  if (typeof r.from === 'string' && r.from && typeof r.to === 'string' && r.to) { o.from = r.from.slice(0, 20); o.to = r.to.slice(0, 20); }
  if (typeof r.ev === 'string' && r.ev) o.ev = r.ev.slice(0, 30);
  // 一部区間で運転再開したときの、事象の元の区間（空文字＝全線）
  if (typeof r.evFrom === 'string' && typeof r.evTo === 'string') { o.evFrom = r.evFrom.slice(0, 20); o.evTo = r.evTo.slice(0, 20); }
  // 一部区間で再開した区間の、分ける前の区間（空文字＝全線）
  if (typeof r.pFrom === 'string' && typeof r.pTo === 'string') { o.pFrom = r.pFrom.slice(0, 20); o.pTo = r.pTo.slice(0, 20); }
  // 運転支障の当該列車（列番）
  if (typeof r.cause === 'string' && r.cause) o.cause = r.cause.slice(0, 20);
  // 運転見合わせ（開始・再開の時刻、ミリ秒）
  const sa = Number(r.stopAt), ra = Number(r.resumeAt);
  if (sa > 0) { o.stopAt = Math.round(sa); if (ra > sa) o.resumeAt = Math.round(ra); }
  return o;
}

// 運輸司令の変更（列車ごとの入線番線・行先・種別・抑止・停車駅・時刻）
function cleanEdit_(e) {
  if (!e || typeof e.route !== 'string' || !e.route || typeof e.train !== 'string' || !e.train) return null;
  const o = { route: e.route.slice(0, 40), train: e.train.slice(0, 20) };
  if (typeof e.type === 'string' && e.type) o.type = e.type.slice(0, 20);
  if (typeof e.dest === 'string' && e.dest) o.dest = e.dest.slice(0, 20);
  if (e.tracks && typeof e.tracks === 'object') {
    const t = {}; let n = 0;
    for (const k in e.tracks) { if (n >= 40) break; const v = e.tracks[k]; if (typeof v === 'string' && v && k) { t[String(k).slice(0, 20)] = v.slice(0, 10); n++; } }
    if (n) o.tracks = t;
  }
  // 抑止 [[抑止した時刻, 解除した時刻(0=抑止中)], ...]（ミリ秒）
  if (Array.isArray(e.holds)) {
    const h = e.holds.slice(0, 20).map(x => [Math.round(Number(x && x[0]) || 0), Math.round(Number(x && x[1]) || 0)]).filter(x => x[0] > 0);
    if (h.length) o.holds = h;
  }
  // 停車駅の変更 {駅名: 1=停車 / 2=通過}
  if (e.stops && typeof e.stops === 'object') {
    const t = {}; let n = 0;
    for (const k in e.stops) { if (n >= 60) break; const v = Number(e.stops[k]); if ((v === 1 || v === 2) && k) { t[String(k).slice(0, 20)] = v; n++; } }
    if (n) o.stops = t;
  }
  // 時刻の変更 {駅名: [着(秒), 発(秒)]}（null は変更なし）
  if (e.times && typeof e.times === 'object') {
    const t = {}; let n = 0;
    const num = v => (v === null || v === undefined || v === '') ? null : (isFinite(Number(v)) ? Math.round(Number(v)) : null);
    for (const k in e.times) { if (n >= 60) break; const v = e.times[k]; if (!Array.isArray(v) || !k) continue;
      const a = num(v[0]), d = num(v[1]); if (a === null && d === null) continue; t[String(k).slice(0, 20)] = [a, d]; n++; }
    if (n) o.times = t;
  }
  // 運休（列車の運行を取りやめる）と、途中駅からの運転
  if (e.cancel === true) o.cancel = true;
  if (typeof e.from === 'string' && e.from) o.from = e.from.slice(0, 20);
  return (o.type || o.dest || o.tracks || o.holds || o.stops || o.times || o.cancel || o.from) ? o : null;
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
