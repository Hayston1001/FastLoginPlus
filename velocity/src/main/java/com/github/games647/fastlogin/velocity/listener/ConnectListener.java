/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;

import com.github.games647.fastlogin.velocity.event.VelocityFastLoginAntiBotEvent;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.shared.ForwardingAttributes;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.github.games647.fastlogin.protocol.CrackedNoticeClaim;
import com.github.games647.fastlogin.protocol.BedrockClaim;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockSession;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.task.AsyncPremiumCheck;
import com.github.games647.fastlogin.velocity.task.FloodgateAuthTask;
import com.github.games647.fastlogin.velocity.task.ForceLoginTask;
import com.google.common.cache.Cache;
import com.google.common.collect.ListMultimap;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.GameProfile.Property;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
public class ConnectListener {

    private static final String FLOODGATE_PLUGIN_NAME = "org.geysermc.floodgate.VelocityPlugin";

    private final FastLoginVelocity plugin;
    private final AntiBotService antiBotService;
    private final EarlyDeclarationSupport earlyDeclarations;

    public ConnectListener(FastLoginVelocity plugin, AntiBotService antiBotService) {
        this.plugin = plugin;
        this.antiBotService = antiBotService;
        this.earlyDeclarations = new EarlyDeclarationSupport(plugin.getLog());
    }

    // Later than the reviewed AuthMe NORMAL handler. Other plugins can still run later;
    // this is a guarantee for the reviewed combination, not exclusive event ownership.
    @Subscribe(order = PostOrder.LAST)
    public EventTask onPreLogin(PreLoginEvent preLoginEvent) {
        if (!preLoginEvent.getResult().isAllowed()) {
            return null;
        }

        InboundConnection connection = preLoginEvent.getConnection();
        String username = preLoginEvent.getUsername();
        InetSocketAddress address = connection.getRemoteAddress();
        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("Incoming login request for {} from {}", username, address);
        }

        // FloodgateVelocity only sets the correct username in GetProfileRequestEvent, but we need it here too.
        if (plugin.getFloodgateService() != null) {
            String floodgateUsername = getFloodgateUsername(connection);
            if (floodgateUsername != null) {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("Found player's Floodgate: {}", floodgateUsername);
                }
                username = floodgateUsername;
            }
        }

        // effectively-final snapshot for the lambda captures below (username
        // may have been rewritten by the Floodgate lookup above)
        final String checkedUsername = username;
        Action action = antiBotService.onIncomingConnection(address, checkedUsername);
        if (action != Action.Continue) {
            VelocityFastLoginAntiBotEvent antiBotEvent =
                    new VelocityFastLoginAntiBotEvent(address, checkedUsername, action);
            // Non-blocking: pause the event, fire the anti-bot
            // event asynchronously and resume from the completion callback —
            // the Netty event loop must never block on a synchronous .get().
            // per-event guard so the continuation is resumed exactly
            // once even when the callback or the decision applying throws
            AtomicBoolean resumed = new AtomicBoolean(false);
            return EventTask.withContinuation(continuation -> {
                try {
                    plugin.getProxy().getEventManager().fire(antiBotEvent).whenComplete((unused, ex) -> {
                        if (ex != null) {
                            plugin.getLog().error("Error firing anti-bot event", ex);
                            resumeOnce(continuation, resumed);
                            return;
                        }
                        Action effective = antiBotEvent.isCancelled() ? Action.Continue : action;
                        applyDecisionSafely(preLoginEvent, connection, checkedUsername, effective,
                                continuation, resumed);
                    });
                } catch (Exception fireEx) {
                    plugin.getLog().error("Error firing anti-bot event", fireEx);
                    resumeOnce(continuation, resumed);
                }
            });
        }

        // no anti-bot action — continue with the premium check
        // run it on the plugin scheduler instead of the shared
        // async event executor, so blocking Mojang lookups (with retry sleeps)
        // cannot starve the event executor for all other handlers
        // per-event guard so the continuation is resumed exactly once
        AtomicBoolean resumed = new AtomicBoolean(false);
        return EventTask.withContinuation(continuation ->
                applyDecisionSafely(preLoginEvent, connection, checkedUsername, action, continuation,
                        resumed));
    }

    /**
     * Apply the effective anti-bot decision to the pre-login event and resume
     * the paused event pipeline.
     *
     * @param preLoginEvent the paused pre-login event
     * @param connection    the inbound connection
     * @param username      the username from the pre-login event
     * @param action        the effective anti-bot action (third-party handlers
     *                      may have cancelled the original one)
     * @param continuation  the event continuation to resume
     * @param resumed       per-event exactly-once resume guard
     */
    private void applyAntiBotDecision(PreLoginEvent preLoginEvent, InboundConnection connection,
                                      String username, Action action, Continuation continuation,
                                      AtomicBoolean resumed) {
        switch (action) {
            case Block:
                String message = plugin.getCore().getMessage("kick-antibot");
                TextComponent messageParsed = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
                preLoginEvent.setResult(PreLoginComponentResult.denied(messageParsed));
                resumeOnce(continuation, resumed);
                break;
            case Continue:
            default:
                // third-party handler cancelled the anti-bot action — run the
                // premium check off the event loop and resume when it completes
                CompletableFuture<Void> scheduled = plugin.getScheduler().runAsync(() -> {
                    try {
                        new AsyncPremiumCheck(plugin, connection, username, preLoginEvent).run();
                    } catch (Exception runEx) {
                        plugin.getLog().error("Error during premium check", runEx);
                    } finally {
                        // same guard as the outer paths: at most one resume
                        resumeOnce(continuation, resumed);
                    }
                });
                // A stopped scheduler completes without running the task. Its
                // completion must still resume the paused login.
                scheduled.whenComplete((unused, error) -> resumeOnce(continuation, resumed));
                break;
        }
    }

    /**
     * apply the decision and guarantee that the continuation is
     * resumed even when applying it throws — otherwise the login hangs until
     * the read timeout. Failing connections surface as an undecided or
     * missing identity fact; the backend decides how to treat them.
     *
     * @param preLoginEvent the paused pre-login event
     * @param connection    the inbound connection
     * @param username      the username from the pre-login event
     * @param action        the effective anti-bot action
     * @param continuation  the event continuation to resume
     * @param resumed       per-event exactly-once guard
     */
    void applyDecisionSafely(PreLoginEvent preLoginEvent, InboundConnection connection,
                             String username, Action action, Continuation continuation,
                             AtomicBoolean resumed) {
        try {
            applyAntiBotDecision(preLoginEvent, connection, username, action, continuation, resumed);
        } catch (Exception decisionEx) {
            plugin.getLog().error("Error applying anti-bot decision for {}", username, decisionEx);
            resumeOnce(continuation, resumed);
        }
    }

    /**
     * resume the continuation at most once per event.
     *
     * @param continuation the event continuation
     * @param resumed      CAS guard; flipped to true on the first call
     */
    static void resumeOnce(Continuation continuation, AtomicBoolean resumed) {
        if (resumed.compareAndSet(false, true)) {
            continuation.resume();
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        LoginSession claimSession = plugin.getSession().get(event.getConnection().getRemoteAddress());
        if (event.isOnlineMode()) {
            LoginSession session = plugin.getSession().get(event.getConnection().getRemoteAddress());
            if (session == null) {
                plugin.getLog().error("No active login session found for onlinemode player {}", event.getUsername());
                earlyDeclarations.attempt(EarlyDeclarationSupport.FailureKind.PREMIUM_CLAIM,
                        () -> attachIdentityClaim(event, null));
                return;
            }

            // read the immutable original profile, not the mutable one. AuthMe's
            // Velocity premium handler (keepOfflineUuidCompatibility=true) caches the real
            // Mojang UUID and then rewrites the event's profile to the name-derived offline
            // UUID. Both handlers subscribe at the default priority, so registration order
            // decides who runs first — and FLP registers later, inside ProxyInitializeEvent.
            // getGameProfile() would therefore hand us the offline UUID, which we would go
            // on to store as the player's premium UUID. getOriginalProfile() is backed by a
            // final field and is the same one AuthMe reads, so the value no longer depends
            // on listener order.
            UUID verifiedUUID = event.getOriginalProfile().getId();
            String verifiedUsername = event.getUsername();
            session.setUuid(verifiedUUID);
            session.setVerifiedUsername(verifiedUsername);
            // this connection passed the Mojang verification: it refutes any
            // pending same-name cracked suspicion for the name
            plugin.getCore().resetVerificationState(verifiedUsername);

            StoredProfile playerProfile = session.getProfile();
            playerProfile.setId(verifiedUUID);
            if (!(boolean) plugin.getCore().getConfig().get("premiumUuid")) {
                UUID offlineUUID = UUIDAdapter.generateOfflineId(event.getUsername());
                // hand the verified Mojang UUID to the backend as a GameProfile
                // property. It rides the modern player-info forwarding payload, which the
                // backend decodes in the login phase — before AuthMe's preJoin dialog — so
                // the backend can pre-create the record and close the dialog on the very
                // first login. A separate plugin message cannot get there that early.
                // The forwarding HMAC protects the payload in transit,
                // but authorship is the proxy process as a whole, not this plugin: any
                // proxy-side code could inject the same property. Written BEFORE withId so the
                // rewrite below cannot discard it.
                // Property's constructor rejects a null signature; an empty string is
                // accepted and serialises as hasSignature=false.
                event.setGameProfile(event.getGameProfile().addProperty(new GameProfile.Property(
                        ForwardingAttributes.PREMIUM_UUID, verifiedUUID.toString(), "")));
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("Attaching verified premium UUID {} to the forwarded profile",
                            verifiedUUID);
                }
                event.setGameProfile(event.getGameProfile().withId(offlineUUID));
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("Overridden UUID from {} to {} (based of {}) on {}",
                            verifiedUUID, offlineUUID, verifiedUsername, event.getConnection());
                }
            }

            if (!(boolean) plugin.getCore().getConfig().get("forwardSkin")) {
                List<Property> newProp = removeSkin(event.getGameProfile().getProperties());
                event.setGameProfile(event.getGameProfile().withProperties(newProp));
            }
        }
        // Best effort: early failure is visible, but never disables the late sender.
        earlyDeclarations.attempt(event.isOnlineMode() ? EarlyDeclarationSupport.FailureKind.PREMIUM_CLAIM
                : EarlyDeclarationSupport.FailureKind.UNVERIFIED_DECLARATION,
                () -> attachIdentityClaim(event, claimSession));
        earlyDeclarations.attempt(EarlyDeclarationSupport.FailureKind.UNVERIFIED_DECLARATION,
                () -> attachBedrockClaim(event));
    }

    void attachBedrockClaim(GameProfileRequestEvent event) {
        GameProfile profile = event.getGameProfile();
        List<Property> properties = new ArrayList<>(profile.getProperties());
        boolean stripped = properties.removeIf(property -> BedrockClaim.PROPERTY_NAME.equals(property.getName()));
        FloodgatePlayer player = null;
        if (plugin.getFloodgateService() != null) {
            player = getFloodgatePlayer(event.getConnection());
            if (player == null) {
                player = plugin.getFloodgateService().getBedrockPlayer(profile.getId());
            }
        }
        if (player != null) {
            BedrockSession policy = BedrockSession.fromConfig(plugin.getCore().getConfig(),
                    player.getLinkedPlayer() != null);
            BedrockClaim claim = new BedrockClaim(profile.getName(), profile.getId(), policy.isLinked(),
                    policy.getAutoLogin(), policy.getAutoRegister(), policy.getAllowConflict(),
                    System.currentTimeMillis(), UUID.randomUUID());
            properties.add(new Property(BedrockClaim.PROPERTY_NAME, claim.encode(plugin.getProxyId()), ""));
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Attached signed proxy Bedrock facts for {}", profile.getName());
            }
        }
        if (player != null || stripped) {
            event.setGameProfile(profile.withProperties(properties));
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void refreshBedrockClaim(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        FloodgatePlayer bedrock = plugin.getFloodgateService() == null ? null
                : plugin.getFloodgateService().getBedrockPlayer(player.getUniqueId());
        if (bedrock == null) {
            return;
        }
        BedrockSession policy = BedrockSession.fromConfig(plugin.getCore().getConfig(),
                bedrock.getLinkedPlayer() != null);
        List<Property> properties = new ArrayList<>(player.getGameProfileProperties());
        properties.removeIf(property -> BedrockClaim.PROPERTY_NAME.equals(property.getName()));
        BedrockClaim claim = new BedrockClaim(player.getUsername(), player.getUniqueId(), policy.isLinked(),
                policy.getAutoLogin(), policy.getAutoRegister(), policy.getAllowConflict(),
                System.currentTimeMillis(), UUID.randomUUID());
        properties.add(new Property(BedrockClaim.PROPERTY_NAME, claim.encode(plugin.getProxyId()), ""));
        player.setGameProfileProperties(properties);
    }

    private void attachIdentityClaim(GameProfileRequestEvent event, LoginSession session) {
        GameProfile profile = event.getGameProfile();
        UUID forwardedUuid = profile.getId();
        if (forwardedUuid == null) {
            throw new IllegalArgumentException("Cannot attach identity claim without a forwarded UUID");
        }

        // Clear any profile property with our reserved names. Exactly one value may be
        // forwarded; malformed or repeated values must never survive as an alternate claim.
        List<Property> properties = new ArrayList<>(profile.getProperties().size());
        for (Property property : profile.getProperties()) {
            if (!ForwardingAttributes.IDENTITY.equals(property.getName())
                    && !ForwardingAttributes.IDENTITY_SIGNATURE.equals(property.getName())
                    && !CrackedNoticeClaim.PROPERTY_NAME.equals(property.getName())) {
                properties.add(property);
            }
        }
        IdentityClaim claim;
        try {
            boolean bedrock = plugin.getFloodgateService() != null
                    && getFloodgateUsername(event.getConnection()) != null;
            if (session != null) {
                // observation facts + upgrade consent, computed from the record state
                // this connection saw at login time
                session.computeFacts(plugin.getCore());
            }
            claim = IdentityDecisionPolicy.decide(session, event.isOnlineMode(), bedrock,
                    (boolean) plugin.getCore().getConfig().get("premiumUuid"), profile.getName(), forwardedUuid);
        } catch (IllegalArgumentException invalidProfile) {
            throw new IllegalArgumentException("Cannot attach identity claim for an invalid profile", invalidProfile);
        }
        String identityValue = claim.encode();
        properties.add(new Property(ForwardingAttributes.IDENTITY, identityValue, ""));
        // Legacy and BungeeGuard forwarding do not sign the profile, so the backend
        // authenticates flp_identity with this HMAC keyed by the shared proxy id.
        // Modern forwarding signs the whole payload and ignores the extra property.
        properties.add(new Property(ForwardingAttributes.IDENTITY_SIGNATURE,
                IdentityClaim.sign(identityValue, plugin.getProxyId()), ""));
        if (claim.getDecision() == IdentityClaim.Decision.OFFLINE && claim.isSameNameCracked()) {
            try {
                CrackedNoticeClaim notice = plugin.getCrackedNoticeDelivery().prepare(
                        event.getConnection(), profile.getName(), forwardedUuid);
                if (notice != null) {
                    properties.add(new Property(CrackedNoticeClaim.PROPERTY_NAME, notice.encode(), ""));
                }
            } catch (RuntimeException unavailable) {
                // Notification failures must not interfere with the identity claim or authentication.
                plugin.getLog().warn("Could not attach the optional cracked notice for {}", profile.getName());
            }
        }
        event.setGameProfile(profile.withProperties(properties));
    }

    private List<GameProfile.Property> removeSkin(Collection<Property> oldProperties) {
        List<GameProfile.Property> newProperties = new ArrayList<>(oldProperties.size());
        for (GameProfile.Property property : oldProperties) {
            if (!"textures".equals(property.getName())) {
                newProperties.add(property);
            }
        }

        return newProperties;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent serverConnectedEvent) {
        Player player = serverConnectedEvent.getPlayer();
        RegisteredServer server = serverConnectedEvent.getServer();

        FloodgateService floodgateService = plugin.getFloodgateService();
        if (floodgateService != null) {
            FloodgatePlayer floodgatePlayer = floodgateService.getBedrockPlayer(player.getUniqueId());
            if (floodgatePlayer != null) {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("Running floodgate handling for {}", player);
                }
                Runnable floodgateAuthTask = new FloodgateAuthTask(plugin.getCore(), player, floodgatePlayer, server);
                plugin.getScheduler().runAsync(floodgateAuthTask);
                return;
            }
        }

        VelocityLoginSession session = plugin.getSession().get(player.getRemoteAddress());
        if (session == null) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("No active login session found on server connect for {}", player);
            }
            return;
        }
        if (!player.isOnlineMode() && session.getIdentityDecision() == IdentityClaim.Decision.OFFLINE) {
            plugin.getScheduler().runAsync(() -> plugin.getCrackedNoticeDelivery().deliver(player, server));
        }

        // delay sending force command, because Paper will process the login event asynchronously
        // In this case it means that the force command (plugin message) is already received and processed while
        // player is still in the login phase and reported to be offline.
        Runnable loginTask = new ForceLoginTask(plugin.getCore(), player, server, session);

        // Delay at least one second, otherwise the login command can be missed
        plugin.getScheduler().runAsyncDelayed(loginTask, Duration.ofSeconds(1));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        Player player = disconnectEvent.getPlayer();
        plugin.getCrackedNoticeDelivery().disconnected(player);
        plugin.getCore().getPendingConfirms().remove(player.getUniqueId());

        plugin.getSession().remove(player.getRemoteAddress());
    }

    /**
     * Get the Floodgate username from the Floodgate plugin's playerCache using lots of reflections
     *
     * @param connection
     * @return the Floodgate username or null if not found
     */
    private String getFloodgateUsername(InboundConnection connection) {
        FloodgatePlayer player = getFloodgatePlayer(connection);
        return player == null ? null : player.getCorrectUsername();
    }

    private FloodgatePlayer getFloodgatePlayer(InboundConnection connection) {
        try {
            // get floodgate's event handler
            Object floodgateEventHandler = getFloodgateHandler();
            if (floodgateEventHandler == null) {
                return null;
            }

            // Get the Floodgate playerCache field
            Field playerCacheField = floodgateEventHandler.getClass().getDeclaredField("playerCache");
            playerCacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Cache<InboundConnection, FloodgatePlayer> playerCache =
                    (Cache<InboundConnection, FloodgatePlayer>) playerCacheField.get(floodgateEventHandler);

            // Find the FloodgatePlayer instance in playerCache
            FloodgatePlayer floodgatePlayer = playerCache.getIfPresent(connection);
            if (floodgatePlayer == null) {
                return null;
            }

            return floodgatePlayer;
        } catch (Exception ex) {
            plugin.getLog().error("Failed to fetch current floodgate username", ex);
        }

        return null;
    }

    private Object getFloodgateHandler()
            throws NoSuchFieldException, IllegalAccessException {
        // Get Velocity's event manager
        EventManager eventManager = plugin.getServer().getEventManager();
        Field handlerField = eventManager.getClass().getDeclaredField("handlersByType");
        handlerField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ListMultimap<Class<?>, ?> handlersByType = (ListMultimap<Class<?>, ?>) handlerField.get(eventManager);

        // Get all registered PreLoginEvent handlers
        List<?> loginEventRegistrations = handlersByType.get(PreLoginEvent.class);
        Field pluginField = loginEventRegistrations.get(0).getClass().getDeclaredField("plugin");
        pluginField.setAccessible(true);

        // Find the Floodgate plugin's PreLoginEvent handler registration (Velocity implementation)
        Object floodgateRegistration = null;
        for (Object handler : loginEventRegistrations) {
            PluginContainer eventHandlerPlugin = (PluginContainer) pluginField.get(handler);
            String eventHandlerPluginName = eventHandlerPlugin.getInstance().get().getClass().getName();
            if (eventHandlerPluginName.equals(FLOODGATE_PLUGIN_NAME)) {
                floodgateRegistration = handler;
                break;
            }
        }

        if (floodgateRegistration == null) {
            return null;
        }

        // Extract the EventHandler instance (floodgate impl) from Velocity's internal registration handler storage
        Field eventHandlerField = floodgateRegistration.getClass().getDeclaredField("instance");
        eventHandlerField.setAccessible(true);
        return eventHandlerField.get(floodgateRegistration);
    }
}
