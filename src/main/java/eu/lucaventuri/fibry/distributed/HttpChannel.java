package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.Exceptions;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HttpChannel<T, R> implements RemoteActorChannel<T, R> {
    private final String url;
    private final Executor executor;
    private final boolean encodeBase64;
    private final HttpMethod method;
    private final Consumer<HttpRequest> requestCustomizer;
    private final HttpClient client = HttpClient.newBuilder().build();

    public enum HttpMethod {
        GET {
            @Override
            HttpRequest getRequest(String url, String remoteActorNameEncoded, String objectTypeEncoded, String messageEncoded) {
                return HttpRequest.newBuilder().GET().uri(URI.create(url + "?actorName=" + remoteActorNameEncoded + "&type=" + objectTypeEncoded + "&message=" + messageEncoded)).build();
            }
        },
        PUT {
            @Override
            HttpRequest getRequest(String url, String remoteActorNameEncoded, String objectTypeEncoded, String messageEncoded) {
                return HttpRequest.newBuilder().PUT(dataAsPublisher(remoteActorNameEncoded, objectTypeEncoded, messageEncoded)).uri(URI.create(url)).build();
            }
        },
        POST {
            @Override
            HttpRequest getRequest(String url, String remoteActorNameEncoded, String objectTypeEncoded, String messageEncoded) {
                return HttpRequest.newBuilder().POST(dataAsPublisher(remoteActorNameEncoded, objectTypeEncoded, messageEncoded)).uri(URI.create(url)).build();
            }
        };

        private static HttpRequest.BodyPublisher dataAsPublisher(String remoteActorNameEncoded, String objectTypeEncoded, String messageEncoded) {
            var builder = new StringBuilder();

            builder.append("actorName=");
            builder.append(remoteActorNameEncoded);
            builder.append("&type=");
            builder.append(objectTypeEncoded);
            builder.append("&message=");
            builder.append(messageEncoded);

            return HttpRequest.BodyPublishers.ofString(builder.toString());
        }

        abstract HttpRequest getRequest(String url, String actorName, String objectType, String messageEncoded);
    }

    public HttpChannel(String url, HttpMethod method, Executor executor, Consumer<HttpRequest> requestCustomizer, boolean encodeBase64) {
        this.url = url;
        this.method = method;
        this.executor = executor;
        this.encodeBase64 = encodeBase64;
        this.requestCustomizer = requestCustomizer;
    }

    @Override
    public CompletableFuture<R> sendMessageReturn(String remoteActorName, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, T message) {
        Supplier<R> supplier = () -> {
            try {
                String messageEncoded = encodeBase64 ? Base64.getEncoder().encodeToString(ser.serialize(message)) : encodeValue(ser.serializeToString(message));
                var request = method.getRequest(url, encodeValue(remoteActorName), encodeValue(message.getClass().getName()), messageEncoded);

                if (requestCustomizer != null)
                    requestCustomizer.accept(request);

                var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (encodeBase64)
                    return deser.deserialize(Base64.getDecoder().decode(response.body()));
                else
                    return deser.deserializeString(response.body());
            } catch (Throwable t) {
                throw new CompletionException(t);
            }
        };

        if (executor != null)
            return CompletableFuture.supplyAsync(supplier, executor);
        else
            return CompletableFuture.supplyAsync(supplier);
    }

    private static String encodeValue(String value) {
        return Exceptions.silence(() -> URLEncoder.encode(value, StandardCharsets.UTF_8.toString()), "");
    }
}
