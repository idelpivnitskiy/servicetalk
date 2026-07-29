/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.netty.BufferAllocators;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static io.servicetalk.http.api.HttpResponseStatus.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpResponseStatusTest {

    @Test
    void testGetResponseStatusReturnsConstant() {
        assertSame(OK, of(200, "OK"));
        assertSame(OK, of(200, ""));
    }

    @Test
    void testGetResponseStatusReturnsNewInstance() {
        final HttpResponseStatus newStatusObject =
                HttpResponseStatus.of(200, "YES");
        assertNotSame(OK, newStatusObject);
        assertEquals(OK, newStatusObject);    // reasonPhrase is ignored according to RFC
    }

    @Test
    void testConstant() {
        final HttpResponseStatus status = NO_CONTENT;

        assertEquals(204, status.code());
        assertEquals(SUCCESSFUL_2XX, status.statusClass());
        assertEquals("204 No Content", status.toString());
        assertWriteToBuffer("204 No Content", status);
    }

    @Test
    void testNewObject() {
        final HttpResponseStatus status = HttpResponseStatus.of(590, "My Own Status Code");

        assertEquals(590, status.code());
        assertEquals(SERVER_ERROR_5XX, status.statusClass());
        assertEquals("590 My Own Status Code", status.toString());
        assertWriteToBuffer("590 My Own Status Code", status);
    }

    private static void assertWriteToBuffer(final String expected, final HttpResponseStatus status) {
        final Buffer buffer = BufferAllocators.DEFAULT_ALLOCATOR.newBuffer();
        status.writeTo(buffer);
        assertEquals(DEFAULT_RO_ALLOCATOR.fromAscii(expected), buffer);
    }

    @Test
    void test2DigitStatusCodeIsNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> HttpResponseStatus.of(99, "My Own Status Code"));
    }

    @Test
    void test4DigitStatusCodeIsNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> HttpResponseStatus.of(1000, "My Own Status Code"));
    }

    @Test
    void testCharSequenceStatusCode() {
        assertSame(OK, of("200"));
        assertEquals(590, of("590").code());
        assertThrows(IllegalArgumentException.class, () -> of("99"));
        assertThrows(IllegalArgumentException.class, () -> of("1000"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] statusCode={0}")
    @ValueSource(strings = {"4294967496", "4294967796", "8589934792", "-4294967096"})
    void testStatusCodeOutsideIntRangeIsNotAllowed(String statusCode) {
        // Each of these narrows to a valid-looking 3-digit code (200, 500, 200, 200), which would otherwise slip
        // past the [100-999] validation.
        assertThrows(IllegalArgumentException.class, () -> of(statusCode));
    }
}
