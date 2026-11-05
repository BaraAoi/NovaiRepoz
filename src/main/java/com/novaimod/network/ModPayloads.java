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

	public static void registerPayloadTypes() {
		PayloadTypeRegistry.playS2C().register(AccountSyncPayload.ID, AccountSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenLoanScreenPayload.ID, OpenLoanScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenShopScreenPayload.ID, OpenShopScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenRevivalScreenPayload.ID, OpenRevivalScreenPayload.CODEC);

		PayloadTypeRegistry.playC2S().register(LoanActionPayload.ID, LoanActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ShopBuyPayload.ID, ShopBuyPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RepairToolPayload.ID, RepairToolPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(VendingSellAllPayload.ID, VendingSellAllPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RevivePlayerPayload.ID, RevivePlayerPayload.CODEC);
	}
}
