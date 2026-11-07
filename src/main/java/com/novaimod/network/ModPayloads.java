package com.novaimod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.novaimod.NovaiMod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class ModPayloads {
	private ModPayloads() {
	}

	// ===================== S2C =====================

	public record AccountSyncPayload(long balance, long debt, int sizoSecondsLeft) implements CustomPayload {
		public static final CustomPayload.Id<AccountSyncPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "account_sync"));
		public static final PacketCodec<RegistryByteBuf, AccountSyncPayload> CODEC =
				PacketCodec.of(AccountSyncPayload::write, AccountSyncPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeLong(balance);
			buf.writeLong(debt);
			buf.writeVarInt(sizoSecondsLeft);
		}

		private static AccountSyncPayload read(RegistryByteBuf buf) {
			return new AccountSyncPayload(buf.readLong(), buf.readLong(), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record OpenLoanScreenPayload(long balance, long debt) implements CustomPayload {
		public static final CustomPayload.Id<OpenLoanScreenPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "open_loan_screen"));
		public static final PacketCodec<RegistryByteBuf, OpenLoanScreenPayload> CODEC =
				PacketCodec.of(OpenLoanScreenPayload::write, OpenLoanScreenPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeLong(balance);
			buf.writeLong(debt);
		}

		private static OpenLoanScreenPayload read(RegistryByteBuf buf) {
			return new OpenLoanScreenPayload(buf.readLong(), buf.readLong());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ShopOfferNet(String offerId, ItemStack stack, long price, String tierLabel, int stock) {
		public void write(RegistryByteBuf buf) {
			buf.writeString(offerId);
			ItemStack.PACKET_CODEC.encode(buf, stack);
			buf.writeLong(price);
			buf.writeString(tierLabel);
			buf.writeVarInt(stock);
		}

		public static ShopOfferNet read(RegistryByteBuf buf) {
			String offerId = buf.readString();
			ItemStack stack = ItemStack.PACKET_CODEC.decode(buf);
			long price = buf.readLong();
			String tier = buf.readString();
			int stock = buf.readVarInt();
			return new ShopOfferNet(offerId, stack, price, tier, stock);
		}
	}

	public record OpenShopScreenPayload(
			String title,
			String shopKey,
			boolean allowRepair,
			boolean limitedElite,
			List<ShopOfferNet> offers
	) implements CustomPayload {
		public static final CustomPayload.Id<OpenShopScreenPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "open_shop_screen"));
		public static final PacketCodec<RegistryByteBuf, OpenShopScreenPayload> CODEC =
				PacketCodec.of(OpenShopScreenPayload::write, OpenShopScreenPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeString(title);
			buf.writeString(shopKey);
			buf.writeBoolean(allowRepair);
			buf.writeBoolean(limitedElite);
			buf.writeVarInt(offers.size());
			for (ShopOfferNet o : offers) {
				o.write(buf);
			}
		}

		private static OpenShopScreenPayload read(RegistryByteBuf buf) {
			String title = buf.readString();
			String shopKey = buf.readString();
			boolean allowRepair = buf.readBoolean();
			boolean limitedElite = buf.readBoolean();
			int size = buf.readVarInt();
			List<ShopOfferNet> offers = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				offers.add(ShopOfferNet.read(buf));
			}
			return new OpenShopScreenPayload(title, shopKey, allowRepair, limitedElite, offers);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ReviveCandidateNet(UUID uuid, String name) {
		public void write(RegistryByteBuf buf) {
			buf.writeUuid(uuid);
			buf.writeString(name);
		}

		public static ReviveCandidateNet read(RegistryByteBuf buf) {
			return new ReviveCandidateNet(buf.readUuid(), buf.readString());
		}
	}

	public record OpenRevivalScreenPayload(List<ReviveCandidateNet> candidates) implements CustomPayload {
		public static final CustomPayload.Id<OpenRevivalScreenPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "open_revival_screen"));
		public static final PacketCodec<RegistryByteBuf, OpenRevivalScreenPayload> CODEC =
				PacketCodec.of(OpenRevivalScreenPayload::write, OpenRevivalScreenPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(candidates.size());
			for (ReviveCandidateNet c : candidates) {
				c.write(buf);
			}
		}

		private static OpenRevivalScreenPayload read(RegistryByteBuf buf) {
			int n = buf.readVarInt();
			List<ReviveCandidateNet> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				list.add(ReviveCandidateNet.read(buf));
			}
			return new OpenRevivalScreenPayload(list);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record OpenCoinflipScreenPayload(long balance) implements CustomPayload {
		public static final CustomPayload.Id<OpenCoinflipScreenPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "open_coinflip_screen"));
		public static final PacketCodec<RegistryByteBuf, OpenCoinflipScreenPayload> CODEC =
				PacketCodec.of(OpenCoinflipScreenPayload::write, OpenCoinflipScreenPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeLong(balance);
		}

		private static OpenCoinflipScreenPayload read(RegistryByteBuf buf) {
			return new OpenCoinflipScreenPayload(buf.readLong());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record CoinflipResultPayload(boolean landedHeads, boolean won, long payout, int animTicks) implements CustomPayload {
		public static final CustomPayload.Id<CoinflipResultPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "coinflip_result"));
		public static final PacketCodec<RegistryByteBuf, CoinflipResultPayload> CODEC =
				PacketCodec.of(CoinflipResultPayload::write, CoinflipResultPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeBoolean(landedHeads);
			buf.writeBoolean(won);
			buf.writeLong(payout);
			buf.writeVarInt(animTicks);
		}

		private static CoinflipResultPayload read(RegistryByteBuf buf) {
			return new CoinflipResultPayload(buf.readBoolean(), buf.readBoolean(), buf.readLong(), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record OpenRouletteScreenPayload(long balance) implements CustomPayload {
		public static final CustomPayload.Id<OpenRouletteScreenPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "open_roulette_screen"));
		public static final PacketCodec<RegistryByteBuf, OpenRouletteScreenPayload> CODEC =
				PacketCodec.of(OpenRouletteScreenPayload::write, OpenRouletteScreenPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeLong(balance);
		}

		private static OpenRouletteScreenPayload read(RegistryByteBuf buf) {
			return new OpenRouletteScreenPayload(buf.readLong());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record RouletteResultPayload(int winningNumber, boolean won, long payout, int animTicks) implements CustomPayload {
		public static final CustomPayload.Id<RouletteResultPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "roulette_result"));
		public static final PacketCodec<RegistryByteBuf, RouletteResultPayload> CODEC =
				PacketCodec.of(RouletteResultPayload::write, RouletteResultPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(winningNumber);
			buf.writeBoolean(won);
			buf.writeLong(payout);
			buf.writeVarInt(animTicks);
		}

		private static RouletteResultPayload read(RegistryByteBuf buf) {
			return new RouletteResultPayload(buf.readVarInt(), buf.readBoolean(), buf.readLong(), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record OpenOddsConfigScreenPayload(
			int fateTokenChance,
			int fateNothingChance,
			int fateDeathChance,
			int coinHeadsChance,
			int rouletteGreenChance,
			int rouletteRedChance,
			int rouletteBlackChance
	) implements CustomPayload {
		public static final CustomPayload.Id<OpenOddsConfigScreenPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "open_odds_config_screen"));
		public static final PacketCodec<RegistryByteBuf, OpenOddsConfigScreenPayload> CODEC =
				PacketCodec.of(OpenOddsConfigScreenPayload::write, OpenOddsConfigScreenPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(fateTokenChance);
			buf.writeVarInt(fateNothingChance);
			buf.writeVarInt(fateDeathChance);
			buf.writeVarInt(coinHeadsChance);
			buf.writeVarInt(rouletteGreenChance);
			buf.writeVarInt(rouletteRedChance);
			buf.writeVarInt(rouletteBlackChance);
		}

		private static OpenOddsConfigScreenPayload read(RegistryByteBuf buf) {
			return new OpenOddsConfigScreenPayload(
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt()
			);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record OpenTeleporterScreenPayload(long balance, List<ReviveCandidateNet> targets) implements CustomPayload {
		public static final CustomPayload.Id<OpenTeleporterScreenPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "open_teleporter_screen"));
		public static final PacketCodec<RegistryByteBuf, OpenTeleporterScreenPayload> CODEC =
				PacketCodec.of(OpenTeleporterScreenPayload::write, OpenTeleporterScreenPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeLong(balance);
			buf.writeVarInt(targets.size());
			for (ReviveCandidateNet t : targets) {
				t.write(buf);
			}
		}

		private static OpenTeleporterScreenPayload read(RegistryByteBuf buf) {
			long bal = buf.readLong();
			int n = buf.readVarInt();
			List<ReviveCandidateNet> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				list.add(ReviveCandidateNet.read(buf));
			}
			return new OpenTeleporterScreenPayload(bal, list);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ShadowCuePayload(boolean leftSide, float intensity) implements CustomPayload {
		public static final CustomPayload.Id<ShadowCuePayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "shadow_cue"));
		public static final PacketCodec<RegistryByteBuf, ShadowCuePayload> CODEC =
				PacketCodec.of(ShadowCuePayload::write, ShadowCuePayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeBoolean(leftSide);
			buf.writeFloat(intensity);
		}

		private static ShadowCuePayload read(RegistryByteBuf buf) {
			return new ShadowCuePayload(buf.readBoolean(), buf.readFloat());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ===================== C2S =====================

	public record LoanActionPayload(boolean borrow, long amount) implements CustomPayload {
		public static final CustomPayload.Id<LoanActionPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "loan_action"));
		public static final PacketCodec<RegistryByteBuf, LoanActionPayload> CODEC =
				PacketCodec.of(LoanActionPayload::write, LoanActionPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeBoolean(borrow);
			buf.writeLong(amount);
		}

		private static LoanActionPayload read(RegistryByteBuf buf) {
			return new LoanActionPayload(buf.readBoolean(), buf.readLong());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ShopBuyPayload(String shopKey, String offerId) implements CustomPayload {
		public static final CustomPayload.Id<ShopBuyPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "shop_buy"));
		public static final PacketCodec<RegistryByteBuf, ShopBuyPayload> CODEC =
				PacketCodec.of(ShopBuyPayload::write, ShopBuyPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeString(shopKey);
			buf.writeString(offerId);
		}

		private static ShopBuyPayload read(RegistryByteBuf buf) {
			return new ShopBuyPayload(buf.readString(), buf.readString());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record RepairToolPayload() implements CustomPayload {
		public static final CustomPayload.Id<RepairToolPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "repair_tool"));
		public static final PacketCodec<RegistryByteBuf, RepairToolPayload> CODEC =
				PacketCodec.unit(new RepairToolPayload());

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record VendingSellAllPayload() implements CustomPayload {
		public static final CustomPayload.Id<VendingSellAllPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "vending_sell_all"));
		public static final PacketCodec<RegistryByteBuf, VendingSellAllPayload> CODEC =
				PacketCodec.unit(new VendingSellAllPayload());

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record RevivePlayerPayload(UUID targetUuid) implements CustomPayload {
		public static final CustomPayload.Id<RevivePlayerPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "revive_player"));
		public static final PacketCodec<RegistryByteBuf, RevivePlayerPayload> CODEC =
				PacketCodec.of(RevivePlayerPayload::write, RevivePlayerPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeUuid(targetUuid);
		}

		private static RevivePlayerPayload read(RegistryByteBuf buf) {
			return new RevivePlayerPayload(buf.readUuid());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record PlayCoinflipPayload(long bet, boolean betHeads) implements CustomPayload {
		public static final CustomPayload.Id<PlayCoinflipPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "play_coinflip"));
		public static final PacketCodec<RegistryByteBuf, PlayCoinflipPayload> CODEC =
				PacketCodec.of(PlayCoinflipPayload::write, PlayCoinflipPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeLong(bet);
			buf.writeBoolean(betHeads);
		}

		private static PlayCoinflipPayload read(RegistryByteBuf buf) {
			return new PlayCoinflipPayload(buf.readLong(), buf.readBoolean());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record PlayRoulettePayload(long bet, int betTypeOrd, int chosenNumber) implements CustomPayload {
		public static final CustomPayload.Id<PlayRoulettePayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "play_roulette"));
		public static final PacketCodec<RegistryByteBuf, PlayRoulettePayload> CODEC =
				PacketCodec.of(PlayRoulettePayload::write, PlayRoulettePayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeLong(bet);
			buf.writeVarInt(betTypeOrd);
			buf.writeVarInt(chosenNumber);
		}

		private static PlayRoulettePayload read(RegistryByteBuf buf) {
			return new PlayRoulettePayload(buf.readLong(), buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record SaveOddsConfigPayload(
			int fateTokenChance,
			int fateNothingChance,
			int fateDeathChance,
			int coinHeadsChance,
			int rouletteGreenChance,
			int rouletteRedChance,
			int rouletteBlackChance
	) implements CustomPayload {
		public static final CustomPayload.Id<SaveOddsConfigPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "save_odds_config"));
		public static final PacketCodec<RegistryByteBuf, SaveOddsConfigPayload> CODEC =
				PacketCodec.of(SaveOddsConfigPayload::write, SaveOddsConfigPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(fateTokenChance);
			buf.writeVarInt(fateNothingChance);
			buf.writeVarInt(fateDeathChance);
			buf.writeVarInt(coinHeadsChance);
			buf.writeVarInt(rouletteGreenChance);
			buf.writeVarInt(rouletteRedChance);
			buf.writeVarInt(rouletteBlackChance);
		}

		private static SaveOddsConfigPayload read(RegistryByteBuf buf) {
			return new SaveOddsConfigPayload(
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt()
			);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record UseTeleporterPayload(UUID targetUuid, boolean bringTargetToMe) implements CustomPayload {
		public static final CustomPayload.Id<UseTeleporterPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "use_teleporter"));
		public static final PacketCodec<RegistryByteBuf, UseTeleporterPayload> CODEC =
				PacketCodec.of(UseTeleporterPayload::write, UseTeleporterPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeUuid(targetUuid);
			buf.writeBoolean(bringTargetToMe);
		}

		private static UseTeleporterPayload read(RegistryByteBuf buf) {
			return new UseTeleporterPayload(buf.readUuid(), buf.readBoolean());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record TeleporterFastMoneyPayload() implements CustomPayload {
		public static final CustomPayload.Id<TeleporterFastMoneyPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "teleporter_fast_money"));
		public static final PacketCodec<RegistryByteBuf, TeleporterFastMoneyPayload> CODEC =
				PacketCodec.unit(new TeleporterFastMoneyPayload());

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record CycleUberGunModePayload() implements CustomPayload {
		public static final CustomPayload.Id<CycleUberGunModePayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "cycle_ubergun_mode"));
		public static final PacketCodec<RegistryByteBuf, CycleUberGunModePayload> CODEC =
				PacketCodec.unit(new CycleUberGunModePayload());

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record SeamlessTeleportPayload(String dimensionPath, boolean crossDimension) implements CustomPayload {
		public static final CustomPayload.Id<SeamlessTeleportPayload> ID =
				new CustomPayload.Id<>(Identifier.of(NovaiMod.MOD_ID, "seamless_teleport"));
		public static final PacketCodec<RegistryByteBuf, SeamlessTeleportPayload> CODEC =
				PacketCodec.of(SeamlessTeleportPayload::write, SeamlessTeleportPayload::read);

		private void write(RegistryByteBuf buf) {
			buf.writeString(dimensionPath);
			buf.writeBoolean(crossDimension);
		}

		private static SeamlessTeleportPayload read(RegistryByteBuf buf) {
			return new SeamlessTeleportPayload(buf.readString(), buf.readBoolean());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void registerPayloadTypes() {
		PayloadTypeRegistry.playS2C().register(AccountSyncPayload.ID, AccountSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenLoanScreenPayload.ID, OpenLoanScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenShopScreenPayload.ID, OpenShopScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenRevivalScreenPayload.ID, OpenRevivalScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenCoinflipScreenPayload.ID, OpenCoinflipScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CoinflipResultPayload.ID, CoinflipResultPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenRouletteScreenPayload.ID, OpenRouletteScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RouletteResultPayload.ID, RouletteResultPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenOddsConfigScreenPayload.ID, OpenOddsConfigScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenTeleporterScreenPayload.ID, OpenTeleporterScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ShadowCuePayload.ID, ShadowCuePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SeamlessTeleportPayload.ID, SeamlessTeleportPayload.CODEC);

		PayloadTypeRegistry.playC2S().register(LoanActionPayload.ID, LoanActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ShopBuyPayload.ID, ShopBuyPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RepairToolPayload.ID, RepairToolPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(VendingSellAllPayload.ID, VendingSellAllPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RevivePlayerPayload.ID, RevivePlayerPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PlayCoinflipPayload.ID, PlayCoinflipPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PlayRoulettePayload.ID, PlayRoulettePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveOddsConfigPayload.ID, SaveOddsConfigPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UseTeleporterPayload.ID, UseTeleporterPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(TeleporterFastMoneyPayload.ID, TeleporterFastMoneyPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CycleUberGunModePayload.ID, CycleUberGunModePayload.CODEC);
	}
}
