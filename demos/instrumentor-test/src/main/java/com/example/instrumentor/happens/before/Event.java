package com.example.instrumentor.happens.before;

public class Event {

    private final String type;
    private final Object payload;
    private final long timestamp;

    public Event(String type, Object payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.nanoTime();
    }

    public String getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Event{type='" + type + "', ts=" + timestamp + "}";
    }
}