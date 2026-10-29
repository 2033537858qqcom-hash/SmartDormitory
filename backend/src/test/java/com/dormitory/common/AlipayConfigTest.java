package com.dormitory.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlipayConfigTest {
    @Test
    void incompleteEnabledPaymentConfigurationIsDisabled() {
        AlipayConfig config = new AlipayConfig();
        config.setEnabled(true);
        config.setAppId("test-app");
        config.setPrivateKey("");
        config.init();
        assertFalse(config.isEnabled());
    }
}
