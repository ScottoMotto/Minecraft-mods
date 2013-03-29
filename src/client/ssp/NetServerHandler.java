package net.minecraft.src;

import java.io.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;

public class NetServerHandler extends NetHandler
{
    /** The underlying network manager for this server handler. */
    public final INetworkManager netManager;

    /** Reference to the MinecraftServer object. */
    private final MinecraftServer mcServer;

    /** This is set to true whenever a player disconnects from the server. */
    public boolean connectionClosed;

    /** Reference to the EntityPlayerMP object. */
    public EntityPlayerMP playerEntity;

    /** incremented each tick */
    private int currentTicks;

    /**
     * player is kicked if they float for over 80 ticks without flying enabled
     */
    private int ticksForFloatKick;
    private boolean field_72584_h;
    private int keepAliveRandomID;
    private long keepAliveTimeSent;
    private static Random randomGenerator = new Random();
    private long ticksOfLastKeepAlive;
    private int chatSpamThresholdCount;
    private int creativeItemCreationSpamThresholdTally;

    /** The last known x position for this connection. */
    private double lastPosX;

    /** The last known y position for this connection. */
    private double lastPosY;

    /** The last known z position for this connection. */
    private double lastPosZ;

    /** is true when the player has moved since his last movement packet */
    private boolean hasMoved;
    private IntHashMap field_72586_s;

    public NetServerHandler(MinecraftServer par1, INetworkManager par2, EntityPlayerMP par3)
    {
        connectionClosed = false;
        chatSpamThresholdCount = 0;
        creativeItemCreationSpamThresholdTally = 0;
        hasMoved = true;
        field_72586_s = new IntHashMap();
        mcServer = par1;
        netManager = par2;
        par2.setNetHandler(this);
        playerEntity = par3;
        par3.playerNetServerHandler = this;
    }

    public NetServerHandler(EntityPlayerMP par3EntityPlayerMP)
    {
        connectionClosed = false;
        chatSpamThresholdCount = 0;
        creativeItemCreationSpamThresholdTally = 0;
        hasMoved = true;
        field_72586_s = new IntHashMap();
        playerEntity = par3EntityPlayerMP;
        par3EntityPlayerMP.playerNetServerHandler = this;
        netManager = null;
        mcServer = null;
    }

    public EntityPlayerMP getPlayer()
    {
        return playerEntity;
    }

    /**
     * run once each game tick
     */
    public void networkTick()
    {
        field_72584_h = false;
        currentTicks++;
        mcServer.theProfiler.startSection("packetflow");
        netManager.processReadPackets();
        mcServer.theProfiler.endStartSection("keepAlive");

        if ((long)currentTicks - ticksOfLastKeepAlive > 20L)
        {
            ticksOfLastKeepAlive = currentTicks;
            keepAliveTimeSent = System.nanoTime() / 0xf4240L;
            keepAliveRandomID = randomGenerator.nextInt();
            sendPacketToPlayer(new Packet0KeepAlive(keepAliveRandomID));
        }

        if (chatSpamThresholdCount > 0)
        {
            chatSpamThresholdCount--;
        }

        if (creativeItemCreationSpamThresholdTally > 0)
        {
            creativeItemCreationSpamThresholdTally--;
        }

        mcServer.theProfiler.endStartSection("playerTick");
        mcServer.theProfiler.endSection();
    }

    public void kickPlayerFromServer(String par1Str)
    {
        if (connectionClosed)
        {
            return;
        }
        else
        {
            playerEntity.mountEntityAndWakeUp();
            sendPacketToPlayer(new Packet255KickDisconnect(par1Str));
            netManager.serverShutdown();
            mcServer.getConfigurationManager().sendPacketToAllPlayers(new Packet3Chat((new StringBuilder()).append(EnumChatFormatting.YELLOW).append(playerEntity.username).append(" left the game.").toString()));
            mcServer.getConfigurationManager().playerLoggedOut(playerEntity);
            connectionClosed = true;
            return;
        }
    }

    public void handleFlying(Packet10Flying par1Packet10Flying)
    {
        WorldServer worldserver = mcServer.worldServerForDimension(playerEntity.dimension);
        field_72584_h = true;

        if (playerEntity.playerConqueredTheEnd)
        {
            return;
        }

        if (!hasMoved)
        {
            double d = par1Packet10Flying.yPosition - lastPosY;

            if (par1Packet10Flying.xPosition == lastPosX && d * d < 0.01D && par1Packet10Flying.zPosition == lastPosZ)
            {
                hasMoved = true;
            }
        }

        if (hasMoved)
        {
            if (playerEntity.ridingEntity != null)
            {
                float f = playerEntity.rotationYaw;
                float f1 = playerEntity.rotationPitch;
                playerEntity.ridingEntity.updateRiderPosition();
                double d2 = playerEntity.posX;
                double d4 = playerEntity.posY;
                double d6 = playerEntity.posZ;
                double d8 = 0.0D;
                double d9 = 0.0D;

                if (par1Packet10Flying.rotating)
                {
                    f = par1Packet10Flying.yaw;
                    f1 = par1Packet10Flying.pitch;
                }

                if (par1Packet10Flying.moving && par1Packet10Flying.yPosition == -999D && par1Packet10Flying.stance == -999D)
                {
                    if (Math.abs(par1Packet10Flying.xPosition) > 1.0D || Math.abs(par1Packet10Flying.zPosition) > 1.0D)
                    {
                        System.err.println((new StringBuilder()).append(playerEntity.username).append(" was caught trying to crash the server with an invalid position.").toString());
                        kickPlayerFromServer("Nope!");
                        return;
                    }

                    d8 = par1Packet10Flying.xPosition;
                    d9 = par1Packet10Flying.zPosition;
                }

                playerEntity.onGround = par1Packet10Flying.onGround;
                playerEntity.onUpdateEntity();
                playerEntity.moveEntity(d8, 0.0D, d9);
                playerEntity.setPositionAndRotation(d2, d4, d6, f, f1);
                playerEntity.motionX = d8;
                playerEntity.motionZ = d9;

                if (playerEntity.ridingEntity != null)
                {
                    worldserver.uncheckedUpdateEntity(playerEntity.ridingEntity, true);
                }

                if (playerEntity.ridingEntity != null)
                {
                    playerEntity.ridingEntity.updateRiderPosition();
                }

                mcServer.getConfigurationManager().serverUpdateMountedMovingPlayer(playerEntity);
                lastPosX = playerEntity.posX;
                lastPosY = playerEntity.posY;
                lastPosZ = playerEntity.posZ;
                worldserver.updateEntity(playerEntity);
                return;
            }

            if (playerEntity.isPlayerSleeping())
            {
                playerEntity.onUpdateEntity();
                playerEntity.setPositionAndRotation(lastPosX, lastPosY, lastPosZ, playerEntity.rotationYaw, playerEntity.rotationPitch);
                worldserver.updateEntity(playerEntity);
                return;
            }

            double d1 = playerEntity.posY;
            lastPosX = playerEntity.posX;
            lastPosY = playerEntity.posY;
            lastPosZ = playerEntity.posZ;
            double d3 = playerEntity.posX;
            double d5 = playerEntity.posY;
            double d7 = playerEntity.posZ;
            float f2 = playerEntity.rotationYaw;
            float f3 = playerEntity.rotationPitch;

            if (par1Packet10Flying.moving && par1Packet10Flying.yPosition == -999D && par1Packet10Flying.stance == -999D)
            {
                par1Packet10Flying.moving = false;
            }

            if (par1Packet10Flying.moving)
            {
                d3 = par1Packet10Flying.xPosition;
                d5 = par1Packet10Flying.yPosition;
                d7 = par1Packet10Flying.zPosition;
                double d10 = par1Packet10Flying.stance - par1Packet10Flying.yPosition;

                if (!playerEntity.isPlayerSleeping() && (d10 > 1.6499999999999999D || d10 < 0.10000000000000001D))
                {
                    kickPlayerFromServer("Illegal stance");
                    mcServer.getLogAgent().logWarning((new StringBuilder()).append(playerEntity.username).append(" had an illegal stance: ").append(d10).toString());
                    return;
                }

                if (Math.abs(par1Packet10Flying.xPosition) > 32000000D || Math.abs(par1Packet10Flying.zPosition) > 32000000D)
                {
                    kickPlayerFromServer("Illegal position");
                    return;
                }
            }

            if (par1Packet10Flying.rotating)
            {
                f2 = par1Packet10Flying.yaw;
                f3 = par1Packet10Flying.pitch;
            }

            playerEntity.onUpdateEntity();
            playerEntity.ySize = 0.0F;
            playerEntity.setPositionAndRotation(lastPosX, lastPosY, lastPosZ, f2, f3);

            if (!hasMoved)
            {
                return;
            }

            double d11 = d3 - playerEntity.posX;
            double d12 = d5 - playerEntity.posY;
            double d13 = d7 - playerEntity.posZ;
            double d14 = Math.min(Math.abs(d11), Math.abs(playerEntity.motionX));
            double d15 = Math.min(Math.abs(d12), Math.abs(playerEntity.motionY));
            double d16 = Math.min(Math.abs(d13), Math.abs(playerEntity.motionZ));
            double d17 = d14 * d14 + d15 * d15 + d16 * d16;

            if (d17 > 100D && (!mcServer.isSinglePlayer() || !mcServer.getServerOwner().equals(playerEntity.username)))
            {
                mcServer.getLogAgent().logWarning((new StringBuilder()).append(playerEntity.username).append(" moved too quickly! ").append(d11).append(",").append(d12).append(",").append(d13).append(" (").append(d14).append(", ").append(d15).append(", ").append(d16).append(")").toString());
                setPlayerLocation(lastPosX, lastPosY, lastPosZ, playerEntity.rotationYaw, playerEntity.rotationPitch);
                return;
            }

            float f4 = 0.0625F;
            boolean flag = worldserver.getCollidingBoundingBoxes(playerEntity, playerEntity.boundingBox.copy().contract(f4, f4, f4)).isEmpty();

            if (playerEntity.onGround && !par1Packet10Flying.onGround && d12 > 0.0D)
            {
                playerEntity.addExhaustion(0.2F);
            }

            playerEntity.moveEntity(d11, d12, d13);
            playerEntity.onGround = par1Packet10Flying.onGround;
            playerEntity.addMovementStat(d11, d12, d13);
            double d18 = d12;
            d11 = d3 - playerEntity.posX;
            d12 = d5 - playerEntity.posY;

            if (d12 > -0.5D || d12 < 0.5D)
            {
                d12 = 0.0D;
            }

            d13 = d7 - playerEntity.posZ;
            d17 = d11 * d11 + d12 * d12 + d13 * d13;
            boolean flag1 = false;

            if (d17 > 0.0625D && !playerEntity.isPlayerSleeping() && !playerEntity.theItemInWorldManager.isCreative())
            {
                flag1 = true;
                mcServer.getLogAgent().logWarning((new StringBuilder()).append(playerEntity.username).append(" moved wrongly!").toString());
            }

            playerEntity.setPositionAndRotation(d3, d5, d7, f2, f3);
            boolean flag2 = worldserver.getCollidingBoundingBoxes(playerEntity, playerEntity.boundingBox.copy().contract(f4, f4, f4)).isEmpty();

            if (flag && (flag1 || !flag2) && !playerEntity.isPlayerSleeping())
            {
                setPlayerLocation(lastPosX, lastPosY, lastPosZ, f2, f3);
                return;
            }

            AxisAlignedBB axisalignedbb = playerEntity.boundingBox.copy().expand(f4, f4, f4).addCoord(0.0D, -0.55000000000000004D, 0.0D);

            if (!mcServer.isFlightAllowed() && !playerEntity.theItemInWorldManager.isCreative() && !worldserver.isAABBNonEmpty(axisalignedbb))
            {
                if (d18 >= -0.03125D)
                {
                    ticksForFloatKick++;

                    if (ticksForFloatKick > 80)
                    {
                        mcServer.getLogAgent().logWarning((new StringBuilder()).append(playerEntity.username).append(" was kicked for floating too long!").toString());
                        kickPlayerFromServer("Flying is not enabled on this server");
                        return;
                    }
                }
            }
            else
            {
                ticksForFloatKick = 0;
            }

            playerEntity.onGround = par1Packet10Flying.onGround;
            mcServer.getConfigurationManager().serverUpdateMountedMovingPlayer(playerEntity);
            playerEntity.updateFlyingState(playerEntity.posY - d1, par1Packet10Flying.onGround);
        }
    }

    /**
     * Moves the player to the specified destination and rotation
     */
    public void setPlayerLocation(double par1, double par3, double par5, float par7, float par8)
    {
        hasMoved = false;
        lastPosX = par1;
        lastPosY = par3;
        lastPosZ = par5;
        playerEntity.setPositionAndRotation(par1, par3, par5, par7, par8);
        playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet13PlayerLookMove(par1, par3 + 1.6200000047683716D, par3, par5, par7, par8, false));
    }

    public void handleBlockDig(Packet14BlockDig par1Packet14BlockDig)
    {
        WorldServer worldserver = mcServer.worldServerForDimension(playerEntity.dimension);

        if (par1Packet14BlockDig.status == 4)
        {
            playerEntity.dropOneItem(false);
            return;
        }

        if (par1Packet14BlockDig.status == 3)
        {
            playerEntity.dropOneItem(true);
            return;
        }

        if (par1Packet14BlockDig.status == 5)
        {
            playerEntity.stopUsingItem();
            return;
        }

        boolean flag = false;

        if (par1Packet14BlockDig.status == 0)
        {
            flag = true;
        }

        if (par1Packet14BlockDig.status == 1)
        {
            flag = true;
        }

        if (par1Packet14BlockDig.status == 2)
        {
            flag = true;
        }

        int i = par1Packet14BlockDig.xPosition;
        int j = par1Packet14BlockDig.yPosition;
        int k = par1Packet14BlockDig.zPosition;

        if (flag)
        {
            double d = playerEntity.posX - ((double)i + 0.5D);
            double d1 = (playerEntity.posY - ((double)j + 0.5D)) + 1.5D;
            double d2 = playerEntity.posZ - ((double)k + 0.5D);
            double d3 = d * d + d1 * d1 + d2 * d2;

            if (d3 > 36D)
            {
                return;
            }

            if (j >= mcServer.getBuildLimit())
            {
                return;
            }
        }

        if (par1Packet14BlockDig.status == 0)
        {
            if (!mcServer.func_96290_a(worldserver, i, j, k, playerEntity))
            {
                playerEntity.theItemInWorldManager.onBlockClicked(i, j, k, par1Packet14BlockDig.face);
            }
            else
            {
                playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet53BlockChange(i, j, k, worldserver));
            }
        }
        else if (par1Packet14BlockDig.status == 2)
        {
            playerEntity.theItemInWorldManager.uncheckedTryHarvestBlock(i, j, k);

            if (worldserver.getBlockId(i, j, k) != 0)
            {
                playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet53BlockChange(i, j, k, worldserver));
            }
        }
        else if (par1Packet14BlockDig.status == 1)
        {
            playerEntity.theItemInWorldManager.cancelDestroyingBlock(i, j, k);

            if (worldserver.getBlockId(i, j, k) != 0)
            {
                playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet53BlockChange(i, j, k, worldserver));
            }
        }
    }

    public void handlePlace(Packet15Place par1Packet15Place)
    {
        WorldServer worldserver = mcServer.worldServerForDimension(playerEntity.dimension);
        ItemStack itemstack = playerEntity.inventory.getCurrentItem();
        boolean flag = false;
        int i = par1Packet15Place.getXPosition();
        int j = par1Packet15Place.getYPosition();
        int k = par1Packet15Place.getZPosition();
        int l = par1Packet15Place.getDirection();

        if (par1Packet15Place.getDirection() == 255)
        {
            if (itemstack == null)
            {
                return;
            }

            playerEntity.theItemInWorldManager.tryUseItem(playerEntity, worldserver, itemstack);
        }
        else if (par1Packet15Place.getYPosition() < mcServer.getBuildLimit() - 1 || par1Packet15Place.getDirection() != 1 && par1Packet15Place.getYPosition() < mcServer.getBuildLimit())
        {
            if (hasMoved && playerEntity.getDistanceSq((double)i + 0.5D, (double)j + 0.5D, (double)k + 0.5D) < 64D && !mcServer.func_96290_a(worldserver, i, j, k, playerEntity))
            {
                playerEntity.theItemInWorldManager.activateBlockOrUseItem(playerEntity, worldserver, itemstack, i, j, k, l, par1Packet15Place.getXOffset(), par1Packet15Place.getYOffset(), par1Packet15Place.getZOffset());
            }

            flag = true;
        }
        else
        {
            playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet3Chat((new StringBuilder()).append("").append(EnumChatFormatting.GRAY).append("Height limit for building is ").append(mcServer.getBuildLimit()).toString()));
            flag = true;
        }

        if (flag)
        {
            playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet53BlockChange(i, j, k, worldserver));

            if (l == 0)
            {
                j--;
            }

            if (l == 1)
            {
                j++;
            }

            if (l == 2)
            {
                k--;
            }

            if (l == 3)
            {
                k++;
            }

            if (l == 4)
            {
                i--;
            }

            if (l == 5)
            {
                i++;
            }

            playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet53BlockChange(i, j, k, worldserver));
        }

        itemstack = playerEntity.inventory.getCurrentItem();

        if (itemstack != null && itemstack.stackSize == 0)
        {
            playerEntity.inventory.mainInventory[playerEntity.inventory.currentItem] = null;
            itemstack = null;
        }

        if (itemstack == null || itemstack.getMaxItemUseDuration() == 0)
        {
            playerEntity.playerInventoryBeingManipulated = true;
            playerEntity.inventory.mainInventory[playerEntity.inventory.currentItem] = ItemStack.copyItemStack(playerEntity.inventory.mainInventory[playerEntity.inventory.currentItem]);
            Slot slot = playerEntity.openContainer.getSlotFromInventory(playerEntity.inventory, playerEntity.inventory.currentItem);
            playerEntity.openContainer.detectAndSendChanges();
            playerEntity.playerInventoryBeingManipulated = false;

            if (!ItemStack.areItemStacksEqual(playerEntity.inventory.getCurrentItem(), par1Packet15Place.getItemStack()))
            {
                sendPacketToPlayer(new Packet103SetSlot(playerEntity.openContainer.windowId, slot.slotNumber, playerEntity.inventory.getCurrentItem()));
            }
        }
    }

    public void handleErrorMessage(String par1Str, Object par2ArrayOfObj[])
    {
        mcServer.getLogAgent().logInfo((new StringBuilder()).append(playerEntity.username).append(" lost connection: ").append(par1Str).toString());
        mcServer.getConfigurationManager().sendPacketToAllPlayers(new Packet3Chat((new StringBuilder()).append(EnumChatFormatting.YELLOW).append(playerEntity.func_96090_ax()).append(" left the game.").toString()));
        mcServer.getConfigurationManager().playerLoggedOut(playerEntity);
        connectionClosed = true;

        if (mcServer.isSinglePlayer() && playerEntity.username.equals(mcServer.getServerOwner()))
        {
            mcServer.getLogAgent().logInfo("Stopping singleplayer server as player logged out");
            mcServer.initiateShutdown();
        }
    }

    /**
     * Default handler called for packets that don't have their own handlers in NetClientHandler; currentlly does
     * nothing.
     */
    public void unexpectedPacket(Packet par1Packet)
    {
        mcServer.getLogAgent().logWarning((new StringBuilder()).append(getClass()).append(" wasn't prepared to deal with a ").append(par1Packet.getClass()).toString());
        kickPlayerFromServer("Protocol error, unexpected packet");
    }

    /**
     * addToSendQueue. if it is a chat packet, check before sending it
     */
    public void sendPacketToPlayer(Packet par1Packet)
    {
        if (par1Packet instanceof Packet3Chat)
        {
            Packet3Chat packet3chat = (Packet3Chat)par1Packet;
            int i = playerEntity.getChatVisibility();

            if (i == 2)
            {
                return;
            }

            if (i == 1 && !packet3chat.getIsServer())
            {
                return;
            }
        }

        try
        {
            netManager.addToSendQueue(par1Packet);
            if (par1Packet instanceof Packet1Login){
               net.minecraft.client.Minecraft.getMinecraft().onLoginServer(playerEntity);
            }
        }
        catch (Throwable throwable)
        {
            CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Sending packet");
            CrashReportCategory crashreportcategory = crashreport.makeCategory("Packet being sent");
            crashreportcategory.addCrashSectionCallable("Packet ID", new CallablePacketID(this, par1Packet));
            crashreportcategory.addCrashSectionCallable("Packet class", new CallablePacketClass(this, par1Packet));
            throw new ReportedException(crashreport);
        }
    }

    public void handleBlockItemSwitch(Packet16BlockItemSwitch par1Packet16BlockItemSwitch)
    {
        if (par1Packet16BlockItemSwitch.id < 0 || par1Packet16BlockItemSwitch.id >= InventoryPlayer.getHotbarSize())
        {
            mcServer.getLogAgent().logWarning((new StringBuilder()).append(playerEntity.username).append(" tried to set an invalid carried item").toString());
            return;
        }
        else
        {
            playerEntity.inventory.currentItem = par1Packet16BlockItemSwitch.id;
            return;
        }
    }

    public void handleChat(Packet3Chat par1Packet3Chat)
    {
        net.minecraft.client.Minecraft.invokeModMethod("ModLoader", "serverChat", new Class[]{NetServerHandler.class, String.class}, this, par1Packet3Chat.message);
        if (playerEntity.getChatVisibility() == 2)
        {
            sendPacketToPlayer(new Packet3Chat("Cannot send chat message."));
            return;
        }

        String s = par1Packet3Chat.message;

        if (s.length() > 100)
        {
            kickPlayerFromServer("Chat message too long");
            return;
        }

        s = s.trim();

        for (int i = 0; i < s.length(); i++)
        {
            if (!ChatAllowedCharacters.isAllowedCharacter(s.charAt(i)))
            {
                kickPlayerFromServer("Illegal characters in chat");
                return;
            }
        }

        if (s.startsWith("/"))
        {
            handleSlashCommand(s);
        }
        else
        {
            if (playerEntity.getChatVisibility() == 1)
            {
                sendPacketToPlayer(new Packet3Chat("Cannot send chat message."));
                return;
            }

            s = (new StringBuilder()).append("<").append(playerEntity.func_96090_ax()).append("> ").append(s).toString();
            mcServer.getLogAgent().logInfo(s);
            mcServer.getConfigurationManager().sendPacketToAllPlayers(new Packet3Chat(s, false));
        }

        chatSpamThresholdCount += 20;

        if (chatSpamThresholdCount > 200 && !mcServer.getConfigurationManager().areCommandsAllowed(playerEntity.username))
        {
            kickPlayerFromServer("disconnect.spam");
        }
    }

    /**
     * Processes a / command
     */
    private void handleSlashCommand(String par1Str)
    {
        mcServer.getCommandManager().executeCommand(playerEntity, par1Str);
    }

    public void handleAnimation(Packet18Animation par1Packet18Animation)
    {
        if (par1Packet18Animation.animate == 1)
        {
            playerEntity.swingItem();
        }
    }

    /**
     * runs registerPacket on the given Packet19EntityAction
     */
    public void handleEntityAction(Packet19EntityAction par1Packet19EntityAction)
    {
        if (par1Packet19EntityAction.state == 1)
        {
            playerEntity.setSneaking(true);
        }
        else if (par1Packet19EntityAction.state == 2)
        {
            playerEntity.setSneaking(false);
        }
        else if (par1Packet19EntityAction.state == 4)
        {
            playerEntity.setSprinting(true);
        }
        else if (par1Packet19EntityAction.state == 5)
        {
            playerEntity.setSprinting(false);
        }
        else if (par1Packet19EntityAction.state == 3)
        {
            playerEntity.wakeUpPlayer(false, true, true);
            hasMoved = false;
        }
    }

    public void handleKickDisconnect(Packet255KickDisconnect par1Packet255KickDisconnect)
    {
        netManager.networkShutdown("disconnect.quitting", new Object[0]);
    }

    /**
     * returns 0 for memoryMapped connections
     */
    public int packetSize()
    {
        return netManager.packetSize();
    }

    public void handleUseEntity(Packet7UseEntity par1Packet7UseEntity)
    {
        WorldServer worldserver = mcServer.worldServerForDimension(playerEntity.dimension);
        Entity entity = worldserver.getEntityByID(par1Packet7UseEntity.targetEntity);

        if (entity != null)
        {
            boolean flag = playerEntity.canEntityBeSeen(entity);
            double d = 36D;

            if (!flag)
            {
                d = 9D;
            }

            if (playerEntity.getDistanceSqToEntity(entity) < d)
            {
                if (par1Packet7UseEntity.isLeftClick == 0)
                {
                    playerEntity.interactWith(entity);
                }
                else if (par1Packet7UseEntity.isLeftClick == 1)
                {
                    playerEntity.attackTargetEntityWithCurrentItem(entity);
                }
            }
        }
    }

    public void handleClientCommand(Packet205ClientCommand par1Packet205ClientCommand)
    {
        if (par1Packet205ClientCommand.forceRespawn == 1)
        {
            if (playerEntity.playerConqueredTheEnd)
            {
                playerEntity = mcServer.getConfigurationManager().respawnPlayer(playerEntity, 0, true);
            }
            else if (playerEntity.getServerForPlayer().getWorldInfo().isHardcoreModeEnabled())
            {
                if (mcServer.isSinglePlayer() && playerEntity.username.equals(mcServer.getServerOwner()))
                {
                    playerEntity.playerNetServerHandler.kickPlayerFromServer("You have died. Game over, man, it's game over!");
                    mcServer.deleteWorldAndStopServer();
                }
                else
                {
                    BanEntry banentry = new BanEntry(playerEntity.username);
                    banentry.setBanReason("Death in Hardcore");
                    mcServer.getConfigurationManager().getBannedPlayers().put(banentry);
                    playerEntity.playerNetServerHandler.kickPlayerFromServer("You have died. Game over, man, it's game over!");
                }
            }
            else
            {
                if (playerEntity.getHealth() > 0)
                {
                    return;
                }

                playerEntity = mcServer.getConfigurationManager().respawnPlayer(playerEntity, 0, false);
            }
        }
    }

    /**
     * If this returns false, all packets will be queued for the main thread to handle, even if they would otherwise be
     * processed asynchronously. Used to avoid processing packets on the client before the world has been downloaded
     * (which happens on the main thread)
     */
    public boolean canProcessPacketsAsync()
    {
        return true;
    }

    /**
     * respawns the player
     */
    public void handleRespawn(Packet9Respawn packet9respawn)
    {
    }

    public void handleCloseWindow(Packet101CloseWindow par1Packet101CloseWindow)
    {
        playerEntity.closeInventory();
    }

    public void handleWindowClick(Packet102WindowClick par1Packet102WindowClick)
    {
        if (playerEntity.openContainer.windowId == par1Packet102WindowClick.window_Id && playerEntity.openContainer.isPlayerNotUsingContainer(playerEntity))
        {
            ItemStack itemstack = playerEntity.openContainer.slotClick(par1Packet102WindowClick.inventorySlot, par1Packet102WindowClick.mouseClick, par1Packet102WindowClick.holdingShift, playerEntity);

            if (ItemStack.areItemStacksEqual(par1Packet102WindowClick.itemStack, itemstack))
            {
                playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet106Transaction(par1Packet102WindowClick.window_Id, par1Packet102WindowClick.action, true));
                playerEntity.playerInventoryBeingManipulated = true;
                playerEntity.openContainer.detectAndSendChanges();
                playerEntity.updateHeldItem();
                playerEntity.playerInventoryBeingManipulated = false;
            }
            else
            {
                field_72586_s.addKey(playerEntity.openContainer.windowId, Short.valueOf(par1Packet102WindowClick.action));
                playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet106Transaction(par1Packet102WindowClick.window_Id, par1Packet102WindowClick.action, false));
                playerEntity.openContainer.setPlayerIsPresent(playerEntity, false);
                ArrayList arraylist = new ArrayList();

                for (int i = 0; i < playerEntity.openContainer.inventorySlots.size(); i++)
                {
                    arraylist.add(((Slot)playerEntity.openContainer.inventorySlots.get(i)).getStack());
                }

                playerEntity.sendContainerAndContentsToPlayer(playerEntity.openContainer, arraylist);
            }
        }
    }

    public void handleEnchantItem(Packet108EnchantItem par1Packet108EnchantItem)
    {
        if (playerEntity.openContainer.windowId == par1Packet108EnchantItem.windowId && playerEntity.openContainer.isPlayerNotUsingContainer(playerEntity))
        {
            playerEntity.openContainer.enchantItem(playerEntity, par1Packet108EnchantItem.enchantment);
            playerEntity.openContainer.detectAndSendChanges();
        }
    }

    /**
     * Handle a creative slot packet.
     */
    public void handleCreativeSetSlot(Packet107CreativeSetSlot par1Packet107CreativeSetSlot)
    {
        if (playerEntity.theItemInWorldManager.isCreative())
        {
            boolean flag = par1Packet107CreativeSetSlot.slot < 0;
            ItemStack itemstack = par1Packet107CreativeSetSlot.itemStack;
            boolean flag1 = par1Packet107CreativeSetSlot.slot >= 1 && par1Packet107CreativeSetSlot.slot < 36 + InventoryPlayer.getHotbarSize();
            boolean flag2 = itemstack == null || itemstack.itemID < Item.itemsList.length && itemstack.itemID >= 0 && Item.itemsList[itemstack.itemID] != null;
            boolean flag3 = itemstack == null || itemstack.getItemDamage() >= 0 && itemstack.getItemDamage() >= 0 && itemstack.stackSize <= 64 && itemstack.stackSize > 0;

            if (flag1 && flag2 && flag3)
            {
                if (itemstack == null)
                {
                    playerEntity.inventoryContainer.putStackInSlot(par1Packet107CreativeSetSlot.slot, null);
                }
                else
                {
                    playerEntity.inventoryContainer.putStackInSlot(par1Packet107CreativeSetSlot.slot, itemstack);
                }

                playerEntity.inventoryContainer.setPlayerIsPresent(playerEntity, true);
            }
            else if (flag && flag2 && flag3 && creativeItemCreationSpamThresholdTally < 200)
            {
                creativeItemCreationSpamThresholdTally += 20;
                EntityItem entityitem = playerEntity.dropPlayerItem(itemstack);

                if (entityitem != null)
                {
                    entityitem.setAgeToCreativeDespawnTime();
                }
            }
        }
    }

    public void handleTransaction(Packet106Transaction par1Packet106Transaction)
    {
        Short short1 = (Short)field_72586_s.lookup(playerEntity.openContainer.windowId);

        if (short1 != null && par1Packet106Transaction.shortWindowId == short1.shortValue() && playerEntity.openContainer.windowId == par1Packet106Transaction.windowId && !playerEntity.openContainer.isPlayerNotUsingContainer(playerEntity))
        {
            playerEntity.openContainer.setPlayerIsPresent(playerEntity, true);
        }
    }

    /**
     * Updates Client side signs
     */
    public void handleUpdateSign(Packet130UpdateSign par1Packet130UpdateSign)
    {
        WorldServer worldserver = mcServer.worldServerForDimension(playerEntity.dimension);

        if (worldserver.blockExists(par1Packet130UpdateSign.xPosition, par1Packet130UpdateSign.yPosition, par1Packet130UpdateSign.zPosition))
        {
            TileEntity tileentity = worldserver.getBlockTileEntity(par1Packet130UpdateSign.xPosition, par1Packet130UpdateSign.yPosition, par1Packet130UpdateSign.zPosition);

            if (tileentity instanceof TileEntitySign)
            {
                TileEntitySign tileentitysign = (TileEntitySign)tileentity;

                if (!tileentitysign.isEditable())
                {
                    mcServer.logWarning((new StringBuilder()).append("Player ").append(playerEntity.username).append(" just tried to change non-editable sign").toString());
                    return;
                }
            }

            for (int i = 0; i < 4; i++)
            {
                boolean flag = true;

                if (par1Packet130UpdateSign.signLines[i].length() > 15)
                {
                    flag = false;
                }
                else
                {
                    for (int l = 0; l < par1Packet130UpdateSign.signLines[i].length(); l++)
                    {
                        if (ChatAllowedCharacters.allowedCharacters.indexOf(par1Packet130UpdateSign.signLines[i].charAt(l)) < 0)
                        {
                            flag = false;
                        }
                    }
                }

                if (!flag)
                {
                    par1Packet130UpdateSign.signLines[i] = "!?";
                }
            }

            if (tileentity instanceof TileEntitySign)
            {
                int j = par1Packet130UpdateSign.xPosition;
                int k = par1Packet130UpdateSign.yPosition;
                int i1 = par1Packet130UpdateSign.zPosition;
                TileEntitySign tileentitysign1 = (TileEntitySign)tileentity;
                System.arraycopy(par1Packet130UpdateSign.signLines, 0, tileentitysign1.signText, 0, 4);
                tileentitysign1.onInventoryChanged();
                worldserver.markBlockForUpdate(j, k, i1);
            }
        }
    }

    /**
     * Handle a keep alive packet.
     */
    public void handleKeepAlive(Packet0KeepAlive par1Packet0KeepAlive)
    {
        if (par1Packet0KeepAlive.randomId == keepAliveRandomID)
        {
            int i = (int)(System.nanoTime() / 0xf4240L - keepAliveTimeSent);
            playerEntity.ping = (playerEntity.ping * 3 + i) / 4;
        }
    }

    /**
     * determine if it is a server handler
     */
    public boolean isServerHandler()
    {
        return true;
    }

    /**
     * Handle a player abilities packet.
     */
    public void handlePlayerAbilities(Packet202PlayerAbilities par1Packet202PlayerAbilities)
    {
        playerEntity.capabilities.isFlying = par1Packet202PlayerAbilities.getFlying() && playerEntity.capabilities.allowFlying;
    }

    public void handleAutoComplete(Packet203AutoComplete par1Packet203AutoComplete)
    {
        StringBuilder stringbuilder = new StringBuilder();
        String s;

        for (Iterator iterator = mcServer.getPossibleCompletions(playerEntity, par1Packet203AutoComplete.getText()).iterator(); iterator.hasNext(); stringbuilder.append(s))
        {
            s = (String)iterator.next();

            if (stringbuilder.length() > 0)
            {
                stringbuilder.append("\0");
            }
        }

        playerEntity.playerNetServerHandler.sendPacketToPlayer(new Packet203AutoComplete(stringbuilder.toString()));
    }

    public void handleClientInfo(Packet204ClientInfo par1Packet204ClientInfo)
    {
        playerEntity.updateClientInfo(par1Packet204ClientInfo);
    }

    public void handleCustomPayload(Packet250CustomPayload par1Packet250CustomPayload)
    {
        if ("MC|BEdit".equals(par1Packet250CustomPayload.channel))
        {
            try
            {
                DataInputStream datainputstream = new DataInputStream(new ByteArrayInputStream(par1Packet250CustomPayload.data));
                ItemStack itemstack = Packet.readItemStack(datainputstream);

                if (!ItemWritableBook.validBookTagPages(itemstack.getTagCompound()))
                {
                    throw new IOException("Invalid book tag!");
                }

                ItemStack itemstack2 = playerEntity.inventory.getCurrentItem();

                if (itemstack != null && itemstack.itemID == Item.writableBook.itemID && itemstack.itemID == itemstack2.itemID)
                {
                    itemstack2.setTagInfo("pages", itemstack.getTagCompound().getTagList("pages"));
                }
            }
            catch (Exception exception)
            {
                exception.printStackTrace();
            }
        }
        else if ("MC|BSign".equals(par1Packet250CustomPayload.channel))
        {
            try
            {
                DataInputStream datainputstream1 = new DataInputStream(new ByteArrayInputStream(par1Packet250CustomPayload.data));
                ItemStack itemstack1 = Packet.readItemStack(datainputstream1);

                if (!ItemEditableBook.validBookTagContents(itemstack1.getTagCompound()))
                {
                    throw new IOException("Invalid book tag!");
                }

                ItemStack itemstack3 = playerEntity.inventory.getCurrentItem();

                if (itemstack1 != null && itemstack1.itemID == Item.writtenBook.itemID && itemstack3.itemID == Item.writableBook.itemID)
                {
                    itemstack3.setTagInfo("author", new NBTTagString("author", playerEntity.username));
                    itemstack3.setTagInfo("title", new NBTTagString("title", itemstack1.getTagCompound().getString("title")));
                    itemstack3.setTagInfo("pages", itemstack1.getTagCompound().getTagList("pages"));
                    itemstack3.itemID = Item.writtenBook.itemID;
                }
            }
            catch (Exception exception1)
            {
                exception1.printStackTrace();
            }
        }
        else if ("MC|TrSel".equals(par1Packet250CustomPayload.channel))
        {
            try
            {
                DataInputStream datainputstream2 = new DataInputStream(new ByteArrayInputStream(par1Packet250CustomPayload.data));
                int i = datainputstream2.readInt();
                Container container = playerEntity.openContainer;

                if (container instanceof ContainerMerchant)
                {
                    ((ContainerMerchant)container).setCurrentRecipeIndex(i);
                }
            }
            catch (Exception exception2)
            {
                exception2.printStackTrace();
            }
        }
        else if ("MC|AdvCdm".equals(par1Packet250CustomPayload.channel))
        {
            if (!mcServer.isCommandBlockEnabled())
            {
                playerEntity.sendChatToPlayer(playerEntity.translateString("advMode.notEnabled", new Object[0]));
            }
            else if (playerEntity.canCommandSenderUseCommand(2, "") && playerEntity.capabilities.isCreativeMode)
            {
                try
                {
                    DataInputStream datainputstream3 = new DataInputStream(new ByteArrayInputStream(par1Packet250CustomPayload.data));
                    int j = datainputstream3.readInt();
                    int l = datainputstream3.readInt();
                    int j1 = datainputstream3.readInt();
                    String s1 = Packet.readString(datainputstream3, 256);
                    TileEntity tileentity = playerEntity.worldObj.getBlockTileEntity(j, l, j1);

                    if (tileentity != null && (tileentity instanceof TileEntityCommandBlock))
                    {
                        ((TileEntityCommandBlock)tileentity).setCommand(s1);
                        playerEntity.worldObj.markBlockForUpdate(j, l, j1);
                        playerEntity.sendChatToPlayer((new StringBuilder()).append("Command set: ").append(s1).toString());
                    }
                }
                catch (Exception exception3)
                {
                    exception3.printStackTrace();
                }
            }
            else
            {
                playerEntity.sendChatToPlayer(playerEntity.translateString("advMode.notAllowed", new Object[0]));
            }
        }
        else if ("MC|Beacon".equals(par1Packet250CustomPayload.channel))
        {
            if (playerEntity.openContainer instanceof ContainerBeacon)
            {
                try
                {
                    DataInputStream datainputstream4 = new DataInputStream(new ByteArrayInputStream(par1Packet250CustomPayload.data));
                    int k = datainputstream4.readInt();
                    int i1 = datainputstream4.readInt();
                    ContainerBeacon containerbeacon = (ContainerBeacon)playerEntity.openContainer;
                    Slot slot = containerbeacon.getSlot(0);

                    if (slot.getHasStack())
                    {
                        slot.decrStackSize(1);
                        TileEntityBeacon tileentitybeacon = containerbeacon.getBeacon();
                        tileentitybeacon.setPrimaryEffect(k);
                        tileentitybeacon.setSecondaryEffect(i1);
                        tileentitybeacon.onInventoryChanged();
                    }
                }
                catch (Exception exception4)
                {
                    exception4.printStackTrace();
                }
            }
        }
        else if ("MC|ItemName".equals(par1Packet250CustomPayload.channel) && (playerEntity.openContainer instanceof ContainerRepair))
        {
            ContainerRepair containerrepair = (ContainerRepair)playerEntity.openContainer;

            if (par1Packet250CustomPayload.data == null || par1Packet250CustomPayload.data.length < 1)
            {
                containerrepair.updateItemName("");
            }
            else
            {
                String s = ChatAllowedCharacters.filerAllowedCharacters(new String(par1Packet250CustomPayload.data));

                if (s.length() <= 30)
                {
                    containerrepair.updateItemName(s);
                }
            }
        }
        else
        {
            net.minecraft.client.Minecraft.invokeModMethod("ModLoader", "serverCustomPayload", new Class[]{NetServerHandler.class, Packet250CustomPayload.class}, this, par1Packet250CustomPayload);
        }
    }
}
