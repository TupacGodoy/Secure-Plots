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
package com.zhilius.secureplots.client;

import com.zhilius.secureplots.config.BorderConfig;

/**
 * Client-side holder for the border config received from the server.
 * Updated via SyncBorderConfigPayload on every join.
 */
public class PlotBorderRendererConfig {

    /** Current config — starts with defaults, replaced when server syncs. */
    public static BorderConfig current = BorderConfig.createDefault();

    public static void apply(BorderConfig config) {
        config.applyDefaults();
        current = config;
    }
}
