package com.tom.cpm.client;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

import com.mojang.authlib.GameProfile;

import com.tom.cpm.CommonProxy;
import com.tom.cpm.shared.config.ConfigKeys;
import com.tom.cpm.shared.config.ModConfig;
import com.tom.cpm.shared.config.Player;
import com.tom.cpm.shared.definition.ModelDefinition;
import com.tom.cpm.shared.editor.gui.EditorGui;
import com.tom.cpm.shared.gui.GestureGui;
import com.tom.cpm.shared.model.RenderManager;
import com.tom.cpm.shared.model.TextureSheetType;
import com.tom.cpm.shared.network.NetHandler;

import io.netty.buffer.Unpooled;

public class ClientProxy extends CommonProxy {
	public static final ResourceLocation DEFAULT_CAPE = new ResourceLocation("cpm:textures/template/cape.png");
	public static MinecraftObject mc;
	private Minecraft minecraft;
	public static ClientProxy INSTANCE;
	public RenderManager<GameProfile, EntityPlayer, ModelBase, Void> manager;
	public NetHandler<ResourceLocation, NBTTagCompound, EntityPlayer, PacketBuffer, NetHandlerPlayClient> netHandler;

	@Override
	public void init() {
		super.init();
		INSTANCE = this;
		minecraft = Minecraft.getMinecraft();
		mc = new MinecraftObject(minecraft);
		MinecraftForge.EVENT_BUS.register(this);
		KeyBindings.init();
		manager = new RenderManager<>(mc.getPlayerRenderManager(), mc.getDefinitionLoader(), EntityPlayer::getGameProfile);
		netHandler = new NetHandler<>(ResourceLocation::new);
		netHandler.setNewNbt(NBTTagCompound::new);
		netHandler.setNewPacketBuffer(() -> new PacketBuffer(Unpooled.buffer()));
		netHandler.setWriteCompound(PacketBuffer::writeNBTTagCompoundToBuffer, t -> {
			try {
				return t.readNBTTagCompoundFromBuffer();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		netHandler.setNBTSetters(NBTTagCompound::setBoolean, NBTTagCompound::setByteArray, NBTTagCompound::setFloat);
		netHandler.setNBTGetters(NBTTagCompound::getBoolean, NBTTagCompound::getByteArray, NBTTagCompound::getFloat);
		netHandler.setContains(NBTTagCompound::hasKey);
		Executor ex = minecraft::addScheduledTask;
		netHandler.setExecutor(() -> ex);
		netHandler.setSendPacket((c, rl, pb) -> c.addToSendQueue(new C17PacketCustomPayload(rl.toString(), pb)), null);
		netHandler.setPlayerToLoader(EntityPlayer::getGameProfile);
		netHandler.setReadPlayerId(PacketBuffer::readVarIntFromBuffer, id -> {
			Entity ent = Minecraft.getMinecraft().theWorld.getEntityByID(id);
			if(ent instanceof EntityPlayer) {
				return (AbstractClientPlayer) ent;
			}
			return null;
		});
		netHandler.setGetClient(() -> minecraft.thePlayer);
		netHandler.setGetNet(c -> ((EntityPlayerSP)c).sendQueue);
	}

	@SubscribeEvent
	public void playerRenderPre(RenderPlayerEvent.Pre event) {
		manager.bindPlayer(event.entityPlayer, null);
		manager.bindSkin(TextureSheetType.SKIN);
	}

	@SubscribeEvent
	public void playerRenderPost(RenderPlayerEvent.Post event) {
		manager.tryUnbind();
	}

	@SubscribeEvent
	public void initGui(GuiScreenEvent.InitGuiEvent.Post evt) {
		if((evt.gui instanceof GuiMainMenu && ModConfig.getCommonConfig().getSetBoolean(ConfigKeys.TITLE_SCREEN_BUTTON, true)) ||
				evt.gui instanceof GuiCustomizeSkin) {
			evt.buttonList.add(new Button(0, 0));
		}
	}

	@SubscribeEvent
	public void buttonPress(GuiScreenEvent.ActionPerformedEvent.Pre evt) {
		if(evt.button instanceof Button) {
			Minecraft.getMinecraft().displayGuiScreen(new GuiImpl(EditorGui::new, evt.gui));
		}
	}

	@SubscribeEvent
	public void openGui(GuiOpenEvent openGui) {
		if(openGui.gui == null && minecraft.currentScreen instanceof GuiImpl.Overlay) {
			openGui.gui = ((GuiImpl.Overlay) minecraft.currentScreen).getGui();
		}
		if(openGui.gui instanceof GuiMainMenu && EditorGui.doOpenEditor()) {
			openGui.gui = new GuiImpl(EditorGui::new, openGui.gui);
		}
	}

	@SubscribeEvent
	public void renderHand(RenderHandEvent evt) {
		manager.bindHand(Minecraft.getMinecraft().thePlayer, null);
		manager.bindSkin(TextureSheetType.SKIN);
	}

	public void renderSkull(ModelBase skullModel, GameProfile profile) {
		manager.bindSkull(profile, null, skullModel);
		manager.bindSkin(skullModel, TextureSheetType.SKIN);
	}

	public void renderArmor(ModelBase modelArmor, ModelBase modelLeggings,
			EntityPlayer player) {
		manager.bindArmor(player, null, modelArmor, 1);
		manager.bindArmor(player, null, modelLeggings, 2);
		manager.bindSkin(modelArmor, TextureSheetType.ARMOR1);
		manager.bindSkin(modelLeggings, TextureSheetType.ARMOR2);
	}

	@SubscribeEvent
	public void renderTick(RenderTickEvent evt) {
		if(evt.phase == Phase.START) {
			mc.getPlayerRenderManager().getAnimationEngine().update(evt.renderTickTime);
		}
	}

	@SubscribeEvent
	public void clientTick(ClientTickEvent evt) {
		if(evt.phase == Phase.START && !minecraft.isGamePaused()) {
			mc.getPlayerRenderManager().getAnimationEngine().tick();
		}

		if (minecraft.thePlayer == null || evt.phase == Phase.START)
			return;

		if(KeyBindings.gestureMenuBinding.isPressed()) {
			minecraft.displayGuiScreen(new GuiImpl(GestureGui::new, null));
		}

		if(KeyBindings.renderToggleBinding.isPressed()) {
			Player.setEnableRendering(!Player.isEnableRendering());
		}

		for (Entry<Integer, KeyBinding> e : KeyBindings.quickAccess.entrySet()) {
			if(e.getValue().isPressed()) {
				mc.getPlayerRenderManager().getAnimationEngine().onKeybind(e.getKey());
			}
		}
	}

	@SubscribeEvent
	public void onRenderName(RenderLivingEvent.Specials.Pre<AbstractClientPlayer> evt) {
		if(evt.entity instanceof AbstractClientPlayer) {
			if(!Player.isEnableNames())
				evt.setCanceled(true);
		}
	}

	public static class Button extends GuiButton {

		public Button(int x, int y) {
			super(99, x, y, 100, 20, I18n.format("button.cpm.open_editor"));
		}

	}

	public void onLogout() {
		mc.getDefinitionLoader().clearServerData();
	}

	public void unbind(ModelBase model) {
		manager.tryUnbind(model);
	}

	public void unbindHand(AbstractClientPlayer player) {
		manager.tryUnbindPlayer(player);
	}

	//Copy from LayerCape
	public static void renderCape(AbstractClientPlayer playerIn, float partialTicks, ModelPlayer model, ModelDefinition modelDefinition) {
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.pushMatrix();
		GlStateManager.translate(0.0F, 0.0F, 0.125F);
		float f1, f2, f3;

		if(playerIn != null) {
			double lvt_10_1_ = playerIn.prevChasingPosX
					+ (playerIn.chasingPosX - playerIn.prevChasingPosX) * partialTicks
					- (playerIn.prevPosX + (playerIn.posX - playerIn.prevPosX) * partialTicks);
			double lvt_12_1_ = playerIn.prevChasingPosY
					+ (playerIn.chasingPosY - playerIn.prevChasingPosY) * partialTicks
					- (playerIn.prevPosY + (playerIn.posY - playerIn.prevPosY) * partialTicks);
			double lvt_14_1_ = playerIn.prevChasingPosZ
					+ (playerIn.chasingPosZ - playerIn.prevChasingPosZ) * partialTicks
					- (playerIn.prevPosZ + (playerIn.posZ - playerIn.prevPosZ) * partialTicks);
			float lvt_16_1_ = playerIn.prevRenderYawOffset
					+ (playerIn.renderYawOffset - playerIn.prevRenderYawOffset) * partialTicks;
			double lvt_17_1_ = MathHelper.sin(lvt_16_1_ * 0.017453292F);
			double lvt_19_1_ = (-MathHelper.cos(lvt_16_1_ * 0.017453292F));
			f1 = (float) lvt_12_1_ * 10.0F;
			f1 = MathHelper.clamp_float(f1, -6.0F, 32.0F);
			f2 = (float) (lvt_10_1_ * lvt_17_1_ + lvt_14_1_ * lvt_19_1_) * 100.0F;
			f3 = (float) (lvt_10_1_ * lvt_19_1_ - lvt_14_1_ * lvt_17_1_) * 100.0F;
			if (f2 < 0.0F) {
				f2 = 0.0F;
			}

			float lvt_24_1_ = playerIn.prevCameraYaw
					+ (playerIn.cameraYaw - playerIn.prevCameraYaw) * partialTicks;
			f1 += MathHelper.sin((playerIn.prevDistanceWalkedModified
					+ (playerIn.distanceWalkedModified - playerIn.prevDistanceWalkedModified) * partialTicks)
					* 6.0F) * 32.0F * lvt_24_1_;
			if (playerIn.isSneaking()) {
				f1 += 25.0F;
			}
			if (playerIn.getEquipmentInSlot(3) != null) {
				if (playerIn.isSneaking()) {
					model.bipedCape.rotationPointZ = 1.4F + 0.125F * 3;
					model.bipedCape.rotationPointY = 1.85F + 1 - 0.125F * 4;
				} else {
					model.bipedCape.rotationPointZ = 0.0F + 0.125F * 16f;
					model.bipedCape.rotationPointY = 0.0F;
				}
			} else if (playerIn.isSneaking()) {
				model.bipedCape.rotationPointZ = 0.3F + 0.125F * 16f;
				model.bipedCape.rotationPointY = 0.8F + 0.3f;
			} else {
				model.bipedCape.rotationPointZ = -1.1F + 0.125F * 32f;
				model.bipedCape.rotationPointY = -0.85F + 1;
			}
		} else {
			f1 = 0;
			f2 = 0;
			f3 = 0;
		}

		model.bipedCape.rotateAngleX = (float) -Math.toRadians(6.0F + f2 / 2.0F + f1);
		model.bipedCape.rotateAngleY = (float) Math.toRadians(180.0F - f3 / 2.0F);
		model.bipedCape.rotateAngleZ = (float) Math.toRadians(f3 / 2.0F);
		model.renderCape(0.0625F);
		GlStateManager.popMatrix();
	}
}
