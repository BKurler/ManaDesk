package com.reflexit.magiccards.ui.preferences.feditors;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.model.CardTypes;
import com.reflexit.magiccards.core.model.FilterField;
import com.reflexit.magiccards.core.model.Languages;
import com.reflexit.magiccards.ui.widgets.ContextAssist;

public class TextSearchPreferenceGroup extends MFieldEditorPreferencePage {
	private Collection<String> ids = new ArrayList<>(6);

	@Override
	public Collection<String> getIds() {
		return ids;
	}

	// private Group group;
	@Override
	protected void createFieldEditors() {
		// this.group = new Group(getFieldEditorParent(), SWT.NONE);
		// this.group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		// this.group.setText("Subtype");
		// Composite parent = this.group;
		// addCheckBox("Any", parent);
		String id;
		id = FilterField.NAME_LINE.getPrefConstant();
		getPreferenceStore().setDefault(id, "");
		ids.add(id);
		StringFieldEditor nameSfe = new StringFieldEditor(id, "Name", getFieldEditorParent());
		addField(nameSfe);
		String toolTip = "Search expression can contain words separated by spaces,\n"
				+ "which would be searched using AND connector.\n" //
				+ "Adding '-' in front of the word makes it NOT.\n"
				+ "Special symbols can be search using {X} type syntax (i.e. {T} for tap).\n" //
				+ "See help for details.";
		addTooltip(nameSfe, toolTip);
		// type
		String typeId = FilterField.TYPE_LINE.getPrefConstant();
		getPreferenceStore().setDefault(typeId, "");
		StringFieldEditor sfe = new StringFieldEditor(typeId, "Type", getFieldEditorParent());
		addContextAssist(sfe, CardTypes.getProposals());
		addField(sfe);
		addTooltip(sfe, toolTip);
		ids.add(typeId);
		// text
		String textId = FilterField.TEXT_LINE.getPrefConstant();
		getPreferenceStore().setDefault(textId, "");
		StringFieldEditor textSfe = new StringFieldEditor(textId, "Text", getFieldEditorParent());
		addContextAssist(textSfe, getTextProposals());
		addField(textSfe);
		addTooltip(textSfe, toolTip);
		ids.add(textId);
		// legality
		String legalityId = FilterField.FORMAT_TEXT.getPrefConstant();
		getPreferenceStore().setDefault(legalityId, "");

		String[] formats = new String[] { "", // no filter
				"Standard", "Pioneer", "Modern", "Commander", "Legacy", "Vintage" };

		// Convert to label/value pairs (label == value)
		String[][] legalities = new String[formats.length][2];
		for (int i = 0; i < formats.length; i++) {
			legalities[i][0] = formats[i];
			legalities[i][1] = formats[i];
		}

		ComboFieldEditor legalitySfe = new ComboFieldEditor(legalityId, "Legality", legalities, getFieldEditorParent());

		addField(legalitySfe);
		ids.add(legalityId);

		// artist
		String artistId = FilterField.ARTIST.getPrefConstant();
		getPreferenceStore().setDefault(artistId, "");
		StringFieldEditor artistSfe = new StringFieldEditor(artistId, "Artist", getFieldEditorParent());
		addField(artistSfe);
		addTooltip(artistSfe, toolTip);
		ids.add(artistId);
		// language
		String langId = FilterField.LANG.getPrefConstant();
		getPreferenceStore().setDefault(langId, "");
		String[][] langs;
		String[] langValues = Languages.getInstance().getLangValues();
		langs = new String[langValues.length + 1][2];
		langs[0][0] = langs[0][1] = "";
		for (int i = 0; i < langs.length - 1; i++) {
			langs[i + 1][0] = langs[i + 1][1] = langValues[i];
		}
		ComboFieldEditor langSfe = new ComboFieldEditor(langId, "Language", langs, getFieldEditorParent());
		addField(langSfe);
		ids.add(langId);
	}

	static String[] textProposals = new String[] {

			"Living weapon", "Jump-start", "Commander ninjutsu", "Legendary landwalk", "Nonbasic landwalk", "Megamorph",
			"Haunt", "Forecast", "Graft", "Fortify", "Frenzy", "Gravestorm", "Hideaway", "Level Up", "Infect", "Reach",
			"Rampage", "Phasing", "Multikicker", "Morph", "Provoke", "Modular", "Ninjutsu", "Replicate", "Recover",
			"Poisonous", "Reinforce", "Persist", "Retrace", "Rebound", "Miracle", "Overload", "Outlast", "Prowess",
			"Renown", "Myriad", "Shroud", "Trample", "Vigilance", "Storm", "Soulshift", "Splice", "Transmute", "Ripple",
			"Suspend", "Vanishing", "Transfigure", "Wither", "Undying", "Soulbond", "Unleash", "Ascend", "Assist",
			"Afterlife", "Companion", "Fabricate", "Embalm", "Escape", "Fuse", "Menace", "Ingest", "Melee", "Improvise",
			"Mentor", "Partner", "Mutate", "Tribute", "Surge", "Skulk", "Riot", "Spectacle", "Forestwalk", "Islandwalk",
			"Mountainwalk", "Double strike", "Cumulative upkeep", "First strike", "Scavenge", "Encore", "Deathtouch",
			"Defender", "Amplify", "Affinity", "Bushido", "Convoke", "Bloodthirst", "Absorb", "Aura Swap", "Changeling",
			"Conspire", "Cascade", "Annihilator", "Battle Cry", "Cipher", "Bestow", "Dash", "Awaken", "Crew",
			"Aftermath", "Afflict", "Flanking", "Foretell", "Fading", "Eternalize", "Entwine", "Epic", "Dredge",
			"Delve", "Evoke", "Exalted", "Evolve", "Extort", "Dethrone", "Exploit", "Devoid", "Emerge", "Escalate",
			"Flying", "Haste", "Hexproof", "Indestructible", "Intimidate", "Lifelink", "Horsemanship", "Kicker",
			"Madness", "Swampwalk", "Desertwalk", "Craft", "Plainswalk", "Split second", "Augment", "Double agenda",
			"Reconfigure", "Ward", "Partner with", "Daybound", "Nightbound", "Decayed", "Disturb", "Squad", "Enlist",
			"Read Ahead", "Ravenous", "Blitz", "Offering", "Living metal", "Backup", "Banding", "Hidden agenda",
			"For Mirrodin!", "Friends forever", "Casualty", "Protection", "Compleated", "Enchant", "Flash", "Boast",
			"Demonstrate", "Sunburst", "Flashback", "Cycling", "Equip", "Buyback", "Hexproof from",
			"More Than Meets the Eye", "Cleave", "Champion", "Specialize", "Training", "Prototype", "Toxic", "Unearth",
			"Intensity", "Plainscycling", "Swampcycling", "Typecycling", "Wizardcycling", "Mountaincycling",
			"Basic landcycling", "Islandcycling", "Forestcycling", "Slivercycling", "Landcycling", "Bargain",
			"Choose a background", "Echo", "Disguise", "Doctor's companion", "Landwalk", "Umbra armor", "Freerunning",
			"Spree", "Saddle", "Shadow", "Warp", "Station", "Devour", "Undaunted", "Offspring", "Impending", "Gift",
			"Harmonize", "Exhaust", "Max speed", "Fear", "Tiered", "Mobilize", "Double team", "Job select", "Mayhem",
			"Web-slinging", "Prowl", "Solved", "Sneak", "Increment", "Paradigm", "Power-up", "Firebending",

			"Scry", "Seek", "Activate", "Attach", "Cast", "Counter", "Create", "Destroy", "Discard", "Exchange",
			"Exile", "Adapt", "Support", "Play", "Regenerate", "Reveal", "Sacrifice", "Shuffle", "Tap", "Untap", "Vote",
			"Time Travel", "Goad", "Transform", "Surveil", "Planeswalk", "Mill", "Learn", "Connive",
			"Venture into the dungeon", "Exert", "Open an Attraction", "Food", "Discover", "Abandon", "Explore",
			"Treasure", "Roll to Visit Your Attractions", "Set in motion", "Fateseal", "Manifest", "Populate", "Detain",
			"Investigate", "Monstrosity", "Clash", "Incubate", "Proliferate", "Meld", "Convert", "Fight", "Bolster",
			"Assemble", "Conjure", "Amass", "Cloak", "Suspect", "Collect evidence", "Role token", "Plot", "Harness",
			"Heist", "Forage", "Manifest dread", "Endure", "Prepared", "Incorporate", "Waterbend", "Airbend",
			"Earthbend", "Blight", "Behold", "Double", "Triple",

			"Eerie", "Battalion", "Bloodrush", "Channel", "Chroma", "Cohort", "Constellation", "Converge", "Delirium",
			"Domain", "Fateful hour", "Ferocious", "Formidable", "Grandeur", "Hellbent", "Heroic", "Imprint",
			"Inspired", "Join forces", "Kinship", "Landfall", "Lieutenant", "Metalcraft", "Morbid", "Parley",
			"Radiance", "Raid", "Rally", "Spell mastery", "Strive", "Sweep", "Tempting offer", "Threshold",
			"Will of the council", "Adamant", "Addendum", "Council's dilemma", "Eminence", "Enrage", "Hero's Reward",
			"Kinfall", "Landship", "Legacy", "Revolt", "Underdog", "Undergrowth", "Void", "Descend",
			"Fathomless descent", "Magecraft", "Teamwork", "Pack tactics", "Coven", "Alliance", "Corrupted",
			"Secret council", "Celebration", "Paradox", "Disappear", "Will of the Planeswalkers", "Survival", "Flurry",
			"Valiant", "Start your engines!", "Renew", "Repartee", "Opus", "Infusion", "Covercast", "Vivid",

	};

	/**
	 * TODO: refactor
	 * 
	 * @return
	 */
	private String[] getTextProposals() {
		// TODO Auto-generated method stub
		return textProposals;
	}

	private void addContextAssist(StringFieldEditor sfe, String[] proposals) {
		Text t = sfe.getTextControl(getFieldEditorParent());
		ContextAssist.addContextAssist(t, proposals, true);
	}

	@Override
	protected void adjustGridLayout() {
		GridLayout layout = (GridLayout) ((Composite) this.getControl()).getLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		super.adjustGridLayout();
	}
}
