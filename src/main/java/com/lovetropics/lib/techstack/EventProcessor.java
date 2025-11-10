package com.lovetropics.lib.techstack;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/* package-private */ class EventProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<EventKey, EventSubscription<?>> subscriptions;

    private EventProcessor(Map<EventKey, EventSubscription<?>> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public Set<String> subscriptionKeys() {
        return subscriptions.keySet().stream().map(EventKey::asSubscriptionKey).collect(Collectors.toSet());
    }

    public void parseAndHandle(JsonElement eventJson) throws JsonSyntaxException {
        JsonObject eventObject = GsonHelper.convertToJsonObject(eventJson, "event");
        EventKey eventKey = EventKey.MAP_CODEC.codec().parse(JsonOps.INSTANCE, eventObject).getOrThrow(JsonSyntaxException::new);
        EventSubscription<?> subscription = subscriptions.get(eventKey);
        JsonObject payload = GsonHelper.getAsJsonObject(eventObject, "payload");
        if (subscription == null) {
            LOGGER.error("Received event that has no subscription: {}", eventJson);
            return;
        }
        subscription.parseAndHandle(payload);
    }

    /* package-private */ static class Builder {
        private final ImmutableMap.Builder<EventKey, EventSubscription<?>> subscriptions = ImmutableMap.builder();

        public <T> Builder subscribe(Crud crud, String id, Codec<T> codec, Consumer<T> handler) {
            subscriptions.put(new EventKey(crud, id), new EventSubscription<>(codec, handler));
            return this;
        }

        public EventProcessor build() {
            Map<EventKey, EventSubscription<?>> subscriptions = this.subscriptions.buildOrThrow();
            if (subscriptions.isEmpty()) {
                throw new IllegalStateException("Must have at least one event subscription");
            }
            return new EventProcessor(subscriptions);
        }
    }

    private record EventKey(Crud crud, String id) {
        public static final MapCodec<EventKey> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Crud.CODEC.fieldOf("crud").forGetter(EventKey::crud),
                Codec.STRING.fieldOf("type").forGetter(EventKey::id)
        ).apply(i, EventKey::new));

        public String asSubscriptionKey() {
            return crud.getSerializedName() + "_" + id;
        }
    }

    private record EventSubscription<T>(Codec<T> codec, Consumer<T> handler) {
        public void parseAndHandle(JsonElement payload) throws JsonSyntaxException {
            T value = codec.parse(JsonOps.INSTANCE, payload).getOrThrow(JsonSyntaxException::new);
            try {
                handler.accept(value);
            } catch (Exception e) {
                LOGGER.error("An unexpected exception occurred while handling event: {}", value);
            }
        }
    }
}
