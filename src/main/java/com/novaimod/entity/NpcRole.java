package com.novaimod.entity;

/**
 * Все типы кастомных NPC мода.
 */
public enum NpcRole {
	GROCERY_SELLER_1("grocery_1", "Продавец продуктов", true),
	GROCERY_SELLER_2("grocery_2", "Мясник-деликатесник", true),
	MINER_TRADER("miner", "Шахтёр-торговец", true),
	ADOLF_GIDLER("adolf_gidler", "Адольф Гидлер", true),
	CASINO_BARTENDER("bartender", "Бармен", true),
	MFO_BARKER("barker", "Зазывала", true),
	MFO_LENDER("lender", "Кредитор", true),
	MFO_ELITE_SELLER("elite_seller", "Элитный продавец", true),
	// Персонажи вечеринки на Эго-острове
	EGO_CHEF("ego_chef", "Шеф-повар", true),
	EINSTEIN("einstein", "Альберт Эйнштейн", true),
	STALIN("stalin", "Иосиф Сталин", true),
	SWEETIE_FIK("sweetie_fik", "Свити Фик", true),
	PARTY_GIRL("party_girl", "Тусовщица", true),
	// Взыскатели МФО
	VOLODYA("volodya", "Володя", false),
	COLLECTOR("collector", "Коллектор", false),
	ARMENIAN_BRIGADE("brigade", "Бригада армян", false),
	MASTER_OF_SPORTS("master_of_sports", "Мастер спорта", false),
	OMON("omon", "ОМОН", false);

	private final String id;
	private final String displayName;
	private final boolean stationaryShopNpc;

	NpcRole(String id, String displayName, boolean stationaryShopNpc) {
		this.id = id;
		this.displayName = displayName;
		this.stationaryShopNpc = stationaryShopNpc;
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public boolean isStationaryShopNpc() {
		return stationaryShopNpc;
	}

	public static NpcRole fromId(String id) {
		for (NpcRole role : values()) {
			if (role.id.equals(id)) {
				return role;
			}
		}
		return GROCERY_SELLER_1;
	}
}
