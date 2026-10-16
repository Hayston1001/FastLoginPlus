/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.literal;

/**
 * Validates an authenticated forwarding claim before configuration and applies
 * the fixed EasyAuth 3.4.4 account policy at the start of play.
 */
public final class FastLoginFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");

    @Override
    public void onInitialize() {
        LOGGER.info("FastLoginPlus Fabric {} | EasyAuth {}",
                modVersion("fastloginplus_fabric"), modVersion("easyauth"));
        EasyAuth344Bridge bridge = new EasyAuth344Bridge();
        FabricCrackedNotice crackedNotice = new FabricCrackedNotice();
        crackedNotice.register();
        BindingConsentWriter.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) ->
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
                        }))));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            bridge.verifyPolicy();
            try {
                FabricConfig.warnIfUntrusted(FabricConfig.loadAtStartup());
            } catch (RuntimeException unreadable) {
                // never fail the server start over our own settings (R-6)
                LOGGER.warn("Fabric configuration could not be read: {}", unreadable.getMessage());
            }
        });
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((listener, server) -> {
            GameProfile profile = listener.getOwner();
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
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                GameProfile profile = handler.player.getGameProfile();
                IdentityClaim claim = parseClaim(profile);
                bridge.onJoin(handler.player, claim);
                crackedNotice.onJoin(handler.player, claim);
            } catch (RuntimeException rejected) {
                LOGGER.warn("Rejecting Fabric player {}: {}", handler.player.getGameProfile().name(),
                        rejected.getMessage());
                handler.disconnect(FabricMessages.reason(rejected, "auth-unavailable"));
            }
        });
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
