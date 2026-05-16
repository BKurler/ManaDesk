package com.reflexit.magiccards.ui.gallery;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.IMagicCardPhysical;

public final class GalleryHtmlBuilder {

	private GalleryHtmlBuilder() {
	}

	private static String escapeHtml(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	public static String buildHtml(Object input) {
		StringBuilder sb = new StringBuilder();

		sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
		sb.append("<style>").append(GALLERY_CSS).append("</style>");
		sb.append("</head><body>");

		// --- DEBUG BLOCK: show the generated HTML inside the page ---
		sb.append("<div style='white-space:pre;font-family:monospace;"
				+ "background:#222;color:#0f0;padding:10px;margin-bottom:10px;'>");

		// Build the gallery HTML into a separate buffer
		StringBuilder gallery = new StringBuilder();
		gallery.append("<div class='gallery'>");

		if (input instanceof Iterable<?>) {
			for (Object o : (Iterable<?>) input) {
				if (o instanceof IMagicCard) {
					appendCard(gallery, (IMagicCard) o);
				}
			}
		}

		gallery.append("</div>");

		// Display the encoded HTML for debugging
		sb.append(escapeHtml(gallery.toString()));
		sb.append("</div>");
		// --- END DEBUG BLOCK ---

		// Insert the actual gallery HTML
		sb.append(gallery);

		sb.append("<script>").append(GALLERY_JS).append("</script>");
		sb.append("</body></html>");

		return sb.toString();
	}

	private static void appendCard(StringBuilder sb, IMagicCard card) {
		// --- Instrumentation header ---
		System.out.println("=== appendCard() ===");
		System.out.println("Card class: " + card.getClass().getName());
		System.out.println("Card toString(): " + card);
		System.out.println("Card ID (raw): " + card.getCardId());

		// Resolve ID
		String id = safe(card.getCardId());
		System.out.println("Card ID (safe): " + id);

		// Resolve image URL
		String url = "";
		try {
			java.net.URL u = com.reflexit.magiccards.core.sync.CardCache.getImageURL(card);
			if (u != null) {
				url = safe(u.toString());
			}
		} catch (Exception e) {
			System.out.println("Image URL resolution failed: " + e.getMessage());
		}

		System.out.println("Image URL: " + url);

		// Resolve count
		int count = (card instanceof IMagicCardPhysical) ? ((IMagicCardPhysical) card).getCount() : 0;

		System.out.println("Physical count: " + count);

		// --- Build HTML ---
		sb.append("<div class='card' data-id='").append(id).append("'>");
		sb.append("<div class='card-inner'>");

		sb.append("<img src='").append(url).append("' loading='lazy'/>");

		if (count > 1) {
			sb.append("<div class='count-badge'>x").append(count).append("</div>");
		}

		sb.append("</div></div>");

		// --- Instrumentation footer ---
		System.out.println("HTML appended for card ID: " + id);
		System.out.println("=======================");
	}

	private static String safe(Object s) {
		if (s == null)
			return "";
		return String.valueOf(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"",
				"&quot;");
	}

	// ============================================================
	// CSS
	// ============================================================

	private static final String GALLERY_CSS = "html, body {" + "  margin: 0;" + "  padding: 0;" + "  width: 100%;"
			+ "  height: 100%;" + "  overflow: auto;" + "}" + ".gallery {" + "  padding: 8px;" + "  background: white;"
			+ "  color: black;" + "  font-family: sans-serif;" + "  width: 100%;" + "}" + ".card {"
			+ "  position: relative;" + "  display: inline-block;" + "  margin: 6px;" + "  cursor: pointer;" + "}"
			+ ".card-inner {" + "  position: relative;" + "  width: 325px;" + "}" + ".card img {" + "  width: 100%;"
			+ "  border-radius: 4px;" + "  box-shadow: 0 0 4px #000;" + "  display: block;" + "}" + ".count-badge {"
			+ "  position: absolute;" + "  left: 6px;" + "  bottom: 6px;" + "  background: rgba(0,0,0,0.75);"
			+ "  color: #fff;" + "  padding: 4px 10px;" + "  border-radius: 12px;" + "  font-size: 16px;"
			+ "  font-weight: bold;" + "}";

	// ============================================================
	// JS
	// ============================================================
	private static final String GALLERY_JS = "document.addEventListener('click', function(e) {"
			+ "  var target = e.target || e.srcElement;" + "  var node = target;" + "  var card = null;"
			+ "  while (node && node !== document) {" + "    var cls = node.className || '';"
			+ "    if (typeof cls === 'string' && cls.indexOf('card') !== -1) {" + "      card = node;" + "      break;"
			+ "    }" + "    node = node.parentNode;" + "  }" + "  if (!card) return;"
			+ "  var id = card.getAttribute('data-id');" + "  var imgs = document.getElementsByTagName('img');"
			+ "  for (var i = 0; i < imgs.length; i++) {" + "    imgs[i].style.outline = '';"
			+ "    imgs[i].style.boxShadow = '';" + "    imgs[i].style.background = '';" + "  }"
			+ "  var img = card.getElementsByTagName('img')[0];" + "  if (img) {"
			+ "    img.style.outline = '4px solid #1E90FF';"
			+ "    img.style.boxShadow = '0 0 20px 6px rgba(30,144,255,0.9)';"
			+ "    img.style.background = 'rgba(30,144,255,0.15)';" + "  }" + "  if (window.javaSelectCard) {"
			+ "    window.javaSelectCard(id);" + "  }" + "});";

}
