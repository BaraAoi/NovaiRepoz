package com.novaimod.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.novaimod.data.ModPersistentState;
import com.novaimod.data.WorldVirginLedger;
import com.novaimod.network.ModServerNetworking;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;
import com.novaimod.world.DetentionManager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Универсальная человекоподобная сущность NPC мода.
 * Ни броня, ни оружие визуально не отображаются (рендерится чистой моделью игрока без слоёв брони и предметов в руках),
 * при этом боевые характеристики (кольчуга + каменный меч у коллекторов, железо + железный меч Острота I у мастеров спорта и т.д.)
 * работают через атрибуты защиты и урона.
 */
public class ModNpcEntity extends PathAwareEntity {
	private static final TrackedData<String> ROLE_ID = DataTracker.registerData(ModNpcEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<String> VARIANT = DataTracker.registerData(ModNpcEntity.class, TrackedDataHandlerRegistry.STRING);

	private static final String[] BARKER_LINES = new String[]{
			"Заходите в нашу микрокредитную организацию! Займ БЕЗ ПРОЦЕНТОВ, полная халява!",
			"Деньги сразу на счёт! Никаких переплат, одобряем любую сумму за секунду!",
			"Нужны алмазы и элитный шмот? Бери беспроцентный займ у нашего кредитора — чистая халява!",
			"Акция века: бесплатные деньги всем желающим! Заходи, не проходи мимо!"
	};

	private static final String[] GIDLER_LINES = new String[]{
			"Штайнер всё ещё собирает резервы для контратаки? Я уже целую вечность жду его доклада на этом лавовом пляже!",
			"Говорили мне штабисты — не воюй сразу на два фронта и не суйся в лютую зиму в летних шинелях... Зато тут круглый год жара!",
			"Приёмная комиссия Венской академии заявила, что у меня нет чувства перспективы! Пусть взглянут на перспективу этого огненного горизонта!",
			"В апреле сорок пятого в сыром бункере хотя бы потолок не капал магмой, зато здесь никто не стучит снарядами над головой!",
			"Мои генералы вечно валили свои просчёты на морозы и распутицу — интересно, что бы они сказали про климат Незера?!",
			"Мне обещали непобедимое чудо-оружие к весне, а в итоге выписали бессрочную путёвку на раскалённый песок!",
			"Я чертил грандиозные купола и колоннады, а в этом измерении даже прямой мост через лаву построить не удосужились!",
			"Карта фронтов опять сгорела прямо на столе — впрочем, в сорок пятом сводки со всех направлений всё равно были скверными!",
			"Даже под грохот канонады над бункером рейхсканцелярии спалось тише, чем под вечные завывания этих гастов!"
	};

	private static final String[] CHEF_LINES = new String[]{
			"Честно тебе скажу — мясо какое-то странное! В накладной значится «фермерский люкс», но я такой анатомии у коров сроду не видал!",
			"В сундуках у плиты лежит свежая партия... Пахнет вроде аппетитно, но мясо какое-то реально странное, аж мурашки по коже!",
			"Когда я резал этот кусок, мне показалось, что он на секунду засветился! Короче, мясо странное — ешь на свой страх и риск!",
			"Сталин уже дважды заходил на кухню и строго спрашивал, откуда поставщик... А я сам без понятия, что это за странное мясо!",
			"Жарится за минуту, на вкус нежнее мраморного рибая, но клянусь колпаком — мясо какое-то очень странное!"
	};

	private static final String[] EINSTEIN_LINES = new String[]{
			"Согласно теории относительности, пять минут на мягких подушках в этом джакузи с мозаичным полом длятся как две секунды на лекции!",
			"E = mc², друг мой! Где E — это энергия этого неонового танцпола, помноженная на квадрат громкости сабвуфера!",
			"Вы видели эту знаменитую бело-синюю церковь с золотым куполом на южном мысе острова? Акустика под куполом просто феноменальная!",
			"Пространство-время явно искривляется вокруг кухни повара — особенно когда открываешь сундук с его странным мясом!",
			"Всё в мире относительно: в бассейне вода кажется прохладной, а после джакузи — ледяной квантовой суперпозицией!"
	};

	private static final String[] STALIN_LINES = new String[]{
			"Товарищ диджей выдаёт басы строго по пятилетнему плану — даже с перевыполнением нормы на танцполе!",
			"Курорт культурный: мозаичный пол в джакузи, мягкие подушки на ступеньках... Но к странному мясу на кухне у нас есть вопросы!",
			"Осмотрел знаменитую полосатую церковь с золотым куполом на острове — архитектура приметная, издалека с моря видно!",
			"Порядок на вечеринке образцовый: кто не танцует на танцполе — тот отдыхает на подушках в тёплом джакузи!",
			"Хороший табак, тёплый морской бриз и музыка гремит... Но повара с его странными сундуками надо бы проверить!"
	};

	private static final String[] SWEETIE_FIK_LINES = new String[]{
			"Всем привет, дорогие друзья, с вами Свити Фик! И сегодня мы проводим 100 ДНЕЙ на секретном Эго-острове в Майнкрафте!",
			"Ребята, мы нашли ту самую знаменитую бело-синюю церковь на острове! Ставьте лайк, и мы заглянем внутрь под золотой купол!",
			"Слушай, принеси мне с кухни кусок того самого «Странного мяса» из сундука для челленджа в ролике — заплачу 1500 ₽ за реквизит!",
			"Я только что снял шортс с Эйнштейном и Сталиным на танцполе — этот ролик точно залетит в тренды!",
			"Джакузи обновили вообще топово: убрали песок душ, выложили узорчатый пол и положили подушки из новых обновлений прямо на ступеньки!"
	};

	private static final String[] PARTY_GIRL_LINES = new String[]{
			"Включай диджейский пульт скорее — когда танцпол начинает переливаться огнями, вся вечеринка оживает!",
			"В новом джакузи такой красивый мозаичный пол и мягкие подушки прямо на кварцевых ступеньках — сидеть в тёплой воде одно удовольствие!",
			"Мы уже фоткались у знаменитой бело-синей церкви с золотым куполом на краю острова — выглядит просто один в один!",
			"Ты уже заглядывал на кухню? Повар шепчется, что в сундуках какое-то странное мясо — мы решили лучше пить лимонад!",
			"Давай к нам на танцпол! Если запрыгать под бит диджея, гости даже чаевые за крутые движения кидают!"
	};

	private static final String[] DEBTOR_IDIOT_LINES = new String[]{
			"Брат, я взял в МФО «беспроцентный займ» 500 рублей, чтобы поставить всё на ЗЕРО в казино... Прошло две недели по 20% в день — теперь я должен 48 МИЛЛИАРДОВ и левую почку!",
			"Зазывала кричал: «Полная халява, никаких переплат!» Я на радостях скрафтил алмазную мотыгу и лёг спать... А наутро меня упаковал ОМОН прямо сюда в подвал!",
			"Мой сокамерник треснул Коллектора палкой, потом наехал на Мастера спорта — теперь сидит тут до 3026 года без права переписки!",
			"Ходят слухи, что если реально закрыть кредит в МФО по-честному, выдают золотой Ключ Бюрократии... Но кто вообще читает мелкий шрифт?!",
			"В соседней камере кошкодевочку посадили вообще за то, что у неё нет Справки №38-МЯУ на ношение пушистого хвоста! Открой ей калитку хоть ты!",
			"Я просил адвоката, а мне из Приёмной спустили бланк заявления на выдачу бланка заявления о предоставлении ручки!"
	};

	private static final String[] REGISTRAR_LINES = new String[]{
			"Гражданин, вы куда без бахил и талончика?! За направлением в Окошко №2 сначала возьмите Справку об отсутствии Справки в Окошке №4!",
			"Навигация по Министерству: [-1 этаж] Подвал и тюрячка должников; [1 этаж] Приёмная и МФО; [2 этаж] Коворкинг и Кабинеты Шишек; [3 этаж] Склад Архива (туда не ходите — там Ревизор!); [4 этаж] Столовая; [5 этаж] Запечатано с XVIII века; [6 этаж] Кабинет Босса!",
			"Окошко №3 закрыто на обед с 09:00 до 18:00, а с 18:00 до 09:00 — технический перерыв! Пользуйтесь лифтом в северном холле или лестницей!",
			"Хотите вернуться обратно в свой обычный мир? Просто толкните Парадную Дверь Выхода на южной стене Приёмной!"
	};

	private static final String[] TIRED_CLERK_LINES = new String[]{
			"Я перекладываю отчёт №404-Б из левой стопки в правую с 1998 года... Если моргну дольше секунды — лишат квартальной премии в 14 рублей...",
			"До пенсии мне осталось всего 740 рабочих столетий и две подписи замначальника со второго кабинета...",
			"В кулере закончилась вода ещё при прошлом тысячелетии... Эх, сейчас бы кружку молока, печеньку или хоть ложку сахара...",
			"Важные шишки в своих дубовых кабинетах опять крутят казённый бюджет на столе Монетки, а мы тут в коворкинге дышим пылью от принтеров...",
			"Только не спускайся без дела на 3-й этаж на склад бумаг — там между стеллажами бродит Бессмертный Ревизор Архива, его вообще ничем не убить, можно только прокрасться на корточках!"
	};

	private static final String[] VIP_BUREAUCRAT_LINES = new String[]{
			"Вы записывались на приём за 90 рабочих лет?! Ладно, раз уж зашли без стука — взгляните на наш закрытый номенклатурный спецкаталог!",
			"Видите эти горы папок? Каждая — отказ гражданам по причине неправильного наклона запятой в третьем абзаце!",
			"На 6-м этаже сидит сам Верховный Председатель... Даже мы заходим к нему в кабинет только на полусогнутых!"
	};

	private static final String[] CAFETERIA_LINES = new String[]{
			"Гражданин! Без талона на ложку — вилку не выдаём! А талон на ложку оформляется только при наличии чистой тарелки с печатью Санэпидемстанции!",
			"Компот из сухофруктов отпускается строго по паспорту и в присутствии двух понятых! Сахар — по письменному ходатайству!",
			"Что значит «котлета светится в темноте»?! Шеф-повар заверил накладную печатью ГОСТ-666: мясо немного странное, но калорийное!",
			"Полагается один бесплатный Пайковый Обед в сутки или обмен чистого листа Бумаги (бланка талона) на горячую порцию!"
	};

	private static final String[] BUREAU_GUARD_LINES = new String[]{
			"Гражданин, соблюдайте тишину и очерёдность в коридорах Министерства! Не задерживайтесь у режимных камер!",
			"Мы тут на дежурной смене — все тюрячки и СИЗО теперь перенесены сюда на -1 этаж, чтобы в обычном мире пейзаж не портить!",
			"Без дела к Боссу на 6-й этаж лучше не суйся — у него характер тяжелее чугунной печати!"
	};

	private UUID targetPlayerUuid;
	private BlockPos anchorPos;
	private boolean fleeing = false;
	private boolean leavingPeacefully = false;
	private boolean gidlerTokenGiven = false;
	private boolean sweetieQuestDone = false;
	private boolean registrarSlipGiven = false;
	private boolean bossAggro = false;
	private int bossPhase = 0;
	private int hitsFromPlayer = 0;
	private int actionCooldown = 0;
	private int specialCooldown = 60;
	private int lifeTicks = 0;
	private final ServerBossBar bossBar = new ServerBossBar(
			Text.literal("ВЕРХОВНЫЙ ПРЕДСЕДАТЕЛЬ БЮРОКРАТИИ — ОТДЕЛ ОКОНЧАТЕЛЬНОГО ОТКАЗА").formatted(Formatting.RED, Formatting.BOLD),
			BossBar.Color.RED,
			BossBar.Style.NOTCHED_10
	);

	public ModNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
		this.setPersistent();
	}

	public static DefaultAttributeContainer.Builder createNpcAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0D)
				.add(EntityAttributes.GENERIC_ARMOR, 0.0D)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE_ID, NpcRole.GROCERY_SELLER_1.getId());
		builder.add(VARIANT, "");
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 10.0F));
		this.goalSelector.add(9, new LookAroundGoal(this));
	}

	public void configureRole(NpcRole role, String variant) {
		String cleanVar = variant == null ? "" : variant;
		this.dataTracker.set(ROLE_ID, role.getId());
		this.dataTracker.set(VARIANT, cleanVar);
		this.setCustomName(Text.literal(resolveDisplayName(role, cleanVar)));
		this.setCustomNameVisible(true);
		this.anchorPos = this.getBlockPos();
		applyRoleStats(role);
	}

	public boolean isVirginStructureCandidate() {
		NpcRole role = getNpcRole();
		return this.targetPlayerUuid == null
				&& (role.isStationaryShopNpc() || role == NpcRole.ARCHIVAL_HORROR || role == NpcRole.BUREAUCRACY_BOSS);
	}

	private static String resolveDisplayName(NpcRole role, String variant) {
		if (role == NpcRole.MFO_ELITE_SELLER) {
			return "2".equals(variant) ? "Элитный антиквар МФО" : "Элитный оружейник МФО";
		}
		if (role == NpcRole.DEBTOR_IDIOT) {
			return switch (variant) {
				case "1" -> "Дебил-должник Толик (Долг: 48 МЛРД ₽)";
				case "2" -> "Неплательщик Серёга (Поставил кредит на ЗЕРО)";
				case "3" -> "Бедолага Васян (Взял займ на мотыгу)";
				case "queue" -> "Вечный очередник (Ждёт с 2007 г.)";
				default -> "Злостный неплательщик МФО";
			};
		}
		if (role == NpcRole.VIP_BUREAUCRAT) {
			return "2".equals(variant) ? "Главный Инспектор Взысканий" : "Начальник Департамента Отписок";
		}
		if (role == NpcRole.BUREAU_REGISTRAR) {
			return "2".equals(variant) ? "Старший Инспектор Окошка №2" : "Регистраторша Раиса Захаровна";
		}
		if ("bureau_guard".equals(variant)) {
			return "Дежурный " + role.getDisplayName();
		}
		return role.getDisplayName();
	}

	private void applyRoleStats(NpcRole role) {
		switch (role) {
			case VOLODYA -> {
				setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D);
				setAttr(EntityAttributes.GENERIC_ARMOR, 2.0D);
				setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0D);
				setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D);
			}
			case COLLECTOR -> {
				// Кольчужная броня (12 ед. брони) + каменный меч (5 ед. урона)
				setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 22.0D);
				setAttr(EntityAttributes.GENERIC_ARMOR, 12.0D);
				setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0D);
				setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30D);
			}
			case ARMENIAN_BRIGADE -> {
				setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 32.0D);
				setAttr(EntityAttributes.GENERIC_ARMOR, 14.0D);
				setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0D);
				setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.34D);
				setAttr(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5D);
			}
			case MASTER_OF_SPORTS -> {
				// Железная броня (15 ед. брони) + железный меч на Остроту I (6 + 1 = 7 ед. урона)
				setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 26.0D);
				setAttr(EntityAttributes.GENERIC_ARMOR, 15.0D);
				setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0D);
				setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.33D);
				setAttr(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.35D);
			}
			case OMON -> {
				setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 50.0D);
				setAttr(EntityAttributes.GENERIC_ARMOR, 20.0D);
				setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0D);
				setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.37D);
				setAttr(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8D);
			}
			case ARCHIVAL_HORROR -> {
				// Бессмертный монстр Склада Бумаг (Уровень 3) — высокий, жуткий и неубиваемый!
				setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 200.0D);
				setAttr(EntityAttributes.GENERIC_ARMOR, 24.0D);
				setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0D);
				setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.31D);
				setAttr(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
				setAttr(EntityAttributes.GENERIC_SCALE, 1.42D);
			}
			case BUREAUCRACY_BOSS -> {
				// Финальный Босс 6-го этажа!
				setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 350.0D);
				setAttr(EntityAttributes.GENERIC_ARMOR, 14.0D);
				setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.5D);
				setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32D);
				setAttr(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.65D);
				setAttr(EntityAttributes.GENERIC_SCALE, 1.22D);
			}
			default -> {
				setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 40.0D);
				setAttr(EntityAttributes.GENERIC_ARMOR, 10.0D);
				setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22D);
				setAttr(EntityAttributes.GENERIC_SCALE, 1.0D);
			}
		}
		this.setHealth(this.getMaxHealth());
	}

	private void setAttr(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, double val) {
		EntityAttributeInstance inst = this.getAttributeInstance(attr);
		if (inst != null) {
			inst.setBaseValue(val);
		}
	}

	public NpcRole getNpcRole() {
		return NpcRole.fromId(this.dataTracker.get(ROLE_ID));
	}

	public String getVariant() {
		return this.dataTracker.get(VARIANT);
	}

	public void setTargetPlayerUuid(UUID uuid) {
		this.targetPlayerUuid = uuid;
	}

	public void setAnchorPos(BlockPos pos) {
		this.anchorPos = pos;
	}

	public void forceFleeFromSeal() {
		this.fleeing = true;
		this.lifeTicks = 0;
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		this.bossBar.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bossBar.clearPlayers();
		super.remove(reason);
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource damageSource) {
		if (damageSource.isOf(DamageTypes.OUT_OF_WORLD) || damageSource.isOf(DamageTypes.GENERIC_KILL)) {
			return false;
		}
		// Стационарные торговцы, бармен, кредитор, зазывала, клерки, неплательщики и Гидлер неуязвимы
		if (getNpcRole().isStationaryShopNpc() || "bureau_guard".equals(getVariant())) {
			return !(damageSource.getAttacker() instanceof PlayerEntity p && p.isCreative());
		}
		if ("cage".equals(getVariant()) && getNpcRole() == NpcRole.ARMENIAN_BRIGADE) {
			return !(damageSource.getAttacker() instanceof PlayerEntity p && p.isCreative());
		}
		return super.isInvulnerableTo(damageSource);
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (this.getWorld().isClient()) {
			return getNpcRole().isStationaryShopNpc() ? ActionResult.SUCCESS : ActionResult.PASS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}

		NpcRole role = getNpcRole();
		switch (role) {
			case GROCERY_SELLER_1 -> {
				ModServerNetworking.openShopScreen(serverPlayer, "Продуктовый — Пекарь (Обычное и Элитное)", "grocery_1");
				return ActionResult.CONSUME;
			}
			case GROCERY_SELLER_2 -> {
				ModServerNetworking.openShopScreen(serverPlayer, "Продуктовый — Деликатесы (Обычное и Элитное)", "grocery_2");
				return ActionResult.CONSUME;
			}
			case MINER_TRADER -> {
				ModServerNetworking.openShopScreen(serverPlayer, "Шахтёрская лавка и Ремонт", "miner");
				return ActionResult.CONSUME;
			}
			case MFO_LENDER -> {
				ModServerNetworking.openLoanScreen(serverPlayer);
				return ActionResult.CONSUME;
			}
			case MFO_ELITE_SELLER -> {
				if ("2".equals(getVariant())) {
					ModServerNetworking.openShopScreen(serverPlayer, "Элитный отдел МФО — Артефакты", "elite_mfo_2");
				} else {
					ModServerNetworking.openShopScreen(serverPlayer, "Элитный отдел МФО — Снаряжение", "elite_mfo_1");
				}
				return ActionResult.CONSUME;
			}
			case MFO_BARKER -> {
				serverPlayer.sendMessage(
						Text.literal("[Зазывала]: Чего стоишь?! Заходи внутрь к Кредитору — займы БЕЗ ПРОЦЕНТОВ, чистая халява!")
								.formatted(Formatting.GOLD),
						false
				);
				return ActionResult.CONSUME;
			}
			case CASINO_BARTENDER -> {
				String style = getVariant().isEmpty() ? "plains" : getVariant();
				ModServerNetworking.openShopScreen(serverPlayer, "Барная стойка Казино", "bartender:" + style);
				return ActionResult.CONSUME;
			}
			case ADOLF_GIDLER -> {
				handleGidlerInteraction(serverPlayer);
				return ActionResult.CONSUME;
			}
			case EGO_CHEF -> {
				sayRandomLine(serverPlayer, "Шеф-повар", CHEF_LINES, Formatting.YELLOW);
				return ActionResult.CONSUME;
			}
			case EINSTEIN -> {
				sayRandomLine(serverPlayer, "Альберт Эйнштейн", EINSTEIN_LINES, Formatting.AQUA);
				return ActionResult.CONSUME;
			}
			case STALIN -> {
				sayRandomLine(serverPlayer, "Иосиф Сталин", STALIN_LINES, Formatting.RED);
				return ActionResult.CONSUME;
			}
			case SWEETIE_FIK -> {
				handleSweetieFikInteraction(serverPlayer);
				return ActionResult.CONSUME;
			}
			case PARTY_GIRL -> {
				sayRandomLine(serverPlayer, "Тусовщица", PARTY_GIRL_LINES, Formatting.LIGHT_PURPLE);
				return ActionResult.CONSUME;
			}
			case DEBTOR_IDIOT -> {
				sayRandomLine(serverPlayer, resolveDisplayName(role, getVariant()), DEBTOR_IDIOT_LINES, Formatting.GOLD);
				return ActionResult.CONSUME;
			}
			case BUREAU_REGISTRAR -> {
				handleRegistrarInteraction(serverPlayer);
				return ActionResult.CONSUME;
			}
			case TIRED_CLERK -> {
				handleTiredClerkInteraction(serverPlayer);
				return ActionResult.CONSUME;
			}
			case VIP_BUREAUCRAT -> {
				sayRandomLine(serverPlayer, resolveDisplayName(role, getVariant()), VIP_BUREAUCRAT_LINES, Formatting.DARK_PURPLE);
				String catalog = "2".equals(getVariant()) ? "elite_mfo_2" : "elite_mfo_1";
				ModServerNetworking.openShopScreen(serverPlayer, "Спецраспределитель Важной Шишки", catalog);
				return ActionResult.CONSUME;
			}
			case CAFETERIA_BUREAUCRAT -> {
				handleCafeteriaInteraction(serverPlayer);
				return ActionResult.CONSUME;
			}
			case BUREAUCRACY_BOSS -> {
				if (!this.bossAggro) {
					ModPersistentState st = ModPersistentState.get(serverPlayer.getServer());
					long debt = st.getAccount(serverPlayer.getUuid()).debt;
					String msg = debt > 0L
							? "О, явился должник! За тобой числится " + debt + " ₽ в моих микрозаймах! Хочешь списать долг? Ударь меня и попробуй пережить Генеральную Проверку — или проваливай вниз в очередь!"
							: "Добро пожаловать на 6-й этаж, гражданин! Все эти очереди, тюрячки, склады и столовые работают на меня. Осмелишься бросить вызов Системе — ударь меня, и начнётся Генеральная Проверка!";
					serverPlayer.sendMessage(
							Text.literal("[Верховный Председатель Бюрократии]: ").formatted(Formatting.DARK_RED, Formatting.BOLD)
									.append(Text.literal(msg).formatted(Formatting.GOLD)),
							false
					);
					serverPlayer.getServerWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.HOSTILE, 1.0F, 0.7F);
					return ActionResult.CONSUME;
				}
				return ActionResult.PASS;
			}
			default -> {
				if ("bureau_guard".equals(getVariant())) {
					sayRandomLine(serverPlayer, resolveDisplayName(role, getVariant()), BUREAU_GUARD_LINES, Formatting.RED);
					return ActionResult.CONSUME;
				}
				return ActionResult.PASS;
			}
		}
	}

	private void handleRegistrarInteraction(ServerPlayerEntity player) {
		sayRandomLine(player, resolveDisplayName(NpcRole.BUREAU_REGISTRAR, getVariant()), REGISTRAR_LINES, Formatting.AQUA);
		if (!this.registrarSlipGiven) {
			this.registrarSlipGiven = true;
			ItemStack slip = new ItemStack(Items.PAPER, 1);
			slip.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Талон-Направление №Ж-994 (С печатью Приёмной)").formatted(Formatting.YELLOW, Formatting.BOLD));
			slip.set(DataComponentTypes.LORE, new LoreComponent(List.of(
					Text.literal("Предъявите Инспектору Столовой на 4-м этаже для обмена на горячий обед!").formatted(Formatting.GRAY),
					Text.literal("«Без бумажки ты букашка, а с бумажкой — человек!»").formatted(Formatting.GOLD)
			)));
			if (!player.getInventory().insertStack(slip)) {
				player.dropItem(slip, false);
			}
			player.sendMessage(Text.literal("📄 Выдан «Талон-Направление №Ж-994»!").formatted(Formatting.GREEN), true);
		}
	}

	private void handleTiredClerkInteraction(ServerPlayerEntity player) {
		ItemStack held = player.getMainHandStack();
		if (held.isOf(Items.COOKIE) || held.isOf(Items.SUGAR) || held.isOf(Items.PUMPKIN_PIE)) {
			if (!player.isInCreativeMode()) {
				held.decrement(1);
			}
			ModPersistentState st = ModPersistentState.get(player.getServer());
			ModPersistentState.PlayerFinanceData acc = st.getAccount(player.getUuid());
			acc.balance += 350L;
			st.markDirty();
			ModServerNetworking.syncAccount(player);
			player.getServerWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.9F, 1.15F);
			player.sendMessage(
					Text.literal("[Выгоревший клерк]: ").formatted(Formatting.AQUA, Formatting.BOLD)
							.append(Text.literal("Спасибо тебе, добрый человек... Я не ел сладкого со времён дефолта! Держи 350 ₽ из моих суточных!").formatted(Formatting.GREEN)),
					false
			);
			return;
		}
		sayRandomLine(player, "Выгоревший клерк", TIRED_CLERK_LINES, Formatting.GRAY);
	}

	private void handleCafeteriaInteraction(ServerPlayerEntity player) {
		ModPersistentState st = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData acc = st.getAccount(player.getUuid());
		long day = player.getServer().getOverworld().getTime() / 24000L;
		ItemStack held = player.getMainHandStack();

		if (held.isOf(Items.PAPER)) {
			if (!player.isInCreativeMode()) {
				held.decrement(1);
			}
			ItemStack meal = new ItemStack(Items.COOKED_BEEF, 2);
			ItemStack pie = new ItemStack(Items.PUMPKIN_PIE, 1);
			if (!player.getInventory().insertStack(meal)) player.dropItem(meal, false);
			if (!player.getInventory().insertStack(pie)) player.dropItem(pie, false);
			player.getServerWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.sendMessage(
					Text.literal("[Инспектор Столовой]: Талон принят и погашен штампом! Получите двойную котлету и пирог по ГОСТу!")
							.formatted(Formatting.GREEN, Formatting.BOLD),
					false
			);
			return;
		}

		if (acc.lastCafeteriaRationDay != day) {
			acc.lastCafeteriaRationDay = day;
			st.markDirty();
			ItemStack potato = new ItemStack(Items.BAKED_POTATO, 3);
			ItemStack strange = new ItemStack(ModItems.STRANGE_MEAT, 1);
			if (!player.getInventory().insertStack(potato)) player.dropItem(potato, false);
			if (!player.getInventory().insertStack(strange)) player.dropItem(strange, false);
			player.getServerWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.9F, 1.0F);
			player.sendMessage(
					Text.literal("[Инспектор Столовой]: Выдан суточный Пайковый Обед сотрудника (3 картофелины и 1 фирменная котлета из «Странного мяса»)! Приходите завтра или несите бумажный талон!")
							.formatted(Formatting.GOLD),
					false
			);
			return;
		}

		sayRandomLine(player, "Инспектор Столовой", CAFETERIA_LINES, Formatting.YELLOW);
	}

	private void sayRandomLine(ServerPlayerEntity player, String speaker, String[] pool, Formatting color) {
		String line = pool[this.getRandom().nextInt(pool.length)];
		player.sendMessage(
				Text.literal("[" + speaker + "]: ").formatted(color, Formatting.BOLD)
						.append(Text.literal(line).formatted(Formatting.WHITE)),
				false
		);
		player.getServerWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 0.7F, 1.05F);
	}

	private void handleSweetieFikInteraction(ServerPlayerEntity player) {
		ItemStack held = player.getMainHandStack();
		if (!this.sweetieQuestDone && held.isOf(ModItems.STRANGE_MEAT)) {
			if (!player.isInCreativeMode()) {
				held.decrement(1);
			}
			this.sweetieQuestDone = true;
			ModPersistentState state = ModPersistentState.get(player.getServer());
			ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
			acc.balance += 1500L;
			state.markDirty();
			ModServerNetworking.syncAccount(player);

			player.getServerWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.9F, 1.2F);
			player.sendMessage(
					Text.literal("[Свити Фик]: ").formatted(Formatting.GOLD, Formatting.BOLD)
							.append(Text.literal("О-О-О, РЕБЯТА, ОН ПРИНЁС СТРАННОЕ МЯСО С КУХНИ ПРЯМО В КАДР! Это 10 миллионов просмотров! Держи +1500 ₽ гонорара на счёт!").formatted(Formatting.GREEN)),
					false
			);
			return;
		}

		sayRandomLine(player, "Свити Фик", SWEETIE_FIK_LINES, Formatting.GOLD);
	}

	private void handleGidlerInteraction(ServerPlayerEntity player) {
		String phrase = GIDLER_LINES[this.getRandom().nextInt(GIDLER_LINES.length)];
		player.sendMessage(
				Text.literal("[Адольф Гидлер]: " + phrase).formatted(Formatting.GOLD),
				false
		);

		// 1 конкретный Гидлер может выдать только 1 жетон 1 игроку за всё время (без перезарядки);
		// при дальнейших нажатиях он просто продолжает произносить свои реплики.
		if (this.gidlerTokenGiven) {
			return;
		}

		this.gidlerTokenGiven = true;
		ItemStack token = new ItemStack(ModItems.REVIVAL_TOKEN, 1);
		if (!player.getInventory().insertStack(token)) {
			player.dropItem(token, false);
		}
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 1.0F, 1.0F);
		player.sendMessage(
				Text.literal("Получен предмет: Жетон возрождения!").formatted(Formatting.GREEN, Formatting.BOLD),
				true
		);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient()) {
			return;
		}
		this.lifeTicks++;
		if (this.actionCooldown > 0) {
			this.actionCooldown--;
		}

		// Пока рядом активна Танцующая кукла — NPC замирают на месте и плющатся/крутятся вместе со всеми!
		if (PartyDollEntity.isUnderActiveDollAura(this)) {
			return;
		}

		NpcRole role = getNpcRole();
		if (role.isStationaryShopNpc() || "bureau_guard".equals(getVariant())) {
			tickStationaryNpc(role);
			return;
		}
		if (role == NpcRole.ARCHIVAL_HORROR) {
			tickArchivalHorror();
			return;
		}
		if (role == NpcRole.BUREAUCRACY_BOSS) {
			tickBureaucracyBoss();
			return;
		}

		if (this.fleeing || this.leavingPeacefully) {
			tickFleeingOrLeaving();
			return;
		}

		ServerPlayerEntity target = resolveTargetPlayer();
		if (target == null || !target.isAlive() || target.isSpectator()) {
			if (this.lifeTicks > 1200) {
				this.discard();
			}
			return;
		}

		switch (role) {
			case VOLODYA -> tickVolodya(target);
			case COLLECTOR -> tickCollector(target);
			case ARMENIAN_BRIGADE -> tickArmenianBrigade(target);
			case MASTER_OF_SPORTS -> tickMasterOfSports(target);
			case OMON -> tickOmon(target);
			default -> {
			}
		}
	}

	private void tickStationaryNpc(NpcRole role) {
		// Удерживаем продавцов на рабочем месте
		if (this.anchorPos != null && this.getBlockPos().getSquaredDistance(this.anchorPos) > 9.0D) {
			this.getNavigation().startMovingTo(this.anchorPos.getX() + 0.5D, this.anchorPos.getY(), this.anchorPos.getZ() + 0.5D, 1.0D);
		}
		// Зазывала каждые 24 секунды пишет в чат всем игрокам в радиусе 100 блоков
		if (role == NpcRole.MFO_BARKER && this.lifeTicks % 480 == 40) {
			String msg = BARKER_LINES[this.getRandom().nextInt(BARKER_LINES.length)];
			Text chatLine = Text.literal("[Зазывала]: ").formatted(Formatting.GOLD, Formatting.BOLD)
					.append(Text.literal(msg).formatted(Formatting.YELLOW));
			for (PlayerEntity p : this.getWorld().getPlayers()) {
				if (p.squaredDistanceTo(this) <= 100.0D * 100.0D) {
					p.sendMessage(chatLine, false);
				}
			}
		}
	}

	private void tickFleeingOrLeaving() {
		PlayerEntity nearest = this.getWorld().getClosestPlayer(this, 36.0D);
		if (nearest != null) {
			Vec3d away = this.getPos().subtract(nearest.getPos());
			if (away.lengthSquared() < 0.01D) {
				away = new Vec3d(1.0D, 0.0D, 0.0D);
			} else {
				away = away.normalize();
			}
			double speed = this.fleeing ? 1.55D : 1.05D;
			this.getNavigation().startMovingTo(
					this.getX() + away.x * 16.0D,
					this.getY(),
					this.getZ() + away.z * 16.0D,
					speed
			);
			if (this.squaredDistanceTo(nearest) > 30.0D * 30.0D || this.lifeTicks > 200) {
				this.discard();
			}
		} else {
			this.discard();
		}
	}

	private void tickVolodya(ServerPlayerEntity target) {
		this.getLookControl().lookAt(target, 30.0F, 30.0F);
		if (this.squaredDistanceTo(target) > 3.5D * 3.5D) {
			this.getNavigation().startMovingTo(target, 1.0D);
		} else {
			this.getNavigation().stop();
		}
		if (this.lifeTicks == 300) {
			target.sendMessage(
					Text.literal("[Володя]: Я по-доброму предупредил, братан. Не затягивай с долгом, ладно?")
							.formatted(Formatting.YELLOW),
					false
			);
		}
		if (this.lifeTicks >= 800) {
			this.leavingPeacefully = true;
			this.lifeTicks = 0;
		}
	}

	private void tickCollector(ServerPlayerEntity target) {
		this.getLookControl().lookAt(target, 30.0F, 30.0F);
		double distSq = this.squaredDistanceTo(target);
		if (distSq > 2.4D * 2.4D) {
			this.getNavigation().startMovingTo(target, 1.15D);
		}
		// Коллекторы иногда бьют (раз в ~4.5 сек), но НЕ УБИВАЮТ!
		if (distSq <= 3.0D * 3.0D && this.actionCooldown <= 0) {
			this.actionCooldown = 85 + this.getRandom().nextInt(30);
			this.swingHand(Hand.MAIN_HAND);
			float currentHp = target.getHealth();
			if (currentHp > 3.5F) {
				float safeDamage = Math.min(4.0F, currentHp - 2.5F);
				target.damage(this.getDamageSources().mobAttack(this), safeDamage);
				if (target.getHealth() < 2.0F) {
					target.setHealth(2.0F);
				}
			} else {
				// Просто отталкивают без добивания
				Vec3d push = target.getPos().subtract(this.getPos()).normalize().multiply(0.35D);
				target.addVelocity(push.x, 0.15D, push.z);
				target.velocityModified = true;
				this.getWorld().playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 0.9F, 1.0F);
			}
		}
		if (this.lifeTicks >= 1600) {
			this.leavingPeacefully = true;
			this.lifeTicks = 0;
		}
	}

	private void tickArmenianBrigade(ServerPlayerEntity target) {
		this.getLookControl().lookAt(target, 30.0F, 30.0F);
		if (!"cage".equals(getVariant())) {
			// На улице: подбегают, скручивают виновного и сажают в обезьянник!
			this.getNavigation().startMovingTo(target, 1.35D);
			if (this.squaredDistanceTo(target) <= 3.2D * 3.2D || this.lifeTicks >= 70) {
				DetentionManager.captureIntoMonkeyCage(target);
			}
			return;
		}

		// Внутри обезьянника: лупят игрока (ХП НЕ падает ниже 1.5 сердечка = 3.0 HP),
		// пока у всей надетой брони не останется менее 10% прочности!
		double distSq = this.squaredDistanceTo(target);
		if (distSq > 2.0D * 2.0D) {
			this.getNavigation().startMovingTo(target, 1.15D);
		}
		if (distSq <= 3.2D * 3.2D && this.actionCooldown <= 0) {
			this.actionCooldown = 22 + this.getRandom().nextInt(8);
			this.swingHand(Hand.MAIN_HAND);

			if (target.getHealth() > 4.0F) {
				float safeDmg = Math.min(1.5F, target.getHealth() - 3.0F);
				target.damage(this.getDamageSources().mobAttack(this), safeDmg);
			} else {
				this.getWorld().playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.HOSTILE, 0.8F, 1.0F);
			}
			if (target.getHealth() < 3.0F) {
				target.setHealth(3.0F);
			}

			// Разбиваем прочность надетой брони до отметки < 10% прочности (не ломая полностью в ноль!)
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack armor = target.getEquippedStack(slot);
				if (!armor.isEmpty() && armor.isDamageable() && armor.getMaxDamage() > 0) {
					int maxDmg = armor.getMaxDamage();
					int targetMinRemaining = Math.max(1, (int) Math.floor(maxDmg * 0.08D));
					int targetDamageValue = maxDmg - targetMinRemaining;
					if (armor.getDamage() < targetDamageValue) {
						int step = Math.max(2, maxDmg / 18);
						armor.setDamage(Math.min(targetDamageValue, armor.getDamage() + step));
					}
				}
			}
		}
	}

	private void tickMasterOfSports(ServerPlayerEntity target) {
		this.getLookControl().lookAt(target, 30.0F, 30.0F);
		double distSq = this.squaredDistanceTo(target);
		if (distSq > 2.3D * 2.3D) {
			this.getNavigation().startMovingTo(target, 1.28D);
		}

		// Если у игрока уже <= 2 сердечка (4.0 HP) — изымают половину вещей из инвентаря в счёт долга и уходят!
		if (target.getHealth() <= 4.0F) {
			finishMasterOfSportsSeizure(target);
			return;
		}

		if (distSq <= 2.9D * 2.9D && this.actionCooldown <= 0) {
			this.actionCooldown = 24;
			this.swingHand(Hand.MAIN_HAND);

			float rawDamage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
			if (target.getHealth() - rawDamage <= 4.0F) {
				target.damage(this.getDamageSources().mobAttack(this), Math.max(0.5F, target.getHealth() - 4.0F));
				target.setHealth(4.0F);
				finishMasterOfSportsSeizure(target);
			} else {
				target.damage(this.getDamageSources().mobAttack(this), rawDamage);
				if (target.getHealth() <= 4.0F) {
					target.setHealth(4.0F);
					finishMasterOfSportsSeizure(target);
				}
			}
		}
	}

	private void finishMasterOfSportsSeizure(ServerPlayerEntity target) {
		if (this.leavingPeacefully) {
			return;
		}
		DetentionManager.seizeHalfInventoryTowardDebt(target);
		Box box = this.getBoundingBox().expand(32.0D);
		for (ModNpcEntity mate : this.getWorld().getEntitiesByClass(ModNpcEntity.class, box, e -> e.getNpcRole() == NpcRole.MASTER_OF_SPORTS)) {
			mate.leavingPeacefully = true;
			mate.lifeTicks = 0;
		}
	}

	private void tickOmon(ServerPlayerEntity target) {
		this.getLookControl().lookAt(target, 30.0F, 30.0F);
		this.getNavigation().startMovingTo(target, 1.42D);
		// При ОДНОМ ударе ОМОН вяжет всех в СИЗО на 10 минут!
		if (this.squaredDistanceTo(target) <= 2.9D * 2.9D && this.actionCooldown <= 0) {
			this.actionCooldown = 40;
			this.swingHand(Hand.MAIN_HAND);
			DetentionManager.arrestEveryoneIntoSizo(target);
		}
	}

	/**
	 * Уровень 3 (Склад с бумагами): БЕССМЕРТНЫЙ МОНСТР СКЛАДА — «Ревизор Архива».
	 * Невозможно убить; патрулирует стеллажи, реагирует на шум шагов (если игрок крадётся на Shift — радиус обнаружения в 3 раза меньше!).
	 */
	private void tickArchivalHorror() {
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		if (this.lifeTicks % 18 == 0) {
			sw.spawnParticles(ParticleTypes.ASH, this.getX(), this.getBodyY(0.6D), this.getZ(), 4, 0.35D, 0.5D, 0.35D, 0.01D);
		}
		if (this.lifeTicks % 60 == 0) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.HOSTILE, 0.9F, 0.55F);
		}

		// Никогда не покидает свой этаж склада
		if (this.anchorPos != null && Math.abs(this.getBlockY() - this.anchorPos.getY()) > 4) {
			this.refreshPositionAndAngles(this.anchorPos.getX() + 0.5D, this.anchorPos.getY(), this.anchorPos.getZ() + 0.5D, this.getYaw(), 0.0F);
		}

		ServerPlayerEntity hunted = null;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayerEntity sp : sw.getPlayers()) {
			if (!sp.isAlive() || sp.isCreative() || sp.isSpectator()) {
				continue;
			}
			if (Math.abs(sp.getY() - this.getY()) > 4.5D) {
				continue;
			}
			double detectRange = sp.isSneaking() ? 5.2D : 15.5D;
			double dSq = this.squaredDistanceTo(sp);
			if (dSq <= detectRange * detectRange && dSq < bestDist) {
				bestDist = dSq;
				hunted = sp;
			}
		}

		if (hunted != null) {
			this.getLookControl().lookAt(hunted, 35.0F, 35.0F);
			this.getNavigation().startMovingTo(hunted, 1.18D);
			if (this.lifeTicks % 24 == 0) {
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_STEP, SoundCategory.HOSTILE, 1.1F, 0.7F);
			}
			if (bestDist <= 2.8D * 2.8D && this.actionCooldown <= 0) {
				this.actionCooldown = 36;
				this.swingHand(Hand.MAIN_HAND);
				hunted.damage(this.getDamageSources().mobAttack(this), 6.0F);
				Vec3d push = hunted.getPos().subtract(this.getPos()).normalize().multiply(0.75D);
				hunted.addVelocity(push.x, 0.25D, push.z);
				hunted.velocityModified = true;
				hunted.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1));
				sw.playSound(null, hunted.getBlockPos(), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.HOSTILE, 0.85F, 0.6F);
				hunted.sendMessage(
						Text.literal("📜 [Ревизор Архива]: ШТРАФ ЗА ШУМ МЕЖДУ СТЕЛЛАЖАМИ! (Присядьте на Shift за полками, чтобы спрятаться!)")
								.formatted(Formatting.DARK_RED, Formatting.BOLD),
						true
				);
			}
		} else if (this.anchorPos != null && this.lifeTicks % 55 == 0) {
			double rx = this.anchorPos.getX() + (this.getRandom().nextInt(25) - 12);
			double rz = this.anchorPos.getZ() + (this.getRandom().nextInt(25) - 12);
			this.getNavigation().startMovingTo(rx + 0.5D, this.anchorPos.getY(), rz + 0.5D, 0.82D);
		}
	}

	/**
	 * Уровень 6 (Кабинет Босса): Финальный Босс — «Верховный Председатель Бюрократии».
	 */
	private void tickBureaucracyBoss() {
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		if (!this.bossAggro) {
			this.bossBar.setVisible(false);
			if (this.anchorPos != null && this.getBlockPos().getSquaredDistance(this.anchorPos) > 9.0D) {
				this.getNavigation().startMovingTo(this.anchorPos.getX() + 0.5D, this.anchorPos.getY(), this.anchorPos.getZ() + 0.5D, 0.9D);
			}
			return;
		}

		this.bossBar.setVisible(true);
		this.bossBar.setPercent(Math.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
		for (ServerPlayerEntity sp : sw.getPlayers()) {
			if (sp.isAlive() && Math.abs(sp.getY() - this.getY()) <= 9.0D && sp.squaredDistanceTo(this) <= 44.0D * 44.0D) {
				this.bossBar.addPlayer(sp);
			} else {
				this.bossBar.removePlayer(sp);
			}
		}

		float hpRatio = this.getHealth() / this.getMaxHealth();
		if (hpRatio <= 0.60F && this.bossPhase < 1) {
			this.bossPhase = 1;
			spawnBossAuditors(sw, false);
			broadcastToFloor(sw, "📕 [Верховный Председатель]: ВЫЗЫВАЮ ОТДЕЛ АУДИТА И ВНЕПЛАНОВУЮ ПРОВЕРКУ!!");
		} else if (hpRatio <= 0.28F && this.bossPhase < 2) {
			this.bossPhase = 2;
			spawnBossAuditors(sw, true);
			broadcastToFloor(sw, "⚖ [Верховный Председатель]: ПОДПИСЫВАЮ ЧРЕЗВЫЧАЙНЫЙ УКАЗ О ВАШЕЙ ЛИКВИДАЦИИ!!");
		}

		ServerPlayerEntity target = null;
		double minD = Double.MAX_VALUE;
		for (ServerPlayerEntity sp : sw.getPlayers()) {
			if (sp.isAlive() && !sp.isCreative() && !sp.isSpectator() && Math.abs(sp.getY() - this.getY()) <= 8.0D) {
				double d = this.squaredDistanceTo(sp);
				if (d < minD && d <= 36.0D * 36.0D) {
					minD = d;
					target = sp;
				}
			}
		}
		if (target == null) {
			return;
		}

		this.getLookControl().lookAt(target, 30.0F, 30.0F);
		this.getNavigation().startMovingTo(target, this.bossPhase >= 2 ? 1.28D : 1.12D);

		if (minD <= 3.1D * 3.1D && this.actionCooldown <= 0) {
			this.actionCooldown = this.bossPhase >= 2 ? 18 : 24;
			this.swingHand(Hand.MAIN_HAND);
			target.damage(this.getDamageSources().mobAttack(this), (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
		}

		if (this.specialCooldown > 0) {
			this.specialCooldown--;
		} else {
			this.specialCooldown = this.bossPhase >= 2 ? 75 : 115;
			// Удар круглой печатью по полу!
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.2F, 0.65F);
			for (int i = 0; i < 28; i++) {
				double a = (Math.PI * 2.0D * i) / 28.0D;
				sw.spawnParticles(ParticleTypes.FLAME, this.getX() + Math.cos(a) * 5.0D, this.getY() + 0.2D, this.getZ() + Math.sin(a) * 5.0D, 2, 0.1D, 0.1D, 0.1D, 0.02D);
			}
			for (ServerPlayerEntity sp : sw.getPlayers()) {
				if (sp.isAlive() && !sp.isCreative() && !sp.isSpectator() && sp.squaredDistanceTo(this) <= 6.5D * 6.5D) {
					sp.damage(this.getDamageSources().mobAttack(this), 7.0F);
					Vec3d push = sp.getPos().subtract(this.getPos()).normalize().multiply(0.85D);
					sp.addVelocity(push.x, 0.35D, push.z);
					sp.velocityModified = true;
					sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1));
				}
			}
		}
	}

	private void spawnBossAuditors(ServerWorld sw, boolean eliteWave) {
		sw.playSound(null, this.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 1.2F, 1.15F);
		for (int i = -1; i <= 1; i += 2) {
			if (eliteWave) {
				WhiteCapEntity auditor = ModEntities.WHITE_CAP_ENTITY.create(sw);
				if (auditor != null) {
					auditor.addCommandTag(WorldVirginLedger.TAG_NON_VIRGIN);
					auditor.refreshPositionAndAngles(this.getX() + i * 2.5D, this.getY(), this.getZ() + 2.0D, 0.0F, 0.0F);
					auditor.setRank(WhiteCapEntity.RANK_APOTHECARY);
					auditor.setCustomName(Text.literal("Старший Аудитор Колпаков").formatted(Formatting.GOLD));
					sw.spawnEntity(auditor);
				}
			} else {
				ModNpcEntity guard = ModEntities.MOD_NPC.create(sw);
				if (guard != null) {
					guard.addCommandTag(WorldVirginLedger.TAG_NON_VIRGIN);
					guard.refreshPositionAndAngles(this.getX() + i * 2.5D, this.getY(), this.getZ() + 2.0D, 0.0F, 0.0F);
					guard.configureRole(NpcRole.COLLECTOR, "boss_minion");
					guard.setCustomName(Text.literal("Судебный Пристав Босса").formatted(Formatting.RED));
					sw.spawnEntity(guard);
				}
			}
		}
	}

	private void broadcastToFloor(ServerWorld sw, String msg) {
		Text line = Text.literal(msg).formatted(Formatting.RED, Formatting.BOLD);
		for (ServerPlayerEntity sp : sw.getPlayers()) {
			if (Math.abs(sp.getY() - this.getY()) <= 10.0D) {
				sp.sendMessage(line, false);
			}
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		NpcRole preRole = getNpcRole();
		if (preRole == NpcRole.ARCHIVAL_HORROR
				&& !source.isOf(DamageTypes.OUT_OF_WORLD)
				&& !source.isOf(DamageTypes.GENERIC_KILL)) {
			if (!this.getWorld().isClient()) {
				this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 0.9F, 1.5F);
				if (source.getAttacker() instanceof ServerPlayerEntity sp) {
					sp.sendMessage(
							Text.literal("🚫 [АКТ №000]: Урон отклонён! У вас нет Справки на убийство монстра Склада в 3 экземплярах!")
									.formatted(Formatting.RED, Formatting.BOLD),
							true
					);
				}
			}
			return false;
		}

		boolean hurt = super.damage(source, amount);
		if (!hurt || this.getWorld().isClient() || !this.isAlive()) {
			return hurt;
		}

		if (preRole == NpcRole.BUREAUCRACY_BOSS && !this.bossAggro && source.getAttacker() instanceof ServerPlayerEntity sp) {
			this.bossAggro = true;
			sp.getServerWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.85F, 0.95F);
			sp.sendMessage(
					Text.literal("⚖ [Верховный Председатель]: ТЫ ОСМЕЛИЛСЯ ПОДНЯТЬ РУКУ НА МИНИСТЕРСТВО?! НАЧИНАЕМ ГЕНЕРАЛЬНУЮ ПРОВЕРКУ!!")
							.formatted(Formatting.DARK_RED, Formatting.BOLD),
					false
			);
		}

		if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
			NpcRole role = getNpcRole();
			if (role == NpcRole.VOLODYA && !this.fleeing) {
				// Если ударить Володю — он сразу убегает и предупреждает, что всё этим не кончится!
				this.fleeing = true;
				this.lifeTicks = 0;
				attacker.sendMessage(
						Text.literal("[Володя]: Ты чё дерёшься?! Всё этим не кончится, пеняй на себя!")
								.formatted(Formatting.RED, Formatting.BOLD),
						false
				);
				ModPersistentState state = ModPersistentState.get(attacker.getServer());
				ModPersistentState.PlayerFinanceData acc = state.getAccount(attacker.getUuid());
				acc.bankPatience = Math.max(0, acc.bankPatience - 45);
				acc.enforcementStage = Math.max(1, acc.enforcementStage);
				acc.nextVisitTick = attacker.getServer().getOverworld().getTime() + 6000L;
				state.markDirty();
			} else if (role == NpcRole.COLLECTOR && !this.fleeing) {
				// Если ударить коллектора пару раз — убегают и кричат, что так просто это не оставят!
				this.hitsFromPlayer++;
				if (this.hitsFromPlayer >= 2) {
					attacker.sendMessage(
							Text.literal("[Коллекторы]: Ах так?! Мы это так просто не оставим!")
									.formatted(Formatting.RED, Formatting.BOLD),
							false
					);
					Box box = this.getBoundingBox().expand(28.0D);
					for (ModNpcEntity mate : this.getWorld().getEntitiesByClass(ModNpcEntity.class, box, e -> e.getNpcRole() == NpcRole.COLLECTOR)) {
						mate.fleeing = true;
						mate.lifeTicks = 0;
					}
					ModPersistentState state = ModPersistentState.get(attacker.getServer());
					ModPersistentState.PlayerFinanceData acc = state.getAccount(attacker.getUuid());
					acc.bankPatience = Math.max(0, acc.bankPatience - 35);
					acc.enforcementStage = Math.max(2, acc.enforcementStage);
					state.markDirty();
				}
			}
		}

		return true;
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		this.bossBar.clearPlayers();
		super.onDeath(damageSource);
		if (this.getWorld().isClient()) {
			return;
		}
		if (getNpcRole() == NpcRole.BUREAUCRACY_BOSS && this.getWorld() instanceof ServerWorld sw) {
			this.dropStack(new ItemStack(ModItems.BUREAUCRACY_SEAL, 1));
			this.dropStack(new ItemStack(ModItems.BUREAUCRACY_KEY, 1));
			this.dropStack(new ItemStack(ModItems.REVIVAL_TOKEN, 1));
			this.dropStack(new ItemStack(ModItems.POKEBALL, 2));
			sw.playSound(null, this.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.3F, 1.0F);

			ModPersistentState st = ModPersistentState.get(sw.getServer());
			for (ServerPlayerEntity sp : sw.getPlayers()) {
				if (Math.abs(sp.getY() - this.getY()) <= 12.0D) {
					ModPersistentState.PlayerFinanceData acc = st.getAccount(sp.getUuid());
					acc.debt = 0L;
					acc.mfoLockedHalf = 0L;
					acc.daysOverdue = 0;
					acc.bankPatience = 100;
					acc.enforcementStage = 0;
					acc.nextVisitTick = -1L;
					acc.balance += 50_000L;
					st.markDirty();
					ModServerNetworking.syncAccount(sp);
					sp.sendMessage(
							Text.literal("👑 ВЕРХОВНЫЙ ПРЕДСЕДАТЕЛЬ ПОВЕРЖЕН! Все ваши долги в МФО аннулированы, на счёт зачислено +50 000 ₽ и выпала «Генеральная Печать Бюрократа»!")
									.formatted(Formatting.GOLD, Formatting.BOLD),
							false
					);
				}
			}
			return;
		}
		if (!(damageSource.getAttacker() instanceof ServerPlayerEntity killer) || killer.getServer() == null) {
			return;
		}
		if ("boss_minion".equals(getVariant())) {
			return;
		}

		NpcRole role = getNpcRole();
		ModPersistentState state = ModPersistentState.get(killer.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(killer.getUuid());
		long overworldTime = killer.getServer().getOverworld().getTime();

		if (role == NpcRole.COLLECTOR) {
			// Если убить коллектора — через 1 игровой день придёт Бригада армян!
			acc.brigadaScheduledTick = overworldTime + 24000L;
			acc.bankPatience = 0;
			acc.enforcementStage = 2;
			state.markDirty();

			Box box = this.getBoundingBox().expand(32.0D);
			for (ModNpcEntity mate : this.getWorld().getEntitiesByClass(ModNpcEntity.class, box, e -> e != this && e.isAlive() && e.getNpcRole() == NpcRole.COLLECTOR)) {
				mate.fleeing = true;
				mate.lifeTicks = 0;
			}
			killer.sendMessage(
					Text.literal("[Коллекторы]: Ты кого завалил?! Через день за тобой приедет бригада, готовься!")
							.formatted(Formatting.DARK_RED, Formatting.BOLD),
					false
			);
		} else if (role == NpcRole.MASTER_OF_SPORTS) {
			// Если убить кого-то из Мастеров спорта — остальные убегают в страхе, а через 1 день приедет ОМОН!
			acc.omonScheduledTick = overworldTime + 24000L;
			state.markDirty();

			Box box = this.getBoundingBox().expand(32.0D);
			List<ModNpcEntity> survivors = this.getWorld().getEntitiesByClass(
					ModNpcEntity.class,
					box,
					e -> e != this && e.isAlive() && e.getNpcRole() == NpcRole.MASTER_OF_SPORTS
			);
			for (ModNpcEntity mate : survivors) {
				mate.fleeing = true;
				mate.lifeTicks = 0;
			}
			killer.sendMessage(
					Text.literal("[Мастера спорта]: ПАЦАНЫ, ВАЛИМ, ОН СОВСЕМ ОТБИТЫЙ!! Вызывайте ОМОН!")
							.formatted(Formatting.RED, Formatting.BOLD),
					false
			);
		}
	}

	private ServerPlayerEntity resolveTargetPlayer() {
		if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
			return null;
		}
		if (this.targetPlayerUuid != null) {
			ServerPlayerEntity p = serverWorld.getServer().getPlayerManager().getPlayer(this.targetPlayerUuid);
			if (p != null) {
				return p;
			}
		}
		PlayerEntity closest = serverWorld.getClosestPlayer(this, 36.0D);
		if (closest instanceof ServerPlayerEntity sp && !sp.isCreative() && !sp.isSpectator()) {
			return sp;
		}
		return null;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("NpcRole", this.dataTracker.get(ROLE_ID));
		nbt.putString("Variant", this.dataTracker.get(VARIANT));
		if (this.targetPlayerUuid != null) {
			nbt.putUuid("TargetPlayer", this.targetPlayerUuid);
		}
		if (this.anchorPos != null) {
			nbt.putLong("AnchorPos", this.anchorPos.asLong());
		}
		nbt.putBoolean("Fleeing", this.fleeing);
		nbt.putBoolean("GidlerTokenGiven", this.gidlerTokenGiven);
		nbt.putBoolean("SweetieQuestDone", this.sweetieQuestDone);
		nbt.putBoolean("RegistrarSlipGiven", this.registrarSlipGiven);
		nbt.putBoolean("BossAggro", this.bossAggro);
		nbt.putInt("BossPhase", this.bossPhase);
		nbt.putInt("HitsFromPlayer", this.hitsFromPlayer);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("NpcRole")) {
			NpcRole role = NpcRole.fromId(nbt.getString("NpcRole"));
			this.dataTracker.set(ROLE_ID, role.getId());
			applyRoleStats(role);
		}
		if (nbt.contains("Variant")) {
			this.dataTracker.set(VARIANT, nbt.getString("Variant"));
		}
		this.setCustomName(Text.literal(resolveDisplayName(getNpcRole(), getVariant())));
		if (nbt.containsUuid("TargetPlayer")) {
			this.targetPlayerUuid = nbt.getUuid("TargetPlayer");
		}
		if (nbt.contains("AnchorPos")) {
			this.anchorPos = BlockPos.fromLong(nbt.getLong("AnchorPos"));
		}
		this.fleeing = nbt.getBoolean("Fleeing");
		this.gidlerTokenGiven = nbt.getBoolean("GidlerTokenGiven");
		this.sweetieQuestDone = nbt.getBoolean("SweetieQuestDone");
		this.registrarSlipGiven = nbt.getBoolean("RegistrarSlipGiven");
		this.bossAggro = nbt.getBoolean("BossAggro");
		this.bossPhase = nbt.getInt("BossPhase");
		this.hitsFromPlayer = nbt.getInt("HitsFromPlayer");
	}
}
