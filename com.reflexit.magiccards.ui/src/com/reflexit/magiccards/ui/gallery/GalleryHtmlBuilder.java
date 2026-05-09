package com.reflexit.magiccards.ui.gallery;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.IMagicCardPhysical;

public final class GalleryHtmlBuilder {

	private GalleryHtmlBuilder() {
	}

	public static String buildHtml(Object input) {
		StringBuilder sb = new StringBuilder();

		sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
		sb.append("<style>").append(GALLERY_CSS).append("</style>");
		sb.append("</head><body>");
		sb.append("<div class='gallery'>");

		if (input instanceof Iterable<?>) {
			for (Object o : (Iterable<?>) input) {
				if (o instanceof IMagicCard) {
					appendCard(sb, (IMagicCard) o);
				}
			}
		}

		sb.append("</div>");
		sb.append("<script>").append(GALLERY_JS).append("</script>");
		sb.append("</body></html>");

		return sb.toString();
	}

	private static void appendCard(StringBuilder sb, IMagicCard card) {
		String id = safe(card.getCardId());

		// Convertir l’URL en String
		String url = "";
		try {
			java.net.URL u = com.reflexit.magiccards.core.sync.CardCache.getImageURL(card);
			if (u != null) {
				url = safe(u.toString());
			}
		} catch (Exception e) {
			// ignore, fallback to empty URL
		}

		int count = (card instanceof IMagicCardPhysical) ? ((IMagicCardPhysical) card).getCount() : 0;

		sb.append("<div class='card' data-id='").append(id).append("'>");
		sb.append("<div class='card-inner'>");

		sb.append("<img src='").append(url).append("' loading='lazy'/>");

		if (count > 1) {
			sb.append("<div class='count-badge'>x").append(count).append("</div>");
		}

		sb.append("</div></div>");
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

	private static final String GALLERY_CSS = ".gallery { padding: 8px; background: white; color: black; font-family: sans-serif; }"
			+ ".card { position: relative; display: inline-block; margin: 6px; cursor: pointer; }"
			+ ".card-inner { position: relative; width: 325px; }"
			+ ".card img { width: 100%; border-radius: 4px; box-shadow: 0 0 4px #000; display: block; }"
			+ ".count-badge {" + "  position: absolute;" + "  left: 6px;" + "  bottom: 6px;"
			+ "  background: rgba(0,0,0,0.75);" + "  color: #fff;" + "  padding: 4px 10px;" + "  border-radius: 12px;"
			+ "  font-size: 16px;" + "  font-weight: bold;" + "}";

	// ============================================================
	// JS
	// ============================================================

	private static final String GALLERY_JS = "document.addEventListener('click', function(e) {"
			+ "  var card = e.target.closest('.card');" + "  if (!card) return;"

			// Clear previous highlight
			+ "  document.querySelectorAll('.card img').forEach(function(img) {" + "    img.style.outline = '';"
			+ "    img.style.boxShadow = '';" + "    img.style.background = '';" + "  });"

			// Apply highlight directly on the image
			+ "  var img = card.querySelector('img');" + "  if (img) {" + "    img.style.outline = '4px solid #1E90FF';"
			+ "    img.style.boxShadow = '0 0 20px 6px rgba(30,144,255,0.9)';"
			+ "    img.style.background = 'rgba(30,144,255,0.15)';" + "  }"

			// Notify Java
			+ "  var id = card.getAttribute('data-id');" + "  if (window.javaSelectCard) window.javaSelectCard(id);"
			+ "});";

}
