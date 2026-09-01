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

	public static String buildVirtualGalleryHtml(int totalCards) {
		return buildVirtualGalleryHtml(totalCards, null, -1);
	}

	public static String buildVirtualGalleryHtml(int totalCards, String selectId) {
		return buildVirtualGalleryHtml(totalCards, selectId, -1);
	}

	/**
	 * @param selectIndex the flat index of {@code selectId} in the current input
	 *                    ({@code -1} if unknown). Lets a programmatic reveal
	 *                    bulk-load straight to a deep card (e.g. card #1900 while
	 *                    the tree "All" node is selected) instead of giving up
	 *                    after a fixed number of 80-card pages.
	 */
	public static String buildVirtualGalleryHtml(int totalCards, String selectId, int selectIndex) {

		StringBuilder sb = new StringBuilder();

		sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");

		// Reuse your existing gallery CSS so the layout matches the old viewer
		sb.append("<style>").append(GALLERY_CSS).append("</style>");

		sb.append("</head><body>");

		// Same structure as your old gallery: a <div class='gallery'>
		sb.append("<div class='gallery' id='gallery'></div>");

		sb.append("<script>");

		// Paging state
		sb.append("window.totalCards=").append(totalCards).append(";");
		sb.append("var pageSize=80;");
		sb.append("var loadedUntil=0;");
		sb.append("var loading=false;");
		// timestamps used to swallow stray clicks while the view is still
		// streaming/reflowing after an auto-select move (a card slides under the
		// cursor and steals the selection - the gallery 'misclick race').
		sb.append("window.__renderedAt=Date.now();");
		sb.append("window.__lastLoadAt=0;");

		// Load a page of cards from Java
		sb.append("function loadNextPage(){");
		sb.append(" if(loading) return;");
		sb.append(" loading=true;");
		sb.append(" var json=window.loadCardRange(loadedUntil,pageSize);");
		sb.append(" var cards=JSON.parse(json);");
		sb.append(" appendCards(cards);");
		sb.append(" loadedUntil+=cards.length;");
		sb.append(" loading=false;");
		sb.append(" window.__lastLoadAt=Date.now();");
		sb.append("}");

		// Append cards using the SAME DOM structure as your old appendCard()
		sb.append("function appendCards(cards){");
		sb.append(" var gallery=document.getElementById('gallery');");
		sb.append(" for(var i=0;i<cards.length;i++){");
		sb.append("  var c=cards[i];");

		// <div class='card' data-id='...'>
		sb.append("  var card=document.createElement('div');");
		sb.append("  card.className='card';");
		sb.append("  card.setAttribute('data-id', c.id);");

		// <div class='card-inner'>
		sb.append("  var inner=document.createElement('div');");
		sb.append("  inner.className='card-inner';");

		// <img src='...'>
		sb.append("  var img=document.createElement('img');");
		sb.append("  img.src=c.image;");
		sb.append("  img.loading='lazy';");
		sb.append("  inner.appendChild(img);");

		// Count badge (only if count > 1)
		sb.append("  if(c.count && c.count > 1){");
		sb.append("    var badge=document.createElement('div');");
		sb.append("    badge.className='count-badge';");
		sb.append("    badge.textContent='x' + c.count;");
		sb.append("    inner.appendChild(badge);");
		sb.append("  }");

		// Close structure
		sb.append("  card.appendChild(inner);");
		sb.append("  gallery.appendChild(card);");
		sb.append(" }");
		// NB: do NOT call __applySelect() here - it drives its own synchronous
		// page loading and calling it mid-loadNextPage recurses.
		sb.append("}");

		// Programmatic selection (Java -> JS): mark + scroll to a card by id.
		// Bounded page loading so it can't runaway-load the whole virtual list.
		// __wantSelect may be pre-seeded so the selection is part of THIS render.
		sb.append("window.__selectedId=null;"); // what is currently highlighted (idempotency)
		if (selectId != null && !selectId.isEmpty()) {
			String sid = safe(selectId).replace("'", "");
			sb.append("window.__wantSelect='").append(sid).append("';");
			sb.append("window.__wantIndex=").append(selectIndex).append(";");
			sb.append("try{if(window.javaLog) javaLog('render: pre-seeded wantSelect=").append(sid)
					.append(" idx=").append(selectIndex).append("');}catch(e){}");
		} else {
			sb.append("window.__wantSelect=null;");
			sb.append("window.__wantIndex=-1;");
		}
		sb.append("window.__selTries=0;");
		// Ignore repeat calls for the same card - the SWT Browser fires 'completed'
		// several times and each one would otherwise re-scroll, so the selected
		// card keeps jumping out of view while pages stream in.
		sb.append("function selectCardById(id,idx){"
				+ " if(id && id===window.__selectedId){ if(window.javaLog) javaLog('selectCardById '+id+' (already selected, ignored)'); return; }"
				+ " if(window.javaLog) javaLog('selectCardById '+id+' idx='+idx);"
				+ " window.__wantSelect=id; window.__wantIndex=(typeof idx==='number'?idx:-1);"
				+ " window.__selTries=0; __applySelect(); }");
		sb.append("function jlog(m){ try{ if(window.javaLog) javaLog(m); }catch(e){} }");
		// Load pages synchronously until the wanted card is in the DOM. loadCardRange
		// is a synchronous BrowserFunction, so this cannot deadlock; the `loading`
		// flag only guards scroll re-entrancy, so we bypass it here. We keep going
		// past __wantIndex (it can be stale by a row or two after a move) until the
		// card actually appears or the list is exhausted.
		sb.append("function __ensureLoadedFor(id, wi){");
		sb.append(" var guard=0;");
		sb.append(" while(loadedUntil<window.totalCards && guard++<2000){");
		sb.append("  if(document.querySelector(\".card[data-id='\"+id+\"']\")) return true;");
		sb.append("  if(typeof wi==='number' && wi>=0 && loadedUntil>wi+pageSize){");
		sb.append("   /* past where it should be and still not found - keep scanning a bit */");
		sb.append("  }");
		sb.append("  var before=loadedUntil; loading=false; loadNextPage();");
		sb.append("  if(loadedUntil===before) break;");
		sb.append(" }");
		sb.append(" return !!document.querySelector(\".card[data-id='\"+id+\"']\");");
		sb.append("}");
		sb.append("function __applySelect(){");
		sb.append(" var id=window.__wantSelect; if(!id) return;");
		sb.append(" __ensureLoadedFor(id, window.__wantIndex);");
		sb.append(" var card=document.querySelector(\".card[data-id='\"+id+\"']\");");
		sb.append(" jlog('__applySelect id='+id+' found='+(!!card)+' loaded='+loadedUntil+'/'+window.totalCards+' tries='+window.__selTries+' scrollY='+window.scrollY);");
		sb.append(" if(card){");
		sb.append("  var prev=document.querySelectorAll('.card.sel');");
		sb.append("  for(var i=0;i<prev.length;i++) prev[i].classList.remove('sel');");
		sb.append("  card.classList.add('sel');");
		// scroll ONLY if the card is fully off-screen - otherwise leave the view
		// exactly where it is (a nudge shifts every card and hides the selection).
		sb.append("  if(window.__selectedId!==id){");
		sb.append("   var r=card.getBoundingClientRect();");
		sb.append("   var off=(r.bottom<=0 || r.top>=window.innerHeight);");
		sb.append("   jlog('  card rect top='+Math.round(r.top)+' bottom='+Math.round(r.bottom)+' innerH='+window.innerHeight+' offscreen='+off);");
		sb.append("   if(off){ try{card.scrollIntoView({block:'center'});}catch(e){} jlog('  scrolled, scrollY now '+window.scrollY); }");
		sb.append("  }");
		sb.append("  window.__selectedId=id; window.__wantSelect=null;");
		sb.append(" } else {");
		// Not in this list at all (id belongs to a card that was moved away, or a
		// stale page about to be replaced by a fresh render). Give up quietly - no
		// retry spin, no leftover highlight hunt.
		sb.append("  jlog('__applySelect gave up, id not in list: '+id);");
		sb.append("  window.__wantSelect=null;");
		sb.append(" }");
		sb.append("}");

		// SAFE viewport fill (NO while loop)
		sb.append("function fillViewport(){");
		sb.append(" if(loadedUntil < window.totalCards && ");
		sb.append("    document.body.offsetHeight < window.innerHeight + 100){");
		sb.append("    loadNextPage();");
		sb.append(" }");
		sb.append("}");

		// Infinite scroll
		sb.append("window.addEventListener('scroll', function(){");
		sb.append(" if(window.innerHeight + window.scrollY >= document.body.offsetHeight - 300){");
		sb.append("  loadNextPage();");
		sb.append(" }");
		sb.append("});");

		// Resize handling
		sb.append("window.addEventListener('resize', function(){");
		sb.append(" fillViewport();");
		sb.append("});");

		// Initial load
		sb.append("loadNextPage();");
		sb.append("fillViewport();");

		sb.append("</script>");

		// Reuse your existing selection JS (GALLERY_JS)
		sb.append("<script>").append(GALLERY_JS).append("</script>");

		sb.append("</body></html>");

		return sb.toString();
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
			+ "  font-size: 14px;" + "  font-weight: bold;" + "}"
			// selection indicator drawn INSIDE the image (outline-offset) so it
			// never changes layout / triggers a scrollbar
			+ ".card.sel img {" + "  outline: 4px solid #1E90FF;" + "  outline-offset: -4px;" + "}";

	// ============================================================
	// JS
	// ============================================================
	private static final String GALLERY_JS = "document.addEventListener('click', function(e) {"
			// Swallow stray clicks while the gallery is still settling after an
			// auto-select move: cards keep streaming in and reflowing, so a card
			// can slide under the cursor and steal the selection right when the
			// click lands. The user's real 'pick another card' clicks happen when
			// the view is idle, so a short dead-zone is safe.
			+ "  var now = Date.now();"
			+ "  if (now - (window.__renderedAt || 0) < 700"
			+ "      || (typeof loading !== 'undefined' && loading)"
			+ "      || now - (window.__lastLoadAt || 0) < 300) {"
			+ "    if (window.jlog) jlog('click ignored (gallery still settling)');"
			+ "    return;"
			+ "  }"
			+ "  var target = e.target || e.srcElement;" + "  var node = target;" + "  var card = null;"
			+ "  while (node && node !== document) {" + "    var cls = node.className || '';"
			+ "    if (typeof cls === 'string' && cls.split(' ').indexOf('card') !== -1) {" + "      card = node;"
			+ "      break;" + "    }" + "    node = node.parentNode;" + "  }" + "  if (!card) return;"
			+ "  var id = card.getAttribute('data-id');" + "  if (!id) return;"
			+ "  var prev = document.querySelectorAll('.card.sel');"
			+ "  for (var i = 0; i < prev.length; i++) prev[i].classList.remove('sel');"
			+ "  card.classList.add('sel');"
			+ "  if (window.javaSelectCard) { window.javaSelectCard(id); }" + "});";

}
