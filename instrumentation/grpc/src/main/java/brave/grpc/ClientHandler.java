package brave.grpc;

import brave.Span;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import zipkin.Constants;

// Not final so it can be overridden to customize tags
public class ClientHandler extends brave.ClientHandler<MethodDescriptor, Status> {
  @Override public String spanName(MethodDescriptor methodDescriptor) {
    return methodDescriptor.getFullMethodName();
  }

  @Override public void responseTags(Status status, Span span) {
    if (!status.getCode().equals(Status.Code.OK)) {
      span.tag(Constants.ERROR, String.valueOf(status.getCode()));
    }
  }
}
