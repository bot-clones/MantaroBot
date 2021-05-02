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

package net.kodehawa.mantarobot.commands.action;

import net.kodehawa.mantarobot.MantaroInfo;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.utils.Utils;
import okhttp3.Request;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class WeebAPIRequester {
    private static final Logger log = LoggerFactory.getLogger(WeebAPIRequester.class);
    private static final String ALL_TAGS = "/tags";
    private static final String ALL_TYPES = "/types";
    private static final String API_BASE_URL = "https://api.weeb.sh/images";
    private static final String AUTH_HEADER = "Bearer " + MantaroData.config().get().weebapiKey;
    private static final String RANDOM_IMAGE = "/random";

    public Pair<String, String> getRandomImageByType(String type, boolean nsfw, String filetype) {
        HashMap<String, Object> queryParams = new HashMap<>();
        queryParams.put("type", type);

        if (nsfw) {
            queryParams.put("nsfw", "only");
        }
        else {
            queryParams.put("nsfw", false);
        }

        if (filetype != null) {
            queryParams.put("filetype", filetype);
        }

        var req = request(RANDOM_IMAGE, Utils.urlEncodeUTF8(queryParams));
        if (req == null) {
            return Pair.of(null, null);
        }

        var object = new JSONObject(req);
        return Pair.of(object.getString("url"), object.getString("id"));
    }

    public JSONObject getTypes() {
        var req = request(ALL_TYPES, null);
        if (req == null) {
            return null;
        }

        return new JSONObject(req);
    }

    public JSONObject getTags() {
        var req = request(ALL_TAGS, null);
        if (req == null) {
            return null;
        }

        return new JSONObject(req);
    }

    private String request(String endpoint, String e) {
        try {
            var builder = new StringBuilder(endpoint);
            if (e != null) {
                builder.append("?");
                builder.append(e);
            }

            var r = new Request.Builder()
                    .url(API_BASE_URL + builder)
                    .addHeader("User-Agent", MantaroInfo.USER_AGENT)
                    .addHeader("Authorization", AUTH_HEADER)
                    .build();

            try(var response = Utils.httpClient.newCall(r).execute()) {
                var body = response.body();
                if (body == null) {
                    throw new IllegalStateException("body == null");
                }

                return body.string();
            }
        } catch (Exception ex) {
            log.error("Error getting image from weeb.sh", ex);
            return null;
        }
    }
}
