package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.locale.CardTextLocal;
import com.reflexit.magiccards.core.locale.LocalizedText;

public class CardTypes implements ISearchableProperty {
	public static CardTextLocal TYPES = CardTextLocal.getCardText(LocalizedText.ENGLISH);

	private CardTypes() {
		this.names = new LinkedHashMap<String, String>();
		add(TYPES.Type_Land);
		add(TYPES.Type_Creature);
		add(TYPES.Type_Instant);
		add(TYPES.Type_Sorcery);
		add(TYPES.Type_Enchantment);
		add(TYPES.Type_Artifact);
		add(TYPES.Type_Planeswalker);
	}

	static CardTypes instance = new CardTypes();
	private LinkedHashMap<String, String> names;

	private void add(String string) {
		String id = getPrefConstant(string);
		this.names.put(id, string);
	}

	public boolean hasType(IMagicCard card, String type) {
		String typeText = card.getType();
		if (containsType(typeText, type))
			return true;
		String language = card.getLanguage();
		if (language != null && language.length() > 0) {
			CardTextLocal localized = CardTextLocal.getCardText(language);
			String localizedType = TYPES.translate(type, localized);
			if (localizedType != null && containsType(typeText, localizedType))
				return true;
		}
		if (type == TYPES.Type_Creature) {
			return hasType(card, TYPES.Type_Summon);
		}
		if (type == TYPES.Type_Instant) {
			return hasType(card, TYPES.Type_Interrupt);
		}
		return false;
	}

	private boolean containsType(String text, String type) {
		if (text == null)
			return false;
		return Pattern.compile(type, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.CARD_TYPE;
	}

	public static CardTypes getInstance() {
		return instance;
	}

	@Override
	public Collection<String> getIds() {
		return new ArrayList<String>(this.names.keySet());
	}

	public String getPrefConstant(String name) {
		return FilterField.getPrefConstant(getIdPrefix(), name);
	}

	@Override
	public String getNameById(String id) {
		return this.names.get(id);
	}

	public String getLocalizedNameById(String id) {
		String enName = getNameById(id);
		return TYPES.translate(enName, Locale.getDefault());
	}

	public static String[] proposals = new String[] { "Basic", "Elite", "Legendary", "Ongoing", "Snow", "Token",
			"World",

			"Artifact", "Battle", "Boss", "Conspiracy", "Creature", "Dungeon", "Emblem", "Enchantment", "Event", "Hero",
			"Instant", "Kindred", "Land", "Phenomenon", "Plane", "Planeswalker", "Scheme", "Sorcery", "Vanguard",

			"Siege",

			"Advisor", "Aetherborn", "Alien", "Ally", "Angel", "Antelope", "Ape", "Archer", "Archon", "Armadillo",
			"Army", "Artificer", "Assassin", "Assembly-Worker", "Astartes", "Atog", "Aurochs", "Automaton", "Avatar",
			"Azra", "Badger", "Balloon", "Barbarian", "Bard", "Basilisk", "Bat", "Bear", "Beast", "Beaver", "Beeble",
			"Beholder", "Berserker", "Bird", "Bison", "Blinkmoth", "Boar", "Brainiac", "Bringer", "Brushwagg", "C'tan",
			"Camarid", "Camel", "Capybara", "Caribou", "Carrier", "Cat", "Centaur", "Chicken", "Child", "Chimera",
			"Citizen", "Cleric", "Clown", "Cockatrice", "Construct", "Coward", "Coyote", "Crab", "Crocodile",
			"Custodes", "Cyberman", "Cyclops", "Dalek", "Dauthi", "Demigod", "Demon", "Deserter", "Detective", "Devil",
			"Dinosaur", "Djinn", "Doctor", "Dog", "Dragon", "Drake", "Dreadnought", "Drix", "Drone", "Druid", "Dryad",
			"Dwarf", "Echidna", "Efreet", "Egg", "Elder", "Eldrazi", "Elemental", "Elephant", "Elf", "Elk", "Employee",
			"Eternal", "Eye", "Faerie", "Ferret", "Fish", "Flagbearer", "Fox", "Fractal", "Frog", "Fungus", "Gamer",
			"Gamma", "Gargoyle", "Germ", "Giant", "Giraffe", "Gith", "Glimmer", "Gnoll", "Gnome", "Goat", "Goblin",
			"God", "Golem", "Gorgon", "Graveborn", "Gremlin", "Griffin", "Guest", "Hag", "Halfling", "Hamster", "Harpy",
			"Head", "Hedgehog", "Hellion", "Hero", "Hippo", "Hippogriff", "Homarid", "Homunculus", "Horror", "Horse",
			"Human", "Hydra", "Hyena", "Illusion", "Imp", "Incarnation", "Inhuman", "Inkling", "Inquisitor", "Insect",
			"Jackal", "Jellyfish", "Juggernaut", "Kangaroo", "Kavu", "Kirin", "Kithkin", "Knight", "Kobold", "Kor",
			"Kraken", "Kree", "Lamia", "Lammasu", "Leech", "Lemur", "Leviathan", "Lhurgoyf", "Licid", "Lizard", "Llama",
			"Lobster", "Manticore", "Masticore", "Mercenary", "Merfolk", "Metathran", "Minion", "Minotaur", "Mite",
			"Mole", "Monger", "Mongoose", "Monk", "Monkey", "Moogle", "Moonfolk", "Mount", "Mouse", "Mutant", "Myr",
			"Mystic", "Naga", "Nautilus", "Necron", "Nephilim", "Nightmare", "Nightstalker", "Ninja", "Noble", "Noggle",
			"Nomad", "Nymph", "Octopus", "Officer", "Ogre", "Ooze", "Orb", "Orc", "Orgg", "Otter", "Ouphe", "Ox",
			"Oyster", "Pangolin", "Peasant", "Pegasus", "Pentavite", "Performer", "Pest", "Phelddagrif", "Phoenix",
			"Phyrexian", "Pilot", "Pincher", "Pirate", "Plant", "Platypus", "Porcupine", "Possum", "Praetor",
			"Primarch", "Prism", "Processor", "Qu", "Rabbit", "Raccoon", "Ranger", "Rat", "Rebel", "Reflection",
			"Reveler", "Rhino", "Rigger", "Robot", "Rogue", "Rukh", "Sable", "Salamander", "Samurai", "Sand",
			"Saproling", "Satyr", "Scarecrow", "Scientist", "Scion", "Scorpion", "Scout", "Sculpture", "Seal", "Serf",
			"Serpent", "Servo", "Shade", "Shaman", "Shapeshifter", "Shark", "Sheep", "Siren", "Skeleton", "Skrull",
			"Skunk", "Slith", "Sliver", "Sloth", "Slug", "Snail", "Snake", "Soldier", "Soltari", "Sorcerer", "Spawn",
			"Specter", "Spellshaper", "Sphinx", "Spider", "Spike", "Spirit", "Splinter", "Sponge", "Spy", "Squid",
			"Squirrel", "Starfish", "Surrakar", "Survivor", "Symbiote", "Synth", "Teddy", "Tentacle", "Tetravite",
			"Thalakos", "Thopter", "Thrull", "Tiefling", "Time Lord", "Toy", "Treefolk", "Trilobite", "Triskelavite",
			"Troll", "Turtle", "Tyranid", "Unicorn", "Urzan", "Utrom", "Vampire", "Varmint", "Vedalken", "Villain",
			"Volver", "Wall", "Walrus", "Warlock", "Warrior", "Weasel", "Weird", "Werewolf", "Whale", "Wizard", "Wolf",
			"Wolverine", "Wombat", "Worm", "Wraith", "Wurm", "Yeti", "Zombie", "Zubera",

			"Aura", "Background", "Cartouche", "Case", "Class", "Curse", "Role", "Room", "Rune", "Saga", "Shard",
			"Shrine",

			"Cave", "Cloud", "Desert", "Forest", "Gate", "Island", "Lair", "Locus", "Mine", "Mountain", "Sphere",
			"Plains", "Planet", "Power-Plant", "Swamp", "Tower", "Town", "Urza's",

			"Abian", "Ajani", "Aminatou", "Angrath", "Arlinn", "Ashiok", "B.O.B.", "Bahamut", "Basri", "Bolas", "Calix",
			"Chandra", "Comet", "Dack", "Dakkon", "Daretti", "Davriel", "Deb", "Dellian", "Dihada", "Domri", "Dovin",
			"Duck", "Dungeon", "Ellywick", "Elminster", "Elspeth", "Ersta", "Estrid", "Freyalise", "Garruk", "Gideon",
			"Grist", "Guff", "Huatli", "Inzerva", "Jace", "Jared", "Jaya", "Jeska", "Kaito", "Karn", "Kasmina", "Kaya",
			"Kiora", "Koth", "Liliana", "Lolth", "Lukka", "Luxior", "Master", "Minsc", "Monopoly", "Mordenkainen",
			"Nahiri", "Narset", "Niko", "Nissa", "Nixilis", "Oko", "Quintorius", "Ral", "Rowan", "Saheeli", "Samut",
			"Sarkhan", "Serra", "Sivitri", "Sorin", "Svega", "Szat", "Tamiyo", "Tasha", "Teferi", "Teyo", "Tezzeret",
			"Tibalt", "Tyvar", "Ugin", "Urza", "Venser", "Vivien", "Vraska", "Vronos", "Wanderer", "Will", "Windgrace",
			"Wrenn", "Xenagos", "Yanggu", "Yanling", "Zariel",

			"Adventure", "Arcane", "Chorus", "Lesson", "Omen", "Trap",

	};

	/**
	 * @return
	 */
	public static String[] getProposals() {
		return proposals;
	}

	public Collection<String> getLocalizedNames() {
		ArrayList<String> names2 = new ArrayList<String>();
		for (Iterator<String> iterator = names.values().iterator(); iterator.hasNext();) {
			String string = iterator.next();
			names2.add(TYPES.translate(string, Locale.getDefault()));
		}
		return names2;
	}
}
