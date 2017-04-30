package brave;

import brave.internal.Nullable;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import zipkin.Constants;
import zipkin.Endpoint;

public class ClientHandler<Req, Resp> {

  public String spanName(Req req) {
    return "";
  }

  public void requestTags(Req req, Span span) {
    return;
  }

  public void responseTags(Resp res, Span span) {
    return;
  }

  public Req handleSend(Req request, Span span) {
    if (span.isNoop()) return request;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.CLIENT).name(spanName(request));
    requestTags(request, span);
    span.start();
    return request;
  }

  public static void remoteEndpoint(@Nullable InetAddress addr, int port, Span span) {
    if (span.isNoop() || addr == null) return;
    Endpoint.Builder builder = Endpoint.builder().serviceName("");
    byte[] addressBytes = addr.getAddress();
    if (addressBytes.length == 4) {
      builder.ipv4(ByteBuffer.wrap(addressBytes).getInt());
    } else if (addressBytes.length == 16) {
      builder.ipv6(addressBytes);
    }
    if (port != -1) builder.port(port);
    span.remoteEndpoint(builder.build());
  }

  public Resp handleReceive(Resp response, Span span) {
    if (span.isNoop()) return response;

    try {
      responseTags(response, span);
    } finally {
      span.finish();
    }
    return response;
  }

  public <T extends Throwable> T handleError(T throwable, Span span) {
    if (span.isNoop()) return throwable;

    try {
      String message = throwable.getMessage();
      if (message == null) message = throwable.getClass().getSimpleName();
      span.tag(Constants.ERROR, message);
      return throwable;
    } finally {
      span.finish();
    }
  }
}
