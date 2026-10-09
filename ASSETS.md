# Tool sprites

## Optional 32×32 variants (1.6.5)

The original 16×16 PNGs remain unchanged. The matching `mortar_trowel_32.png`, `garden_trowel_32.png`, and `prospecting_pick_32.png` add finer pixel detail to the same tool designs. Both resolutions use the same model transforms and on-screen size.

The built-in image generation tool produced the higher-resolution artwork using the original sprites as references. Source images and generation specifications are in [`artwork/32x32-source/`](artwork/32x32-source/), with prompts recorded in [`prompts.json`](artwork/32x32-source/prompts.json). Development artwork is excluded from the mod JAR.

Each source was normalized to a native 32×32 PNG using ImageMagick: `magick SOURCE -filter point -resize 32x32! -channel A -threshold 50% +channel -strip PNG32:DESTINATION`. Every final pixel has alpha 0 or 255; adjacent `.png.mcmeta` files disable texture blur. The final sprites are in `src/main/resources/assets/blocktonepathtools/textures/item/`. See the README for automatic selection and personal resolution commands.

Source artwork was created with the built-in image generation tool, then normalized with nearest-neighbor sampling to actual 16×16 PNGs. Each pixel has alpha 0 or 255. Texture metadata disables blur, and all models use Minecraft's `item/handheld` parent.

Assets are saved beneath `src/main/resources/assets/blocktonepathtools/textures/item/`.

## mortar_trowel.png

Final source-art prompt:

Production Minecraft item texture. Generate ONE flat 2D pixel-art sprite on genuine transparent alpha background, with EXACTLY a 16 by 16 coarse logical pixel grid (large crisp square pixel cells, NO details smaller than a cell). Subject: a mason's triangular mortar trowel: a BROAD FLAT TRIANGULAR silver steel blade tapering to a sharp point at upper right, wide straight heel, visibly narrow bent metal tang offset into a short dark brown wooden handle at lower left. Clear triangular plastering trowel silhouette; blade noticeably wider than a garden trowel. Icon only, orthographic side silhouette, item diagonal lower-left handle to upper-right head like vanilla Minecraft tools, centered with 1 logical pixel transparent margin, all parts inside canvas. Use only six solid opaque colors: dark charcoal outline, dark/medium/light steel gray, dark brown and light brown wood. All material cells opaque, all background cells fully transparent. Strong recognizable silhouette, minimal vanilla iron-tool pixel art, flat colors, simple chunky geometry. No perspective, no smooth edges, no anti-aliasing, no gradients, no shadows, no glow, no text, no checkerboard painted in, no frame. This is a standalone game sprite, not a rendered physical object.

## garden_trowel.png

Final source-art prompt:

Production Minecraft item texture. Generate ONE flat 2D pixel-art sprite on genuine transparent alpha background, with EXACTLY a 16 by 16 coarse logical pixel grid (large crisp square pixel cells, NO details smaller than a cell). Subject: a small gardener's hand trowel: an elongated narrow silver steel SCOOP blade, rounded shoulders narrowing to a pointed tip at upper right, central dark crease suggesting a concave scoop, short warm brown wooden grip at lower left. Clearly a garden planting trowel with long narrow scoop, NOT a triangular masonry trowel. Icon only, orthographic side silhouette, item diagonal lower-left handle to upper-right head like vanilla Minecraft tools, centered with 1 logical pixel transparent margin, all parts inside canvas. Use only six solid opaque colors: dark charcoal outline, dark/medium/light steel gray, dark brown and light brown wood. All material cells opaque, all background cells fully transparent. Strong recognizable silhouette, minimal vanilla iron-tool pixel art, flat colors, simple chunky geometry. No perspective, no smooth edges, no anti-aliasing, no gradients, no shadows, no glow, no text, no checkerboard painted in, no frame. This is a standalone game sprite, not a rendered physical object.

## prospecting_pick.png

Final source-art prompt:

Production Minecraft item texture. Generate ONE flat 2D pixel-art sprite on genuine transparent alpha background, with EXACTLY a 16 by 16 coarse logical pixel grid (large crisp square pixel cells, NO details smaller than a cell). Subject: a geologist's rock hammer / prospecting pick: long brown wooden handle from lower left toward upper right, a perpendicular asymmetric gray steel hammer head at the upper right, with a short BLOCKY SQUARE FLAT striking face on one end and a LONG SLIM sharply pointed gently downward-curved pick on the opposite end. Clearly a rock hammer, NOT a symmetric double-point mining pickaxe. Icon only, orthographic side silhouette, item diagonal lower-left handle to upper-right head like vanilla Minecraft tools, centered with 1 logical pixel transparent margin, all parts inside canvas. Use only six solid opaque colors: dark charcoal outline, dark/medium/light steel gray, dark brown and light brown wood. All material cells opaque, all background cells fully transparent. Strong recognizable silhouette, minimal vanilla iron-tool pixel art, flat colors, simple chunky geometry. No perspective, no smooth edges, no anti-aliasing, no gradients, no shadows, no glow, no text, no checkerboard painted in, no frame. This is a standalone game sprite, not a rendered physical object.
