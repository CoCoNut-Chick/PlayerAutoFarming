package com.autofarm.client.task;

/**
 * 自动收割任务的状态机。
 * 第二期开始移动/收割会依次用到 MOVING / HARVESTING / REPLANTING / TO_SUPPLY / TRANSFERRING。
 */
public enum FarmState {

    IDLE("autofarm.state.idle"),
    SCANNING("autofarm.state.scanning"),
    MOVING("autofarm.state.moving"),
    PICKUP("autofarm.state.pickup"),
    ATTACKING("autofarm.state.attacking"),
    RETURNING("autofarm.state.returning"),
    HARVESTING("autofarm.state.harvesting"),
    REPLANTING("autofarm.state.replanting"),
    TO_SUPPLY("autofarm.state.supply"),
    TRANSFERRING("autofarm.state.transfer"),
    PAUSED("autofarm.state.paused");

    private final String translationKey;

    FarmState(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
