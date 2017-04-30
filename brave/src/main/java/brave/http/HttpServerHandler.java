package brave.http;

import brave.ServerHandler;
import brave.Span;
import brave.internal.Nullable;
import java.nio.ByteBuffer;
import zipkin.Endpoint;

import static brave.internal.InetAddresses.ipStringToBytes;

public class HttpServerHandler<Req, Resp> extends ServerHandler<Req, Resp> {
  final HttpAdapter<Req, Resp> adapter;
  final HttpServerParser parser;

  public HttpServerHandler(HttpAdapter<Req, Resp> adapter, HttpServerParser parser) {
    this.adapter = adapter;
    this.parser = parser;
  }

  @Override public String spanName(Req req) {
    return parser.spanName(adapter, req);
  }

  @Override public void requestTags(Req req, Span span) {
    parser.requestTags(adapter, req, span);
  }

  @Override public void responseTags(Resp res, Span span) {
    parser.responseTags(adapter, res, span);
  }

  @Nullable public Endpoint.Builder remoteEndpoint(Req req) {
    byte[] addressBytes = ipStringToBytes(adapter.requestHeader(req, "X-Forwarded-For"));
    if (addressBytes == null) return null;
    Endpoint.Builder builder = Endpoint.builder().serviceName("");
    if (addressBytes.length == 4) {
      builder.ipv4(ByteBuffer.wrap(addressBytes).getInt());
    } else if (addressBytes.length == 16) {
      builder.ipv6(addressBytes);
    }
    return builder;
  }
}
