/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.event;

import com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class BukkitFastLoginPremiumToggleEvent extends Event implements FastLoginPremiumToggleEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender invoker;
    private final StoredProfile profile;
    private final PremiumToggleReason reason;

    public BukkitFastLoginPremiumToggleEvent(CommandSender invoker, StoredProfile profile, PremiumToggleReason reason) {
        super(true);

        this.invoker = invoker;
        this.profile = profile;
        this.reason = reason;
    }

    @Override
    public StoredProfile getProfile() {
        return profile;
    }

    /**
     * @return who triggered this change. This could be a Player for itself or others (Admin) or the console.
     */
    public CommandSender getInvoker() {
        return invoker;
    }

    @Override
    public PremiumToggleReason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
