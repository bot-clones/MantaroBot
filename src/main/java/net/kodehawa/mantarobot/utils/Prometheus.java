/*
 * Copyright (C) 2016-2021 David Rubio Escares / Kodehawa
 *
 *  Mantaro is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  Mantaro is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro. If not, see http://www.gnu.org/licenses/
 */

package net.kodehawa.mantarobot.utils;

import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.BufferPoolsExports;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import io.prometheus.client.hotspot.StandardExports;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.utils.exporters.DiscordLatencyExports;
import net.kodehawa.mantarobot.utils.exporters.JFRExports;
import net.kodehawa.mantarobot.utils.exporters.MemoryUsageExports;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class Prometheus {
    public static final Duration UPDATE_PERIOD = Duration.ofSeconds(3);

    private static final AtomicReference<State> STATE = new AtomicReference<>(State.DISABLED);
    private static volatile HTTPServer server;

    public static State currentState() {
        return STATE.get();
    }

    public static void registerPostStartup() {
        DiscordLatencyExports.register();
        MemoryUsageExports.register();
    }

    public static void enable() throws IOException {
        if (STATE.compareAndSet(State.DISABLED, State.ENABLING)) {
            //replaced by jfr? needs testing, if yes then remove
            //used for cpu usage
            new StandardExports().register();
            //replaced by jfr? needs testing, if yes then remove
            //used for memory usage
            new MemoryPoolsExports().register();
            //ig we can keep this one for now
            new BufferPoolsExports().register();
            JFRExports.register();
            server = new HTTPServer(MantaroData.config().get().prometheusPort);
            STATE.set(State.ENABLED);
        }
    }

    public static void disable() {
        while (!STATE.compareAndSet(State.ENABLED, State.DISABLED)) {
            if (STATE.get() == State.DISABLED) {
                return;
            }

            Thread.yield();
        }
        server.stop();
    }

    public enum State {
        DISABLED, ENABLING, ENABLED
    }
}
