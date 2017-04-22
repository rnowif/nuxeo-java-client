/*
 * (C) Copyright 2016-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Vladimir Pasquier <vpasquier@nuxeo.com>
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.client.marshaller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.client.ConstantsV1;
import org.nuxeo.client.MediaType;
import org.nuxeo.client.MediaTypes;
import org.nuxeo.client.Responses;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;
import org.nuxeo.client.objects.EntityTypes;
import org.nuxeo.client.objects.RecordSet;
import org.nuxeo.client.objects.blob.Blob;
import org.nuxeo.client.objects.blob.Blobs;
import org.nuxeo.client.objects.user.User;
import org.nuxeo.client.spi.NuxeoClientException;
import org.nuxeo.client.util.IOUtils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import okhttp3.ResponseBody;
import retrofit2.Converter;

/**
 * @since 0.1
 */
public final class NuxeoResponseConverterFactory<T> implements Converter<ResponseBody, T> {

    protected static final Map<String, Class<?>> entityTypeToClass = new HashMap<>();

    protected final JavaType javaType;

    protected final ObjectMapper objectMapper;

    protected NuxeoResponseConverterFactory(ObjectMapper objectMapper, JavaType javaType) {
        this.objectMapper = objectMapper;
        this.javaType = javaType;
        // register default entities
        entityTypeToClass.put(EntityTypes.DOCUMENT, Document.class);
        entityTypeToClass.put(EntityTypes.DOCUMENTS, Documents.class);
        entityTypeToClass.put(EntityTypes.RECORDSET, RecordSet.class);
        entityTypeToClass.put(EntityTypes.USER, User.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T convert(ResponseBody body) throws IOException {
        // Checking if multipart outputs.
        MediaType mediaType = MediaType.fromOkHttpMediaType(body.contentType());
        if (!MediaTypes.APPLICATION_JSON.equalsTypeSubType(mediaType)
                && !MediaTypes.APPLICATION_JSON_NXENTITY.equalsTypeSubType(mediaType)) {
            if (mediaType.type().equals(MediaTypes.MULTIPART_S)) {
                Blobs blobs = new Blobs();
                try (InputStream is = body.byteStream()) {
                    MimeMultipart mp = new MimeMultipart(new ByteArrayDataSource(is, mediaType.toString()));
                    int size = mp.getCount();
                    for (int i = 0; i < size; i++) {
                        BodyPart part = mp.getBodyPart(i);
                        // IOUtils.copyToTempFile close the input stream for us
                        blobs.add(part.getFileName(), IOUtils.copyToTempFile(part.getInputStream()));
                    }
                } catch (MessagingException reason) {
                    throw new IOException(reason);
                }
                return (T) blobs;
            } else {
                // IOUtils.copyToTempFile close the input stream for us
                File tmpFile = IOUtils.copyToTempFile(body.byteStream());
                return (T) new Blob(tmpFile);
            }
        }
        // Checking the type of the method clientside - aka object for Automation calls.
        if (javaType.getRawClass().equals(Object.class)) {
            String entityType = mediaType.nuxeoEntity();
            String bodyString = Responses.bodyToString(body);
            body.close();
            if (entityType == null) {
                // Handle the legacy case when no 'entity-type' header has been set in the response but
                // `entity-type` is written in the json payload
                entityType = bodyString.replaceFirst(".*\"" + ConstantsV1.ENTITY_TYPE + "\":\"([^\"]*)\".*", "$1");
                entityType = StringUtils.trimToNull(entityType);
            }
            Class<?> entityClass = entityTypeToClass.get(entityType);
            // If we can't find an appropriate class to map response just return the plain text
            if (entityClass == null) {
                return (T) bodyString;
            }
            return (T) readJSON(bodyString, entityClass);
        }
        // Delegate other cases to jackson
        try (Reader reader = body.charStream()) {
            ObjectReader objectReader = objectMapper.readerFor(javaType);
            return objectReader.readValue(reader);
        } catch (IOException reason) {
            throw new NuxeoClientException(reason);
        }
    }

    public <U> U readJSON(String value, Class<U> javaType) {
        try {
            return objectMapper.readValue(value, javaType);
        } catch (IOException reason) {
            throw new NuxeoClientException("Converter Read Issue.", reason);
        }
    }

    /**
     * Register an entity pojo to the automation unmarshalling mechanism.
     * 
     * @since 3.0
     */
    public static void registerEntity(String entityType, Class<?> clazz) {
        entityTypeToClass.put(entityType, clazz);
    }

}
