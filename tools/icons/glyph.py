"""Glyph outline -> SVG path string, positioned in a 108 viewport."""
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

def glyph_path(font_file, text, height, cx, cy, by="bbox", tracking=0.0):
    f = TTFont(font_file)
    gs = f.getGlyphSet(); cmap = f.getBestCmap()
    # layout: advance widths
    names = [cmap[ord(ch)] for ch in text]
    x = 0; placed = []
    for n in names:
        placed.append((n, x)); x += gs[n].width + tracking * f['head'].unitsPerEm
    # bbox
    bp = BoundsPen(gs)
    for n, dx in placed:
        gs[n].draw(TransformPen(bp, (1,0,0,1,dx,0)))
    x0,y0,x1,y1 = bp.bounds
    s = height / (y1 - y0)
    tx = cx - (x0 + x1) / 2 * s
    ty = cy + (y0 + y1) / 2 * s
    sp = SVGPathPen(gs, ntos=lambda v: f"{v:.2f}".rstrip('0').rstrip('.'))
    for n, dx in placed:
        gs[n].draw(TransformPen(sp, (s, 0, 0, -s, tx + dx * s, ty)))
    return sp.getCommands(), ((x1-x0)*s, height)
