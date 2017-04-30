package brave;

import brave.internal.Nullable;
import zipkin.Constants;
import zipkin.Endpoint;

public abstract class ServerHandler<Req, Resp> {

  public String spanName(Req req) {
    return "";
  }

  public void requestTags(Req req, Span span) {
    return;
  }

  @Nullable public Endpoint.Builder remoteEndpoint(Req req) {
    return null;
  }

  public void responseTags(Resp res, Span span) {
    return;
  }

  public Req handleReceive(Req request, Span span) {
    if (span.isNoop()) return request;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.SERVER).name(spanName(request));
    requestTags(request, span);
    Endpoint.Builder remoteEndpoint = remoteEndpoint(request);
    if (remoteEndpoint != null) span.remoteEndpoint(remoteEndpoint.build());
    span.start();
    return request;
  }

  public Resp handleSend(Resp response, Span span) {
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
