/**
 * Copyright 2015-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.internal.v2.codec;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import zipkin.internal.v2.Annotation;
import zipkin.internal.v2.Endpoint;
import zipkin.internal.v2.Span;
import zipkin.internal.v2.internal.Buffer;
import zipkin.internal.v2.internal.JsonCodec;

import static zipkin.internal.v2.internal.Buffer.asciiSizeInBytes;
import static zipkin.internal.v2.internal.JsonEscaper.jsonEscape;
import static zipkin.internal.v2.internal.JsonEscaper.jsonEscapedSizeInBytes;

/** Limited interface needed by those writing span reporters */
public enum SpanBytesEncoder implements BytesEncoder<Span> {
  /** Corresponds to the Zipkin v2 json format */
  JSON {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public int sizeInBytes(Span input) {
      return SPAN_WRITER.sizeInBytes(input);
    }

    @Override public byte[] encode(Span span) {
      return JsonCodec.write(SPAN_WRITER, span);
    }

    @Override public byte[] encodeList(List<Span> spans) {
      return JsonCodec.writeList(SPAN_WRITER, spans);
    }
  };

  static final Buffer.Writer<Endpoint> ENDPOINT_WRITER = new Buffer.Writer<Endpoint>() {
    @Override public int sizeInBytes(Endpoint value) {
      int sizeInBytes = 1; // {
      if (value.serviceName() != null) {
        sizeInBytes += 16; // "serviceName":""
        sizeInBytes += jsonEscapedSizeInBytes(value.serviceName());
      }
      if (value.ipv4() != null) {
        if (sizeInBytes != 1) sizeInBytes++; // ,
        sizeInBytes += 9; // "ipv4":""
        sizeInBytes += value.ipv4().length();
      }
      if (value.ipv6() != null) {
        if (sizeInBytes != 1) sizeInBytes++; // ,
        sizeInBytes += 9; // "ipv6":""
        sizeInBytes += value.ipv6().length();
      }
      if (value.port() != null) {
        if (sizeInBytes != 1) sizeInBytes++; // ,
        sizeInBytes += 7; // "port":
        sizeInBytes += asciiSizeInBytes(value.port());
      }
      return ++sizeInBytes; // }
    }

    @Override public void write(Endpoint value, Buffer b) {
      b.writeByte('{');
      boolean wroteField = false;
      if (value.serviceName() != null) {
        b.writeAscii("\"serviceName\":\"");
        b.writeUtf8(jsonEscape(value.serviceName())).writeByte('"');
        wroteField = true;
      }
      if (value.ipv4() != null) {
        if (wroteField) b.writeByte(',');
        b.writeAscii("\"ipv4\":\"");
        b.writeAscii(value.ipv4()).writeByte('"');
        wroteField = true;
      }
      if (value.ipv6() != null) {
        if (wroteField) b.writeByte(',');
        b.writeAscii("\"ipv6\":\"");
        b.writeAscii(value.ipv6()).writeByte('"');
        wroteField = true;
      }
      if (value.port() != null) {
        if (wroteField) b.writeByte(',');
        b.writeAscii("\"port\":").writeAscii(value.port());
      }
      b.writeByte('}');
    }
  };

  static final Buffer.Writer<Annotation> ANNOTATION_WRITER = new Buffer.Writer<Annotation>() {
    @Override public int sizeInBytes(Annotation value) {
      int sizeInBytes = 25; // {"timestamp":,"value":""}
      sizeInBytes += asciiSizeInBytes(value.timestamp());
      sizeInBytes += jsonEscapedSizeInBytes(value.value());
      return sizeInBytes;
    }

    @Override public void write(Annotation value, Buffer b) {
      b.writeAscii("{\"timestamp\":").writeAscii(value.timestamp());
      b.writeAscii(",\"value\":\"").writeUtf8(jsonEscape(value.value())).writeAscii("\"}");
    }
  };

  static final Buffer.Writer<Span> SPAN_WRITER = new Buffer.Writer<Span>() {
    @Override public int sizeInBytes(Span value) {
      int sizeInBytes = 13; // {"traceId":""
      sizeInBytes += value.traceId().length();
      if (value.parentId() != null) {
        sizeInBytes += 30; // ,"parentId":"0123456789abcdef"
      }
      sizeInBytes += 24; // ,"id":"0123456789abcdef"
      if (value.kind() != null) {
        sizeInBytes += 10; // ,"kind":""
        sizeInBytes += value.kind().name().length();
      }
      if (value.name() != null) {
        sizeInBytes += 10; // ,"name":""
        sizeInBytes += jsonEscapedSizeInBytes(value.name());
      }
      if (value.timestamp() != null) {
        sizeInBytes += 13; // ,"timestamp":
        sizeInBytes += asciiSizeInBytes(value.timestamp());
      }
      if (value.duration() != null) {
        sizeInBytes += 12; // ,"duration":
        sizeInBytes += asciiSizeInBytes(value.duration());
      }
      if (value.localEndpoint() != null) {
        sizeInBytes += 17; // ,"localEndpoint":
        sizeInBytes += ENDPOINT_WRITER.sizeInBytes(value.localEndpoint());
      }
      if (value.remoteEndpoint() != null) {
        sizeInBytes += 18; // ,"remoteEndpoint":
        sizeInBytes += ENDPOINT_WRITER.sizeInBytes(value.remoteEndpoint());
      }
      if (!value.annotations().isEmpty()) {
        sizeInBytes += 17; // ,"annotations":[]
        int length = value.annotations().size();
        if (length > 1) sizeInBytes += length - 1; // comma to join elements
        for (int i = 0; i < length; i++) {
          sizeInBytes += ANNOTATION_WRITER.sizeInBytes(value.annotations().get(i));
        }
      }
      if (!value.tags().isEmpty()) {
        sizeInBytes += 10; // ,"tags":{}
        int tagCount = value.tags().size();
        if (tagCount > 1) sizeInBytes += tagCount - 1; // comma to join elements
        for (Map.Entry<String, String> entry : value.tags().entrySet()) {
          sizeInBytes += 5; // "":""
          sizeInBytes += jsonEscapedSizeInBytes(entry.getKey());
          sizeInBytes += jsonEscapedSizeInBytes(entry.getValue());
        }
      }
      if (Boolean.TRUE.equals(value.debug())) {
        sizeInBytes += 13; // ,"debug":true
      }
      if (Boolean.TRUE.equals(value.shared())) {
        sizeInBytes += 14; // ,"shared":true
      }
      return ++sizeInBytes; // }
    }

    @Override public void write(Span value, Buffer b) {
      b.writeAscii("{\"traceId\":\"").writeAscii(value.traceId()).writeByte('"');
      if (value.parentId() != null) {
        b.writeAscii(",\"parentId\":\"").writeAscii(value.parentId()).writeByte('"');
      }
      b.writeAscii(",\"id\":\"").writeAscii(value.id()).writeByte('"');
      if (value.kind() != null) {
        b.writeAscii(",\"kind\":\"").writeAscii(value.kind().toString()).writeByte('"');
      }
      if (value.name() != null) {
        b.writeAscii(",\"name\":\"").writeUtf8(jsonEscape(value.name())).writeByte('"');
      }
      if (value.timestamp() != null) {
        b.writeAscii(",\"timestamp\":").writeAscii(value.timestamp());
      }
      if (value.duration() != null) {
        b.writeAscii(",\"duration\":").writeAscii(value.duration());
      }
      if (value.localEndpoint() != null) {
        b.writeAscii(",\"localEndpoint\":");
        ENDPOINT_WRITER.write(value.localEndpoint(), b);
      }
      if (value.remoteEndpoint() != null) {
        b.writeAscii(",\"remoteEndpoint\":");
        ENDPOINT_WRITER.write(value.remoteEndpoint(), b);
      }
      if (!value.annotations().isEmpty()) {
        b.writeAscii(",\"annotations\":");
        b.writeByte('[');
        for (int i = 0, length = value.annotations().size(); i < length; ) {
          ANNOTATION_WRITER.write(value.annotations().get(i++), b);
          if (i < length) b.writeByte(',');
        }
        b.writeByte(']');
      }
      if (!value.tags().isEmpty()) {
        b.writeAscii(",\"tags\":{");
        Iterator<Map.Entry<String, String>> i = value.tags().entrySet().iterator();
        while (i.hasNext()) {
          Map.Entry<String, String> entry = i.next();
          b.writeByte('"').writeUtf8(jsonEscape(entry.getKey())).writeAscii("\":\"");
          b.writeUtf8(jsonEscape(entry.getValue())).writeByte('"');
          if (i.hasNext()) b.writeByte(',');
        }
        b.writeByte('}');
      }
      if (Boolean.TRUE.equals(value.debug())) {
        b.writeAscii(",\"debug\":true");
      }
      if (Boolean.TRUE.equals(value.shared())) {
        b.writeAscii(",\"shared\":true");
      }
      b.writeByte('}');
    }

    @Override public String toString() {
      return "Span";
    }
  };
}
