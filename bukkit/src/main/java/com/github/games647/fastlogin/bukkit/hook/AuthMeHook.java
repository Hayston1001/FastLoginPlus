/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.RestoreSessionEvent;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.process.register.executors.ApiPasswordRegisterParams;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GitHub: <a href="https://github.com/Xephi/AuthMeReloaded/">...</a>
 * <p>
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/bukkit-plugins/authme-reloaded/">Bukkit</a>
 * <p>
 * <a href="https://www.spigotmc.org/resources/authme-reloaded.6269/">Spigot</a>
 */
public class AuthMeHook implements AuthPlugin<Player>, PreLoginAuthPlugin, Listener {

    private final FastLoginBukkit plugin;

    private final AuthMeApi authmeAPI;
    private Management authmeManagement;

    private volatile Method forceLoginFromProxyMethod;
    private volatile boolean forceLoginFromProxyResolved;
    private final AtomicBoolean preLoginUnsupportedLogged = new AtomicBoolean();

    public AuthMeHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
        this.authmeAPI = AuthMeApi.getInstance();

        if (plugin.getCore().getConfig().getBoolean("respectIpLimit")) {
            try {
                Field managementField = this.authmeAPI.getClass().getDeclaredField("management");
                managementField.setAccessible(true);
                this.authmeManagement = (Management) managementField.get(this.authmeAPI);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                this.authmeManagement = null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSessionRestore(RestoreSessionEvent restoreSessionEvent) {
        Player player = restoreSessionEvent.getPlayer();

        BukkitLoginSession session = plugin.getSession(player.spigot().getRawAddress());
        if (session != null && session.isVerifiedPremium()) {
            // AuthMe 6.0 with enablePremium: let AuthMe handle premium bypass itself
            // Only cancel RestoreSession if AuthMe is NOT handling premium
            com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
                plugin.getAuthMePremiumIntegrator();
            if (integrator != null && integrator.isAuthMePremiumEnabled()) {
                // AuthMe 6.0 premium is active — don't interfere with its flow
                return;
            }
            restoreSessionEvent.setCancelled(true);
        }
    }

    @Override
    public boolean notifyGeneratedPassword() {
        // AuthMe 6.0's takeover pre-creates the record itself with an empty password
        // hash (AuthMePremiumIntegrator), so the password FastLogin passes to forceRegister()
        // never reaches the database - announcing it would promise a password that cannot be
        // used. Verified on a real Spigot backend: the row ends up with password=''. AuthMe 5.x
        // registers through AuthMe's own API and does store it, so it keeps the default.
        com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
            plugin.getAuthMePremiumIntegrator();
        return integrator == null || !integrator.isAuthMePremiumEnabled();
    }

    @Override
    public boolean forceLogin(Player player) {
        if (authmeAPI.isAuthenticated(player)) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
            }
            return false;
        }

        //skips registration and login
        authmeAPI.forceLogin(player);
        return true;
    }

    @Override
    public boolean isRegistered(String playerName) {
        return authmeAPI.isRegistered(playerName);
    }

    @Override
    public boolean isAuthenticated(Player player) {
        return authmeAPI != null && authmeAPI.isAuthenticated(player);
    }

    @Override
    public boolean isPreLoginForceLoginSupported() {
        if (resolveForceLoginFromProxy() != null) {
            return true;
        }
        if (preLoginUnsupportedLogged.compareAndSet(false, true)) {
            plugin.getLog().error(
                    "AuthMe does not expose forceLoginFromProxy(String); the backend Floodgate "
                            + "pre-login path requires AuthMe 6.0.1+ and stays disabled");
        }
        return false;
    }

    @Override
    public boolean registerPlayer(String playerName, String password) {
        if (authmeAPI == null) {
            return false;
        }
        try {
            return authmeAPI.registerPlayer(playerName, password);
        } catch (RuntimeException registrationError) {
            // The external message may carry database details. Keep the type.
            plugin.getLog().error(
                    "AuthMe registerPlayer failed for {} during backend Floodgate pre-login "
                            + "(errorType={})",
                    playerName, registrationError.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public Registration captureRegistration(String playerName) {
        com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
                plugin.getAuthMePremiumIntegrator();
        return integrator == null ? null : integrator.captureRegistration(playerName);
    }

    @Override
    public boolean deleteRegistration(Registration registration) {
        com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
                plugin.getAuthMePremiumIntegrator();
        return integrator != null && integrator.deleteRegistration(registration);
    }

    @Override
    public boolean forceLoginFromProxy(String playerName) {
        Method method = resolveForceLoginFromProxy();
        if (method == null) {
            return false;
        }
        try {
            method.invoke(authmeAPI, playerName);
            return true;
        } catch (ReflectiveOperationException | RuntimeException invocationError) {
            plugin.getLog().error(
                    "AuthMe forceLoginFromProxy failed for {} during backend Floodgate pre-login "
                            + "(errorType={})",
                    playerName, invocationError.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Resolves AuthMe's name-based force login once. AuthMe 5.x does not have
     * the method; the caller must not create an account in that case because the
     * generated password is never delivered to the Bedrock player.
     *
     * @return the method, or null when this AuthMe version cannot do a pre-join login
     */
    private Method resolveForceLoginFromProxy() {
        if (!forceLoginFromProxyResolved) {
            synchronized (this) {
                if (!forceLoginFromProxyResolved) {
                    Method resolved = null;
                    if (authmeAPI != null) {
                        try {
                            resolved = authmeAPI.getClass()
                                    .getMethod("forceLoginFromProxy", String.class);
                        } catch (NoSuchMethodException missing) {
                            if (plugin.getCore().isDebug()) {
                                plugin.getLog().info(
                                        "AuthMe API has no forceLoginFromProxy(String) - backend "
                                                + "Floodgate auto-login is unavailable");
                            }
                        }
                    }
                    forceLoginFromProxyMethod = resolved;
                    forceLoginFromProxyResolved = true;
                }
            }
        }
        return forceLoginFromProxyMethod;
    }

    @Override
    public com.github.games647.fastlogin.core.shared.LoginMethodDecider.AccountState inspectAccount(
            String playerName) throws Exception {
        com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
                plugin.getAuthMePremiumIntegrator();
        if (integrator != null) {
            return integrator.inspectAccount(playerName);
        }
        return AuthPlugin.super.inspectAccount(playerName);
    }

    @Override
    //this automatically login the player too
    public boolean forceRegister(Player player, String password) {
        //if we have the management - we can trigger register with IP limit checks
        if (authmeManagement != null) {
            authmeManagement.performRegister(RegistrationMethod.PASSWORD_REGISTRATION,
                    ApiPasswordRegisterParams.of(player, password, true));
        } else {
            authmeAPI.forceRegister(player, password);
        }

        return true;
    }
}
