#!/usr/bin/env python3
import os
import glob
import base64
import json
import shutil

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI_DIR = os.path.join(ROOT_DIR, "common/src/main/resources/assets/cobblemon_smartphone/textures/gui")
OUT_HTML = os.path.join(ROOT_DIR, "tools/theme-creator/index.html")
ROOT_HTML = os.path.join(ROOT_DIR, "theme_creator.html")

def collect_assets():
    assets = {
        "colors_small": {},
        "colors_large": {},
        "elements": {},
        "buttons": {},
        "wallpapers": {}
    }

    colors = ["black", "blue", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
              "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow"]

    for c in colors:
        sp = os.path.join(GUI_DIR, f"smartphone_{c}.png")
        lp = os.path.join(GUI_DIR, f"large_smartphone_{c}.png")
        if os.path.exists(sp):
            with open(sp, "rb") as f:
                assets["colors_small"][c] = "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")
        if os.path.exists(lp):
            with open(lp, "rb") as f:
                assets["colors_large"][c] = "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")

    for p in glob.glob(os.path.join(GUI_DIR, "elements/*.png")):
        name = os.path.basename(p)
        with open(p, "rb") as f:
            assets["elements"][name] = "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")

    for p in glob.glob(os.path.join(GUI_DIR, "buttons/*.png")):
        name = os.path.basename(p)
        with open(p, "rb") as f:
            assets["buttons"][name] = "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")

    for p in glob.glob(os.path.join(GUI_DIR, "wallpapers/*.png")) + [os.path.join(GUI_DIR, "home_screen.png"), os.path.join(GUI_DIR, "large_screen.png")]:
        name = os.path.basename(p)
        with open(p, "rb") as f:
            assets["wallpapers"][name] = "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")

    return assets

def build_html():
    assets = collect_assets()
    assets_json = json.dumps(assets)

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Cobblemon Smartphone — Theme Creator Studio</title>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/jszip/3.10.1/jszip.min.js"></script>
  <style>
    :root {{
      --bg-dark: #0e1117;
      --bg-panel: #161b22;
      --bg-subpanel: #21262d;
      --border-color: #30363d;
      --accent: #5bd1da;
      --accent-hover: #7ce3eb;
      --text-main: #f0f6fc;
      --text-muted: #8b949e;
      --danger: #f85149;
      --success: #3fb950;
      --gold: #d29922;
    }}

    * {{
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }}

    body {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background-color: var(--bg-dark);
      color: var(--text-main);
      display: flex;
      flex-direction: column;
      height: 100vh;
      overflow: hidden;
    }}

    header {{
      background: var(--bg-panel);
      border-bottom: 1px solid var(--border-color);
      padding: 10px 20px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 15px;
      flex-shrink: 0;
    }}

    .header-title {{
      display: flex;
      align-items: center;
      gap: 12px;
    }}

    .header-title h1 {{
      font-size: 1.15rem;
      font-weight: 700;
      letter-spacing: -0.3px;
      display: flex;
      align-items: center;
      gap: 8px;
    }}

    .header-title .badge {{
      font-size: 0.7rem;
      background: rgba(91, 209, 218, 0.15);
      color: var(--accent);
      border: 1px solid rgba(91, 209, 218, 0.4);
      padding: 2px 7px;
      border-radius: 12px;
      font-weight: 600;
    }}

    .header-actions {{
      display: flex;
      align-items: center;
      gap: 10px;
    }}

    button, .btn {{
      background: var(--bg-subpanel);
      color: var(--text-main);
      border: 1px solid var(--border-color);
      border-radius: 6px;
      padding: 6px 12px;
      font-size: 0.82rem;
      font-weight: 500;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.15s ease;
      text-decoration: none;
    }}

    button:hover, .btn:hover {{
      background: #2b323c;
      border-color: #8b949e;
    }}

    button.primary, .btn.primary {{
      background: #1f6feb;
      border-color: #388bfd;
      color: #fff;
    }}

    button.primary:hover, .btn.primary:hover {{
      background: #388bfd;
    }}

    button.accent, .btn.accent {{
      background: #195b60;
      border-color: var(--accent);
      color: #fff;
    }}

    button.accent:hover, .btn.accent:hover {{
      background: #23787f;
    }}

    select, input[type="text"], input[type="number"] {{
      background: var(--bg-subpanel);
      color: var(--text-main);
      border: 1px solid var(--border-color);
      border-radius: 6px;
      padding: 5px 8px;
      font-size: 0.82rem;
      outline: none;
    }}

    select:focus, input[type="text"]:focus, input[type="number"]:focus {{
      border-color: var(--accent);
    }}

    /* Main Container */
    .app-body {{
      display: flex;
      flex: 1;
      height: calc(100vh - 54px);
      overflow: hidden;
    }}

    /* Left Sidebar: Controls & Tabs */
    .editor-pane {{
      width: 580px;
      min-width: 480px;
      background: var(--bg-panel);
      border-right: 1px solid var(--border-color);
      display: flex;
      flex-direction: column;
      height: 100%;
    }}

    .tab-bar {{
      display: flex;
      background: var(--bg-dark);
      border-bottom: 1px solid var(--border-color);
      overflow-x: auto;
      flex-shrink: 0;
    }}

    .tab-btn {{
      background: transparent;
      border: none;
      border-bottom: 2px solid transparent;
      border-radius: 0;
      padding: 10px 14px;
      color: var(--text-muted);
      font-size: 0.82rem;
      font-weight: 600;
      white-space: nowrap;
    }}

    .tab-btn:hover {{
      background: rgba(255, 255, 255, 0.04);
      color: var(--text-main);
    }}

    .tab-btn.active {{
      color: var(--accent);
      border-bottom-color: var(--accent);
      background: rgba(91, 209, 218, 0.08);
    }}

    .tab-content {{
      flex: 1;
      overflow-y: auto;
      padding: 18px 20px;
      display: none;
    }}

    .tab-content.active {{
      display: block;
    }}

    .section-title {{
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.6px;
      color: var(--text-muted);
      margin: 16px 0 10px 0;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 6px;
    }}

    .section-title:first-child {{
      margin-top: 0;
    }}

    .form-group {{
      margin-bottom: 14px;
    }}

    .form-group label {{
      display: block;
      font-size: 0.82rem;
      font-weight: 600;
      margin-bottom: 5px;
      color: var(--text-main);
    }}

    .form-group .desc {{
      font-size: 0.74rem;
      color: var(--text-muted);
      margin-top: 4px;
      line-height: 1.35;
    }}

    .form-row {{
      display: flex;
      gap: 12px;
      align-items: center;
    }}

    .form-row > * {{
      flex: 1;
    }}

    /* Dropzone / Upload Box */
    .upload-box {{
      border: 1px dashed var(--border-color);
      border-radius: 6px;
      background: rgba(255, 255, 255, 0.02);
      padding: 10px 14px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      transition: all 0.15s ease;
      cursor: pointer;
    }}

    .upload-box:hover {{
      border-color: var(--accent);
      background: rgba(91, 209, 218, 0.05);
    }}

    .upload-info {{
      display: flex;
      align-items: center;
      gap: 10px;
    }}

    .upload-thumb {{
      width: 32px;
      height: 32px;
      border-radius: 4px;
      border: 1px solid var(--border-color);
      background: #000;
      object-fit: contain;
      image-rendering: pixelated;
    }}

    .upload-text {{
      font-size: 0.8rem;
    }}

    .upload-text small {{
      color: var(--text-muted);
      display: block;
      font-size: 0.7rem;
    }}

    .upload-actions {{
      display: flex;
      gap: 6px;
    }}

    .upload-actions button {{
      padding: 4px 8px;
      font-size: 0.72rem;
    }}

    /* Color Grid */
    .color-grid {{
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 10px;
    }}

    .color-item {{
      background: var(--bg-subpanel);
      border: 1px solid var(--border-color);
      border-radius: 6px;
      padding: 8px 10px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }}

    .color-meta {{
      flex: 1;
      overflow: hidden;
    }}

    .color-meta .color-name {{
      font-size: 0.78rem;
      font-weight: 600;
      white-space: nowrap;
      text-overflow: ellipsis;
      overflow: hidden;
    }}

    .color-meta .color-sub {{
      font-size: 0.68rem;
      color: var(--text-muted);
    }}

    .color-inputs {{
      display: flex;
      align-items: center;
      gap: 6px;
    }}

    .color-picker-wrap {{
      position: relative;
      width: 26px;
      height: 26px;
      border-radius: 4px;
      border: 1px solid var(--border-color);
      overflow: hidden;
      cursor: pointer;
    }}

    .color-picker-wrap input[type="color"] {{
      position: absolute;
      top: -10px;
      left: -10px;
      width: 50px;
      height: 50px;
      border: none;
      cursor: pointer;
    }}

    .hex-input {{
      width: 76px;
      font-family: monospace;
      font-size: 0.74rem;
      text-align: center;
      padding: 4px 6px;
    }}

    /* Right Preview Area */
    .preview-pane {{
      flex: 1;
      background: radial-gradient(circle at center, #1b2028 0%, #0d1015 100%);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      position: relative;
      overflow: hidden;
      padding: 20px;
    }}

    .preview-toolbar {{
      position: absolute;
      top: 15px;
      display: flex;
      align-items: center;
      gap: 12px;
      background: rgba(22, 27, 34, 0.85);
      backdrop-filter: blur(8px);
      border: 1px solid var(--border-color);
      border-radius: 20px;
      padding: 5px 14px;
      z-index: 10;
    }}

    .mode-pills {{
      display: flex;
      gap: 4px;
    }}

    .mode-btn {{
      border: none;
      background: transparent;
      color: var(--text-muted);
      border-radius: 12px;
      padding: 4px 10px;
      font-size: 0.75rem;
      font-weight: 600;
    }}

    .mode-btn.active {{
      background: var(--accent);
      color: #0e1117;
    }}

    .battery-control {{
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.75rem;
      color: var(--text-muted);
    }}

    .battery-control input[type="range"] {{
      width: 70px;
      cursor: pointer;
    }}

    /* Phone Stage */
    .phone-stage {{
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      user-select: none;
      transition: transform 0.1s ease;
    }}

    canvas#phoneCanvas {{
      image-rendering: pixelated;
      image-rendering: crisp-edges;
      border-radius: 8px;
      box-shadow: 0 15px 35px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255, 255, 255, 0.05);
      cursor: pointer;
    }}

    .minecraft-tooltip {{
      position: absolute;
      pointer-events: none;
      display: none;
      background: rgba(16, 0, 16, 0.94);
      border: 1px solid #2a004f;
      box-shadow: 0 0 0 1px #4e197d, 2px 2px 4px rgba(0,0,0,0.6);
      color: #FFFFFF;
      font-family: monospace, sans-serif;
      font-size: 11px;
      font-weight: bold;
      padding: 4px 8px;
      border-radius: 2px;
      z-index: 100;
      white-space: nowrap;
    }}

    /* JSON code preview */
    pre#jsonOutput {{
      background: var(--bg-dark);
      border: 1px solid var(--border-color);
      border-radius: 6px;
      padding: 12px;
      font-family: monospace;
      font-size: 0.78rem;
      color: #a5d6ff;
      max-height: 480px;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-all;
    }}

    .preset-pill {{
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 10px;
      background: var(--bg-subpanel);
      border: 1px solid var(--border-color);
      border-radius: 16px;
      cursor: pointer;
      font-size: 0.75rem;
      font-weight: 500;
      transition: all 0.15s;
    }}

    .preset-pill:hover {{
      border-color: var(--accent);
      background: rgba(91, 209, 218, 0.1);
    }}

    .preset-dot {{
      width: 10px;
      height: 10px;
      border-radius: 50%;
    }}

    /* Scrollbars */
    ::-webkit-scrollbar {{
      width: 7px;
      height: 7px;
    }}
    ::-webkit-scrollbar-track {{
      background: transparent;
    }}
    ::-webkit-scrollbar-thumb {{
      background: var(--border-color);
      border-radius: 4px;
    }}
    ::-webkit-scrollbar-thumb:hover {{
      background: #484f58;
    }}
  </style>
</head>
<body>

  <header>
    <div class="header-title">
      <h1>📱 Cobblemon Smartphone <span class="badge">Theme Creator Studio</span></h1>
    </div>
    <div class="header-actions">
      <button onclick="document.getElementById('importFile').click()" title="Import existing theme.json">
        📥 Import JSON
      </button>
      <input type="file" id="importFile" accept=".json" style="display:none" onchange="importThemeJson(event)">
      
      <button class="primary" onclick="exportThemeJson()">
        💾 Export JSON
      </button>

      <button class="accent" onclick="exportResourcePackZip()">
        📦 Export Resource Pack (.ZIP)
      </button>

      <button onclick="exportSnapshotPng()">
        📸 Export Preview PNG
      </button>

      <button onclick="resetDefaults()" title="Reset to default cyan theme">
        🔄 Reset
      </button>
    </div>
  </header>

  <div class="app-body">
    <!-- Left Configuration Panel -->
    <div class="editor-pane">
      <div class="tab-bar">
        <button class="tab-btn active" onclick="switchTab('general')">⚙️ General</button>
        <button class="tab-btn" onclick="switchTab('wallpapers')">🖼️ Wallpaper & Frame</button>
        <button class="tab-btn" onclick="switchTab('colors')">🎨 Colors</button>
        <button class="tab-btn" onclick="switchTab('elements')">🔘 UI Elements</button>
        <button class="tab-btn" onclick="switchTab('apps')">📱 App Icons</button>
        <button class="tab-btn" onclick="switchTab('json')">📄 JSON Code</button>
      </div>

      <!-- Tab 1: General & Presets -->
      <div id="tab-general" class="tab-content active">
        <div class="section-title">✨ Quick Theme Presets</div>
        <div style="display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px;">
          <div class="preset-pill" onclick="loadPreset('default')">
            <span class="preset-dot" style="background: #5bd1da;"></span> Default Cyan
          </div>
          <div class="preset-pill" onclick="loadPreset('dark')">
            <span class="preset-dot" style="background: #1c1c22; border: 1px solid #5bd1da;"></span> Dark Mode
          </div>
          <div class="preset-pill" onclick="loadPreset('rotom')">
            <span class="preset-dot" style="background: #ea580c;"></span> Rotom Orange
          </div>
          <div class="preset-pill" onclick="loadPreset('neon')">
            <span class="preset-dot" style="background: #00ffcc;"></span> Cyberpunk Neon
          </div>
          <div class="preset-pill" onclick="loadPreset('emerald')">
            <span class="preset-dot" style="background: #10b981;"></span> Emerald Forest
          </div>
          <div class="preset-pill" onclick="loadPreset('gold')">
            <span class="preset-dot" style="background: #eab308;"></span> Luxury Gold
          </div>
          <div class="preset-pill" onclick="loadPreset('lavender')">
            <span class="preset-dot" style="background: #a855f7;"></span> Lavender Dream
          </div>
        </div>

        <div class="section-title">📋 Theme Information</div>
        <div class="form-group">
          <label>Theme ID</label>
          <input type="text" id="themeId" value="mypack:custom_theme" oninput="updateGeneralInfo()" style="width: 100%;">
          <div class="desc">Unique identifier format: <code>namespace:path</code> (e.g. <code>mypack:neon</code>).</div>
        </div>

        <div class="form-group">
          <label>Display Name</label>
          <input type="text" id="themeName" value="Custom Theme" oninput="updateGeneralInfo()" style="width: 100%;">
          <div class="desc">Literal name (e.g. <code>Neon Cyber</code>) or translation key (e.g. <code>theme.mypack.neon</code>).</div>
        </div>

        <div class="form-group">
          <label>Display Order (Priority)</label>
          <input type="number" id="themeOrder" value="50" oninput="updateGeneralInfo()" style="width: 120px;">
          <div class="desc">Order in the in-game Settings themes tab. Lower values appear first.</div>
        </div>
      </div>

      <!-- Tab 2: Wallpapers & Frame -->
      <div id="tab-wallpapers" class="tab-content">
        <div class="section-title">🖼️ Wallpaper Textures</div>
        
        <div class="form-group">
          <label>Normal Wallpaper (131 × 207 px)</label>
          <div class="upload-box" onclick="triggerUpload('wallpaper')">
            <div class="upload-info">
              <img id="wallpaperThumb" class="upload-thumb" src="" alt="">
              <div class="upload-text">
                <span id="wallpaperLabel">Using Built-in Home Screen Wallpaper</span>
                <small>Recommended: 131 × 207 px PNG</small>
              </div>
            </div>
            <div class="upload-actions">
              <button onclick="event.stopPropagation(); triggerUpload('wallpaper')">Upload</button>
              <button onclick="event.stopPropagation(); clearUpload('wallpaper')">Reset</button>
            </div>
          </div>
          <input type="file" id="file_wallpaper" accept="image/png" style="display:none" onchange="handleImageUpload(event, 'wallpaper')">
        </div>

        <div class="form-group">
          <label>Large Wallpaper (211 × 207 px) — PokeInfo, Social, GPS</label>
          <div class="upload-box" onclick="triggerUpload('large_wallpaper')">
            <div class="upload-info">
              <img id="largeWallpaperThumb" class="upload-thumb" src="" alt="">
              <div class="upload-text">
                <span id="largeWallpaperLabel">Using Built-in Large Screen Wallpaper</span>
                <small>Recommended: 211 × 207 px PNG</small>
              </div>
            </div>
            <div class="upload-actions">
              <button onclick="event.stopPropagation(); triggerUpload('large_wallpaper')">Upload</button>
              <button onclick="event.stopPropagation(); clearUpload('large_wallpaper')">Reset</button>
            </div>
          </div>
          <input type="file" id="file_large_wallpaper" accept="image/png" style="display:none" onchange="handleImageUpload(event, 'large_wallpaper')">
        </div>

        <div class="section-title">📱 Phone Frame / Bezel Case</div>
        
        <div class="form-group">
          <label>Built-in Phone Frame Color</label>
          <select id="phoneColorSelect" onchange="changePhoneColor()" style="width: 100%;">
            <option value="red" selected>Red Smartphone</option>
            <option value="black">Black Smartphone</option>
            <option value="blue">Blue Smartphone</option>
            <option value="cyan">Cyan Smartphone</option>
            <option value="green">Green Smartphone</option>
            <option value="lime">Lime Smartphone</option>
            <option value="yellow">Yellow Smartphone</option>
            <option value="orange">Orange Smartphone</option>
            <option value="pink">Pink Smartphone</option>
            <option value="purple">Purple Smartphone</option>
            <option value="magenta">Magenta Smartphone</option>
            <option value="light_blue">Light Blue Smartphone</option>
            <option value="light_gray">Light Gray Smartphone</option>
            <option value="gray">Gray Smartphone</option>
            <option value="brown">Brown Smartphone</option>
            <option value="white">White Smartphone</option>
          </select>
          <div class="desc">Default frame texture dyed to match the Cobblemon Smartphone item color.</div>
        </div>

        <div class="form-group">
          <label>Custom Normal Frame Override (131 × 207 px)</label>
          <div class="upload-box" onclick="triggerUpload('frame')">
            <div class="upload-info">
              <img id="frameThumb" class="upload-thumb" src="" alt="">
              <div class="upload-text">
                <span id="frameLabel">None (uses item color above)</span>
                <small>Optional custom phone bezel texture</small>
              </div>
            </div>
            <div class="upload-actions">
              <button onclick="event.stopPropagation(); triggerUpload('frame')">Upload</button>
              <button onclick="event.stopPropagation(); clearUpload('frame')">Clear</button>
            </div>
          </div>
          <input type="file" id="file_frame" accept="image/png" style="display:none" onchange="handleImageUpload(event, 'frame')">
        </div>

        <div class="form-group">
          <label>Custom Large Frame Override (211 × 207 px)</label>
          <div class="upload-box" onclick="triggerUpload('large_frame')">
            <div class="upload-info">
              <img id="largeFrameThumb" class="upload-thumb" src="" alt="">
              <div class="upload-text">
                <span id="largeFrameLabel">None (uses item color above)</span>
                <small>Optional custom wide bezel texture</small>
              </div>
            </div>
            <div class="upload-actions">
              <button onclick="event.stopPropagation(); triggerUpload('large_frame')">Upload</button>
              <button onclick="event.stopPropagation(); clearUpload('large_frame')">Clear</button>
            </div>
          </div>
          <input type="file" id="file_large_frame" accept="image/png" style="display:none" onchange="handleImageUpload(event, 'large_frame')">
        </div>
      </div>

      <!-- Tab 3: Colors -->
      <div id="tab-colors" class="tab-content">
        <div class="section-title">⚡ Primary & Accents</div>
        <div class="color-grid" id="colors-accents"></div>

        <div class="section-title">🏛️ Surfaces & Backgrounds</div>
        <div class="color-grid" id="colors-surfaces"></div>

        <div class="section-title">📝 Typography & Labels</div>
        <div class="color-grid" id="colors-text"></div>

        <div class="section-title">🔋 Battery & Status Indicators</div>
        <div class="color-grid" id="colors-status"></div>
      </div>

      <!-- Tab 4: UI Elements -->
      <div id="tab-elements" class="tab-content">
        <div class="section-title">🔘 Control Buttons & Handles</div>
        <div class="desc" style="margin-bottom: 14px;">Upload custom textures for buttons and widgets. Sprite layout: top half = normal, bottom half = hover.</div>

        <div id="elements-list"></div>
      </div>

      <!-- Tab 5: App Icons -->
      <div id="tab-apps" class="tab-content">
        <div class="section-title">📱 App Icon Overrides</div>
        <div class="desc" style="margin-bottom: 14px;">Override 36 × 36 px application icon textures and optional hover states.</div>

        <div id="apps-list"></div>
      </div>

      <!-- Tab 6: JSON Output -->
      <div id="tab-json" class="tab-content">
        <div class="section-title" style="justify-content: space-between;">
          <span>📄 Live Theme JSON</span>
          <button onclick="copyJsonToClipboard()">📋 Copy to Clipboard</button>
        </div>
        <pre id="jsonOutput"></pre>
      </div>
    </div>

    <!-- Right Live Preview Stage -->
    <div class="preview-pane">
      <div class="preview-toolbar">
        <div class="mode-pills">
          <button class="mode-btn active" id="btnModeHome" onclick="setScreenMode('home')">Home Screen</button>
          <button class="mode-btn" id="btnModeSettings" onclick="setScreenMode('settings')">Settings</button>
          <button class="mode-btn" id="btnModeLarge" onclick="setScreenMode('large')">Large Screen</button>
        </div>

        <div class="battery-control">
          <span>Battery:</span>
          <input type="range" id="batterySlider" min="0" max="100" value="85" oninput="updateBatterySlider(this.value)">
          <span id="batteryPercentText" style="min-width: 32px; font-weight:600;">85%</span>
        </div>

        <div style="display:flex; align-items:center; gap:6px; font-size:0.75rem; color:var(--text-muted);">
          <span>Zoom:</span>
          <select id="zoomSelect" onchange="changeZoom(this.value)" style="padding: 2px 6px;">
            <option value="1">1x</option>
            <option value="2">2x</option>
            <option value="3" selected>3x</option>
            <option value="4">4x</option>
          </select>
        </div>
      </div>

      <div class="phone-stage" id="phoneStage" style="position: relative;">
        <canvas id="phoneCanvas"></canvas>
        <div id="phoneTooltip" class="minecraft-tooltip"></div>
      </div>
    </div>
  </div>

  <script>
    // Embedded asset catalog
    const ASSETS = {assets_json};

    // Default semantic color palette
    const DEFAULT_COLORS = {{
      "primary": "#5BD1DA",
      "primary_hover": "#7CE3EB",
      "primary_border": "#35454F",
      "surface": "#FFFFFF",
      "surface_alt": "#F5F5FA",
      "surface_hover": "#ECECF4",
      "shadow": "#00000044",
      "header_navy": "#111114",
      "header_navy_light": "#1A1A20",
      "text_primary": "#FFFFFF",
      "text_secondary": "#A0A0B0",
      "text_muted": "#686878",
      "text_dark": "#1A1A2E",
      "text_dark_muted": "#6E6E82",
      "gold": "#FFD700",
      "danger": "#FF4444",
      "success": "#3AAA55",
      "time": "#FFFFFF",
      "battery_border": "#5BD1DA",
      "battery_empty": "#1D4A59",
      "battery_high": "#22C55E",
      "battery_med": "#EAB308",
      "battery_low": "#EF4444"
    }};

    // Active theme state
    const state = {{
      id: "mypack:custom_theme",
      name: "Custom Theme",
      order: 50,
      phoneColor: "red",
      screenMode: "home", // "home" | "settings" | "large"
      zoom: 3,
      battery: 85,
      wallpaper: null,
      largeWallpaper: null,
      frame: null,
      largeFrame: null,
      colors: Object.assign({{}}, DEFAULT_COLORS),
      elements: {{
        home_button: null,
        prev_button: null,
        next_button: null,
        settings_button: null,
        drag_handle: null,
        view_select: null,
        social_pokeball: null
      }},
      appIcons: {{
        "cobblemon_smartphone:pokedex": {{ texture: null, hover: null, name: "Pokédex", iconKey: "pokedex.png" }},
        "cobblemon_smartphone:social": {{ texture: null, hover: null, name: "Social", iconKey: "social.png" }},
        "cobblemon_smartphone:gps": {{ texture: null, hover: null, name: "GPS", iconKey: "gps.png" }},
        "cobblemon_smartphone:pc": {{ texture: null, hover: null, name: "PC", iconKey: "pc.png" }},
        "cobblemon_smartphone:heal": {{ texture: null, hover: null, name: "Heal", iconKey: "heal.png" }},
        "cobblemon_smartphone:structure_compass": {{ texture: null, hover: null, name: "Compass", iconKey: "structure_compass.png" }}
      }},
      hoveredAppId: null,
      hoveredSettings: false,
      hoveredBattery: false,
      hoveredFooter: null,
      hoveredBack: false,
      settingsTab: "apps"
    }};

    // 3x5 Crisp Pixel Art Font Engine (Zero Anti-Aliasing)
    const PIXEL_FONT = {{
      ' ': [0,0,0,0,0],
      '0': [7,5,5,5,7],
      '1': [2,6,2,2,7],
      '2': [7,1,7,4,7],
      '3': [7,1,7,1,7],
      '4': [5,5,7,1,1],
      '5': [7,4,7,1,7],
      '6': [7,4,7,5,7],
      '7': [7,1,2,2,2],
      '8': [7,5,7,5,7],
      '9': [7,5,7,1,7],
      ':': [0,2,0,2,0],
      '<': [1,2,4,2,1],
      '>': [4,2,1,2,4],
      '-': [0,0,7,0,0],
      '+': [0,2,7,2,0],
      '.': [0,0,0,0,2],
      ',': [0,0,0,2,4],
      '!': [2,2,2,0,2],
      '?': [7,1,3,0,2],
      '/': [1,1,2,4,4],
      '%': [5,1,2,4,5],
      '(': [2,4,4,4,2],
      ')': [2,1,1,1,2],
      'A': [2,5,7,5,5],
      'B': [6,5,6,5,6],
      'C': [3,4,4,4,3],
      'D': [6,5,5,5,6],
      'E': [7,4,6,4,7],
      'F': [7,4,6,4,4],
      'G': [3,4,5,5,3],
      'H': [5,5,7,5,5],
      'I': [7,2,2,2,7],
      'J': [1,1,1,5,2],
      'K': [5,6,4,6,5],
      'L': [4,4,4,4,7],
      'M': [5,7,5,5,5],
      'N': [6,5,5,5,5],
      'O': [2,5,5,5,2],
      'P': [6,5,6,4,4],
      'Q': [2,5,5,6,3],
      'R': [6,5,6,5,5],
      'S': [3,4,2,1,6],
      'T': [7,2,2,2,2],
      'U': [5,5,5,5,7],
      'V': [5,5,5,5,2],
      'W': [5,5,5,7,5],
      'X': [5,5,2,5,5],
      'Y': [5,5,2,2,2],
      'Z': [7,1,2,4,7]
    }};

    function drawPixelText(ctx, text, startX, startY, color, align = 'left', spacing = 1) {{
      if (!text) return;
      ctx.fillStyle = color;
      const chars = String(text).toUpperCase().split('');
      let totalW = 0;
      for (let ch of chars) {{
        const w = (ch === ':' || ch === '.' || ch === '!') ? 1 : (ch === ' ' ? 2 : 3);
        totalW += w + spacing;
      }}
      totalW -= spacing;

      let curX = startX;
      if (align === 'center') curX -= Math.round(totalW / 2);
      else if (align === 'right') curX -= totalW;

      for (let ch of chars) {{
        const glyph = PIXEL_FONT[ch] || PIXEL_FONT[' '];
        const w = (ch === ':' || ch === '.' || ch === '!') ? 1 : (ch === ' ' ? 2 : 3);
        for (let r = 0; r < 5; r++) {{
          const rowBits = glyph[r] || 0;
          for (let col = 0; col < w; col++) {{
            if ((rowBits >> (w - 1 - col)) & 1) {{
              ctx.fillRect(curX + col, startY + r, 1, 1);
            }}
          }}
        }}
        curX += w + spacing;
      }}
    }}

    // Image Cache
    const imgCache = new Map();
    function getImage(src) {{
      if (!src) return null;
      if (imgCache.has(src)) return imgCache.get(src);
      const img = new Image();
      img.src = src;
      img.onload = () => renderCanvas();
      imgCache.set(src, img);
      return img;
    }}

    // Preload defaults
    function preloadAssets() {{
      Object.values(ASSETS.colors_small).forEach(getImage);
      Object.values(ASSETS.colors_large).forEach(getImage);
      Object.values(ASSETS.elements).forEach(getImage);
      Object.values(ASSETS.buttons).forEach(getImage);
      Object.values(ASSETS.wallpapers).forEach(getImage);
    }}

    // Tab Navigation
    function switchTab(tabId) {{
      document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
      document.querySelectorAll(".tab-content").forEach(c => c.classList.remove("active"));
      event.target.classList.add("active");
      document.getElementById("tab-" + tabId).classList.add("active");
    }}

    function setScreenMode(mode) {{
      state.screenMode = mode;
      document.querySelectorAll(".mode-btn").forEach(b => b.classList.remove("active"));
      if (mode === "home") document.getElementById("btnModeHome").classList.add("active");
      if (mode === "settings") document.getElementById("btnModeSettings").classList.add("active");
      if (mode === "large") document.getElementById("btnModeLarge").classList.add("active");

      applyZoom();
      renderCanvas();
    }}

    function changeZoom(val) {{
      state.zoom = parseInt(val);
      applyZoom();
    }}

    function applyZoom() {{
      const canvas = document.getElementById("phoneCanvas");
      const isLarge = state.screenMode === "large";
      const baseW = isLarge ? 211 : 131;
      const baseH = 207;
      const zoom = state.zoom || 3;

      canvas.width = baseW * zoom;
      canvas.height = baseH * zoom;
      canvas.style.width = (baseW * zoom) + "px";
      canvas.style.height = (baseH * zoom) + "px";
    }}

    function changePhoneColor() {{
      state.phoneColor = document.getElementById("phoneColorSelect").value;
      renderCanvas();
      updateJson();
    }}

    function updateBatterySlider(val) {{
      state.battery = parseInt(val);
      document.getElementById("batteryPercentText").innerText = val + "%";
      renderCanvas();
    }}

    function updateGeneralInfo() {{
      state.id = document.getElementById("themeId").value.trim() || "mypack:custom_theme";
      state.name = document.getElementById("themeName").value.trim() || "Custom Theme";
      state.order = parseInt(document.getElementById("themeOrder").value) || 50;
      updateJson();
    }}

    // File Upload handling
    function triggerUpload(type) {{
      const input = document.getElementById("file_" + type);
      if (input) input.click();
    }}

    function clearUpload(type) {{
      if (type === "wallpaper") state.wallpaper = null;
      if (type === "large_wallpaper") state.largeWallpaper = null;
      if (type === "frame") state.frame = null;
      if (type === "large_frame") state.largeFrame = null;
      if (state.elements[type] !== undefined) state.elements[type] = null;
      updateUploadUI(type, null);
      renderCanvas();
      updateJson();
    }}

    function handleImageUpload(e, type) {{
      const file = e.target.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (ev) => {{
        const dataUrl = ev.target.result;
        if (type === "wallpaper") state.wallpaper = dataUrl;
        else if (type === "large_wallpaper") state.largeWallpaper = dataUrl;
        else if (type === "frame") state.frame = dataUrl;
        else if (type === "large_frame") state.largeFrame = dataUrl;
        else if (state.elements[type] !== undefined) state.elements[type] = dataUrl;
        else if (type.startsWith("app_")) {{
          const [_, appId, stateType] = type.split("#");
          if (state.appIcons[appId]) {{
            state.appIcons[appId][stateType] = dataUrl;
          }}
        }}

        updateUploadUI(type, dataUrl);
        renderCanvas();
        updateJson();
      }};
      reader.readAsDataURL(file);
    }}

    function updateUploadUI(type, url) {{
      if (type === "wallpaper") {{
        document.getElementById("wallpaperThumb").src = url || ASSETS.wallpapers["home_screen.png"];
        document.getElementById("wallpaperLabel").innerText = url ? "Custom Wallpaper Uploaded" : "Using Built-in Home Screen Wallpaper";
      }} else if (type === "large_wallpaper") {{
        document.getElementById("largeWallpaperThumb").src = url || ASSETS.wallpapers["large_screen.png"];
        document.getElementById("largeWallpaperLabel").innerText = url ? "Custom Large Wallpaper Uploaded" : "Using Built-in Large Screen Wallpaper";
      }} else if (type === "frame") {{
        document.getElementById("frameThumb").src = url || "";
        document.getElementById("frameLabel").innerText = url ? "Custom Phone Frame Uploaded" : "None (uses item color above)";
      }} else if (type === "large_frame") {{
        document.getElementById("largeFrameThumb").src = url || "";
        document.getElementById("largeFrameLabel").innerText = url ? "Custom Large Frame Uploaded" : "None (uses item color above)";
      }} else if (state.elements[type] !== undefined) {{
        const thumb = document.getElementById("elemThumb_" + type);
        if (thumb) thumb.src = url || ASSETS.elements[type + ".png"] || "";
      }}
    }}

    // Render Canvas Preview
    function renderCanvas() {{
      const canvas = document.getElementById("phoneCanvas");
      const ctx = canvas.getContext("2d");
      const isLarge = state.screenMode === "large";
      const baseW = isLarge ? 211 : 131;
      const baseH = 207;
      const zoom = state.zoom || 3;

      if (canvas.width !== baseW * zoom || canvas.height !== baseH * zoom) {{
        canvas.width = baseW * zoom;
        canvas.height = baseH * zoom;
        canvas.style.width = (baseW * zoom) + "px";
        canvas.style.height = (baseH * zoom) + "px";
      }}

      ctx.save();
      ctx.imageSmoothingEnabled = false;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.scale(zoom, zoom);

      // 1. Draw Wallpaper
      let wpImg = null;
      if (isLarge) {{
        wpImg = getImage(state.largeWallpaper) || getImage(ASSETS.wallpapers["large_screen.png"]);
      }} else {{
        wpImg = getImage(state.wallpaper) || getImage(ASSETS.wallpapers["home_screen.png"]);
      }}

      if (wpImg && wpImg.complete) {{
        ctx.drawImage(wpImg, 0, 0, baseW, baseH);
      }} else {{
        ctx.fillStyle = state.colors.header_navy || "#111114";
        ctx.fillRect(0, 0, baseW, baseH);
      }}

      // 2. Draw Screen Content by mode
      if (state.screenMode === "home") {{
        renderHomeScreen(ctx);
      }} else if (state.screenMode === "settings") {{
        renderSettingsScreen(ctx);
      }} else if (state.screenMode === "large") {{
        renderLargeScreen(ctx);
      }}

      // 3. Draw Phone Frame Bezel on top
      let frameImg = null;
      if (isLarge) {{
        frameImg = getImage(state.largeFrame) || getImage(ASSETS.colors_large[state.phoneColor]);
      }} else {{
        frameImg = getImage(state.frame) || getImage(ASSETS.colors_small[state.phoneColor]);
      }}

      if (frameImg && frameImg.complete) {{
        ctx.drawImage(frameImg, 0, 0, baseW, baseH);
      }}

      ctx.restore();
    }}

    function renderHomeScreen(ctx) {{
      // 1. Clock (at 20, 20 with pixel font)
      drawPixelText(ctx, "12:00", 20, 20, state.colors.time || "#FFFFFF");

      // 2. Header Settings button (at 80, 17, 9x9)
      const settingsImg = getImage(state.elements.settings_button) || getImage(ASSETS.elements["settings_button.png"]);
      if (settingsImg && settingsImg.complete) {{
        const sy = state.hoveredSettings ? 11 : 0;
        ctx.drawImage(settingsImg, 0, sy, 11, 11, 80, 17, 9, 9);
      }}

      // 3. Battery Icon (at 101, 19 - perfectly aligned with home_screen.png battery)
      renderBattery(ctx, 101, 19);

      // 4. App Icons (2 columns x 3 rows) - CLEAN, NO TEXT ON TOP!
      const apps = Object.entries(state.appIcons);
      const startX = 26;
      const startY = 37;
      const spacingX = 43;
      const spacingY = 43;

      apps.slice(0, 6).forEach(([appId, item], index) => {{
        const col = index % 2;
        const row = Math.floor(index / 2);
        const x = startX + col * spacingX;
        const y = startY + row * spacingY;

        const isHover = state.hoveredAppId === appId;
        const appImg = (isHover && item.hover) ? getImage(item.hover) : (getImage(item.texture) || getImage(ASSETS.buttons[item.iconKey]));

        if (appImg && appImg.complete) {{
          ctx.drawImage(appImg, x, y, 36, 36);
        }}
      }});

      // 5. Page Dots (at 65, 169)
      renderPageDots(ctx, 131 / 2, 169);

      // 6. Footer Buttons
      renderFooterButtons(ctx);
    }}

    function renderSettingsScreen(ctx) {{
      // Back button at (20, 20)
      const backHover = state.hoveredBack;
      const backColor = backHover ? (state.colors.gold || "#FFD700") : (state.colors.text_primary || "#FFFFFF");
      drawPixelText(ctx, "< BACK", 20, 20, backColor);

      // Battery at (101, 19)
      renderBattery(ctx, 101, 19);

      // Tabs: [ Apps ] [ Themes ]
      const isApps = state.settingsTab === "apps";
      const activeBg = hexToRgba(state.colors.primary, 0.25);

      // Apps Tab
      if (isApps) {{
        ctx.fillStyle = activeBg;
        ctx.fillRect(22, 26, 40, 10);
        ctx.fillStyle = state.colors.primary || "#5BD1DA";
        ctx.fillRect(22, 35, 40, 1);
      }} else {{
        ctx.fillStyle = "rgba(255, 255, 255, 0.05)";
        ctx.fillRect(22, 26, 40, 10);
      }}
      drawPixelText(ctx, "APPS", 42, 29, isApps ? (state.colors.text_primary || "#FFFFFF") : (state.colors.text_muted || "#686878"), "center");

      // Themes Tab
      if (!isApps) {{
        ctx.fillStyle = activeBg;
        ctx.fillRect(66, 26, 42, 10);
        ctx.fillStyle = state.colors.primary || "#5BD1DA";
        ctx.fillRect(66, 35, 42, 1);
      }} else {{
        ctx.fillStyle = "rgba(255, 255, 255, 0.05)";
        ctx.fillRect(66, 26, 42, 10);
      }}
      drawPixelText(ctx, "THEMES", 87, 29, !isApps ? (state.colors.text_primary || "#FFFFFF") : (state.colors.text_muted || "#686878"), "center");

      // Hint text below tabs at y = 38
      drawPixelText(ctx, "RIGHT-CLICK AN APP", 131 / 2, 38, state.colors.text_secondary || "#A0A0B0", "center");

      // Grid of apps with drag handles at y = 46
      const apps = Object.entries(state.appIcons);
      const startX = 26;
      const startY = 46;
      const spacingX = 43;
      const spacingY = 41;

      const dragImg = getImage(state.elements.drag_handle) || getImage(ASSETS.elements["drag_handle.png"]);
      const viewImg = getImage(state.elements.view_select) || getImage(ASSETS.elements["view_select.png"]);

      apps.slice(0, 6).forEach(([appId, item], index) => {{
        const col = index % 2;
        const row = Math.floor(index / 2);
        const x = startX + col * spacingX;
        const y = startY + row * spacingY;

        const appImg = getImage(item.texture) || getImage(ASSETS.buttons[item.iconKey]);
        if (appImg && appImg.complete) {{
          ctx.drawImage(appImg, x, y, 36, 36);
        }}

        // Drag handle (top right of icon)
        if (dragImg && dragImg.complete) {{
          ctx.drawImage(dragImg, 0, 0, 8, 8, x + 24, y + 4, 8, 8);
        }}

        // View select eye (bottom right of icon)
        if (viewImg && viewImg.complete) {{
          ctx.drawImage(viewImg, 0, 0, 8, 8, x + 24, y + 24, 8, 8);
        }}
      }});

      renderPageDots(ctx, 131 / 2, 171);
      renderFooterButtons(ctx);
    }}

    function renderLargeScreen(ctx) {{
      // Header bar
      ctx.fillStyle = state.colors.header_navy || "#111114";
      ctx.fillRect(20, 15, 171, 20);

      // Social pokeball
      const ballImg = getImage(state.elements.social_pokeball) || getImage(ASSETS.elements["social_pokeball.png"]);
      if (ballImg && ballImg.complete) {{
        ctx.drawImage(ballImg, 24, 19, 12, 12);
      }} else {{
        ctx.fillStyle = state.colors.primary || "#5BD1DA";
        ctx.fillRect(24, 19, 12, 12);
      }}

      drawPixelText(ctx, "SOCIAL FEED", 40, 23, state.colors.text_primary || "#FFFFFF");

      // Battery at (181, 17)
      renderBattery(ctx, 181, 17);

      // Two themed mock feed cards
      drawCard(ctx, 24, 42, 163, 56, "TRAINER RED", "CAUGHT A WILD PIKACHU NEAR VIRIDIAN FOREST!", "#3AAA55");
      drawCard(ctx, 24, 104, 163, 56, "PROFESSOR OAK", "REMEMBER TO UPDATE YOUR POKEDEX TODAY!", state.colors.primary);

      renderFooterButtons(ctx, 211);
    }}

    function drawCard(ctx, x, y, w, h, author, text, accentColor) {{
      // Card surface
      ctx.fillStyle = state.colors.surface || "#FFFFFF";
      ctx.fillRect(x, y, w, h);

      // Top colored accent line
      ctx.fillStyle = accentColor || state.colors.primary || "#5BD1DA";
      ctx.fillRect(x, y, w, 2);

      // Author text (pixel font)
      drawPixelText(ctx, author, x + 6, y + 8, state.colors.text_dark || "#1A1A2E");

      // Body text lines (pixel font)
      const line1 = text.slice(0, 26);
      const line2 = text.slice(26, 52);
      drawPixelText(ctx, line1, x + 6, y + 18, state.colors.text_dark_muted || "#6E6E82");
      if (line2) {{
        drawPixelText(ctx, line2, x + 6, y + 26, state.colors.text_dark_muted || "#6E6E82");
      }}

      // Interaction pill
      ctx.fillStyle = state.colors.surface_alt || "#F5F5FA";
      ctx.fillRect(x + 6, y + 38, 36, 11);
      drawPixelText(ctx, "LIKE 12", x + 9, y + 41, state.colors.text_dark_muted || "#6E6E82");
    }}

    function renderBattery(ctx, x, y) {{
      const bBorder = state.colors.battery_border || "#5BD1DA";
      const bEmpty = state.colors.battery_empty || "#1D4A59";
      let bFill = state.colors.battery_high || "#22C55E";
      if (state.battery <= 20) bFill = state.colors.battery_low || "#EF4444";
      else if (state.battery <= 50) bFill = state.colors.battery_med || "#EAB308";

      // 9x5 body outer border
      ctx.fillStyle = bBorder;
      ctx.fillRect(x, y, 9, 1);
      ctx.fillRect(x, y + 4, 9, 1);
      ctx.fillRect(x, y + 1, 1, 3);
      ctx.fillRect(x + 8, y + 1, 1, 3);

      // 1x3 tip at x + 9, y + 1
      ctx.fillRect(x + 9, y + 1, 1, 3);

      // 7x3 empty cavity
      ctx.fillStyle = bEmpty;
      ctx.fillRect(x + 1, y + 1, 7, 3);

      // Dynamic fill inside cavity
      const ratio = Math.max(0, Math.min(100, state.battery)) / 100;
      const fillW = state.battery > 0 ? Math.max(1, Math.round(7 * ratio)) : 0;
      if (fillW > 0) {{
        ctx.fillStyle = bFill;
        ctx.fillRect(x + 1, y + 1, fillW, 3);
      }}

      // If charging, draw small 1px/2px lightning bolt indicator
      if (state.batteryCharging) {{
        ctx.fillStyle = "#FFFFFF";
        ctx.fillRect(x + 4, y + 1, 1, 1);
        ctx.fillRect(x + 3, y + 2, 2, 1);
        ctx.fillRect(x + 4, y + 3, 1, 1);
      }}
    }}

    function renderPageDots(ctx, centerX, y) {{
      const dotW = 9;
      const dotHActive = 9;
      const dotHInactive = 7;
      const spacing = 2;
      const totalW = 3 * dotW + 2 * spacing;
      const startX = Math.round(centerX - totalW / 2);

      for (let i = 0; i < 3; i++) {{
        const active = i === 0;
        ctx.fillStyle = active ? (state.colors.primary || "#5BD1DA") : (state.colors.text_muted || "#686878");
        ctx.fillRect(startX + i * (dotW + spacing), y + (active ? 0 : 1), dotW, active ? dotHActive : dotHInactive);
      }}
    }}

    function renderFooterButtons(ctx, totalWidth = 131) {{
      const homeImg = getImage(state.elements.home_button) || getImage(ASSETS.elements["home_button.png"]);
      const prevImg = getImage(state.elements.prev_button) || getImage(ASSETS.elements["prev_button.png"]);
      const nextImg = getImage(state.elements.next_button) || getImage(ASSETS.elements["next_button.png"]);

      const homeX = totalWidth === 131 ? 62 : Math.round(totalWidth / 2 - 3.5);
      const prevX = totalWidth === 131 ? 36 : (homeX - 26);
      const nextX = totalWidth === 131 ? 88 : (homeX + 26);
      const y = 187;

      const prevY = state.hoveredFooter === "prev" ? 7 : 0;
      const homeY = state.hoveredFooter === "home" ? 7 : 0;
      const nextY = state.hoveredFooter === "next" ? 7 : 0;

      if (prevImg && prevImg.complete) ctx.drawImage(prevImg, 0, prevY, 7, 7, prevX, y, 7, 7);
      if (homeImg && homeImg.complete) ctx.drawImage(homeImg, 0, homeY, 7, 7, homeX, y, 7, 7);
      if (nextImg && nextImg.complete) ctx.drawImage(nextImg, 0, nextY, 7, 7, nextX, y, 7, 7);
    }}

    // Generate JSON string
    function generateThemeJson() {{
      const json = {{
        id: state.id,
        name: state.name
      }};

      if (state.wallpaper) {{
        const filename = state.id.split(":").pop() + ".png";
        json.wallpaper = state.id.split(":")[0] + ":textures/gui/wallpapers/" + filename;
      }} else {{
        json.wallpaper = "cobblemon_smartphone:textures/gui/home_screen.png";
      }}

      if (state.largeWallpaper) {{
        const filename = state.id.split(":").pop() + "_large.png";
        json.large_wallpaper = state.id.split(":")[0] + ":textures/gui/wallpapers/" + filename;
      }}

      if (state.frame) {{
        json.frame = state.id.split(":")[0] + ":textures/gui/frame.png";
      }}

      if (state.largeFrame) {{
        json.large_frame = state.id.split(":")[0] + ":textures/gui/large_frame.png";
      }}

      // Elements
      const elemObj = {{}};
      Object.entries(state.elements).forEach(([k, v]) => {{
        if (v) {{
          elemObj[k] = state.id.split(":")[0] + ":textures/gui/elements/" + k + ".png";
        }}
      }});
      if (Object.keys(elemObj).length > 0) json.elements = elemObj;

      // App Icons
      const appsObj = {{}};
      Object.entries(state.appIcons).forEach(([appId, item]) => {{
        if (item.texture && item.hover) {{
          const cleanApp = appId.split(":").pop();
          appsObj[appId] = {{
            texture: state.id.split(":")[0] + ":textures/gui/apps/" + cleanApp + ".png",
            hover: state.id.split(":")[0] + ":textures/gui/apps/" + cleanApp + "_hover.png"
          }};
        }} else if (item.texture) {{
          const cleanApp = appId.split(":").pop();
          appsObj[appId] = state.id.split(":")[0] + ":textures/gui/apps/" + cleanApp + ".png";
        }}
      }});
      if (Object.keys(appsObj).length > 0) json.app_icons = appsObj;

      // Colors
      const colorsObj = {{}};
      Object.entries(state.colors).forEach(([k, v]) => {{
        if (v && v.toUpperCase() !== DEFAULT_COLORS[k]?.toUpperCase()) {{
          colorsObj[k] = v.toUpperCase();
        }}
      }});
      // If none changed, still include primary and time
      if (Object.keys(colorsObj).length === 0) {{
        colorsObj.primary = state.colors.primary;
        colorsObj.time = state.colors.time;
      }} else {{
        colorsObj.primary = state.colors.primary;
      }}
      json.colors = colorsObj;

      json.order = state.order;

      return JSON.stringify(json, null, 2);
    }}

    function updateJson() {{
      const out = document.getElementById("jsonOutput");
      if (out) out.innerText = generateThemeJson();
    }}

    function copyJsonToClipboard() {{
      navigator.clipboard.writeText(generateThemeJson()).then(() => {{
        alert("Theme JSON copied to clipboard!");
      }});
    }}

    // Export JSON File
    function exportThemeJson() {{
      const jsonText = generateThemeJson();
      const filename = (state.id.split(":").pop() || "theme") + ".json";
      const blob = new Blob([jsonText], {{ type: "application/json" }});
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
    }}

    // Export Preview PNG Snapshot
    function exportSnapshotPng() {{
      const canvas = document.getElementById("phoneCanvas");
      const url = canvas.toDataURL("image/png");
      const a = document.createElement("a");
      a.href = url;
      a.download = (state.id.split(":").pop() || "theme") + "_preview.png";
      a.click();
    }}

    // Export Full Resource Pack ZIP
    async function exportResourcePackZip() {{
      if (typeof JSZip === "undefined") {{
        alert("JSZip library could not be loaded. Downloading JSON instead!");
        exportThemeJson();
        return;
      }}

      const zip = new JSZip();
      const namespace = state.id.split(":")[0] || "custom_theme";
      const baseId = state.id.split(":").pop() || "theme";

      // 1. pack.mcmeta
      const mcmeta = {{
        pack: {{
          pack_format: 34,
          description: "Cobblemon Smartphone Theme - " + state.name
        }}
      }};
      zip.file("pack.mcmeta", JSON.stringify(mcmeta, null, 2));

      // 2. pack.png snapshot (128x128 square Minecraft pack icon)
      const canvas = document.getElementById("phoneCanvas");
      const packCanvas = document.createElement("canvas");
      packCanvas.width = 128;
      packCanvas.height = 128;
      const pCtx = packCanvas.getContext("2d");
      pCtx.imageSmoothingEnabled = false;
      pCtx.fillStyle = state.colors.header_navy || "#111114";
      pCtx.fillRect(0, 0, 128, 128);
      const isLg = state.screenMode === "large";
      const aspect = isLg ? (211 / 207) : (131 / 207);
      const phoneW = Math.round(120 * aspect);
      const phoneX = Math.round((128 - phoneW) / 2);
      pCtx.drawImage(canvas, phoneX, 4, phoneW, 120);
      const canvasData = packCanvas.toDataURL("image/png").replace(/^data:image\/png;base64,/, "");
      zip.file("pack.png", canvasData, {{ base64: true }});

      // 3. Theme JSON
      const jsonContent = generateThemeJson();
      zip.file("assets/" + namespace + "/themes/" + baseId + ".json", jsonContent);

      // Helper to convert dataURL to base64 string
      function b64FromUrl(url) {{
        return url.replace(/^data:image\/[a-z]+;base64,/, "");
      }}

      // 4. Wallpaper files
      if (state.wallpaper) {{
        zip.file("assets/" + namespace + "/textures/gui/wallpapers/" + baseId + ".png", b64FromUrl(state.wallpaper), {{ base64: true }});
      }}
      if (state.largeWallpaper) {{
        zip.file("assets/" + namespace + "/textures/gui/wallpapers/" + baseId + "_large.png", b64FromUrl(state.largeWallpaper), {{ base64: true }});
      }}

      // 5. Frame files
      if (state.frame) {{
        zip.file("assets/" + namespace + "/textures/gui/frame.png", b64FromUrl(state.frame), {{ base64: true }});
      }}
      if (state.largeFrame) {{
        zip.file("assets/" + namespace + "/textures/gui/large_frame.png", b64FromUrl(state.largeFrame), {{ base64: true }});
      }}

      // 6. UI Element files
      Object.entries(state.elements).forEach(([k, dataUrl]) => {{
        if (dataUrl) {{
          zip.file("assets/" + namespace + "/textures/gui/elements/" + k + ".png", b64FromUrl(dataUrl), {{ base64: true }});
        }}
      }});

      // 7. App Icons
      Object.entries(state.appIcons).forEach(([appId, item]) => {{
        const cleanApp = appId.split(":").pop();
        if (item.texture) {{
          zip.file("assets/" + namespace + "/textures/gui/apps/" + cleanApp + ".png", b64FromUrl(item.texture), {{ base64: true }});
        }}
        if (item.hover) {{
          zip.file("assets/" + namespace + "/textures/gui/apps/" + cleanApp + "_hover.png", b64FromUrl(item.hover), {{ base64: true }});
        }}
      }});

      // Generate & Download ZIP
      const content = await zip.generateAsync({{ type: "blob" }});
      const url = URL.createObjectURL(content);
      const a = document.createElement("a");
      a.href = url;
      a.download = baseId + "_resourcepack.zip";
      a.click();
      URL.revokeObjectURL(url);
    }}

    // Import existing theme JSON
    function importThemeJson(e) {{
      const file = e.target.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (ev) => {{
        try {{
          const json = JSON.parse(ev.target.result);
          if (json.id) {{
            state.id = json.id;
            document.getElementById("themeId").value = json.id;
          }}
          if (json.name) {{
            state.name = json.name;
            document.getElementById("themeName").value = json.name;
          }}
          if (json.order !== undefined) {{
            state.order = json.order;
            document.getElementById("themeOrder").value = json.order;
          }}
          if (json.colors) {{
            Object.assign(state.colors, json.colors);
            initColorPickers();
          }}
          renderCanvas();
          updateJson();
          alert("Theme imported successfully!");
        }} catch (err) {{
          alert("Failed to parse JSON file: " + err.message);
        }}
      }};
      reader.readAsText(file);
    }}

    // Preset configurations
    const PRESETS = {{
      default: {{
        id: "cobblemon_smartphone:default",
        name: "Default Theme",
        phoneColor: "red",
        colors: Object.assign({{}}, DEFAULT_COLORS)
      }},
      dark: {{
        id: "cobblemon_smartphone:dark",
        name: "Dark Theme",
        phoneColor: "black",
        colors: {{
          primary: "#5BD1DA",
          primary_hover: "#7CE3EB",
          primary_border: "#35454F",
          surface: "#1C1C22",
          surface_alt: "#22222A",
          surface_hover: "#2E2E38",
          header_navy: "#111114",
          header_navy_light: "#1A1A20",
          text_primary: "#FFFFFF",
          text_secondary: "#A0A0B0",
          text_muted: "#686878",
          text_dark: "#F0F0F5",
          text_dark_muted: "#A0A0B2",
          gold: "#FFD700",
          danger: "#FF4444",
          success: "#3AAA55",
          time: "#A0A0B0",
          battery_border: "#5BD1DA",
          battery_empty: "#111114",
          battery_high: "#48BB78",
          battery_med: "#E5B842",
          battery_low: "#E05252"
        }}
      }},
      rotom: {{
        id: "cobblemon_smartphone:rotom",
        name: "Rotom Orange",
        phoneColor: "orange",
        colors: {{
          primary: "#EA580C",
          primary_hover: "#F97316",
          primary_border: "#9A3412",
          surface: "#24140E",
          surface_alt: "#2D1912",
          surface_hover: "#3B2218",
          header_navy: "#160B07",
          header_navy_light: "#23120C",
          text_primary: "#FFFFFF",
          text_secondary: "#FFAA00",
          text_muted: "#A87E6F",
          text_dark: "#FED7AA",
          text_dark_muted: "#C29E8E",
          gold: "#FBBF24",
          danger: "#EF4444",
          success: "#2EB85C",
          time: "#FFAA00",
          battery_border: "#F97316",
          battery_empty: "#160B07",
          battery_high: "#2EB85C",
          battery_med: "#FBBF24",
          battery_low: "#EF4444"
        }}
      }},
      neon: {{
        id: "cyberpunk:neon_cyber",
        name: "Neon Cyber",
        phoneColor: "cyan",
        colors: {{
          primary: "#00FFCC",
          primary_hover: "#33FFDD",
          primary_border: "#00997A",
          surface: "#0D1117",
          surface_alt: "#161B22",
          surface_hover: "#21262D",
          header_navy: "#070B10",
          header_navy_light: "#0E1520",
          text_primary: "#FFFFFF",
          text_secondary: "#00FFCC",
          text_muted: "#58A6FF",
          text_dark: "#C9D1D9",
          text_dark_muted: "#8B949E",
          gold: "#FFD700",
          danger: "#FF4444",
          success: "#00FFAA",
          time: "#00FFCC",
          battery_border: "#00FFCC",
          battery_empty: "#070B10",
          battery_high: "#00FFAA",
          battery_med: "#FFCC00",
          battery_low: "#FF4444"
        }}
      }},
      emerald: {{
        id: "nature:forest_eco",
        name: "Emerald Forest",
        phoneColor: "green",
        colors: {{
          primary: "#10B981",
          primary_hover: "#34D399",
          primary_border: "#065F46",
          surface: "#062319",
          surface_alt: "#0A3326",
          surface_hover: "#0F4534",
          header_navy: "#04150F",
          header_navy_light: "#08291D",
          text_primary: "#FFFFFF",
          text_secondary: "#34D399",
          text_muted: "#6EE7B7",
          text_dark: "#D1FAE5",
          text_dark_muted: "#A7F3D0",
          gold: "#F59E0B",
          danger: "#EF4444",
          success: "#10B981",
          time: "#34D399",
          battery_border: "#10B981",
          battery_empty: "#04150F",
          battery_high: "#10B981",
          battery_med: "#F59E0B",
          battery_low: "#EF4444"
        }}
      }},
      gold: {{
        id: "luxury:royal_gold",
        name: "Royal Gold",
        phoneColor: "yellow",
        colors: {{
          primary: "#EAB308",
          primary_hover: "#FACC15",
          primary_border: "#854D0E",
          surface: "#1C1917",
          surface_alt: "#292524",
          surface_hover: "#3C3532",
          header_navy: "#0C0A09",
          header_navy_light: "#1C1917",
          text_primary: "#FEF08A",
          text_secondary: "#EAB308",
          text_muted: "#A1A1AA",
          text_dark: "#FEF9C3",
          text_dark_muted: "#CA8A04",
          gold: "#FACC15",
          danger: "#DC2626",
          success: "#16A34A",
          time: "#EAB308",
          battery_border: "#EAB308",
          battery_empty: "#0C0A09",
          battery_high: "#22C55E",
          battery_med: "#EAB308",
          battery_low: "#DC2626"
        }}
      }},
      lavender: {{
        id: "dream:lavender",
        name: "Lavender Soft",
        phoneColor: "purple",
        colors: {{
          primary: "#A855F7",
          primary_hover: "#C084FC",
          primary_border: "#6B21A8",
          surface: "#1E132D",
          surface_alt: "#2A1B3F",
          surface_hover: "#3B2658",
          header_navy: "#120B1C",
          header_navy_light: "#1E132D",
          text_primary: "#FAF5FF",
          text_secondary: "#C084FC",
          text_muted: "#A855F7",
          text_dark: "#F3E8FF",
          text_dark_muted: "#D8B4FE",
          gold: "#FBBF24",
          danger: "#F43F5E",
          success: "#34D399",
          time: "#C084FC",
          battery_border: "#C084FC",
          battery_empty: "#120B1C",
          battery_high: "#34D399",
          battery_med: "#FBBF24",
          battery_low: "#F43F5E"
        }}
      }}
    }};

    function loadPreset(key) {{
      const p = PRESETS[key];
      if (!p) return;
      state.id = p.id;
      state.name = p.name;
      state.phoneColor = p.phoneColor;
      state.colors = Object.assign({{}}, DEFAULT_COLORS, p.colors);

      document.getElementById("themeId").value = p.id;
      document.getElementById("themeName").value = p.name;
      document.getElementById("phoneColorSelect").value = p.phoneColor;

      initColorPickers();
      renderCanvas();
      updateJson();
    }}

    function resetDefaults() {{
      loadPreset('default');
    }}

    // Helpers
    function hexToRgba(hex, alpha = 1) {{
      if (!hex) return "rgba(0,0,0,1)";
      let c = hex.replace("#", "");
      if (c.length === 8) {{
        alpha = parseInt(c.slice(0, 2), 16) / 255;
        c = c.slice(2);
      }}
      if (c.length === 3) c = c[0] + c[0] + c[1] + c[1] + c[2] + c[2];
      const num = parseInt(c, 16);
      return `rgba(${{(num >> 16) & 255}}, ${{(num >> 8) & 255}}, ${{num & 255}}, ${{alpha}})`;
    }}

    // Dynamic UI population
    function initColorPickers() {{
      const categories = {{
        accents: ["primary", "primary_hover", "primary_border"],
        surfaces: ["surface", "surface_alt", "surface_hover", "shadow", "header_navy", "header_navy_light"],
        text: ["text_primary", "text_secondary", "text_muted", "text_dark", "text_dark_muted"],
        status: ["gold", "danger", "success", "time", "battery_border", "battery_empty", "battery_high", "battery_med", "battery_low"]
      }};

      Object.entries(categories).forEach(([catKey, keys]) => {{
        const container = document.getElementById("colors-" + catKey);
        if (!container) return;
        container.innerHTML = "";

        keys.forEach(k => {{
          const item = document.createElement("div");
          item.className = "color-item";

          const meta = document.createElement("div");
          meta.className = "color-meta";
          meta.innerHTML = `<div class="color-name">${{k.replace(/_/g, " ")}}</div><div class="color-sub">${{k}}</div>`;

          const inputs = document.createElement("div");
          inputs.className = "color-inputs";

          const val = state.colors[k] || DEFAULT_COLORS[k] || "#FFFFFF";
          const hex7 = val.startsWith("#") && val.length >= 7 ? val.slice(0, 7) : val;

          inputs.innerHTML = `
            <div class="color-picker-wrap">
              <input type="color" value="${{hex7}}" id="picker_${{k}}">
            </div>
            <input type="text" class="hex-input" value="${{val}}" id="hex_${{k}}">
          `;

          const picker = inputs.querySelector(`#picker_${{k}}`);
          const textInput = inputs.querySelector(`#hex_${{k}}`);

          picker.oninput = (e) => {{
            textInput.value = e.target.value.toUpperCase();
            state.colors[k] = e.target.value.toUpperCase();
            renderCanvas();
            updateJson();
          }};

          textInput.oninput = (e) => {{
            const h = e.target.value.trim();
            if (h.startsWith("#") && (h.length === 7 || h.length === 9)) {{
              picker.value = h.slice(0, 7);
              state.colors[k] = h;
              renderCanvas();
              updateJson();
            }}
          }};

          item.appendChild(meta);
          item.appendChild(inputs);
          container.appendChild(item);
        }});
      }});
    }}

    function initElementsList() {{
      const list = document.getElementById("elements-list");
      if (!list) return;
      list.innerHTML = "";

      const elements = [
        {{ key: "home_button", name: "Home Button (Footer)", dims: "7 × 14 px" }},
        {{ key: "prev_button", name: "Previous Page Button (Footer)", dims: "7 × 14 px" }},
        {{ key: "next_button", name: "Next Page Button (Footer)", dims: "7 × 14 px" }},
        {{ key: "settings_button", name: "Settings Gear (Header)", dims: "11 × 22 px" }},
        {{ key: "drag_handle", name: "App Drag Handle", dims: "8 × 16 px" }},
        {{ key: "view_select", name: "App Visibility Eye", dims: "8 × 16 px" }},
        {{ key: "social_pokeball", name: "Social Pokéball Icon", dims: "12 × 12 px" }}
      ];

      elements.forEach(el => {{
        const group = document.createElement("div");
        group.className = "form-group";
        group.innerHTML = `
          <label>${{el.name}}</label>
          <div class="upload-box" onclick="triggerUpload('${{el.key}}')">
            <div class="upload-info">
              <img id="elemThumb_${{el.key}}" class="upload-thumb" src="${{ASSETS.elements[el.key + '.png'] || ''}}" alt="">
              <div class="upload-text">
                <span id="elemLabel_${{el.key}}">Default (${{el.dims}})</span>
                <small>${{el.dims}} PNG (Top=Normal, Bottom=Hover)</small>
              </div>
            </div>
            <div class="upload-actions">
              <button onclick="event.stopPropagation(); triggerUpload('${{el.key}}')">Upload</button>
              <button onclick="event.stopPropagation(); clearUpload('${{el.key}}')">Reset</button>
            </div>
          </div>
          <input type="file" id="file_${{el.key}}" accept="image/png" style="display:none" onchange="handleImageUpload(event, '${{el.key}}')">
        `;
        list.appendChild(group);
      }});
    }}

    function initAppsList() {{
      const list = document.getElementById("apps-list");
      if (!list) return;
      list.innerHTML = "";

      Object.entries(state.appIcons).forEach(([appId, item]) => {{
        const group = document.createElement("div");
        group.className = "form-group";
        const cleanName = item.name;

        group.innerHTML = `
          <label style="display:flex; justify-content:space-between;">
            <span>${{cleanName}} (<code>${{appId}}</code>)</span>
          </label>
          <div style="display: flex; gap: 8px;">
            <!-- Normal Texture -->
            <div class="upload-box" style="flex:1;" onclick="triggerUpload('app_${{appId}}#texture')">
              <div class="upload-info">
                <img id="appThumb_norm_${{appId}}" class="upload-thumb" src="${{ASSETS.buttons[item.iconKey] || ''}}" alt="">
                <div class="upload-text">
                  <span>Normal Icon</span>
                  <small>36 × 36 px PNG</small>
                </div>
              </div>
              <div class="upload-actions">
                <button onclick="event.stopPropagation(); triggerUpload('app_${{appId}}#texture')">Upload</button>
              </div>
            </div>
            <input type="file" id="file_app_${{appId}}#texture" accept="image/png" style="display:none" onchange="handleImageUpload(event, 'app_${{appId}}#texture')">

            <!-- Hover Texture -->
            <div class="upload-box" style="flex:1;" onclick="triggerUpload('app_${{appId}}#hover')">
              <div class="upload-info">
                <img id="appThumb_hover_${{appId}}" class="upload-thumb" src="${{ASSETS.buttons[item.iconKey.replace('.png', '_hover.png')] || ASSETS.buttons[item.iconKey] || ''}}" alt="">
                <div class="upload-text">
                  <span>Hover Icon</span>
                  <small>Optional 36 × 36 px</small>
                </div>
              </div>
              <div class="upload-actions">
                <button onclick="event.stopPropagation(); triggerUpload('app_${{appId}}#hover')">Upload</button>
              </div>
            </div>
            <input type="file" id="file_app_${{appId}}#hover" accept="image/png" style="display:none" onchange="handleImageUpload(event, 'app_${{appId}}#hover')">
          </div>
        `;
        list.appendChild(group);
      }});
    }}

    // Mouse hover tracking on the canvas
    function initCanvasInteractions() {{
      const canvas = document.getElementById("phoneCanvas");
      const tooltip = document.getElementById("phoneTooltip");
      const stage = document.getElementById("phoneStage");

      canvas.onmousemove = (e) => {{
        const isLarge = state.screenMode === "large";
        const baseW = isLarge ? 211 : 131;
        const baseH = 207;
        const rect = canvas.getBoundingClientRect();
        const stageRect = stage.getBoundingClientRect();
        const x = (e.clientX - rect.left) * (baseW / rect.width);
        const y = (e.clientY - rect.top) * (baseH / rect.height);

        let foundHoverApp = null;
        let foundHoverSettings = false;
        let foundHoverBattery = false;
        let foundHoverFooter = null;
        let foundHoverBack = false;
        let tooltipText = "";

        if (state.screenMode === "home") {{
          // App icons (2x3)
          const apps = Object.entries(state.appIcons);
          const startX = 26;
          const startY = 37;
          const spacingX = 43;
          const spacingY = 43;

          apps.slice(0, 6).forEach(([appId, item], index) => {{
            const col = index % 2;
            const row = Math.floor(index / 2);
            const ax = startX + col * spacingX;
            const ay = startY + row * spacingY;

            if (x >= ax && x <= ax + 36 && y >= ay && y <= ay + 36) {{
              foundHoverApp = appId;
              tooltipText = item.name;
            }}
          }});

          // Settings button (80, 17, 9x9)
          if (x >= 78 && x <= 91 && y >= 15 && y <= 28) {{
            foundHoverSettings = true;
            tooltipText = "Settings";
          }}

          // Battery (101, 19, 10x5)
          if (x >= 99 && x <= 112 && y >= 17 && y <= 25) {{
            foundHoverBattery = true;
            tooltipText = "Battery: " + state.battery + "%" + (state.batteryCharging ? " (Charging)" : "");
          }}

          // Footer buttons (prev: 36, home: 62, next: 88, y: 187)
          if (y >= 184 && y <= 196) {{
            if (x >= 33 && x <= 45) {{ foundHoverFooter = "prev"; tooltipText = "Previous Page"; }}
            else if (x >= 59 && x <= 71) {{ foundHoverFooter = "home"; tooltipText = "Home"; }}
            else if (x >= 85 && x <= 97) {{ foundHoverFooter = "next"; tooltipText = "Next Page"; }}
          }}
        }} else if (state.screenMode === "settings") {{
          if (x >= 18 && x <= 45 && y >= 18 && y <= 26) {{
            foundHoverBack = true;
            tooltipText = "Back to Home";
          }}
          if (x >= 99 && x <= 112 && y >= 17 && y <= 25) {{
            foundHoverBattery = true;
            tooltipText = "Battery: " + state.battery + "%" + (state.batteryCharging ? " (Charging)" : "");
          }}
        }} else if (state.screenMode === "large") {{
          if (x >= 179 && x <= 193 && y >= 15 && y <= 24) {{
            foundHoverBattery = true;
            tooltipText = "Battery: " + state.battery + "%" + (state.batteryCharging ? " (Charging)" : "");
          }}
        }}

        let needsRerender = false;
        if (state.hoveredAppId !== foundHoverApp) {{
          state.hoveredAppId = foundHoverApp;
          needsRerender = true;
        }}
        if (state.hoveredSettings !== foundHoverSettings) {{
          state.hoveredSettings = foundHoverSettings;
          needsRerender = true;
        }}
        if (state.hoveredFooter !== foundHoverFooter) {{
          state.hoveredFooter = foundHoverFooter;
          needsRerender = true;
        }}
        if (state.hoveredBack !== foundHoverBack) {{
          state.hoveredBack = foundHoverBack;
          needsRerender = true;
        }}

        if (needsRerender) {{
          renderCanvas();
        }}

        if (tooltipText && tooltip) {{
          tooltip.style.display = "block";
          tooltip.innerText = tooltipText;
          tooltip.style.left = (e.clientX - stageRect.left + 14) + "px";
          tooltip.style.top = (e.clientY - stageRect.top + 14) + "px";
        }} else if (tooltip) {{
          tooltip.style.display = "none";
        }}
      }};

      canvas.onmouseleave = () => {{
        if (tooltip) tooltip.style.display = "none";
        if (state.hoveredAppId !== null || state.hoveredSettings || state.hoveredFooter || state.hoveredBack) {{
          state.hoveredAppId = null;
          state.hoveredSettings = false;
          state.hoveredFooter = null;
          state.hoveredBack = false;
          renderCanvas();
        }}
      }};

      canvas.onclick = (e) => {{
        const isLarge = state.screenMode === "large";
        const baseW = isLarge ? 211 : 131;
        const baseH = 207;
        const rect = canvas.getBoundingClientRect();
        const x = (e.clientX - rect.left) * (baseW / rect.width);
        const y = (e.clientY - rect.top) * (baseH / rect.height);

        if (state.screenMode === "home" && x >= 78 && x <= 91 && y >= 15 && y <= 28) {{
          setScreenMode("settings");
        }} else if (state.screenMode === "settings" && x >= 18 && x <= 45 && y >= 18 && y <= 26) {{
          setScreenMode("home");
        }} else if (state.screenMode === "settings") {{
          if (x >= 22 && x <= 62 && y >= 26 && y <= 36) {{
            state.settingsTab = "apps";
            renderCanvas();
          }} else if (x >= 66 && x <= 108 && y >= 26 && y <= 36) {{
            state.settingsTab = "themes";
            renderCanvas();
          }}
        }}
      }};
    }}

    // Init Studio
    window.onload = () => {{
      preloadAssets();
      initColorPickers();
      initElementsList();
      initAppsList();
      initCanvasInteractions();
      applyZoom();
      renderCanvas();
      updateJson();
    }};
  </script>
</body>
</html>
"""

    with open(OUT_HTML, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Generated {OUT_HTML} ({len(html)} bytes)")

    shutil.copyfile(OUT_HTML, ROOT_HTML)
    print(f"Copied to root: {ROOT_HTML}")

if __name__ == "__main__":
    build_html()
