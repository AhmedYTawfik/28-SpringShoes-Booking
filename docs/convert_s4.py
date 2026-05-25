#!/usr/bin/env python3
import re, html

def md_to_html(md):
    lines = md.split('\n')
    out, in_code, in_table, in_ul = [], False, False, False
    for line in lines:
        if line.strip().startswith('```'):
            if in_code: out.append('</code></pre>'); in_code = False
            else: out.append(f'<pre><code>'); in_code = True
            continue
        if in_code: out.append(html.escape(line)); continue
        if not line.strip():
            if in_ul: out.append('</ul>'); in_ul = False
            if in_table: out.append('</table>'); in_table = False
            out.append(''); continue
        m = re.match(r'^(#{1,6})\s+(.*)', line)
        if m:
            if in_ul: out.append('</ul>'); in_ul = False
            lv = len(m.group(1))
            out.append(f'<h{lv}>{m.group(2)}</h{lv}>'); continue
        if line.strip() == '---': out.append('<hr>'); continue
        if '|' in line and line.strip().startswith('|'):
            cells = [c.strip() for c in line.strip().strip('|').split('|')]
            if all(set(c) <= set('- :') for c in cells): continue
            if not in_table:
                out.append('<table><tr>' + ''.join(f'<th>{inl(c)}</th>' for c in cells) + '</tr>')
                in_table = True
            else:
                out.append('<tr>' + ''.join(f'<td>{inl(c)}</td>' for c in cells) + '</tr>')
            continue
        m2 = re.match(r'^(\s*)([-*]|\d+\.)\s+(.*)', line)
        if m2:
            if not in_ul: out.append('<ul>'); in_ul = True
            out.append(f'<li>{inl(m2.group(3))}</li>'); continue
        if in_table: out.append('</table>'); in_table = False
        out.append(f'<p>{inl(line)}</p>')
    return '\n'.join(out)

def inl(t):
    t = html.escape(t)
    t = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', t)
    t = re.sub(r'`(.+?)`', r'<code class="i">\1</code>', t)
    t = re.sub(r'\*(.+?)\*', r'<em>\1</em>', t)
    return t

with open('docs/S4_READ_DB_Guide.md') as f: md = f.read()
body = md_to_html(md)
page = f"""<!DOCTYPE html><html><head><meta charset="utf-8">
<title>S4-READ-DB Deep Dive — Ahmed Yasser Tawfik</title>
<style>
@media print {{ h1,h2 {{ page-break-before: auto; }} }}
body {{ font-family: 'Segoe UI',Arial,sans-serif; max-width: 850px; margin: 0 auto; padding: 20px; font-size: 13px; line-height: 1.6; color: #1a1a1a; }}
h1 {{ color: #1a56db; border-bottom: 3px solid #1a56db; padding-bottom: 8px; font-size: 22px; }}
h2 {{ color: #1e40af; border-bottom: 2px solid #dbeafe; padding-bottom: 5px; font-size: 17px; margin-top: 25px; }}
h3 {{ color: #374151; font-size: 14px; }}
h4 {{ color: #4b5563; font-size: 13px; }}
pre {{ background: #1e293b; color: #e2e8f0; padding: 10px; border-radius: 6px; overflow-x: auto; font-size: 11.5px; line-height:1.4; }}
code.i {{ background: #e0e7ff; color: #3730a3; padding: 1px 4px; border-radius: 3px; font-size: 12px; }}
table {{ border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 12px; }}
th {{ background: #1e40af; color: white; padding: 6px; text-align: left; }}
td {{ border: 1px solid #d1d5db; padding: 5px 7px; }}
tr:nth-child(even) {{ background: #f3f4f6; }}
hr {{ border: none; border-top: 2px solid #e5e7eb; margin: 15px 0; }}
ul {{ padding-left: 18px; }} li {{ margin-bottom: 3px; }}
strong {{ color: #1e40af; }}
</style></head><body>{body}</body></html>"""
with open('docs/S4_READ_DB_Guide.html', 'w') as f: f.write(page)
print("Created docs/S4_READ_DB_Guide.html")
