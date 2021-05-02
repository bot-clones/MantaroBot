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

package net.kodehawa.mantarobot.utils.exporters;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public class Metrics {
    public static final ThreadPoolCollector THREAD_POOL_COLLECTOR = new ThreadPoolCollector().register();
    public static final Counter TRACK_EVENTS = Counter.build()
            .name("track_event")
            .help("Music Track Events (failed/loaded/searched)")
            .labelNames("type")
            .register();
    public static final Counter BIRTHDAY_COUNTER = Counter.build()
            .name("birthdays_logged")
            .help("Logged birthdays")
            .register();
    public static final Histogram COMMAND_LATENCY = Histogram.build()
            .name("command_latency")
            .help("Time it takes for a command to process.")
            .register();
    public static final Counter COMMAND_COUNTER = Counter.build()
            .name("commands")
            .help("Amounts of commands ran by name")
            .labelNames("name")
            .register();
    public static final Counter CATEGORY_COUNTER = Counter.build()
            .name("categories")
            .help("Amounts of categories ran by name")
            .labelNames("name")
            .register();
    public static final Gauge GUILD_COUNT = Gauge.build()
            .name("guilds")
            .help("Guild Count")
            .register();
    public static final Gauge USER_COUNT = Gauge.build()
            .name("users")
            .help("User Count")
            .register();
    public static final Counter HTTP_REQUESTS = Counter.build()
            .name("http_requests")
            .help("Successful HTTP Requests (JDA)")
            .register();
    public static final Counter HTTP_429_REQUESTS = Counter.build()
            .name("http_ratelimit_requests")
            .help("429 HTTP Requests (JDA)")
            .register();
    public static final Counter RECEIVED_MESSAGES = Counter.build()
            .name("messages_received")
            .help("Received messages (all users + bots)")
            .register();
    public static final Counter ACTIONS = Counter.build()
            .name("actions")
            .help("Mantaro Actions")
            .labelNames("type")
            .register();
    public static final Counter SHARD_EVENTS = Counter.build()
            .name("shard_events")
            .help("Shard Events")
            .labelNames("type")
            .register();
    public static final Counter GUILD_ACTIONS = Counter.build()
            .name("guild_actions")
            .help("Guild Options")
            .labelNames("type")
            .register();
    public static final Counter PATRON_COUNTER = Counter.build()
            .name("patrons")
            .help("New patrons")
            .register();
}
