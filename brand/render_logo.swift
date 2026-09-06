#!/usr/bin/env swift
import AppKit
import Foundation

let cyan = NSColor(srgbRed: 0x00 / 255, green: 0xC2 / 255, blue: 0xCB / 255, alpha: 1)
let ink = NSColor(srgbRed: 0x0B / 255, green: 0x0B / 255, blue: 0x0B / 255, alpha: 1)
let white = NSColor.white
let muted = NSColor(srgbRed: 0x8A / 255, green: 0x9A / 255, blue: 0x9D / 255, alpha: 1)
let paper = NSColor(srgbRed: 0xF4 / 255, green: 0xF6 / 255, blue: 0xF6 / 255, alpha: 1)
let tile = NSColor(srgbRed: 0x17 / 255, green: 0x20 / 255, blue: 0x23 / 255, alpha: 1)

func wordmarkFont(size: CGFloat) -> NSFont {
    NSFont(name: "HelveticaNeue-Medium", size: size)
        ?? NSFont.systemFont(ofSize: size, weight: .medium)
}

func textAttrs(size: CGFloat, color: NSColor) -> [NSAttributedString.Key: Any] {
    [
        .font: wordmarkFont(size: size),
        .foregroundColor: color,
        .kern: size * -0.03
    ]
}

func textSize(_ string: String, size: CGFloat) -> NSSize {
    NSAttributedString(string: string, attributes: textAttrs(size: size, color: .white)).size()
}

func drawText(_ string: String, at origin: CGPoint, size: CGFloat, color: NSColor) -> CGFloat {
    let str = NSAttributedString(string: string, attributes: textAttrs(size: size, color: color))
    str.draw(at: origin)
    return str.size().width
}

func baseline(for origin: CGPoint, em: CGFloat) -> CGFloat {
    origin.y - wordmarkFont(size: em).descender
}

func fillNoteHead(center: CGPoint, width: CGFloat, height: CGFloat, tiltDegrees: CGFloat, color: NSColor) {
    color.setFill()
    let head = NSBezierPath(ovalIn: CGRect(x: -width / 2, y: -height / 2, width: width, height: height))
    var t = AffineTransform.identity
    t.translate(x: center.x, y: center.y)
    t.rotate(byRadians: tiltDegrees * CGFloat.pi / 180)
    head.transform(using: t)
    head.fill()
}

func strokeFlag(from start: CGPoint, em: CGFloat, color: NSColor, right: Bool) {
    color.setStroke()
    let path = NSBezierPath()
    path.lineWidth = max(1.3, em * 0.052)
    path.lineCapStyle = .round
    path.lineJoinStyle = .round
    let dir: CGFloat = right ? 1 : -1
    path.move(to: start)
    path.curve(
        to: CGPoint(x: start.x + dir * em * 0.20, y: start.y - em * 0.17),
        controlPoint1: CGPoint(x: start.x + dir * em * 0.15, y: start.y + em * 0.01),
        controlPoint2: CGPoint(x: start.x + dir * em * 0.22, y: start.y - em * 0.06)
    )
    path.stroke()
}

/// Capital P as a stem-down eighth note: stem + tilted head on the right + flag.
func drawPEighth(at origin: CGPoint, em: CGFloat, color: NSColor) -> CGFloat {
    let font = wordmarkFont(size: em)
    let cap = font.capHeight
    let stemW = max(1.4, em * 0.088)
    let y = origin.y
    let stemX = origin.x
    color.setFill()
    NSBezierPath(rect: CGRect(x: stemX, y: y, width: stemW, height: cap)).fill()

    let headW = cap * 0.54
    let headH = cap * 0.38
    let head = CGPoint(x: stemX + stemW + headW * 0.30, y: y + cap - headH * 0.52)
    fillNoteHead(center: head, width: headW, height: headH, tiltDegrees: 24, color: color)
    strokeFlag(from: CGPoint(x: stemX + stemW * 0.9, y: y + cap + em * 0.01), em: em * 0.82, color: color, right: true)
    return stemW + headW * 0.78 + em * 0.10
}

/// Lowercase d as a stem-up quarter/eighth note.
func drawDNote(at origin: CGPoint, em: CGFloat, color: NSColor) -> CGFloat {
    let font = wordmarkFont(size: em)
    let xh = font.xHeight
    let cap = font.capHeight
    let stemW = max(1.3, em * 0.082)
    let y = origin.y
    let headW = xh * 0.92
    let headH = xh * 0.66
    let head = CGPoint(x: origin.x + headW * 0.48, y: y + xh * 0.46)
    fillNoteHead(center: head, width: headW, height: headH, tiltDegrees: -22, color: color)

    let stemX = origin.x + headW * 0.78
    let stemBottom = y + xh * 0.12
    let stemTop = y + cap * 1.06
    color.setFill()
    NSBezierPath(rect: CGRect(x: stemX, y: stemBottom, width: stemW, height: stemTop - stemBottom)).fill()
    strokeFlag(from: CGPoint(x: stemX + stemW, y: stemTop - em * 0.008), em: em * 0.85, color: color, right: true)
    return headW + stemW + em * 0.12
}

/// Capital M as equalizer bars tracing an M silhouette.
func drawMEqualizer(at origin: CGPoint, em: CGFloat, color: NSColor) -> CGFloat {
    let font = wordmarkFont(size: em)
    let cap = font.capHeight
    let mW = textSize("M", size: em).width
    let bars = 5
    let barW = max(2.0, em * 0.078)
    let gap = (mW - CGFloat(bars) * barW) / CGFloat(bars - 1)
    let heights: [CGFloat] = [1.0, 0.70, 0.36, 0.70, 1.0]
    color.setFill()
    for i in 0..<bars {
        let h = cap * heights[i]
        let x = origin.x + CGFloat(i) * (barW + gap)
        let rect = CGRect(x: x, y: origin.y, width: barW, height: h)
        NSBezierPath(roundedRect: rect, xRadius: barW / 2, yRadius: barW / 2).fill()
    }
    return mW + em * 0.04
}

/// The i in Musi: stem + quarter-note-head tittle.
func drawINoteDot(at origin: CGPoint, em: CGFloat, color: NSColor) -> CGFloat {
    let font = wordmarkFont(size: em)
    let xh = font.xHeight
    let iW = textSize("i", size: em).width
    let stemW = max(1.2, em * 0.078)
    let stemX = origin.x + (iW - stemW) / 2
    color.setFill()
    NSBezierPath(rect: CGRect(x: stemX, y: origin.y, width: stemW, height: xh)).fill()
    let headW = em * 0.18
    let headH = em * 0.13
    fillNoteHead(
        center: CGPoint(x: stemX + stemW / 2, y: origin.y + xh + em * 0.13),
        width: headW,
        height: headH,
        tiltDegrees: -22,
        color: color
    )
    return iW * 1.08
}

func drawWordmarkP(origin: CGPoint, size: CGFloat, text: NSColor, accent: NSColor) -> CGFloat {
    let musiW = drawText("Musi", at: origin, size: size, color: text) + size * 0.10
    let pW = drawPEighth(at: CGPoint(x: origin.x + musiW, y: baseline(for: origin, em: size)), em: size, color: accent)
    let rest = drawText("edia", at: CGPoint(x: origin.x + musiW + pW + size * 0.04, y: origin.y), size: size, color: text)
    return musiW + pW + size * 0.04 + rest
}

func drawWordmarkD(origin: CGPoint, size: CGFloat, text: NSColor, accent: NSColor) -> CGFloat {
    let leftW = drawText("MusiPe", at: origin, size: size, color: text) + size * 0.04
    let dW = drawDNote(at: CGPoint(x: origin.x + leftW, y: baseline(for: origin, em: size)), em: size, color: accent)
    let rest = drawText("ia", at: CGPoint(x: origin.x + leftW + dW + size * 0.02, y: origin.y), size: size, color: text)
    return leftW + dW + size * 0.02 + rest
}

func drawWordmarkM(origin: CGPoint, size: CGFloat, text: NSColor, accent: NSColor) -> CGFloat {
    let mW = drawMEqualizer(at: CGPoint(x: origin.x, y: baseline(for: origin, em: size)), em: size, color: accent)
    let rest = drawText("usiPedia", at: CGPoint(x: origin.x + mW + size * 0.02, y: origin.y), size: size, color: text)
    return mW + size * 0.02 + rest
}

func drawWordmarkI(origin: CGPoint, size: CGFloat, text: NSColor, accent: NSColor) -> CGFloat {
    let musW = drawText("Mus", at: origin, size: size, color: text) + size * 0.01
    let iW = drawINoteDot(at: CGPoint(x: origin.x + musW, y: baseline(for: origin, em: size)), em: size, color: accent)
    let rest = drawText("Pedia", at: CGPoint(x: origin.x + musW + iW - size * 0.02, y: origin.y), size: size, color: text)
    return musW + iW + rest - size * 0.02
}

func image(width: Int, height: Int, _ draw: (CGRect) -> Void) -> NSImage {
    let img = NSImage(size: NSSize(width: width, height: height))
    img.lockFocus()
    NSGraphicsContext.current?.imageInterpolation = .high
    NSGraphicsContext.current?.shouldAntialias = true
    draw(CGRect(x: 0, y: 0, width: width, height: height))
    img.unlockFocus()
    return img
}

func savePNG(_ image: NSImage, to url: URL) {
    let size = image.size
    let scale: CGFloat = 2
    let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: Int(size.width * scale),
        pixelsHigh: Int(size.height * scale),
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    )!
    rep.size = size
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    NSGraphicsContext.current?.imageInterpolation = .high
    image.draw(in: CGRect(origin: .zero, size: size))
    NSGraphicsContext.restoreGraphicsState()
    try! rep.representation(using: .png, properties: [:])!.write(to: url)
    print("wrote \(url.path)")
}

func wordmarkCard(width: Int, height: Int, bg: NSColor, text: NSColor, accent: NSColor, draw: (CGPoint, CGFloat) -> CGFloat) -> NSImage {
    image(width: width, height: height) { rect in
        bg.setFill()
        rect.fill()
        let size: CGFloat = 96
        let w = draw(.zero, size)
        _ = draw(CGPoint(x: (rect.width - w) / 2, y: (rect.height - size) / 2), size)
    }
}

let out = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)

let variants: [(String, (CGPoint, CGFloat, NSColor, NSColor) -> CGFloat)] = [
    ("p-eighth", { o, s, t, a in drawWordmarkP(origin: o, size: s, text: t, accent: a) }),
    ("d-note", { o, s, t, a in drawWordmarkD(origin: o, size: s, text: t, accent: a) }),
    ("m-eq", { o, s, t, a in drawWordmarkM(origin: o, size: s, text: t, accent: a) }),
    ("i-tittle", { o, s, t, a in drawWordmarkI(origin: o, size: s, text: t, accent: a) }),
]

for (name, draw) in variants {
    let dark = image(width: 1600, height: 420) { rect in
        ink.setFill()
        rect.fill()
        let size: CGFloat = 108
        let w = draw(CGPoint(x: -8000, y: 0), size, white, cyan)
        _ = draw(CGPoint(x: (rect.width - w) / 2, y: (rect.height - size) / 2 + 8), size, white, cyan)
    }
    savePNG(dark, to: out.appendingPathComponent("musicpedia-\(name)-dark.png"))

    let light = image(width: 1600, height: 420) { rect in
        paper.setFill()
        rect.fill()
        let size: CGFloat = 108
        let w = draw(CGPoint(x: -8000, y: 0), size, ink, cyan)
        _ = draw(CGPoint(x: (rect.width - w) / 2, y: (rect.height - size) / 2 + 8), size, ink, cyan)
    }
    savePNG(light, to: out.appendingPathComponent("musicpedia-\(name)-light.png"))
}

let compare = image(width: 1600, height: 1480) { rect in
    ink.setFill()
    rect.fill()
    let labels = ["P  ·  EIGHTH NOTE", "d  ·  ASCENDING NOTE", "M  ·  EQUALIZER", "i  ·  NOTE TITTLE"]
    let drawers: [(CGPoint, CGFloat, NSColor, NSColor) -> CGFloat] = [
        { o, s, t, a in drawWordmarkP(origin: o, size: s, text: t, accent: a) },
        { o, s, t, a in drawWordmarkD(origin: o, size: s, text: t, accent: a) },
        { o, s, t, a in drawWordmarkM(origin: o, size: s, text: t, accent: a) },
        { o, s, t, a in drawWordmarkI(origin: o, size: s, text: t, accent: a) },
    ]
    NSAttributedString(string: "MUSIPEDIA  ·  FOUR TREATMENTS", attributes: [
        .font: wordmarkFont(size: 15),
        .foregroundColor: muted,
        .kern: 2.2
    ]).draw(at: CGPoint(x: 80, y: rect.height - 56))

    for i in 0..<4 {
        let y = rect.height - 220 - CGFloat(i) * 310
        tile.setFill()
        NSBezierPath(roundedRect: CGRect(x: 64, y: y - 40, width: 1472, height: 260), xRadius: 18, yRadius: 18).fill()
        NSAttributedString(string: labels[i], attributes: [
            .font: wordmarkFont(size: 13),
            .foregroundColor: muted,
            .kern: 1.6
        ]).draw(at: CGPoint(x: 100, y: y + 168))
        _ = drawers[i](CGPoint(x: 100, y: y + 36), 84, white, cyan)
    }
}
savePNG(compare, to: out.appendingPathComponent("musicpedia-compare.png"))

func icon(_ drawGlyph: (CGRect, NSColor) -> Void) -> NSImage {
    image(width: 1024, height: 1024) { rect in
        ink.setFill()
        rect.fill()
        let disc = rect.insetBy(dx: rect.width * 0.18, dy: rect.height * 0.18)
        cyan.setFill()
        NSBezierPath(ovalIn: disc).fill()
        drawGlyph(disc, ink)
    }
}

savePNG(icon { disc, color in
    let em = disc.width * 0.72
    _ = drawPEighth(at: CGPoint(x: disc.midX - em * 0.22, y: disc.midY - wordmarkFont(size: em).capHeight / 2), em: em, color: color)
}, to: out.appendingPathComponent("musicpedia-icon-p.png"))

savePNG(icon { disc, color in
    let em = disc.width * 0.72
    _ = drawDNote(at: CGPoint(x: disc.midX - em * 0.22, y: disc.midY - wordmarkFont(size: em).capHeight / 2), em: em, color: color)
}, to: out.appendingPathComponent("musicpedia-icon-d.png"))

savePNG(icon { disc, color in
    let em = disc.width * 0.70
    let mW = textSize("M", size: em).width
    _ = drawMEqualizer(at: CGPoint(x: disc.midX - mW / 2, y: disc.midY - wordmarkFont(size: em).capHeight / 2), em: em, color: color)
}, to: out.appendingPathComponent("musicpedia-icon-m.png"))

savePNG(icon { disc, color in
    let em = disc.width * 0.85
    let iW = textSize("i", size: em).width
    _ = drawINoteDot(at: CGPoint(x: disc.midX - iW / 2, y: disc.midY - wordmarkFont(size: em).xHeight / 2 - em * 0.08), em: em, color: color)
}, to: out.appendingPathComponent("musicpedia-icon-i.png"))
