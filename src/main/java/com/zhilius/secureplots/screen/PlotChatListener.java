/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.zhilius.secureplots.screen;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

/**
 * Chat-message interceptor for plot input flows.
 *
 * Sign-based input (rename, add member, create group, etc.) is handled by
 * {@link SignInputManager} together with {@code UpdateSignMixin}.
 *
 * {@link PendingAdd} and {@link PendingRename} are retained as extension points
 * but are not populated by any current code path; use {@link SignInputManager}
 * for all active input flows.
 */
public class PlotChatListener {

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> true);
    }
}
