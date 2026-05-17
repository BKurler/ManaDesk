/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/

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

		// Build the gallery HTML
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

		// Insert the actual gallery HTML
		sb.append(gallery);

		sb.append("<script>").append(GALLERY_JS).append("</script>");
		sb.append("</body></html>");

		return sb.toString();
	}

	private static void appendCard(StringBuilder sb, IMagicCard card) {
		// Resolve ID
		String id = safe(card.getCardId());

		// Resolve image URL
		String url = "";
		try {
			java.net.URL u = com.reflexit.magiccards.core.sync.CardCache.getImageURL(card);
			if (u != null) {
				url = safe(u.toString());
			}
		} catch (Exception e) {
			// ignore: fallback to empty URL
		}

		// Resolve count
		int count = (card instanceof IMagicCardPhysical) ? ((IMagicCardPhysical) card).getCount() : 0;

		// Build HTML
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

	private static final String GALLERY_CSS = "html, body {" + "  margin: 0;" + "  padding: 0;" + "  width: 100%;"
			+ "  height: 100%;" + "  overflow: auto;" + "}" + ".gallery {" + "  padding: 8px;" + "  background: white;"
			+ "  color: black;" + "  font-family: sans-serif;" + "  width: 100%;" + "}" + ".card {"
			+ "  position: relative;" + "  display: inline-block;" + "  margin: 6px;" + "  cursor: pointer;"
			+ "  vertical-align: top;" + "}" + ".card-inner {" + "  position: relative;" + "  width: 220px;" + "}"
			+ ".card img {" + "  width: 100%;" + "  border-radius: 4px;" + "  box-shadow: 0 0 4px #000;"
			+ "  display: block;" + "}" + ".count-badge {" + "  position: absolute;" + "  left: 6px;" + "  bottom: 6px;"
			+ "  background: rgba(0,0,0,0.75);" + "  color: #fff;" + "  padding: 3px 8px;" + "  border-radius: 10px;"
			+ "  font-size: 14px;" + "  font-weight: bold;" + "}" + ".group-title {" + "  display: block;"
			+ "  max-width: 100%;" + "  white-space: normal;" + "  word-break: break-word;" + "  margin-bottom: 4px;"
			+ "  font-size: 14px;" + "  font-weight: bold;" + "}";

	// ============================================================
	// JS
	// ============================================================
	private static final String GALLERY_JS = "document.addEventListener('click', function(e) {"
			+ "  var target = e.target || e.srcElement;" + "  var node = target;" + "  var card = null;"
			+ "  while (node && node !== document) {" + "    var cls = node.className || '';"
			+ "    if (typeof cls === 'string' && cls.split(' ').indexOf('card') !== -1) {" + "      card = node;"
			+ "      break;" + "    }" + "    node = node.parentNode;" + "  }" + "  if (!card) return;"
			+ "  var id = card.getAttribute('data-id');" + "  if (!id) return;"
			+ "  var imgs = document.getElementsByTagName('img');" + "  for (var i = 0; i < imgs.length; i++) {"
			+ "    imgs[i].style.outline = '';" + "    imgs[i].style.boxShadow = '';"
			+ "    imgs[i].style.background = '';" + "  }" + "  var img = card.getElementsByTagName('img')[0];"
			+ "  if (img) {" + "    img.style.outline = '4px solid #1E90FF';"
			+ "    img.style.boxShadow = '0 0 20px 6px rgba(30,144,255,0.9)';"
			+ "    img.style.background = 'rgba(30,144,255,0.15)';" + "  }" + "  if (window.javaSelectCard) {"
			+ "    window.javaSelectCard(id);" + "  }" + "});";

}
