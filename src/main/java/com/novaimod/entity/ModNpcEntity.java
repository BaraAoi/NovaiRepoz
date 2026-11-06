package com.novaimod.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.novaimod.data.ModPersistentState;
import com.novaimod.network.ModServerNetworking;
import com.novaimod.registry.ModItems;
import com.novaimod.world.DetentionManager;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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
			"Согласно теории относительности, пять минут в этом горячем джакузи с пузырьками длятся как две секунды на лекции!",
			"E = mc², друг мой! Где E — это энергия этого неонового танцпола, помноженная на квадрат громкости сабвуфера!",
			"Я пытался вывести формулу турбулентности пузырьков в джакузи, но Свити Фик случайно уронил туда микрофон!",
			"Пространство-время явно искривляется вокруг кухни повара — особенно когда открываешь сундук с его странным мясом!",
			"Всё в мире относительно: в бассейне вода кажется прохладной, а после джакузи — ледяной квантовой суперпозицией!"
	};

	private static final String[] STALIN_LINES = new String[]{
			"Товарищ диджей выдаёт басы строго по пятилетнему плану — даже с перевыполнением нормы на танцполе!",
			"Курорт, конечно, культурный: бассейн, джакузи... Но к происхождению странного мяса на кухне у нас есть серьёзные вопросы!",
			"Эйнштейн мне тут полчаса про какие-то кванты толковал у бассейна. Сказал ему: физика физикой, а шашлык по расписанию!",
			"Порядок на вечеринке образцовый: кто не танцует на танцполе — тот отдыхает в джакузи с пузырьками!",
			"Хороший табак, тёплый морской бриз и музыка гремит... Но повара с его странными сундуками надо бы проверить!"
	};

	private static final String[] SWEETIE_FIK_LINES = new String[]{
			"Всем привет, дорогие друзья, с вами Свити Фик! И сегодня мы проводим 100 ДНЕЙ на секретном Эго-острове в Майнкрафте!",
			"Ребята, поставьте лайк прямо сейчас, если хотите увидеть, как я прыгну с крыши кухни прямо в бассейн!",
			"Слушай, принеси мне с кухни кусок того самого «Странного мяса» из сундука для челленджа в ролике — заплачу 1500 ₽ за реквизит!",
			"Я только что снял шортс с Эйнштейном и Сталиным на танцполе — этот ролик точно залетит в тренды!",
			"Джакузи с пузырьками тут имбовое: садишься прямо в воду, чиллишь, и хп само регенится — топ контент!"
	};

	private static final String[] PARTY_GIRL_LINES = new String[]{
			"Включай диджейский пульт скорее — когда танцпол начинает переливаться огнями, вся вечеринка оживает!",
			"Мы сначала плавали в бассейне, а потом пересели в джакузи с пузырьками — там реально можно сидеть прямо в тёплой воде!",
			"Эта вечеринка на острове просто лучшая! Тут и музыка качает, и Свити Фик свой новый ролик снимает!",
			"Ты уже заглядывал на кухню? Повар шепчется, что в сундуках какое-то странное мясо — мы решили лучше пить лимонад!",
			"Давай к нам на танцпол! Если запрыгать под бит диджея, гости даже чаевые за крутые движения кидают!"
	};

	private UUID targetPlayerUuid;
	private BlockPos anchorPos;
	private boolean fleeing = false;
	private boolean leavingPeacefully = false;
	private boolean gidlerTokenGiven = false;
	private boolean sweetieQuestDone = false;
	private int hitsFromPlayer = 0;
	private int actionCooldown = 0;
	private int lifeTicks = 0;

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
		this.dataTracker.set(ROLE_ID, role.getId());
		this.dataTracker.set(VARIANT, variant == null ? "" : variant);
		this.setCustomName(Text.literal(role.getDisplayName()));
		this.setCustomNameVisible(true);
		this.anchorPos = this.getBlockPos();
		applyRoleStats(role);
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
			default -> {
				setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 40.0D);
				setAttr(EntityAttributes.GENERIC_ARMOR, 10.0D);
				setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22D);
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

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource damageSource) {
		if (damageSource.isOf(DamageTypes.OUT_OF_WORLD) || damageSource.isOf(DamageTypes.GENERIC_KILL)) {
			return false;
		}
		// Стационарные торговцы, бармен, кредитор, зазывала и Гидлер на пляже неуязвимы для мобов/криперов/лавы
		if (getNpcRole().isStationaryShopNpc()) {
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
				ModServerNetworking.openShopScreen(serverPlayer, "Элитный отдел МФО", "elite_mfo");
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
			default -> {
				return ActionResult.PASS;
			}
		}
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

		NpcRole role = getNpcRole();
		if (role.isStationaryShopNpc()) {
			tickStationaryNpc(role);
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

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (!hurt || this.getWorld().isClient() || !this.isAlive()) {
			return hurt;
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
		super.onDeath(damageSource);
		if (this.getWorld().isClient()) {
			return;
		}
		if (!(damageSource.getAttacker() instanceof ServerPlayerEntity killer) || killer.getServer() == null) {
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
		if (nbt.containsUuid("TargetPlayer")) {
			this.targetPlayerUuid = nbt.getUuid("TargetPlayer");
		}
		if (nbt.contains("AnchorPos")) {
			this.anchorPos = BlockPos.fromLong(nbt.getLong("AnchorPos"));
		}
		this.fleeing = nbt.getBoolean("Fleeing");
		this.gidlerTokenGiven = nbt.getBoolean("GidlerTokenGiven");
		this.sweetieQuestDone = nbt.getBoolean("SweetieQuestDone");
		this.hitsFromPlayer = nbt.getInt("HitsFromPlayer");
	}
}
