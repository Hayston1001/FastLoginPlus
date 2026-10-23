/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/**
 * Validates an authenticated forwarding claim before configuration and applies
 * the verified EasyAuth 3.4.x account API at the start of play.
 */
public final class FastLoginFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");
    private static ScheduledExecutorService updateExecutor;
    private static final FabricBedrockLogin BEDROCK_LOGIN = new FabricBedrockLogin();

    @Override
    public void onInitialize() {
        LOGGER.info("FastLoginPlus Fabric {} | EasyAuth {}",
                modVersion("fastloginplus_fabric"), modVersion("easyauth"));
        EasyAuth344Bridge bridge = new EasyAuth344Bridge();
        LOGGER.info("Fabric Bedrock auto-login requires a signed proxy claim "
                + "and an existing completed EasyAuth account.");
        FabricCrackedNotice crackedNotice = new FabricCrackedNotice();
        crackedNotice.register();
        BindingConsentWriter.register();
        DeleteConsentWriter.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) ->
                registerCommands(dispatcher, bridge));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            bridge.verifyPolicy();
            bridge.warnUnverifiedStorage();
            try {
                FabricConfig.StartupSettings settings = FabricConfig.loadAtStartup();
                FabricConfig.warnIfUntrusted(settings);
                scheduleUpdateCheck(settings);
            } catch (RuntimeException unreadable) {
                // never fail the server start over our own settings
                LOGGER.warn("Fabric configuration could not be read: {}", unreadable.getMessage());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopUpdateCheck());
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((listener, server) ->
                beforeConfigure(listener, server, bridge, crackedNotice));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                onJoin(handler, bridge, crackedNotice));
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) ->
                BEDROCK_LOGIN.disconnect(handler.getPacketContext().orElseThrow(PacketContext.CONNECTION)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BEDROCK_LOGIN.disconnect(handler.getPacketContext().orElseThrow(PacketContext.CONNECTION)));
    }

    static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, EasyAuth344Bridge bridge) {
        dispatcher.register(literal("flp")
                        .then(literal("premium").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            try {
                                bridge.beginBinding(player, parseClaim(player.getGameProfile()));
                                return 1;
                            } catch (RuntimeException rejected) {
                                LOGGER.warn("Premium binding rejected for {}",
                                        player.getGameProfile().name(), rejected);
                                player.sendSystemMessage(FabricMessages.reason(rejected, "auth-unavailable"));
                                return 0;
                            }
                        }).then(argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    var source = context.getSource();
                                    return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                                            ? SharedSuggestionProvider.suggest(source.getOnlinePlayerNames(), builder)
                                            : builder.buildFuture();
                                })
                                .executes(context -> adminPremium(context.getSource(),
                                        StringArgumentType.getString(context, "player"), bridge))))
                        .then(literal("cracked").executes(context -> {
                            var source = context.getSource();
                            if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                                source.sendSuccess(() -> FabricMessages.text("admin-command-permission"), false);
                                return 0;
                            }
                            bridge.cracked(source.getPlayerOrException());
                            return 1;
                        }).then(adminPlayerArgument().executes(context -> adminAccountCommand(context.getSource(),
                                StringArgumentType.getString(context, "player"), bridge, false))))
                        .then(literal("delete").then(adminPlayerArgument().executes(context ->
                                adminAccountCommand(context.getSource(),
                                        StringArgumentType.getString(context, "player"), bridge, true)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> adminPlayerArgument() {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.word())
                .suggests((context, builder) -> {
                    var source = context.getSource();
                    return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                            ? SharedSuggestionProvider.suggest(source.getOnlinePlayerNames(), builder)
                            : builder.buildFuture();
                });
    }

    private static int adminAccountCommand(CommandSourceStack source, String name,
                                           EasyAuth344Bridge bridge, boolean delete) {
        if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            source.sendSuccess(() -> FabricMessages.text("admin-command-permission"), false);
            return 0;
        }
        var carrier = source.getPlayer();
        if (carrier == null) {
            var players = source.getServer().getPlayerList().getPlayers();
            if (players.isEmpty()) {
                source.sendSuccess(() -> FabricMessages.text("admin-premium-no-carrier"), false);
                return 0;
            }
            carrier = players.getFirst();
        }
        if (delete) {
            bridge.adminDelete(carrier, name, source.getTextName(),
                    message -> source.sendSuccess(() -> message, false));
        } else {
            bridge.adminCracked(carrier, name, source.getTextName(),
                    message -> source.sendSuccess(() -> message, false));
        }
        return 1;
    }

    private static int adminPremium(CommandSourceStack source, String targetName, EasyAuth344Bridge bridge) {
        if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            source.sendSuccess(() -> FabricMessages.text("admin-premium-permission"), false);
            return 0;
        }
        var carrier = source.getPlayer();
        if (carrier == null) {
            var players = source.getServer().getPlayerList().getPlayers();
            if (players.isEmpty()) {
                source.sendSuccess(() -> FabricMessages.text("admin-premium-no-carrier"), false);
                return 0;
            }
            carrier = players.getFirst();
        }
        bridge.adminPremium(carrier, targetName, source.getTextName(),
                message -> source.sendSuccess(() -> message, false));
        return 1;
    }

    static void beforeConfigure(ServerConfigurationPacketListenerImpl listener, MinecraftServer server,
                                EasyAuth344Bridge bridge, FabricCrackedNotice crackedNotice) {
        GameProfile profile = listener.getOwner();
        if (FabricBedrockLogin.hasClaim(profile)) {
            try {
                BEDROCK_LOGIN.capture(listener.getPacketContext().orElseThrow(PacketContext.CONNECTION),
                        profile, FabricConfig.startup().allowedProxyIds(), System.currentTimeMillis(),
                        () -> bridge.beforeBedrock(server, profile.name(), profile.id()));
            } catch (RuntimeException rejected) {
                LOGGER.warn("Rejected Bedrock account verification for {}: {}", profile.name(), rejected.getMessage());
                listener.disconnect(FabricMessages.reason(rejected, "invalid-identity"));
            }
            return;
        }
        if (BedrockBoundary.isBedrock(profile.name(), profile.id())) {
            if (FabricConfig.isDebug()) {
                LOGGER.info("Rejecting unsupported Bedrock/Floodgate identity: name={} uuid={}",
                        profile.name(), profile.id());
            }
            listener.disconnect(FabricMessages.text("bedrock-unsupported"));
            return;
        }
        try {
            bridge.verifyPolicy();
            IdentityClaim claim = parseClaim(profile);
            bridge.beforeConfigure(server, claim);
            crackedNotice.beforeConfigure(listener, claim);
            if (FabricConfig.isDebug()) {
                LOGGER.info("Validated {} forwarding claim for {}; profile property names={}",
                        claim.getDecision(), profile.name(), profile.properties().keySet().size());
            }
        } catch (IllegalArgumentException invalidClaim) {
            LOGGER.warn("Invalid forwarding identity for {}: {}", profile.name(), invalidClaim.getMessage());
            listener.disconnect(FabricMessages.text("invalid-identity"));
        } catch (RuntimeException invalidEnvironment) {
            LOGGER.error("EasyAuth policy rejected the connection", invalidEnvironment);
            listener.disconnect(FabricMessages.reason(invalidEnvironment, "auth-unavailable"));
        }
    }

    static void onJoin(ServerGamePacketListenerImpl handler, EasyAuth344Bridge bridge,
                       FabricCrackedNotice crackedNotice) {
        GameProfile profile = handler.player.getGameProfile();
        if (FabricBedrockLogin.hasClaim(profile)) {
            try {
                var attempt = BEDROCK_LOGIN.take(handler.getPacketContext().orElseThrow(PacketContext.CONNECTION),
                        handler.player, profile, System.currentTimeMillis());
                if (attempt != null) {
                    bridge.onBedrockJoin(handler.player, attempt.owner);
                }
            } catch (RuntimeException rejected) {
                LOGGER.warn("Rejected Bedrock login for {}: {}", profile.name(), rejected.getMessage());
                handler.disconnect(FabricMessages.reason(rejected, "auth-unavailable"));
            }
            return;
        }
        if (BedrockBoundary.isBedrock(profile.name(), profile.id())) {
            if (FabricConfig.isDebug()) {
                LOGGER.info("Rejecting unsupported Bedrock/Floodgate identity: name={} uuid={}",
                        profile.name(), profile.id());
            }
            handler.disconnect(FabricMessages.text("bedrock-unsupported"));
            return;
        }
        try {
            IdentityClaim claim = parseClaim(profile);
            bridge.onJoin(handler.player, claim);
            crackedNotice.onJoin(handler.player, claim);
        } catch (RuntimeException rejected) {
            LOGGER.warn("Rejecting Fabric player {}: {}", profile.name(), rejected.getMessage());
            handler.disconnect(FabricMessages.reason(rejected, "auth-unavailable"));
        }
    }

    private static void scheduleUpdateCheck(FabricConfig.StartupSettings settings) {
        stopUpdateCheck();
        if (!settings.checkUpdate()) {
            return;
        }
        FabricUpdateChecker checker = new FabricUpdateChecker(
                LOGGER, modVersion("fastloginplus_fabric"), settings.debug());
        updateExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "FastLoginPlus update check");
            thread.setDaemon(true);
            return thread;
        });
        long intervalHours = Math.max(1, settings.updateCheckInterval());
        updateExecutor.scheduleWithFixedDelay(() -> {
            if (checker.checkForUpdates()) {
                LOGGER.warn(FabricMessages.plain("update-available",
                        "%new%", checker.getLatestVersion(),
                        "%current%", checker.getCurrentVersion()));
            }
        }, 3L, intervalHours * 3600L, TimeUnit.SECONDS);
    }

    private static void stopUpdateCheck() {
        ScheduledExecutorService executor = updateExecutor;
        updateExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static String modVersion(String id) {
        try {
            return FabricLoader.getInstance().getModContainer(id)
                    .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                    .filter(version -> !version.isBlank())
                    .orElse("unknown (unavailable)");
        } catch (RuntimeException unavailable) {
            return "unknown (unavailable)";
        }
    }

    static IdentityClaim parseClaim(GameProfile profile) {
        List<String> values = new ArrayList<>();
        for (Property property : profile.properties().get(IdentityClaim.PROPERTY_NAME)) {
            values.add(property.value());
        }
        return IdentityClaim.parseForFabric(values, profile.name(), profile.id());
    }
}
