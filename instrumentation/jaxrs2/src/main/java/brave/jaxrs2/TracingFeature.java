package brave.jaxrs2;

import brave.http.HttpTracing;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

@Provider
public final class TracingFeature implements Feature {

  public static TracingFeature create(HttpTracing httpTracing) {
    return new TracingFeature(httpTracing);
  }

  final HttpTracing httpTracing;

  TracingFeature(HttpTracing httpTracing) { // intentionally hidden
    this.httpTracing = httpTracing;
  }

  @Override
  public boolean configure(FeatureContext context) {
    context.register(TracingClientFilter.create(httpTracing));
    context.register(TracingContainerFilter.create(httpTracing));
    return true;
  }
}
