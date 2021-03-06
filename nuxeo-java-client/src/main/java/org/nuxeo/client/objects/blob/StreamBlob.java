/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.client.objects.blob;

import java.io.IOException;
import java.io.InputStream;

import org.nuxeo.client.MediaTypes;

/**
 * @since 3.0
 */
public class StreamBlob extends AbstractBlob {

    private final InputStream inputStream;

    public StreamBlob(InputStream inputStream, String filename) {
        this(inputStream, filename, MediaTypes.APPLICATION_OCTET_STREAM_S);
    }

    public StreamBlob(InputStream inputStream, String filename, String mimeType) {
        super(filename, mimeType);
        this.inputStream = inputStream;
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public InputStream getStream() throws IOException {
        return inputStream;
    }

}
