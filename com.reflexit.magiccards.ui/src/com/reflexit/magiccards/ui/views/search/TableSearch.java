/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/
package com.reflexit.magiccards.ui.views.search;

import java.util.regex.Pattern;

import org.eclipse.jface.viewers.TreePath;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;

/**
 * @author Alena
 *
 */
public class TableSearch {

	private static final boolean DEBUG = false;

	public static void search(SearchContext context, IFilteredCardStore store) {
		search(context, store, store == null ? null : store.getCardGroupRoot());
	}

	/**
	 * Search within {@code searchRoot} only. In the split viewer this is the
	 * group the user selected in the tree, so Find is restricted to what the
	 * right pane actually shows (not the whole collection). {@code store} is
	 * still used for the flat fall-back when there is no group tree.
	 */
	public static void search(SearchContext context, IFilteredCardStore store, ICardGroup searchRoot) {

		Object last;
		String inputText;
		boolean wholeWord;
		boolean matchCase;
		boolean needWrap;
		synchronized (context) {
			inputText = context.getText();
			last = context.getLast();
			wholeWord = context.isWholeWord();
			matchCase = context.isMatchCase();
			needWrap = context.isWrapAround();
			context.setFound(false); // don't reset last yet
			context.setDidWrap(false);
		}
		if (isCamelCase(inputText)) {
			matchCase = true;
		} else {
			matchCase = false;
		}

		String escapedInput = escapeAndCamelCase(inputText);
		String pattern = escapedInput;
		if (wholeWord)
			pattern = "\\b" + escapedInput + "\\b";
		pattern = ".*" + pattern + ".*";
		int flags = Pattern.CASE_INSENSITIVE;
		if (matchCase)
			flags = 0;
		Pattern pat = Pattern.compile(pattern, flags);
		if (searchRoot != null && searchRoot.size() > 0) {
			// The anchor path may be rooted at the store root; make it relative
			// to searchRoot so searchTree() can follow it.
			if (last instanceof TreePath)
				last = relativizePath((TreePath) last, searchRoot);
			if (last instanceof TreePath) {
				searchTree(context, (TreePath) last, needWrap, pat, searchRoot, TreePath.EMPTY);
				if (!context.isFound() && needWrap) {
					context.setDidWrap(true);
					searchTree(context, null, needWrap, pat, searchRoot, TreePath.EMPTY);
				}
			} else {
				searchTree(context, null, needWrap, pat, searchRoot, TreePath.EMPTY);
			}
		} else {
			if (last instanceof TreePath) {
				last = ((TreePath) last).getLastSegment();
			}
			searchFlat(context, store, last, needWrap, pat);
		}
	}

	/**
	 * Drop leading segments up to and including {@code root}. If {@code root} is
	 * not on the path (e.g. it already starts below it) the path is returned
	 * unchanged; if the path ends at {@code root} the result is empty/null.
	 */
	private static TreePath relativizePath(TreePath path, ICardGroup root) {
		if (path == null || root == null)
			return path;
		int cut = -1;
		for (int i = 0; i < path.getSegmentCount(); i++) {
			if (root.equals(path.getSegment(i))) {
				cut = i;
				break;
			}
		}
		if (cut < 0)
			return path; // already relative
		int remaining = path.getSegmentCount() - cut - 1;
		if (remaining <= 0)
			return null;
		Object[] seg = new Object[remaining];
		for (int i = 0; i < remaining; i++)
			seg[i] = path.getSegment(cut + 1 + i);
		return new TreePath(seg);
	}

	private static int getIndex(Object anchor, Object[] elements) {

		if (DEBUG) {
			System.out.println("[GETINDEX] anchor=" + anchor);
			System.out.println("[GETINDEX] anchorKey=" + (anchor == null ? null : anchor.toString()));
		}

		String anchorKey = (anchor == null ? null : anchor.toString());

		for (int i = 0; i < elements.length; i++) {
			Object elem = elements[i];
			boolean eq = elem.equals(anchor);

			String elemKey = (elem == null ? null : elem.toString());
			boolean keyEq = (anchorKey != null && anchorKey.equals(elemKey));

			if (DEBUG) {
				System.out.println("  [GETINDEX] i=" + i + " elem=" + elem + " eq=" + eq + " keyEq=" + keyEq);
			}

			if (eq || keyEq) {
				if (DEBUG) {
					System.out.println("[GETINDEX] FOUND by " + (eq ? "equals()" : "key") + " i=" + i);
				}
				return i;
			}
		}

		if (DEBUG) {
			System.out.println("[GETINDEX] NOT FOUND");
		}

		return -1;
	}

	private static String extractKey(Object o) {
		if (o == null)
			return null;

		String s = o.toString().trim();

		// CASE 1 : CARD (has UUID prefix)
		// UUID pattern: 8-4-4-4-12 hex chars
		if (s.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*")) {
			int idx = s.indexOf(':');
			if (idx > 0) {
				return s.substring(0, idx).trim(); // return UUID
			}
		}

		// CASE 2 : GROUP (no UUID)
		// Group name is everything before the first colon (if any)
		int idx = s.indexOf(':');
		if (idx > 0) {
			return s.substring(0, idx).trim();
		}

		// No colon -> whole string is the group name
		return s;
	}

	private static void searchTree(SearchContext context, TreePath last, boolean needWrap, Pattern pat,
			ICardGroup group, TreePath path) {

		Object[] elements = group.getChildren();
		int lastIndex = -1;
		int len = elements.length;
		int i1 = 0, i2 = len - 1;

		// Debug header
		if (DEBUG) {
			System.out.println("=== TREE DEBUG ===");
			System.out.println("Forward: " + context.isForward());
			System.out.println("Group: " + group);
			System.out.println("Path: " + path);
			System.out.println("Children count: " + elements.length);
			for (int k = 0; k < elements.length; k++) {
				System.out.println("  child[" + k + "]: " + elements[k]);
			}
			if (last != null) {
				System.out.println("Last TreePath: " + last);
				System.out.println("  First segment: " + last.getFirstSegment());
				System.out.println("  Last segment: " + last.getLastSegment());
			} else {
				System.out.println("Last TreePath: null");
			}
			System.out.println("==================");
		}

		// Anchor logic
		if (last != null) {

			if (DEBUG) {
				System.out.println("  [ANCHOR] segmentCount=" + last.getSegmentCount());
			}

			if (group.equals(last.getFirstSegment())) {
				// Inside anchor group -> anchor on card
				Object anchor = last.getLastSegment();
				lastIndex = getIndex(anchor, elements);

				if (DEBUG) {
					System.out.println("  [ANCHOR] inside group, anchor card=" + anchor + " lastIndex=" + lastIndex);
				}

				if (lastIndex != -1) {
					i1 = context.isForward() ? lastIndex + 1 : lastIndex - 1;
				}

			} else {
				// Parent group -> anchor on subgroup
				Object anchor = last.getFirstSegment();
				lastIndex = getIndex(anchor, elements);

				if (DEBUG) {
					System.out
							.println("  [ANCHOR] parent group, anchor subgroup=" + anchor + " lastIndex=" + lastIndex);
				}

				if (lastIndex != -1) {
					i1 = lastIndex;
				}
			}

			if (DEBUG) {
				System.out.println("  Anchor segmentCount " + last.getSegmentCount() + " lastIndex " + lastIndex);
			}
		}

		// Wrap-around start
		if (last == null) {
			i1 = context.isForward() ? 0 : len - 1;
		}

		int start = i1;
		int end = i2;
		int off = context.isForward() ? 1 : -1;

		if (!context.isForward()) {
			end = 0;
		}

		if (DEBUG) {
			System.out.println("  [LOOP-SETUP] start=" + start + " end=" + end + " off=" + off + " len=" + len);
			System.out.println("  For loop : start " + start + " end " + end);
		}

		// Main loop
		for (int i = start; !context.isFound() && !context.isCancelled(); i += off) {

			if (DEBUG) {
				System.out.println("  [LOOP] raw i=" + i + " off=" + off);
			}

			if (i < 0 && !context.isForward())
				break;
			if (i >= len && context.isForward())
				break;

			int j = (i + len) % len;
			Object child = elements[j];
			TreePath fullPath = path.createChildPath(child);

			if (DEBUG) {
				System.out.println("  [LOOP] i=" + i + " j=" + j + " child=" + child + " lastIndex=" + lastIndex
						+ " forward=" + context.isForward());
			}

			// GROUP
			if (child instanceof ICardGroup) {

				ICardGroup g = (ICardGroup) child;

				if (DEBUG) {
					System.out.println(
							"    [RECURSE] child is ICardGroup, j=" + j + " equals lastIndex? " + (j == lastIndex));
					System.out.println("    [RECURSE] group name=" + g.getName() + " size=" + g.size());
				}

				// Detect if anchor is inside this group
				boolean anchorInsideThisGroup = false;
				if (last != null) {
					for (int si = 0; si < last.getSegmentCount() - 1; si++) {
						if (last.getSegment(si).equals(g)) {
							anchorInsideThisGroup = true;
							break;
						}
					}
				}

				if (DEBUG) {
					System.out.println("    [RECURSE] group name=" + g.getName() + " size=" + g.size()
							+ " anchorInsideThisGroup=" + anchorInsideThisGroup);
				}

				// SAME-NAME GROUP FAST PATH
				if (!anchorInsideThisGroup && isSameNameGroup(g)) {

					boolean groupMatches = pat.matcher(g.getName()).find();

					if (DEBUG) {
						System.out.println("    [RECURSE] same-name group? groupMatches=" + groupMatches);
					}

					if (groupMatches) {
						Object[] kids = g.getChildren();
						int index = context.isForward() ? 0 : kids.length - 1;
						ICard targetCard = (ICard) kids[index];

						if (DEBUG) {
							System.out.println("    [RECURSE] SAME-NAME GROUP MATCH → "
									+ (context.isForward() ? "first" : "last") + " card=" + targetCard.getName());
						}

						TreePath matchPath = fullPath.createChildPath(targetCard);
						context.setFound(true, matchPath);
						continue;
					}
				}

				// NORMAL RECURSION
				if (j == lastIndex) {
					if (DEBUG)
						System.out.println("    [RECURSE] using cutHead(last) for anchor subgroup");
					searchTree(context, cutHead(last), needWrap, pat, g, fullPath);
					lastIndex = -1;
					if (DEBUG)
						System.out.println("    [RECURSE] returned from anchor subgroup, lastIndex reset to -1");
				} else {
					if (DEBUG)
						System.out.println("    [RECURSE] normal recursion with last=null");
					searchTree(context, null, needWrap, pat, g, fullPath);
				}

				if (DEBUG) {
					System.out.println("    [RECURSE] after recursion, found=" + context.isFound() + " cancelled="
							+ context.isCancelled());
				}

				continue;
			}

			// CARD
			ICard card = (ICard) child;
			boolean skip = (j == lastIndex);

			if (DEBUG) {
				System.out.println(
						"    [MATCH] card=" + card + " j=" + j + " lastIndex=" + lastIndex + " skipAnchorCard=" + skip);
			}

			if (!skip) {
				boolean m = match(pat, card);

				if (DEBUG) {
					System.out.println("[MATCH] name=\"" + card.getName() + "\" pattern=" + pat.pattern() + " j=" + j
							+ " lastIndex=" + lastIndex + " skipAnchorCard=" + skip + " result=" + m);
				}

				if (m) {
					if (DEBUG)
						System.out.println("    [MATCH] FOUND → setFound, fullPath=" + fullPath);
					context.setFound(true, fullPath);
					break;
				}
			}
		}

		if (DEBUG) {
			System.out.println(">>> LOOP END for group " + group + " found=" + context.isFound() + " cancelled="
					+ context.isCancelled());
		}
	}

	private static boolean isSameNameGroup(ICardGroup g) {

		Object[] kids = g.getChildren();
		if (kids.length == 0) {
			return false;
		}

		// All children must be cards
		for (Object k : kids) {
			if (!(k instanceof ICard)) {
				return false;
			}
		}

		// All cards must have the same name
		String name = ((ICard) kids[0]).getName();
		for (Object k : kids) {
			if (!((ICard) k).getName().equals(name)) {
				return false;
			}
		}

		// Group name must match card name
		boolean result = g.getName().equals(name);

		if (DEBUG) {
			System.out.println("[SAME-NAME] group=" + g.getName() + " children=" + kids.length + " result=" + result);
		}

		return result;
	}

	private static TreePath cutHead(TreePath path) {

		if (DEBUG) {
			System.out.println(
					"[CUTHEAD] in=" + path + " segmentCount=" + (path == null ? "null" : path.getSegmentCount()));
		}

		if (path == null || path.getSegmentCount() <= 1) {
			if (DEBUG) {
				System.out.println("[CUTHEAD] out=null (<=1 segment)");
			}
			return null;
		}

		Object[] segments = new Object[path.getSegmentCount() - 1];
		for (int i = 1; i < path.getSegmentCount(); i++) {
			segments[i - 1] = path.getSegment(i);
		}

		TreePath result = new TreePath(segments);

		if (DEBUG) {
			System.out.println("[CUTHEAD] out=" + result + " first=" + result.getFirstSegment() + " last="
					+ result.getLastSegment());
		}

		return result;
	}

	private static void searchFlat(SearchContext context, IFilteredCardStore store, Object last, boolean needWrap,
			Pattern pat) {
		if (store == null)
			return;
		Object[] elements = store.getElements();
		int lastIndex = getIndex(last, elements);

		if (DEBUG) {
			System.out.println("=== Flat DEBUG ===");
			System.out.println("Forward: " + context.isForward());
			System.out.println("Text: " + context.getText());
			System.out.println("Last: " + last);
			System.out.println("LastIndex: " + lastIndex);
			System.out.println("Elements length: " + elements.length);
			System.out.println("================");
		}

		if (context.isForward()) {
			lastIndex++;
			for (int i = lastIndex; i < elements.length; i++) {
				IMagicCard card = (IMagicCard) elements[i];
				if (match(pat, card)) {
					context.setFound(true, card);
					break;
				}
				if (context.isCancelled())
					return;
			}
			if (needWrap && !context.isFound()) {
				context.setDidWrap(true);
				for (int i = 0; i <= lastIndex; i++) {
					IMagicCard card = (IMagicCard) elements[i];
					if (match(pat, card)) {
						context.setFound(true, card);
						break;
					}
					if (context.isCancelled())
						return;
				}
			}
		} else {
			if (lastIndex <= -1)
				lastIndex = elements.length - 1;
			else
				lastIndex--;
			for (int i = lastIndex; i >= 0; i--) {
				IMagicCard card = (IMagicCard) elements[i];
				if (match(pat, card)) {
					context.setFound(true, card);
					break;
				}
				if (context.isCancelled())
					return;
			}
			if (needWrap && !context.isFound()) {
				context.setDidWrap(true);
				if (elements.length == 0)
					return;
				for (int i = elements.length - 1; i >= lastIndex && i >= 0; i--) {
					IMagicCard card = (IMagicCard) elements[i];
					if (match(pat, card)) {
						context.setFound(true, card);
						break;
					}
				}
				if (context.isCancelled())
					return;
			}
		}
	}

	private static boolean isCamelCase(String inputText) {
		char[] charArray = inputText.toCharArray();
		for (int i = 0; i < charArray.length; i++) {
			char c = charArray[i];
			if (Character.isUpperCase(c)) {
				return true;
			}
		}
		return false;
	}

	private static String escapeAndCamelCase(String inputText) {
		char[] charArray = inputText.toCharArray();
		StringBuffer res = new StringBuffer();
		for (int i = 0; i < charArray.length; i++) {
			char c = charArray[i];
			if (c == '*') {
				// '*' never appears in a card name, so it's free to use as an
				// explicit wildcard standing for exactly one arbitrary character
				// (e.g. "Jace*" matches a 5-letter name starting with "Jace")
				res.append(".");
				continue;
			}
			if (!(Character.isLetter(c) || c == ',' || c == ' ')) {
				// digits/punctuation must match literally - quote them so a regex
				// metacharacter (".", "(", "+"...) is escaped, not turned into a
				// "match any character" wildcard (searching "17-" must not match
				// every 3-character name, it must match the literal text "17-")
				res.append(Pattern.quote(String.valueOf(c)));
				continue;
			}
			if (Character.isUpperCase(c)) {
				if (i > 0)
					res.append("\\P{Lu}*");
			}
			res.append(c);
		}
		return res.toString();
	}

	/**
	 * @param pat
	 * @param card
	 * @return
	 */
	protected static boolean match(Pattern pat, ICard card) {
		String name = card.getName();
		boolean result = pat.matcher(name).find();

		if (DEBUG) {
			System.out.println("[MATCH] name=\"" + name + "\" pattern=" + pat.pattern() + " result=" + result);
		}

		return result;
	}

}
