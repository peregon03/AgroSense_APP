"""
AgroSense — Generador de íconos desde Logo_agrosense.png
=========================================================
Ejecutar desde la carpeta raíz del proyecto:
    python generate_icons.py

Requiere Pillow:
    pip install Pillow

Qué hace:
  1. Lee app/src/main/res/drawable/Logo_agrosense.png
  2. Elimina el fondo blanco (lo vuelve transparente)
  3. Genera ic_launcher.png e ic_launcher_round.png en cada carpeta mipmap-*
  4. Guarda logo_agrosense.png (transparente, 512px) en drawable/ para usar en pantallas
"""

import sys
import os
from pathlib import Path

# ── Instalar Pillow si no está ───────────────────────────────────────────────
try:
    from PIL import Image
except ImportError:
    print("Instalando Pillow...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow"])
    from PIL import Image

# ── Rutas ────────────────────────────────────────────────────────────────────
BASE    = Path(__file__).parent
RES     = BASE / "app" / "src" / "main" / "res"
SRC     = BASE / "Logo_agrosense.png"

# Tamaños requeridos por densidad (launcher icon estándar)
SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

# ── Verificar fuente ─────────────────────────────────────────────────────────
if not SRC.exists():
    print(f"ERROR: No se encontró {SRC}")
    sys.exit(1)

print(f"Fuente: {SRC}")

# ── Abrir imagen y eliminar fondo blanco ─────────────────────────────────────
img = Image.open(SRC).convert("RGBA")
data = img.getdata()

THRESHOLD = 230   # píxeles con R,G,B > este valor se vuelven transparentes
EDGE_FUZZ = 200   # umbral más suave para antialiasing en los bordes

new_data = []
for r, g, b, a in data:
    # Píxel blanco o casi blanco → transparente
    if r >= THRESHOLD and g >= THRESHOLD and b >= THRESHOLD:
        new_data.append((r, g, b, 0))
    # Píxel gris claro de antialiasing → semitransparente
    elif r >= EDGE_FUZZ and g >= EDGE_FUZZ and b >= EDGE_FUZZ:
        alpha = int(255 * (1 - (min(r, g, b) - EDGE_FUZZ) / (THRESHOLD - EDGE_FUZZ)))
        new_data.append((r, g, b, max(0, 255 - alpha)))
    else:
        new_data.append((r, g, b, a))

img.putdata(new_data)

print(f"Fondo blanco eliminado — {img.size[0]}×{img.size[1]}px original")

# ── Recortar al contenido real (elimina espacio transparente desigual) ────────
bbox = img.getbbox()   # (left, upper, right, lower) del contenido no-transparente
if bbox:
    img = img.crop(bbox)
    print(f"Recortado al contenido: {img.size[0]}×{img.size[1]}px")

# ── Guardar versión para pantallas de la app (sin padding extra) ─────────────
dest_drawable = RES / "drawable" / "logo_agrosense.png"
logo_screen = img.copy()
logo_screen.thumbnail((512, 512), Image.LANCZOS)
logo_screen.save(dest_drawable, "PNG")
print(f"✓ drawable/logo_agrosense.png  ({logo_screen.size[0]}×{logo_screen.size[1]})")

# ── Crear canvas cuadrado con padding para íconos ────────────────────────────
# LOGO_SCALE: qué fracción del lado ocupa el logo (0.45 = 45% → más pequeño = más margen)
LOGO_SCALE = 0.75   # fracción del canvas que ocupa el logo
OFFSET_X   = 30    # píxeles extra hacia la derecha (0 = centrado)
CANVAS = 512       # píxeles del canvas cuadrado

logo_fit = img.copy()
max_logo_px = int(CANVAS * LOGO_SCALE)
# Escalar manteniendo proporción (sube Y baja, a diferencia de thumbnail que solo baja)
ratio = min(max_logo_px / logo_fit.width, max_logo_px / logo_fit.height)
new_w = int(logo_fit.width * ratio)
new_h = int(logo_fit.height * ratio)
logo_fit = logo_fit.resize((new_w, new_h), Image.LANCZOS)

square_inner = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
ox = (CANVAS - logo_fit.width) // 2 + OFFSET_X
oy = (CANVAS - logo_fit.height) // 2
square_inner.paste(logo_fit, (ox, oy))

# ── Guardar foreground para ícono adaptativo (Android 8+) ────────────────────
dest_fg = RES / "drawable" / "ic_launcher_logo_fg.png"
square_inner.save(dest_fg, "PNG")
print(f"✓ drawable/ic_launcher_logo_fg.png  (logo al {int(LOGO_SCALE*100)}% del canvas)")

# ── Generar íconos de launcher por densidad ──────────────────────────────────
for folder, size in SIZES.items():
    dest_dir = RES / folder
    dest_dir.mkdir(exist_ok=True)

    # Eliminar webp duplicados que causan error "Duplicate resources"
    for webp in dest_dir.glob("*.webp"):
        webp.unlink()
        print(f"  eliminado: {webp.name}")

    icon = square_inner.copy()
    icon = icon.resize((size, size), Image.LANCZOS)

    out = dest_dir / "ic_launcher.png"
    icon.save(out, "PNG")

    out_round = dest_dir / "ic_launcher_round.png"
    icon.save(out_round, "PNG")

    print(f"✓ {folder}/ic_launcher.png  ({size}×{size})")

print("\n=== Listo ===")
print("Ahora compila el proyecto en Android Studio.")
print("Si el ícono no aparece actualizado en el emulador, haz 'wipe data' al dispositivo virtual.")
