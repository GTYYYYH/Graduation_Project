package cn.wzbc.maintenance.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> instant;
    private final ZoneId zone;
    public MutableClock(Instant initial) { this(new AtomicReference<>(initial), ZoneOffset.UTC); }
    private MutableClock(AtomicReference<Instant> instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
    public void set(Instant value) { instant.set(java.util.Objects.requireNonNull(value)); }
    @Override public Instant instant() { return instant.get(); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
}
