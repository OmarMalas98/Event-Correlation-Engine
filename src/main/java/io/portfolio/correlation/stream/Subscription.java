package io.portfolio.correlation.stream;

/**
 * A live subscription. Closing it stops delivery and releases the underlying consumer.
 */
public interface Subscription extends AutoCloseable {

    String topic();

    @Override
    void close();
}
