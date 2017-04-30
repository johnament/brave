package brave.servlet;

import brave.http.HttpAdapter;
import brave.http.HttpServerHandler;
import brave.http.HttpServerParser;
import brave.internal.Nullable;
import java.nio.ByteBuffer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import zipkin.Endpoint;

import static brave.internal.InetAddresses.ipStringToBytes;

public final class HttpServletHandler // public for others like sparkjava to use
    extends HttpServerHandler<HttpServletRequest, HttpServletResponse> {

  public HttpServletHandler(HttpServerParser parser) {
    super(new HttpServletAdapter(), parser);
  }

  @Override @Nullable public Endpoint.Builder remoteEndpoint(HttpServletRequest request) {
    byte[] addressBytes = ipStringToBytes(request.getHeader("X-Forwarded-For"));
    if (addressBytes == null) addressBytes = ipStringToBytes(request.getRemoteAddr());
    if (addressBytes == null) return null;
    Endpoint.Builder builder = Endpoint.builder().serviceName("");
    if (addressBytes.length == 4) {
      builder.ipv4(ByteBuffer.wrap(addressBytes).getInt());
    } else if (addressBytes.length == 16) {
      builder.ipv6(addressBytes);
    }
    int port = request.getRemotePort();
    if (port != -1) builder.port(port);
    return builder;
  }

  static final class HttpServletAdapter
      extends HttpAdapter<HttpServletRequest, HttpServletResponse> {
    final ServletRuntime servlet = ServletRuntime.get();

    @Override public String method(HttpServletRequest request) {
      return request.getMethod();
    }

    @Override public String path(HttpServletRequest request) {
      return request.getRequestURI();
    }

    @Override public String url(HttpServletRequest request) {
      StringBuffer url = request.getRequestURL();
      if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
        url.append('?').append(request.getQueryString());
      }
      return url.toString();
    }

    @Override public String requestHeader(HttpServletRequest request, String name) {
      return request.getHeader(name);
    }

    @Override public Integer statusCode(HttpServletResponse response) {
      return servlet.status(response);
    }
  }
}
