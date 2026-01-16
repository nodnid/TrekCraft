# TrekCraft Asset Generation Specification

## Overview

Generate pixel art textures for a Star Trek themed Minecraft mod. All textures must be **16x16 pixels** in the Minecraft art style - blocky, limited color palette, with clear readability at small sizes.

**Art Direction:** Blend Star Trek: The Next Generation aesthetics (LCARS panels, gold/silver metals, cyan/blue accents) with Minecraft's chunky pixel art style.

---

## Item Textures (16x16)

### 1. Latinum Slip (`latinum_slip.png`)
**Description:** A small, thin piece of gold-pressed latinum currency.
- Shape: Small oval or rounded rectangle, like a coin or chip
- Color: Bright gold (#FFD700) with darker gold (#DAA520) shading
- Style: Shiny, metallic appearance with 1-2 highlight pixels
- Reference: Think of a gold poker chip or small ingot fragment

### 2. Latinum Strip (`latinum_strip.png`)
**Description:** A bar-shaped piece of latinum, more valuable than a slip.
- Shape: Horizontal rectangular bar, like a small gold bar
- Color: Rich gold (#DAA520) base with bright gold (#FFD700) highlights
- Details: Add subtle horizontal line segments to suggest stacked value
- Style: Metallic sheen, slightly darker than slip to show density

### 3. Tricorder (`tricorder.png`)
**Description:** A handheld Star Trek scanning device.
- Shape: Rounded rectangle, handheld device shape (like a flip phone or small tablet)
- Colors:
  - Body: Dark blue-gray (#465A6E) or silver (#A8A8A8)
  - Screen: Cyan (#00FFFF) or light blue glow
  - Accents: Small colored buttons (red, yellow, green pixels)
- Details:
  - Small display screen area (2x3 or 3x4 pixels) with cyan glow
  - Suggestion of buttons or controls below screen
  - Optional: Starfleet insignia hint (single chevron pixel)
- Style: Sleek, technological, clearly a device not a tool

---

## Block Textures (16x16)

### 4. Latinum Bar - All Sides (`latinum_bar.png`)
**Description:** A solid block of gold-pressed latinum (storage block).
- Pattern: Brick-like or ingot-stack pattern showing stored wealth
- Colors: Gold (#FFD700) and darker gold (#B8860B) for depth
- Details:
  - Horizontal lines suggesting stacked bars
  - Subtle shine/highlight in upper-left pixels
- Style: Similar to Minecraft gold block but with more defined bar shapes
- Reference: Like gold block meets hay bale (showing individual units)

### 5. Transporter Pad - Top (`transporter_pad_top.png`)
**Description:** The standing surface of a Star Trek transporter pad.
- Shape: Circular pattern centered in the 16x16 space
- Colors:
  - Base: Dark gray (#404040) or dark metal
  - Rings: Concentric circles in lighter gray (#808080)
  - Center: Cyan (#00FFFF) glow or accent (2x2 or 3x3 pixels)
  - Optional: Subtle blue light dots around the circle
- Pattern: Concentric rings radiating from glowing center
- Style: Technical, clean, suggests "stand here to teleport"
- Reference: TNG transporter pad from above

### 6. Transporter Pad - Side (`transporter_pad_side.png`)
**Description:** The side view of the low transporter pad platform.
- Shape: Should tile as a short block (pad is only 4 pixels tall in-game)
- Colors:
  - Main: Silver/gray metal (#A0A0A0)
  - Border: Darker gray (#606060) top and bottom edge
  - Accent: Thin cyan (#00FFFF) line or light strip
- Details: Industrial/technical look with panel lines
- Style: Sleek metal platform edge

### 7. Transporter Room - Front (`transporter_room_front.png`)
**Description:** The main control face of the transporter room console.
- Colors:
  - Body: Dark gray (#404040) base
  - Panel: LCARS-style colored sections (cyan, orange, red strips)
  - Accents: Cyan (#00FFFF) light indicators
- Details:
  - Horizontal colored bars suggesting LCARS interface (2-3 pixel tall strips)
  - Small square "button" pixels in various colors
  - Central display area with cyan glow
- Style: Star Trek control console, clearly the "front" of the machine
- Reference: TNG transporter room console

### 8. Transporter Room - Side (`transporter_room_side.png`)
**Description:** Side panel of the transporter room block.
- Colors: Dark gray (#404040) with subtle panel lines
- Details:
  - Vertical or horizontal panel seams
  - 1-2 small indicator lights (cyan or yellow pixels)
- Style: Industrial, technical, less detailed than front

### 9. Transporter Room - Top (`transporter_room_top.png`)
**Description:** Top surface of the transporter room console.
- Colors: Dark gray base with cyan accent border or corner lights
- Details:
  - Grid pattern suggesting ventilation or technical surface
  - Cyan (#00FFFF) corner accents or border glow
- Style: Technical equipment top-down view

### 10. Transporter Room - Bottom (`transporter_room_bottom.png`)
**Description:** Bottom of the transporter room block.
- Colors: Solid dark gray (#404040)
- Details: Minimal - plain metal base or subtle grid
- Style: Simple, industrial floor/base

---

## Color Palette Reference

| Color Name | Hex Code | Usage |
|------------|----------|-------|
| Bright Gold | #FFD700 | Latinum highlights |
| Rich Gold | #DAA520 | Latinum base |
| Dark Gold | #B8860B | Latinum shadows |
| Cyan | #00FFFF | Tech accents, screens, transporter glow |
| Light Blue | #87CEEB | Secondary tech highlights |
| Dark Gray | #404040 | Machine/console base |
| Medium Gray | #808080 | Metal, panels |
| Light Gray | #A0A0A0 | Metal highlights |
| Steel Blue | #4682B4 | Tricorder body |
| LCARS Orange | #FF9900 | Console accent |
| LCARS Red | #CC0000 | Console accent |

---

## Style Guidelines

1. **Pixel Density:** Use every pixel intentionally. At 16x16, each pixel matters.

2. **Shading:** Use 2-3 shades per color. Light source from upper-left (Minecraft convention).

3. **Outlines:** Avoid black outlines unless necessary. Use darker shade of the object color instead.

4. **Readability:** Items must be recognizable at inventory size (~32x32 display). Test at small scale.

5. **Minecraft Compatibility:** Reference vanilla Minecraft textures for style matching:
   - Gold ingot (for latinum colors)
   - Iron block (for metallic shading)
   - Enchanting table (for magical/tech glow)

6. **Star Trek Feel:** 
   - Cyan glows suggest transporter/tech energy
   - LCARS-style colored bars on consoles
   - Clean, futuristic metals (not rusty or worn)

---

## Delivery Format

- **Format:** PNG with transparency where appropriate
- **Size:** Exactly 16x16 pixels
- **Color Mode:** RGBA
- **Naming:** Use exact filenames specified above

---

## File Checklist

```
textures/item/
├── latinum_slip.png
├── latinum_strip.png
└── tricorder.png

textures/block/
├── latinum_bar.png
├── transporter_pad_top.png
├── transporter_pad_side.png
├── transporter_room_front.png
├── transporter_room_side.png
├── transporter_room_top.png
└── transporter_room_bottom.png
```

Total: **10 textures**
