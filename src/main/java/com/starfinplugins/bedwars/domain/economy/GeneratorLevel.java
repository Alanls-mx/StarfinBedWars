package com.starfinplugins.bedwars.domain.economy;

public final class GeneratorLevel {
    private final int intervalTicks;
    private final int amount;
    private final int maximumDropped;

    public GeneratorLevel(int intervalTicks, int amount, int maximumDropped) {
        if (intervalTicks < 1 || intervalTicks > 72000 || amount < 1 || amount > 64
                || maximumDropped < amount || maximumDropped > 4096) {
            throw new IllegalArgumentException("Configuracao de nivel de gerador invalida.");
        }
        this.intervalTicks = intervalTicks;
        this.amount = amount;
        this.maximumDropped = maximumDropped;
    }

    public int getIntervalTicks() { return intervalTicks; }
    public int getAmount() { return amount; }
    public int getMaximumDropped() { return maximumDropped; }
}
