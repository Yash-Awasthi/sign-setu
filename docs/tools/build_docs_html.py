"""Builds the two single-file document sets next to system-design.html.

    python docs/tools/build_docs_html.py --mermaid path/to/mermaid.min.js

docs/sign-setu-docs.html: every document under docs/ except docs/teammate/.
docs/teammate-handoff.html: docs/teammate/ plus the repository README and the documents an engineer needs from docs/.
Mermaid is inlined so both pages work offline. Requires markdown-it-py and mdit-py-plugins.
"""
import argparse
import html
import pathlib
import re

from markdown_it import MarkdownIt
from mdit_py_plugins.anchors import anchors_plugin

DOCS = pathlib.Path(__file__).resolve().parents[1]
REPO = DOCS.parent

MAIN = (
    ["README.md"]
    + sorted(p.name for p in DOCS.glob("[0-9][0-9]-*.md"))
    + ["governance/README.md"]
    + sorted(str(p.relative_to(DOCS)).replace("\\", "/") for p in (DOCS / "governance").glob("[0-9][0-9]-*.md"))
    + sorted(str(p.relative_to(DOCS)).replace("\\", "/") for d in ("policies", "agreements", "forms") for p in (DOCS / "governance" / d).glob("*.md"))
)
TEAM_ORDER = ["README", "srs", "dfd", "architecture", "data-model", "api", "sequences", "state-machines", "avatar-rig", "ml", "security", "testing", "ops"]
TEAM = (
    [f"teammate/{n}.md" for n in TEAM_ORDER if (DOCS / "teammate" / f"{n}.md").exists()]
    + sorted(f"teammate/{p.name}" for p in (DOCS / "teammate").glob("*.md") if p.stem not in TEAM_ORDER)
    + ["../README.md", "07-decisions.md", "01-requirements.md", "09-open-source.md"]
)


def slug(rel: str) -> str:
    return "doc-" + re.sub(r"[^a-z0-9]+", "-", rel.lower().removesuffix(".md").removeprefix("../")).strip("-")


def title_of(text: str, rel: str) -> str:
    m = re.search(r"^#\s+(.+)$", text, re.M)
    return m.group(1).strip() if m else rel


def render(rel: str, members: set[str], other_page: str | None, other: set[str]) -> tuple[str, str, list[tuple[str, str]]]:
    path = (DOCS / rel).resolve()
    text = path.read_text(encoding="utf-8")
    sid = slug(rel)
    md = MarkdownIt("commonmark", {"html": True}).enable("table").enable("strikethrough")
    md.use(anchors_plugin, min_level=1, max_level=3, slug_func=lambda s: sid + "-" + re.sub(r"[^\w]+", "-", s.lower()).strip("-"))

    def fence(self, tokens, idx, options, env):
        t = tokens[idx]
        if t.info.strip() == "mermaid":
            return f'<pre class="mermaid">{html.escape(t.content)}</pre>\n'
        return f"<pre><code>{html.escape(t.content)}</code></pre>\n"

    md.add_render_rule("fence", fence)
    body = md.render(text)

    def link(m: re.Match) -> str:
        target = m.group(1)
        if re.match(r"^(https?:|mailto:|#)", target):
            return m.group(0)
        file, _, frag = target.partition("#")
        resolved = (path.parent / file).resolve()
        try:
            key = str(resolved.relative_to(DOCS)).replace("\\", "/")
        except ValueError:
            key = "../" + str(resolved.relative_to(REPO)).replace("\\", "/")
        anchor = slug(key) + ("-" + re.sub(r"[^\w]+", "-", frag.lower()).strip("-") if frag else "")
        if key in members:
            return f'href="#{anchor}"'
        if other_page and key in other:
            return f'href="{other_page}#{anchor}"'
        if key.endswith(".html"):
            return f'href="{key}"'
        return f'href="{html.escape(target)}" class="ext"'

    body = re.sub(r'href="([^"]+)"', link, body)
    body = re.sub(r"(<table>.*?</table>)", r'<div class="scroll">\1</div>', body, flags=re.S)
    heads = [(m.group(1), re.sub(r"<[^>]+>", "", m.group(2))) for m in re.finditer(r'<h2 id="([^"]+)">(.*?)</h2>', body)]
    return sid, title_of(text, rel), heads, body


CSS = """
:root{--bg:#f4f6f9;--panel:#fff;--ink:#142033;--muted:#5b6678;--line:#d3d9e2;--accent:#1d4f91;--accent-soft:#e3ebf6;--code:#eef1f5;
--display:"Archivo","Arial Narrow","Helvetica Neue",Arial,sans-serif;--body:"IBM Plex Sans","Segoe UI",Roboto,Arial,sans-serif;--mono:"IBM Plex Mono",ui-monospace,Consolas,monospace}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0e131a;--panel:#151c26;--ink:#e5eaf1;--muted:#9ba6b6;--line:#2a3442;--accent:#86aeea;--accent-soft:#1b2a40;--code:#1b2330;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#0e131a;--panel:#151c26;--ink:#e5eaf1;--muted:#9ba6b6;--line:#2a3442;--accent:#86aeea;--accent-soft:#1b2a40;--code:#1b2330;color-scheme:dark}
*{box-sizing:border-box}html{scroll-behavior:smooth;-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.6 var(--body)}
header.top{position:sticky;top:0;z-index:5;display:flex;gap:12px;align-items:center;padding:10px 16px;background:var(--panel);border-bottom:1px solid var(--line)}
header.top h1{font:700 18px/1.2 var(--display);margin:0;flex:1}
header.top input{width:min(320px,45vw);padding:7px 10px;border:1px solid var(--line);border-radius:10px;background:var(--bg);color:var(--ink);font:inherit}
header.top button{border:1px solid var(--line);background:var(--bg);color:var(--ink);border-radius:10px;padding:6px 10px;cursor:pointer;font:inherit}
.wrap{display:grid;grid-template-columns:300px minmax(0,1fr);max-width:1400px;margin:0 auto}
nav.toc{position:sticky;top:53px;height:calc(100vh - 53px);overflow:auto;padding:14px 10px 40px 16px;border-right:1px solid var(--line);font-size:13.5px}
nav.toc a{display:block;color:var(--ink);text-decoration:none;padding:3px 8px;border-radius:7px}
nav.toc a:hover{background:var(--accent-soft)}nav.toc a.doc{font-weight:600;margin-top:6px}nav.toc a.sub{color:var(--muted);padding-left:20px}
nav.toc .group{font:700 11px/1 var(--display);letter-spacing:.08em;text-transform:uppercase;color:var(--muted);margin:18px 8px 6px}
main{padding:8px 32px 80px;min-width:0}
section.doc{background:var(--panel);border:1px solid var(--line);border-radius:14px;padding:8px 28px 24px;margin:20px 0}
section.doc>.path{font:12px var(--mono);color:var(--muted);margin-top:14px}
h1,h2,h3,h4{font-family:var(--display);line-height:1.25;scroll-margin-top:70px}
h1{font-size:26px;margin:.4em 0 .6em}h2{font-size:20px;margin:1.6em 0 .5em;padding-top:.4em;border-top:1px solid var(--line)}h3{font-size:16.5px}
a{color:var(--accent)}a.ext{text-decoration-style:dotted}
code{font:13px var(--mono);background:var(--code);padding:1px 5px;border-radius:5px}
pre{background:var(--code);padding:12px 14px;border-radius:10px;overflow:auto}pre code{background:none;padding:0}
pre.mermaid{background:var(--panel);border:1px solid var(--line);text-align:center}
.scroll{overflow-x:auto;margin:12px 0}table{border-collapse:collapse;font-size:13.5px;min-width:100%}
th,td{border:1px solid var(--line);padding:6px 9px;text-align:left;vertical-align:top}th{background:var(--accent-soft)}
blockquote{margin:12px 0;padding:6px 14px;border-left:4px solid var(--accent);background:var(--accent-soft);border-radius:0 10px 10px 0}
.hide{display:none!important}
.docset{display:flex;gap:6px;flex-wrap:wrap}.docset>*{font:500 13px/1 var(--body);padding:7px 12px;border:1px solid var(--line);border-radius:999px;color:var(--ink);text-decoration:none;background:var(--bg)}
.docset b{background:var(--accent);border-color:var(--accent);color:var(--panel);font-weight:600}.docset a:hover{border-color:var(--accent)}
.cover{padding:28px 4px 8px}.cover .eyebrow{font:500 12px var(--mono);letter-spacing:.08em;text-transform:uppercase;color:var(--muted);margin:0}
.cover h1{font-size:clamp(28px,4vw,40px);margin:.2em 0 .3em}.cover p{max-width:75ch;color:var(--muted);margin:.4em 0}
.groups{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:12px;margin-top:18px}
.groups a{display:block;background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:14px 16px;color:var(--ink);text-decoration:none}
.groups a:hover{border-color:var(--accent)}.groups b{display:block;font:700 15px var(--display)}.groups span{font-size:13px;color:var(--muted)}
nav.toc a.sub{display:none}nav.toc a.sub.open{display:block}nav.toc a.active{background:var(--accent-soft);color:var(--accent)}
#top{position:fixed;right:18px;bottom:18px;border:1px solid var(--line);background:var(--panel);color:var(--ink);border-radius:999px;width:42px;height:42px;font-size:18px;cursor:pointer;opacity:0;pointer-events:none;transition:opacity .2s}
#top.show{opacity:1;pointer-events:auto}
header.top h1:empty{display:none}header.top .docset{flex:1}
@media print{section.doc{break-before:page}.cover{break-after:page}#top{display:none}}
@media (max-width:860px){.wrap{grid-template-columns:1fr}nav.toc{position:static;height:auto;border-right:0;border-bottom:1px solid var(--line)}main{padding:8px 16px 60px}section.doc{padding:6px 16px 18px}}
@media print{header.top,nav.toc{display:none}.wrap{display:block}section.doc{break-inside:auto;border:0}}
"""

JS = """
const root=document.documentElement;
try{const t=localStorage.getItem('signsetu-theme');if(t)root.dataset.theme=t;}catch(e){}
const dark=()=>root.dataset.theme==='dark'||(!root.dataset.theme&&matchMedia('(prefers-color-scheme: dark)').matches);
document.getElementById('theme').onclick=()=>{const t=dark()?'light':'dark';try{localStorage.setItem('signsetu-theme',t)}catch(e){};location.reload();};
const q=document.getElementById('q');
q.oninput=()=>{const t=q.value.trim().toLowerCase();document.querySelectorAll('section.doc').forEach(s=>{const hit=!t||s.textContent.toLowerCase().includes(t);s.classList.toggle('hide',!hit);document.querySelectorAll('nav.toc [data-doc="'+s.id+'"]').forEach(a=>a.classList.toggle('hide',!hit));});};
const links=[...document.querySelectorAll("nav.toc a")];
const spy=new IntersectionObserver(es=>{es.forEach(e=>{if(!e.isIntersecting)return;const id=e.target.id;const doc=e.target.closest('section.doc');links.forEach(a=>{a.classList.toggle('active',a.getAttribute('href')==='#'+id);a.classList.toggle('open',a.classList.contains('sub')&&!!doc&&a.dataset.doc===doc.id);});const on=links.find(a=>a.classList.contains('active'));if(on)on.scrollIntoView({block:'nearest'});});},{rootMargin:'-80px 0px -70% 0px'});
document.querySelectorAll('section.doc, section.doc h2[id]').forEach(el=>spy.observe(el));
const up=document.getElementById('top');addEventListener('scroll',()=>up.classList.toggle('show',scrollY>800),{passive:true});up.onclick=()=>scrollTo({top:0});
if(window.mermaid){mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:dark()?'dark':'neutral',fontFamily:'IBM Plex Sans, Segoe UI, sans-serif'});mermaid.run({querySelector:'pre.mermaid'}).catch(()=>{});}
"""


PAGES = [("system-design.html", "System design"), ("sign-setu-docs.html", "Documentation"), ("teammate-handoff.html", "Engineering handoff")]


def docset(current: str) -> str:
    return "".join(f"<b>{t}</b>" if f == current else f'<a href="{f}">{t}</a>' for f, t in PAGES)


def page(name: str, title: str, deck: str, groups: list[tuple[str, list[str], str]], mermaid_js: str, other_page: str | None, other: list[str]) -> None:
    members = {r for _, rs, _ in groups for r in rs}
    nav, sections, cover = [], [], []
    for group, rels, blurb in groups:
        cover.append(f'<a href="#{slug(rels[0])}"><b>{html.escape(group)}</b><span>{len(rels)} documents · {html.escape(blurb)}</span></a>')
        nav.append(f'<div class="group">{html.escape(group)}</div>')
        for rel in rels:
            sid, doc_title, heads, body = render(rel, members, other_page, set(other))
            nav.append(f'<a class="doc" data-doc="{sid}" href="#{sid}">{html.escape(doc_title)}</a>')
            nav += [f'<a class="sub" data-doc="{sid}" href="#{h}">{t}</a>' for h, t in heads]
            shown = rel.removeprefix("../") if rel.startswith("../") else "docs/" + rel
            sections.append(f'<section class="doc" id="{sid}"><div class="path">{html.escape(shown)}</div>{body}</section>')
    out = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Archivo:wght@600;700&family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;500;600&display=swap">
<style>{CSS}</style></head><body>
<header class="top"><nav class="docset" aria-label="Document set">{docset(name)}</nav><h1></h1><input id="q" type="search" placeholder="Filter documents" aria-label="Filter documents"><button id="theme" type="button">Theme</button></header>
<div class="wrap"><nav class="toc" aria-label="Contents">{''.join(nav)}</nav><main>
<div class="cover"><p class="eyebrow">Sign Setu · Release 1</p><h1>{html.escape(title)}</h1><p>{html.escape(deck)}</p><div class="groups">{''.join(cover)}</div></div>
{''.join(sections)}</main></div><button id="top" type="button" aria-label="Back to top">&#8593;</button>
<script>{mermaid_js}</script><script>{JS}</script></body></html>"""
    (DOCS / name).write_text(out, encoding="utf-8", newline="\n")
    print(f"{name}: {len(members)} documents, {len(out) // 1024} KB")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mermaid", required=True, help="mermaid.min.js (MIT), inlined so the pages work offline")
    mermaid_js = pathlib.Path(ap.parse_args().mermaid).read_text(encoding="utf-8").replace("</script", "<\\/script")
    numbered = [r for r in MAIN if re.match(r"\d\d-", r)]
    gov = [r for r in MAIN if r.startswith("governance/")]
    page("sign-setu-docs.html", "Sign Setu Documentation",
         "The complete specification and governance set for Sign Setu: what the app does, how it is built and secured, how it is "
         "tested, why each decision was taken, and the policies, agreements and forms a department needs to run it.", [
        ("Overview", ["README.md", "13-product-overview.md", "14-presentation-script.md"], "index, product overview, presentation script"),
        ("Specification", [r for r in numbered if r not in ("13-product-overview.md", "14-presentation-script.md")], "requirements, architecture, UX, security, tests, decisions"),
        ("Governance", gov, "threat model, DPIA, operations, compliance, policies, forms"),
    ], mermaid_js, "teammate-handoff.html", TEAM)
    page("teammate-handoff.html", "Sign Setu Engineering Handoff",
         "Everything an engineer joining the project needs: requirements, data flows, architecture, data model, interfaces, flows "
         "and states, the character rig, the vision and sign models, security, testing and operations, with the decision log for context.", [
        ("Handoff", [r for r in TEAM if r.startswith("teammate/")], "SRS, DFD, architecture, data, interfaces, flows, rig, ML, security, tests, ops"),
        ("Repository", ["../README.md"], "build, test and repository map"),
        ("Reference", ["01-requirements.md", "07-decisions.md", "09-open-source.md"], "full requirements, decision log, open-source register"),
    ], mermaid_js, "sign-setu-docs.html", MAIN)

if __name__ == "__main__":
    main()
