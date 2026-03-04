package com.chatcui.gateway.relay;

@FunctionalInterface
public interface RelayPublisher {
    PublishReceipt publishFirstHop(RelayEnvelope envelope);

    record PublishReceipt(boolean accepted, String dedupeKey, String messageId) {
        public static PublishReceipt accepted(String dedupeKey) {
            return new PublishReceipt(true, dedupeKey, null);
        }

        public static PublishReceipt accepted(String dedupeKey, String messageId) {
            return new PublishReceipt(true, dedupeKey, messageId);
        }

        public static PublishReceipt duplicate(String dedupeKey) {
            return new PublishReceipt(false, dedupeKey, null);
        }
    }
}
