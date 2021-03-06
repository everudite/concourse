/*
 * Copyright (c) 2013-2015 Cinchapi, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cinchapi.concourse.server.http.routers;

import java.nio.ByteBuffer;

import org.cinchapi.concourse.Timestamp;
import org.cinchapi.concourse.server.ConcourseServer;
import org.cinchapi.concourse.server.GlobalState;
import org.cinchapi.concourse.server.http.Endpoint;
import org.cinchapi.concourse.server.http.HttpRequests;
import org.cinchapi.concourse.server.http.Router;
import org.cinchapi.concourse.thrift.AccessToken;
import org.cinchapi.concourse.util.ByteBuffers;
import org.cinchapi.concourse.util.DataServices;

import com.google.common.primitives.Longs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The core/default router.
 * 
 * @author Jeff Nelson
 */
public class IndexRouter extends Router {

    /**
     * Construct a new instance.
     * 
     * @param concourse
     */
    public IndexRouter(ConcourseServer concourse) {
        super(concourse);
    }

    @Override
    public void routes() {

        /*
         * ########################
         * #### AUTHENTICATION ####
         * ########################
         */

        /**
         * POST /login
         */
        post(new Endpoint("/login") {

            @Override
            protected JsonElement serve() throws Exception {
                JsonElement body = this.request.bodyAsJson();
                if(body.isJsonObject()) {
                    JsonObject creds = (JsonObject) body;
                    ByteBuffer username = ByteBuffers.fromString(creds.get(
                            "username").getAsString());
                    ByteBuffer password = ByteBuffers.fromString(creds.get(
                            "password").getAsString());
                    AccessToken access = concourse.login(username, password,
                            environment);
                    String token = HttpRequests.encodeAuthToken(access,
                            environment);
                    this.response.cookie("/",
                            GlobalState.HTTP_AUTH_TOKEN_COOKIE, token, 900,
                            false);
                    JsonObject response = new JsonObject();
                    response.add("token", new JsonPrimitive(token));
                    response.add("environment", new JsonPrimitive(environment));

                    return response;
                }
                else {
                    throw new IllegalArgumentException(
                            "Please specify username/password credentials "
                                    + "in a JSON object");
                }
            }

        });
        
        /**
         * POST /logout
         */
        post(new Endpoint("/logout"){

            @Override
            protected JsonElement serve() throws Exception {
                concourse.logout(creds, environment);
                response.removeCookie(GlobalState.HTTP_AUTH_TOKEN_COOKIE);
                return NO_DATA;
            }
            
        });

        /**
         * GET /
         */
        get(new Endpoint("/") {

            @Override
            protected JsonElement serve() throws Exception {
                Object data = concourse.find(creds, null, environment);
                return DataServices.gson().toJsonTree(data);
            }

        });

        /**
         * GET /record[?timestamp=<ts>]
         * GET /key[?timestamp=<ts>]
         */
        get(new Endpoint("/:arg1") {

            @Override
            protected JsonElement serve() throws Exception {
                // TODO what about transaction
                String arg1 = getParamValue(":arg1");
                String ts = getParamValue("timestamp");
                Long timestamp = ts == null ? null : Timestamp.parse(ts)
                        .getMicros();
                Long record = Longs.tryParse(arg1);
                Object data;
                if(record != null) {
                    data = timestamp == null ? concourse.selectRecord(record,
                            creds, null, environment) : concourse
                            .selectRecordTime(record, timestamp, creds,
                                    null, environment);
                }
                else {
                    data = timestamp == null ? concourse.browseKey(arg1,
                            creds, null, environment) : concourse
                            .browseKeyTime(arg1, timestamp, creds, null,
                                    environment);
                }
                return DataServices.gson().toJsonTree(data);
            }

        });

        /**
         * GET /key/record[?timestamp=<ts>]
         * GET /record/key[?timestamp=<ts>]
         */
        get(new Endpoint("/:arg1/:arg2") {

            @Override
            protected JsonElement serve() throws Exception {
                String arg1 = getParamValue(":arg1");
                String arg2 = getParamValue(":arg2");
                String ts = getParamValue("timestamp");
                Long timestamp = ts == null ? null : Timestamp.parse(ts)
                        .getMicros();
                Long record = Longs.tryParse(arg1);
                String key;
                Object data;
                if(record != null) {
                    key = arg2;
                }
                else {
                    key = arg1;
                    record = Long.parseLong(arg2);
                }
                if(timestamp == null) {
                    data = concourse.selectKeyRecord(key, record, creds,
                            null, environment);
                }
                else {
                    data = concourse.selectKeyRecordTime(key, record,
                            timestamp, creds, null, environment);
                }
                return DataServices.gson().toJsonTree(data);
            }

        });

    }
}
