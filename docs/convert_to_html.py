#!/usr/bin/env python3
"""Convert MS3_Study_Guide.md to a styled HTML file for printing to PDF."""
import re, sys, html

def md_to_html(md_text):
    lines = md_text.split('\n')
    out = []
    in_code = False
    in_table = False
    in_ul = False
    code_lang = ""
    
    for line in lines:
        # Code blocks
        if line.strip().startswith('```'):
            if in_code:
                out.append('</code></pre>')
                in_code = False
            else:
                code_lang = line.strip()[3:]
                out.append(f'<pre><code class="{code_lang}">')
                in_code = True
            continue
        if in_code:
            out.append(html.escape(line))
            continue
        
        # Empty line
        if not line.strip():
            if in_ul:
                out.append('</ul>')
                in_ul = False
            if in_table:
                out.append('</table>')
                in_table = False
            out.append('')
            continue
        
        # Headings
        m = re.match(r'^(#{1,6})\s+(.*)', line)
        if m:
            level = len(m.group(1))
            text = m.group(2)
            if in_ul:
                out.append('</ul>')
                in_ul = False
            cls = ' class="page-break"' if level <= 2 and len(out) > 5 else ''
            out.append(f'<h{level}{cls}>{text}</h{level}>')
            continue
        
        # Horizontal rule
        if line.strip() == '---':
            out.append('<hr>')
            continue
        
        # Table
        if '|' in line and line.strip().startswith('|'):
            cells = [c.strip() for c in line.strip().strip('|').split('|')]
            if all(set(c) <= set('- :') for c in cells):
                continue  # separator row
            if not in_table:
                out.append('<table><tr>')
                for c in cells:
                    c = apply_inline(c)
                    out.append(f'<th>{c}</th>')
                out.append('</tr>')
                in_table = True
            else:
                out.append('<tr>')
                for c in cells:
                    c = apply_inline(c)
                    out.append(f'<td>{c}</td>')
                out.append('</tr>')
            continue
        
        # List items
        m = re.match(r'^(\s*)([-*]|\d+\.)\s+(.*)', line)
        if m:
            if not in_ul:
                out.append('<ul>')
                in_ul = True
            out.append(f'<li>{apply_inline(m.group(3))}</li>')
            continue
        
        # Paragraph
        if in_table:
            out.append('</table>')
            in_table = False
        out.append(f'<p>{apply_inline(line)}</p>')
    
    if in_ul: out.append('</ul>')
    if in_table: out.append('</table>')
    return '\n'.join(out)

def apply_inline(text):
    text = html.escape(text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', text)
    text = re.sub(r'`(.+?)`', r'<code class="inline">\1</code>', text)
    text = re.sub(r'\*(.+?)\*', r'<em>\1</em>', text)
    return text

with open('docs/MS3_Study_Guide.md', 'r') as f:
    md = f.read()

body = md_to_html(md)

page = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8">
<title>MS3 Study Guide - SpringShoes Booking</title>
<style>
@media print {{ .page-break {{ page-break-before: always; }} }}
body {{ font-family: 'Segoe UI', Arial, sans-serif; max-width: 900px; margin: 0 auto; padding: 20px; font-size: 13px; line-height: 1.6; color: #1a1a1a; }}
h1 {{ color: #1a56db; border-bottom: 3px solid #1a56db; padding-bottom: 8px; font-size: 24px; }}
h2 {{ color: #1e40af; border-bottom: 2px solid #dbeafe; padding-bottom: 5px; font-size: 18px; margin-top: 25px; }}
h3 {{ color: #374151; font-size: 15px; }}
pre {{ background: #1e293b; color: #e2e8f0; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 12px; }}
code.inline {{ background: #e0e7ff; color: #3730a3; padding: 2px 5px; border-radius: 3px; font-size: 12px; }}
table {{ border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 12px; }}
th {{ background: #1e40af; color: white; padding: 8px; text-align: left; }}
td {{ border: 1px solid #d1d5db; padding: 6px 8px; }}
tr:nth-child(even) {{ background: #f3f4f6; }}
hr {{ border: none; border-top: 2px solid #e5e7eb; margin: 20px 0; }}
ul {{ padding-left: 20px; }}
li {{ margin-bottom: 4px; }}
p {{ margin: 6px 0; }}
strong {{ color: #1e40af; }}
</style></head><body>
{body}
</body></html>"""

with open('docs/MS3_Study_Guide.html', 'w') as f:
    f.write(page)
print("Created docs/MS3_Study_Guide.html")
