package brave.http;

import brave.ClientHandler;
import brave.Span;

public class HttpClientHandler<Req, Resp> extends ClientHandler<Req, Resp> {
  final HttpAdapter<Req, Resp> adapter;
  final HttpClientParser parser;

  public HttpClientHandler(HttpAdapter<Req, Resp> adapter, HttpClientParser parser) {
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
}
