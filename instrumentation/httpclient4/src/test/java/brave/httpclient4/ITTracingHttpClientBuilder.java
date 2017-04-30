package brave.httpclient4;

import brave.http.ITHttpClient;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingHttpClientBuilder extends ITHttpClient<CloseableHttpClient> {

  @Override protected CloseableHttpClient newClient(int port) {
    return TracingHttpClientBuilder.create(httpTracing).disableAutomaticRetries().build();
  }

  @Override
  protected void closeClient(CloseableHttpClient client) throws IOException {
    client.close();
  }

  @Override protected void get(CloseableHttpClient client, String pathIncludingQuery)
      throws IOException {
    client.execute(new HttpGet(server.url(pathIncludingQuery).uri())).close();
  }

  @Override protected void getAsync(CloseableHttpClient client, String pathIncludingQuery) {
    throw new AssumptionViolatedException("This is not an async library");
  }

  @Test
  public void currentSpanVisibleToUserInterceptors() throws Exception {
    server.enqueue(new MockResponse());

    try (CloseableHttpClient client = TracingHttpClientBuilder.create(httpTracing)
        .disableAutomaticRetries()
        .addInterceptorFirst((HttpRequestInterceptor) (request, context) -> {
          request.addHeader("my-id",
              httpTracing.tracing().currentTraceContext().get().traceIdString());
        })
        .build()) {

      client.execute(new HttpGet(server.url("/foo").uri())).close();
    }

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(request.getHeader("my-id"));
  }

  @Override
  @Test(expected = AssertionError.class) // base url is not logged in apache
  public void httpUrlTagIncludesQueryParams() throws Exception {
    super.httpUrlTagIncludesQueryParams();
  }
}
