package dev.chipsxo.chipscritters;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
/** Bounded body subscriber retains the request's overall timeout through body completion. */
public final class LimitedRouteBody implements HttpResponse.BodySubscriber<byte[]> {
 private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
 private final int limit;private long count;private Flow.Subscription subscription;private boolean failed;
 public LimitedRouteBody(int limit){this.limit=limit;}
 public CompletionStage<byte[]> getBody(){return delegate.getBody();}
 public void onSubscribe(Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}
 public void onNext(List<ByteBuffer> values){if(failed)return;for(var value:values){count+=value.remaining();if(count>limit){failed=true;subscription.cancel();delegate.onError(new java.io.IOException("route package exceeds size limit"));return;}}delegate.onNext(values);}
 public void onError(Throwable error){delegate.onError(error);}
 public void onComplete(){if(!failed)delegate.onComplete();}
}
