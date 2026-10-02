/**
 * The email a supporter gets with their code, as plain text and as HTML. The words are McCal's, from the first version
 * of this email; the HTML only sets them out. It is table-based with inline styles because mail apps ignore most of
 * CSS, it asks for a dark look only where the app supports one, and it carries no script and one image, the icon, from
 * foliolauncher.com (an app that blocks images shows the wordmark beside it instead).
 *
 * The code is long, 24 groups of up to five characters, so it is set out four groups to a line. Folio's Redeem a Code
 * ignores hyphens, spaces and line breaks and upper-cases what it reads, so pasting the block works as it does on one line.
 */

const TEAL = '#2e5e66'
const TEAL_DEEP = '#173d43'
const MINT = '#7fd8c4'

/** Ko-fi gives the name the supporter typed, so it is escaped before it goes into HTML. */
export function escapeHtml(text) {
  return String(text ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}

/** The code four groups to a line: `AAAAA-BBBBB-CCCCC-DDDDD-` and so on, each line ending on a hyphen but the last. */
export function codeLines(code) {
  const groups = String(code).split('-')
  const lines = []
  for (let i = 0; i < groups.length; i += 4) lines.push(groups.slice(i, i + 4).join('-'))
  return lines.map((line, index) => (index < lines.length - 1 ? `${line}-` : line))
}

/**
 * The link in the email: a page on the site that shows the code and, on Android, opens Folio's own redeem link
 * (folio://redeem, handled by RedeemActivity since 0.6.5). The email cannot link to folio:// itself, because Gmail does
 * nothing with a custom scheme. The code sits after the #, so a browser never sends it to a server.
 */
export function redeemLink(code) {
  return `https://foliolauncher.com/redeem/#${encodeURIComponent(String(code))}`
}

const SUBJECT = 'Your Folio supporter code'
const betaLine = (pool) => pool === 'beta' || pool === 'all'
  ? 'It turns on Beta Features, which you can switch off any time: early features come with more bugs.' : ''

export function supporterEmail({ name = '', code, pool = '' }) {
  const who = String(name).trim()
  const beta = betaLine(pool)
  const text = [
    `Thank you${who ? `, ${who}` : ''}.`,
    '',
    'Here is your Folio supporter code:',
    '',
    `    ${code}`,
    '',
    'On your Android phone, this link opens a page that adds it to Folio:',
    `    ${redeemLink(code)}`,
    '',
    'In Folio: Settings › Supporter › Redeem a Code, and paste it in. It is checked on your phone, so it works',
    'offline and tells nobody that you supported.',
    ...(beta ? ['', beta] : []),
    '',
    'Folio stays free and open source. Thank you for keeping it going.',
    'McCal',
  ].join('\n')

  const link = escapeHtml(redeemLink(code))
  const lines = codeLines(code).map((line) => escapeHtml(line)).join('<br>')
  const font = `-apple-system,BlinkMacSystemFont,'SF Pro Text','Segoe UI',Roboto,Helvetica,Arial,sans-serif`
  const mono = `ui-monospace,'SF Mono',Menlo,Consolas,'Liberation Mono',monospace`
  const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>${SUBJECT}</title>
<style>
  @media (prefers-color-scheme: dark) {
    .page { background:#000000 !important }
    .card { background:#1c1c1e !important }
    .ink { color:#f5f5f7 !important }
    .dim { color:#aeaeb2 !important }
    .codebox { background:#0f2a2f !important; border-color:#2e5e66 !important }
    .codetext { color:#7fd8c4 !important }
    .path { background:#2c2c2e !important; color:#f5f5f7 !important }
    .btn { background:#7fd8c4 !important }
    .btntext { color:#06222a !important }
    .rule { border-color:#2c2c2e !important }
    a { color:#7fd8c4 !important }
  }
  @media (max-width:480px) { .pad { padding-left:22px !important; padding-right:22px !important } .codetext { font-size:15px !important } }
</style>
</head>
<body class="page" style="margin:0;padding:0;background:#f2f2f7;-webkit-text-size-adjust:100%">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent">Your Folio supporter code is inside.</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="page" bgcolor="#f2f2f7" style="background:#f2f2f7">
<tr><td align="center" style="padding:28px 12px">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px">
    <tr><td class="card" bgcolor="#ffffff" style="background:#ffffff;border-radius:20px;overflow:hidden">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        <tr><td bgcolor="${TEAL_DEEP}" style="background:${TEAL_DEEP};background-image:linear-gradient(135deg,${TEAL} 0%,${TEAL_DEEP} 100%);padding:26px 32px" class="pad">
          <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
            <td valign="middle" style="padding-right:14px"><img src="https://foliolauncher.com/icon.png" width="44" height="44" alt="" style="display:block;border-radius:10px;border:0"></td>
            <td valign="middle" style="font:700 22px/1 ${font};color:#ffffff;letter-spacing:.2px">Folio</td>
          </tr></table>
        </td></tr>
        <tr><td class="pad" style="padding:32px 32px 8px">
          <p class="ink" style="margin:0 0 14px;font:700 26px/1.2 ${font};color:#1c1c1e">Thank you${who ? `, ${escapeHtml(who)}` : ''}.</p>
          <p class="ink" style="margin:0 0 16px;font:400 16px/1.5 ${font};color:#1c1c1e">Here is your Folio supporter code:</p>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr>
            <td class="codebox" bgcolor="#eaf6f3" style="background:#eaf6f3;border:1px solid #bfe3da;border-radius:14px;padding:18px 20px">
              <span class="codetext" style="font:600 16px/1.7 ${mono};color:${TEAL_DEEP};letter-spacing:.6px;word-break:break-all">${lines}</span>
            </td>
          </tr></table>
        </td></tr>
        <tr><td class="pad" style="padding:20px 32px 0">
          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%"><tr>
            <td class="btn" align="center" bgcolor="${TEAL}" style="background:${TEAL};border-radius:12px">
              <a href="${link}" class="btntext" style="display:block;padding:15px 22px;font:600 16px/1.2 ${font};color:#ffffff;text-decoration:none">Add to Folio</a>
            </td>
          </tr></table>
          <p class="dim" style="margin:10px 0 0;font:400 13px/1.5 ${font};color:#6c6c70;text-align:center">Opens a page that adds the code in Folio on your Android phone.</p>
        </td></tr>
        <tr><td class="pad" style="padding:22px 32px 4px">
          <p class="ink" style="margin:0 0 14px;font:400 16px/1.55 ${font};color:#1c1c1e">In Folio: <span class="path" style="background:#f2f2f7;color:#1c1c1e;border-radius:6px;padding:2px 7px;font-weight:600;white-space:nowrap">Settings › Supporter › Redeem a Code</span>, and paste it in. It is checked on your phone, so it works offline and tells nobody that you supported.</p>
          ${beta ? `<p class="dim" style="margin:0 0 14px;font:400 15px/1.5 ${font};color:#6c6c70">${escapeHtml(beta)}</p>` : ''}
        </td></tr>
        <tr><td class="pad" style="padding:8px 32px 30px">
          <p class="ink" style="margin:0 0 4px;font:400 16px/1.55 ${font};color:#1c1c1e">Folio stays free and open source. Thank you for keeping it going.</p>
          <p class="ink" style="margin:14px 0 0;font:600 16px/1.4 ${font};color:#1c1c1e">McCal</p>
        </td></tr>
      </table>
    </td></tr>
    <tr><td align="center" class="pad" style="padding:18px 22px 0">
      <p class="dim" style="margin:0;font:400 12.5px/1.6 ${font};color:#636366">You are getting this because you supported Folio. Reply to this email to reach me.<br>
      <a href="https://foliolauncher.com" style="color:${TEAL};text-decoration:none">foliolauncher.com</a> &nbsp;·&nbsp; <a href="https://github.com/McCal-Codes/folio" style="color:${TEAL};text-decoration:none">github.com/McCal-Codes/folio</a></p>
    </td></tr>
  </table>
</td></tr>
</table>
</body>
</html>
`
  return { subject: SUBJECT, text, html }
}
