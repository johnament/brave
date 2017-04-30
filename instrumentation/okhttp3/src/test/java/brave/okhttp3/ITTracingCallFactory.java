package brave.okhttp3;

import brave.http.ITHttpClient;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingCallFactory extends ITHttpClient<TracingCallFactory> {

  @Override protected TracingCallFactory newClient(int port) {
    return TracingCallFactory.create(httpTracing, new OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    );
  }

  @Override protected void closeClient(TracingCallFactory client) throws IOException {
    client.ok.dispatcher().executorService().shutdownNow();
  }

  @Override protected void get(TracingCallFactory client, String pathIncludingQuery)
      throws IOException {
    client.newCall(new Request.Builder().url(server.url(pathIncludingQuery)).build())
        .execute();
  }

  @Override protected void getAsync(TracingCallFactory client, String pathIncludingQuery)
      throws Exception {
    client.newCall(new Request.Builder().url(server.url(pathIncludingQuery)).build())
        .enqueue(new Callback() {
          @Override public void onFailure(Call call, IOException e) {
            e.printStackTrace();
          }

          @Override public void onResponse(Call call, Response response) throws IOException {
          }
        });
  }

  @Test public void currentSpanVisibleToUserInterceptors() throws Exception {
    server.enqueue(new MockResponse());
    closeClient(client);

    client = TracingCallFactory.create(httpTracing, new OkHttpClient.Builder()
        .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
            .addHeader("my-id", httpTracing.tracing().currentTraceContext().get().traceIdString())
            .build()))
        .build());

    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(request.getHeader("my-id"));
  }
}
