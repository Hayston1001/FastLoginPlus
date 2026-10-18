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
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/**
 * Validates an authenticated forwarding claim before configuration and applies
 * the verified EasyAuth 3.4.x account API at the start of play.
 */
public final class FastLoginFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");

    @Override
    public void onInitialize() {
        LOGGER.info("FastLoginPlus Fabric {} | EasyAuth {}",
                modVersion("fastloginplus_fabric"), modVersion("easyauth"));
        EasyAuth344Bridge bridge = new EasyAuth344Bridge();
        LOGGER.info("Fabric mode supports Java identities only; Bedrock/Floodgate connections will be rejected.");
        FabricCrackedNotice crackedNotice = new FabricCrackedNotice();
        crackedNotice.register();
        BindingConsentWriter.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) ->
                registerCommands(dispatcher, bridge));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            bridge.verifyPolicy();
            bridge.warnUnverifiedStorage();
            try {
                FabricConfig.warnIfUntrusted(FabricConfig.loadAtStartup());
            } catch (RuntimeException unreadable) {
                // never fail the server start over our own settings (R-6)
                LOGGER.warn("Fabric configuration could not be read: {}", unreadable.getMessage());
            }
        });
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((listener, server) ->
                beforeConfigure(listener, server, bridge, crackedNotice));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                onJoin(handler, bridge, crackedNotice));
    }

    static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, EasyAuth344Bridge bridge) {
        dispatcher.register(literal("flp")
                        .then(literal("premium").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            try {
                                bridge.beginBinding(context.getSource().getServer(), player,
                                        parseClaim(player.getGameProfile()));
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
                                        StringArgumentType.getString(context, "player"), bridge)))));
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
        bridge.adminPremium(source.getServer(), carrier, targetName, source.getTextName(),
                message -> source.sendSuccess(() -> message, false));
        return 1;
    }

    static void beforeConfigure(ServerConfigurationPacketListenerImpl listener, MinecraftServer server,
                                EasyAuth344Bridge bridge, FabricCrackedNotice crackedNotice) {
        GameProfile profile = listener.getOwner();
        if (BedrockBoundary.isBedrock(profile.name(), profile.id())) {
            LOGGER.info("Rejecting unsupported Bedrock/Floodgate identity: name={} uuid={}",
                    profile.name(), profile.id());
            listener.disconnect(FabricMessages.text("bedrock-unsupported"));
            return;
        }
        try {
            bridge.verifyPolicy();
            IdentityClaim claim = parseClaim(profile);
            bridge.beforeConfigure(server, claim);
            crackedNotice.beforeConfigure(listener, claim);
            LOGGER.info("Validated {} forwarding claim for {}; profile property names={}",
                    claim.getDecision(), profile.name(), profile.properties().keySet().size());
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
        if (BedrockBoundary.isBedrock(profile.name(), profile.id())) {
            LOGGER.info("Rejecting unsupported Bedrock/Floodgate identity: name={} uuid={}",
                    profile.name(), profile.id());
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
