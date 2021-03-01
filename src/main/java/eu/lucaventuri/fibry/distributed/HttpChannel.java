package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.Exceptions;

import java.io.IOException;
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
        GET(true) {
            @Override
            HttpRequest getRequest(String url, String remoteActorNameEncoded, String objectTypeEncoded, String messageEncoded, boolean waitResult) {
                return HttpRequest.newBuilder().GET().uri(URI.create(url + "?actorName=" + remoteActorNameEncoded + "&type=" + objectTypeEncoded + "&waitResult=" + waitResult + "&message=" + messageEncoded)).build();
            }
        },
        PUT(false) {
            @Override
            HttpRequest getRequest(String url, String remoteActorNameEncoded, String objectTypeEncoded, String message, boolean waitResult) {
                return HttpRequest.newBuilder().PUT(HttpRequest.BodyPublishers.ofString(message)).uri(URI.create(url + "?actorName=" + remoteActorNameEncoded + "&type=" + objectTypeEncoded + "&waitResult=" + waitResult)).build();
            }
        },
        POST(false) {
            @Override
            HttpRequest getRequest(String url, String remoteActorNameEncoded, String objectTypeEncoded, String message, boolean waitResult) {
                return HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(message)).uri(URI.create(url + "?actorName=" + remoteActorNameEncoded + "&type=" + objectTypeEncoded + "&waitResult=" + waitResult)).build();
            }
        };

        private final boolean encodeMessage;

        HttpMethod(boolean encodeMessage) {
            this.encodeMessage = encodeMessage;
        }

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

        abstract HttpRequest getRequest(String url, String actorName, String objectType, String messageEncoded, boolean waitResult);
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
                final String messageToSend = prepareMessageToSend(ser, message);

                var request = method.getRequest(url, encodeValue(remoteActorName), encodeValue(message.getClass().getName()), messageToSend, true);

                return sendRequest(deser, request);
            } catch (Throwable t) {
                throw new CompletionException(t);
            }
        };

        if (executor != null)
            return CompletableFuture.supplyAsync(supplier, executor);
        else
            return CompletableFuture.supplyAsync(supplier);
    }

    @Override
    public void sendMessage(String remoteActorName, ChannelSerializer<T> ser, T message) throws IOException {
        try {
            final String messageToSend = prepareMessageToSend(ser, message);

            var request = method.getRequest(url, encodeValue(remoteActorName), encodeValue(message.getClass().getName()), messageToSend, false);

            sendRequest(null, request);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private R sendRequest(ChannelDeserializer<R> deser, HttpRequest request) throws java.io.IOException, InterruptedException {
        if (requestCustomizer != null)
            requestCustomizer.accept(request);

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (deser == null)
            return null;

        if (encodeBase64)
            return deser.deserialize(Base64.getDecoder().decode(response.body()));
        else
            return deser.deserializeString(response.body());
    }

    private String prepareMessageToSend(ChannelSerializer<T> ser, T message) {
        final String messageToSend;

        if (method.encodeMessage)
            messageToSend = encodeBase64 ? Base64.getEncoder().encodeToString(ser.serialize(message)) : encodeValue(ser.serializeToString(message));
        else
            messageToSend = ser.serializeToString(message);
        return messageToSend;
    }

    private static String encodeValue(String value) {
        return Exceptions.silence(() -> URLEncoder.encode(value, StandardCharsets.UTF_8.toString()), "");
    }
}
