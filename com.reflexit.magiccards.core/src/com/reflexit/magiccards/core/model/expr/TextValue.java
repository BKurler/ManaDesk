/*
 * Contributors:
 *     Rémi Dutil (2026) - stripParens: set only for a [ability] search's
 *                         Pattern (see MagicCardFilter#tokenSearch()'s ABI
 *                         case) - matches what Abilities.RegexAbilityMatcher
 *                         #match() already does for ability grouping/stats,
 *                         so a card that only mentions an ability by name
 *                         inside ANOTHER ability's reminder text (e.g. Reach
 *                         saying "creatures with flying") no longer counts as
 *                         having that ability itself. Left false for every
 *                         other Pattern-based TextValue (Name's '*'/'?'
 *                         wildcards, a user's own m/regex/) - those must
 *                         match the field's real, complete text, parentheses
 *                         included.
 */
package com.reflexit.magiccards.core.model.expr;

import java.util.regex.Pattern;

public class TextValue extends Value {
	public boolean wordBoundary = true;
	public boolean caseSensitive = false;
	public boolean regex = false;
	public boolean stripParens = false;
	public Pattern pattern;

	public TextValue(String name, boolean wordBoundary, boolean caseSensitive, boolean regex) {
		super(name);
		this.wordBoundary = wordBoundary;
		this.caseSensitive = caseSensitive;
		this.regex = regex;
	}

	public TextValue(Pattern pattern) {
		this(pattern, false);
	}

	public TextValue(Pattern pattern, boolean stripParens) {
		super(pattern.toString());
		this.wordBoundary = false;
		this.caseSensitive = false;
		this.regex = true;
		this.pattern = pattern;
		this.stripParens = stripParens;
	}

	public void setWordBoundary(boolean b) {
		this.wordBoundary = b;
	}

	public Pattern getPattern() {
		if (pattern == null) {
			pattern = toPattern();
		}
		return pattern;
	}

	private Pattern toPattern() {
		if (regex)
			return Pattern.compile(name());
		int flags = 0;
		if (!caseSensitive)
			flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		if (wordBoundary)
			return Pattern.compile("\\b\\Q" + name() + "\\E\\b", flags);
		flags |= Pattern.LITERAL;
		return Pattern.compile(name(), flags);
	}

	public String getText() {
		return name();
	}
}