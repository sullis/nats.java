// Copyright 2015-2018 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.client.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.CharBuffer;

import org.junit.Test;

import io.nats.client.Nats;
import io.nats.client.NatsTestServer;

public class ParseTests {
    @Test
    public void testGoodNumbers() {
        int i=1;

        while (i < 2_000_000_000 && i > 0) {
            assertEquals(i, NatsConnectionReader.parseLength(String.valueOf(i)));
            i *= 11;
        }

        assertEquals(0, NatsConnectionReader.parseLength("0"));

    }

    @Test(expected=NumberFormatException.class)
    public void testBadChars() {
        NatsConnectionReader.parseLength("2221a");
        assertFalse(true);
    }

    @Test(expected=NumberFormatException.class)
    public void testTooBig() {
        NatsConnectionReader.parseLength(String.valueOf(100_000_000_000L));
        assertFalse(true);
    }


    public String grabProtocol(NatsConnectionReader reader, CharSequence buffer) {
        int remaining = buffer.length();

        if (remaining == 0) {
            return null;
        }

        int index = 0;
        char[] opArray = new char[NatsConnectionReader.MAX_PROTOCOL_OP_LENGTH];

        while (remaining > 0) {
            char c = buffer.charAt(index);

            if (c == ' ') {
                return reader.opFor(opArray, index);
            } else {
                if (index >= NatsConnectionReader.MAX_PROTOCOL_OP_LENGTH) {
                    return NatsConnectionReader.UNKNOWN_OP;
                }
                opArray[index] = c;
                index++;
            }
            remaining--;
        }

        return reader.opFor(opArray, index);
    }

    @Test
    public void testProtocolStrings() throws Exception {
        String[] serverStrings = {
            "+OK", "PONG", "PING", "MSG longer.subject.abitlikeaninbox 22 longer.replyto.abitlikeaninbox 234",
            "-ERR some error with spaces in it", "INFO {" + "\"server_id\":\"myserver\"" + "," + "\"version\":\"1.1.1\"" + ","
            + "\"go\": \"go1.9\"" + "," + "\"host\": \"host\"" + "," + "\"tls_required\": true" + ","
            + "\"auth_required\":false" + "," + "\"port\": 7777" + "," + "\"max_payload\":100000000000" + ","
            + "\"connect_urls\":[\"one\", \"two\"]" + "}", "ping", "msg one 22 33", "+oK", "PoNg", "pong", "MsG one 22 23"
        };

        String[] badStrings = {
            "THISISTOOLONG the rest doesn't matter", "XXX", "XXXX", "XX", "X", "PINX", "PONX", "MSX", "INFX", "+OX", "-ERX",
            "thisistoolong the rest doesn't matter", "xxx", "xxxx", "xx", "x", "pinx", "ponx", "msx", "infx", "+ox", "-erx"
        };

        String[] expected = {
            NatsConnection.OP_OK, NatsConnection.OP_PONG, NatsConnection.OP_PING, NatsConnection.OP_MSG,
            NatsConnection.OP_ERR, NatsConnection.OP_INFO, NatsConnection.OP_PING, NatsConnection.OP_MSG,
            NatsConnection.OP_OK, NatsConnection.OP_PONG, NatsConnection.OP_PONG, NatsConnection.OP_MSG
        };

        try (NatsTestServer ts = new NatsTestServer(false);
                NatsConnection nc = (NatsConnection) Nats.connect(ts.getURI())) {
            NatsConnectionReader reader = nc.getReader();

            for (int i=0; i<serverStrings.length; i++) {
                assertEquals(serverStrings[i], expected[i], grabProtocol(reader, CharBuffer.wrap(serverStrings[i])));
            }

            for (int i=0; i<badStrings.length; i++) {
                assertEquals(badStrings[i], "UNKNOWN", grabProtocol(reader, CharBuffer.wrap(badStrings[i])));
            }
        }
    }
}